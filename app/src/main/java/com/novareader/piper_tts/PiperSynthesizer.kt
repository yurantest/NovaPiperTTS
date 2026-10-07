package com.novareader.piper_tts

import android.content.Context
import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/**
 * Синтез через sherpa-onnx. Вызов БЛОКИРУЮЩИЙ: SynthesisCallback можно использовать только
 * пока выполняется onSynthesizeText — иначе система считает синтез законченным.
 */
class PiperSynthesizer(private val context: Context) {
    private var loadedKey: String? = null
    private var tts: OfflineTts? = null

    @Synchronized
    fun synthesize(
        voice: PiperVoice, manager: PiperModelManager, text: String,
        ratePercent: Int, callback: SynthesisCallback,
        cancelled: () -> Boolean = { false },
    ) {
        try {
            manager.ensureInstalled(voice)
            val engine = obtain(voice, manager)
            val sr = engine.sampleRate()
            if (callback.start(sr, AudioFormat.ENCODING_PCM_16BIT, 1) != 0) return
            val speed = ratePercent.coerceIn(25, 400) / 100.0f
            val max = (callback.maxBufferSize.coerceAtLeast(2) and 1.inv()).coerceAtLeast(2)
            val parts = splitSentences(text)
            FileLogger.log("synth: ${parts.size} part(s), sr=$sr, speed=$speed")
            var failed = false
            var total = 0
            for ((i, part) in parts.withIndex()) {
                if (cancelled() || failed) break
                FileLogger.log("synth: generate #$i len=${part.length}")
                // Без JNI-колбэка: generate() возвращает готовый кусок, дальше отдаём его системе сами.
                val audio = engine.generate(part, 0, speed)
                val samples = audio.samples
                FileLogger.log("synth: #$i -> ${samples.size} samples")
                if (samples.isEmpty()) continue
                val pcm = toPcm16(samples)
                var off = 0
                while (off < pcm.size) {
                    if (cancelled()) break
                    val n = minOf(max, pcm.size - off)
                    if (callback.audioAvailable(pcm, off, n) != 0) { failed = true; break }
                    off += n
                }
                total += samples.size
            }
            if (total > 0 && !failed && !cancelled()) {
                val tail = ByteArray((sr * 0.2).toInt() * 2)   // 200 мс тишины, чтобы плеер не обрезал хвост
                var o = 0
                while (o < tail.size) {
                    val n = minOf(max, tail.size - o)
                    if (callback.audioAvailable(tail, o, n) != 0) { failed = true; break }
                    o += n
                }
            }
            FileLogger.log("synth: finished total=$total samples, cancelled=${cancelled()}, failed=$failed")
            if (total == 0 && !cancelled()) { callback.error(); return }
            if (!failed && !cancelled()) callback.done()
        } catch (e: Throwable) {
            FileLogger.error("synth failed", e)
            drop()
            try { callback.error() } catch (_: Throwable) {}
        }
    }

    private fun obtain(voice: PiperVoice, manager: PiperModelManager): OfflineTts {
        if (loadedKey == voice.key) tts?.let { return it }
        drop()
        val dataDir = EspeakAssets.ensure(context)
        val vits = OfflineTtsVitsModelConfig().apply {
            model = manager.modelFile(voice).absolutePath
            tokens = manager.tokensFile(voice).absolutePath
            this.dataDir = dataDir.absolutePath
        }
        val cfg = OfflineTtsConfig().apply {
            model = OfflineTtsModelConfig().apply {
                this.vits = vits
                numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
                debug = false; provider = "cpu"
            }
            maxNumSentences = 1; silenceScale = 0.2f
        }
        val t = OfflineTts(null, cfg)   // null: пути к файлам, не к assets
        tts = t; loadedKey = voice.key
        FileLogger.log("OfflineTts loaded ${voice.key} sr=${t.sampleRate()}")
        return t
    }

    @Synchronized fun release() = drop()
    @Synchronized fun forget(key: String) { if (loadedKey == key) drop() }

    private fun drop() {
        runCatching { tts?.release() }
        tts = null; loadedKey = null
    }

    /** Делим на предложения (≤ ~250 символов), чтобы звук начинался быстро и отмена срабатывала между ними. */
    private fun splitSentences(text: String): List<String> {
        val out = ArrayList<String>()
        for (line in text.split(Regex("[\\r\\n]+"))) {
            for (sent in line.trim().split(Regex("(?<=[.!?…])\\s+"))) {
                var t = sent.trim()
                while (t.length > 250) {
                    var cut = t.lastIndexOfAny(charArrayOf(',', ';', ':', ' '), 250)
                    if (cut < 50) cut = 250
                    out.add(t.substring(0, cut + 1).trim()); t = t.substring(cut + 1).trim()
                }
                if (t.any { it.isLetterOrDigit() }) {
                    // Без знака в конце Piper «съедает» последний звук (тигр -> «ти.р» глухо).
                    if (t.last() !in ".!?…,;:") t += "."
                    out.add(t)
                }
            }
        }
        return out
    }

    private fun toPcm16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2); var p = 0
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
            out[p++] = (v and 0xff).toByte(); out[p++] = ((v ushr 8) and 0xff).toByte()
        }
        return out
    }
}
