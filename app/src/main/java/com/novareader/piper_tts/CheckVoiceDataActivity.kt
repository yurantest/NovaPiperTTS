package com.novareader.piper_tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.ArrayList

/**
 * Android Settings вызывает эту Activity, чтобы получить список голосов движка.
 * Возвращаем Piper-голоса через PiperVoice.ttsKey() — единый формат
 * iso3Lang-iso3Country-variant, который затем приходит назад в onLoadVoice.
 *
 * Никаких падений: любое исключение внутри -> отдаём пустой список,
 * но Settings всё равно получает CHECK_VOICE_DATA_PASS.
 */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FileLogger.init(this)
        FileLogger.log("CheckVoiceDataActivity: started")

        val available = ArrayList<String>()
        val seen = HashSet<String>()

        try {
            val repo = PiperVoiceRepository(this)
            val selected = repo.selected()
            val all = repo.loadCached()

            // Если пользователь ничего не отметил — движок честно говорит "нет голосов".
            // Если отметил — возвращаем только выбранные.
            val voices = if (selected.isEmpty()) emptyList()
            else all.filter { it.key in selected }

            for (voice in voices) {
                val key = voice.ttsKey() ?: continue
                if (seen.add(key)) available.add(key)
            }
        } catch (e: Throwable) {
            FileLogger.error("CheckVoiceData: failed to build voice list", e)
        }

        FileLogger.log("CheckVoiceData: returning ${available.size} voices")

        val result = Intent().apply {
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList())
        }
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
        finish()
    }
}