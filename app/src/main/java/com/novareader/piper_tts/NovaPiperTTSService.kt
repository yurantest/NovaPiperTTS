package com.novareader.piper_tts

import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import java.util.Locale
import java.util.MissingResourceException

/** Android TTS bridge. Only explicitly selected concrete Piper voices are supported. */
class NovaPiperTTSService : TextToSpeechService() {
    private val repo by lazy { PiperVoiceRepository(this) }
    private val modelManager by lazy { PiperModelManager(this) }
    private val synthesizer by lazy { PiperSynthesizer(applicationContext) }
    @Volatile private var cancelled = false
    private var currentLanguage: Locale? = null

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        FileLogger.log("NovaPiperTTSService создан")
    }

    private fun selectedVoices(): List<PiperVoice> {
        val selected = repo.selected()
        if (selected.isEmpty()) return emptyList()
        return repo.loadCached().filter { it.key in selected }
    }

    override fun onGetVoices(): MutableList<Voice> = try {
        val list = selectedVoices()
        FileLogger.log("onGetVoices: selected=${list.size}")
        list.mapNotNull { v ->
            val key = v.ttsKey() ?: return@mapNotNull null
            FileLogger.log("onGetVoices: ${v.key} -> $key")
            Voice(key, v.androidLocale(), quality(v), Voice.LATENCY_NORMAL, false, emptySet())
        }.toMutableList().also {
            FileLogger.log("onGetVoices: total ${it.size}")
        }
    } catch (e: Throwable) {
        FileLogger.error("onGetVoices", e)
        mutableListOf()
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        if (voiceName.isNullOrBlank()) return TextToSpeech.ERROR
        val ok = selectedVoices().any { it.hasVoiceName(voiceName) }
        FileLogger.log("onIsValidVoiceName: requested=$voiceName result=$ok")
        return if (ok) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }

    override fun onLoadVoice(voiceName: String?): Int {
        val voice = selectedVoices().firstOrNull { it.hasVoiceName(voiceName) }
        if (voice == null) {
            val available = selectedVoices().joinToString { it.ttsKey() ?: "null" }
            FileLogger.log("onLoadVoice: requested=$voiceName NOT FOUND. available=[$available]")
            return TextToSpeech.ERROR
        }
        currentLanguage = voice.androidLocale()
        FileLogger.log("onLoadVoice: requested=$voiceName OK -> ${voice.key}")
        return TextToSpeech.SUCCESS
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        if (variant.isNullOrBlank()) return null
        val voice = selectedVoices().firstOrNull { matches(it, lang, country, variant) }
        val result = voice?.ttsKey()
        FileLogger.log("onGetDefaultVoiceNameFor: lang=$lang country=$country variant=$variant result=$result")
        return result
    }

    override fun onGetLanguage(): Array<String> {
        val locale = currentLanguage ?: return arrayOf("", "", "")
        return arrayOf(iso3Lang(locale), iso3Country(locale), locale.variant ?: "")
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang.isNullOrBlank() || variant.isNullOrBlank()) return TextToSpeech.LANG_NOT_SUPPORTED
        val ok = selectedVoices().any { matches(it, lang, country, variant) }
        FileLogger.log("onIsLanguageAvailable: lang=$lang country=$country variant=$variant result=$ok")
        return if (ok) TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        if (lang.isNullOrBlank() || variant.isNullOrBlank()) return TextToSpeech.LANG_NOT_SUPPORTED
        val voice = selectedVoices().firstOrNull { matches(it, lang, country, variant) }
            ?: return TextToSpeech.LANG_NOT_SUPPORTED
        currentLanguage = voice.androidLocale()
        FileLogger.log("onLoadLanguage: lang=$lang country=$country variant=$variant OK -> ${voice.key}")
        return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        FileLogger.log(
            "onSynthesizeText ENTER: voiceName=${request.voiceName}, " +
                    "lang=${request.language}, country=${request.country}, variant=${request.variant}, " +
                    "textLen=${request.charSequenceText?.length}"
        )
        val voice = resolveVoice(request)
        if (voice == null) {
            FileLogger.log("Synthesis отклонён: voice не определён")
            callback.error()
            return
        }
        FileLogger.log("onSynthesizeText: resolved=${voice.key}")

        cancelled = false
        try {
            val text = request.charSequenceText?.toString().orEmpty()
            if (text.isBlank()) {
                callback.start(22050, android.media.AudioFormat.ENCODING_PCM_16BIT, 1)
                callback.done()
            } else {
                val rate = (request.params?.getInt("rate", request.speechRate) ?: request.speechRate).coerceIn(25, 400)
                synthesizer.synthesize(voice, modelManager, text, rate, callback) { cancelled }
            }
        } catch (e: Throwable) {
            FileLogger.error("synth FAILED", e)
            try { callback.error() } catch (_: Throwable) {}
        }
    }

    override fun onStop() {
        FileLogger.log("onStop")
        cancelled = true
    }

    override fun onDestroy() {
        FileLogger.log("NovaPiperTTSService.onDestroy")
        cancelled = true
        synthesizer.release()
        super.onDestroy()
    }

    private fun resolveVoice(request: SynthesisRequest): PiperVoice? {
        val voices = selectedVoices()
        if (voices.isEmpty()) return null
        request.voiceName?.takeIf { it.isNotBlank() }?.let { requested ->
            voices.firstOrNull { it.hasVoiceName(requested) }?.let { return it }
        }
        val variant = request.variant?.takeIf { it.isNotBlank() } ?: return null
        return voices.firstOrNull { matches(it, request.language, request.country, variant) }
    }

    private fun matches(v: PiperVoice, lang: String?, country: String?, variant: String): Boolean =
        v.matchesTag(lang, country, variant)

    private fun quality(v: PiperVoice): Int = when (v.quality.lowercase(Locale.ROOT)) {
        "high" -> Voice.QUALITY_HIGH
        "low", "x_low" -> Voice.QUALITY_LOW
        else -> Voice.QUALITY_NORMAL
    }

    private fun iso3Lang(locale: Locale): String = try { locale.isO3Language }
    catch (_: MissingResourceException) { locale.language }

    private fun iso3Country(locale: Locale): String = try {
        if (locale.country.isEmpty()) "" else locale.isO3Country
    } catch (_: MissingResourceException) { locale.country }
}