package com.novareader.piper_tts

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** В каталоге Piper пола нет — используем ручной список дикторов (assets/voice_genders.json). */
object VoiceGenders {
    enum class Gender { FEMALE, MALE }

    @Volatile private var map: Map<String, Gender>? = null

    fun of(context: Context, speaker: String): Gender? {
        val m = map ?: load(context).also { map = it }
        return m[speaker.lowercase(Locale.ROOT)]
    }

    private fun load(context: Context): Map<String, Gender> = try {
        val root = JSONObject(context.assets.open("voice_genders.json").bufferedReader().use { it.readText() })
        val out = HashMap<String, Gender>()
        root.optJSONArray("female")?.let { a -> for (i in 0 until a.length()) out[a.getString(i).lowercase(Locale.ROOT)] = Gender.FEMALE }
        root.optJSONArray("male")?.let { a -> for (i in 0 until a.length()) out[a.getString(i).lowercase(Locale.ROOT)] = Gender.MALE }
        out
    } catch (e: Throwable) {
        FileLogger.error("voice_genders.json", e); emptyMap()
    }
}
