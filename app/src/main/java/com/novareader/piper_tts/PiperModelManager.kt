package com.novareader.piper_tts

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PiperModelManager(private val context: Context) {
    private val root = File(context.filesDir, "piper/voices").apply { mkdirs() }

    fun directory(v: PiperVoice) = File(root, v.key).apply { mkdirs() }
    fun modelFile(v: PiperVoice) = File(directory(v), "${v.key}.onnx")
    fun tokensFile(v: PiperVoice) = File(directory(v), "tokens.txt")

    fun isInstalled(v: PiperVoice): Boolean =
        modelFile(v).isFile && modelFile(v).length() > 100_000 && tokensFile(v).isFile

    /** Отмена пользователем: частичный файл удаляется. */
    class DownloadCancelled : RuntimeException("cancelled")

    fun ensureInstalled(
        v: PiperVoice,
        progress: ((Long, Long) -> Unit)? = null,
        cancelled: () -> Boolean = { false },
    ) {
        if (isInstalled(v)) return
        directory(v).mkdirs()
        if (!tokensFile(v).isFile || tokensFile(v).length() == 0L) {
            download(v.tokensUrl, tokensFile(v), null, cancelled)
        }
        if (!modelFile(v).isFile || modelFile(v).length() < 100_000L) {
            download(v.modelUrl, modelFile(v), progress, cancelled)
        }
        check(isInstalled(v)) { "Voice not installed after download: ${v.key}" }
    }

    fun delete(v: PiperVoice) { directory(v).deleteRecursively() }

    /** Удаляет недокачанные .part (при отмене). */
    fun clearPartial(v: PiperVoice) {
        directory(v).listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /** До 4 попыток; докачка с места обрыва (Range), .part сохраняется между попытками и запусками. */
    private fun download(url: String, target: File, progress: ((Long, Long) -> Unit)?, cancelled: () -> Boolean) {
        var attempt = 0
        while (true) {
            try { downloadOnce(url, target, progress, cancelled); return }
            catch (e: DownloadCancelled) { throw e }
            catch (e: java.io.IOException) {
                if (cancelled()) throw DownloadCancelled()
                if (++attempt >= 4) throw e
                FileLogger.log("download retry $attempt for ${target.name}: ${e.message}")
                try { Thread.sleep(2000L * attempt) } catch (_: InterruptedException) { throw DownloadCancelled() }
            }
        }
    }

    private fun downloadOnce(url: String, target: File, progress: ((Long, Long) -> Unit)?, cancelled: () -> Boolean) {
        val part = File(target.parentFile, target.name + ".part")
        val have = if (part.isFile) part.length() else 0L
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "NovaPiperTTS")
        if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
        c.connectTimeout = 15000
        c.readTimeout = 60000
        try {
            val code = c.responseCode
            if (code == 416) { part.delete(); throw java.io.IOException("range rejected, restarting") }
            if (code !in 200..299) throw java.io.IOException("HTTP $code for $url")
            val resumed = code == 206
            val total = if (resumed)
                c.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
            else c.contentLengthLong
            var done = if (resumed) have else 0L
            BufferedInputStream(c.inputStream, 64 * 1024).use { input ->
                FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw DownloadCancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        progress?.invoke(done, total)
                    }
                }
            }
            if (total > 0 && part.length() != total) throw java.io.IOException("incomplete: ${part.length()} of $total")
            check(part.renameTo(target)) { "Cannot rename ${target.name}" }
        } finally {
            c.disconnect()
        }
    }
}
