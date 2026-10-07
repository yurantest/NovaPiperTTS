package com.novareader.piper_tts

import android.content.Context

class PiperVoiceRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("piper_voices", Context.MODE_PRIVATE)

    fun loadCached(): List<PiperVoice> = parseAssets()

    fun selected(): Set<String> =
        prefs.getStringSet("selected", null)?.toSet() ?: emptySet()

    fun setSelected(key: String, enabled: Boolean) {
        val s = selected().toMutableSet()
        if (enabled) s.add(key) else s.remove(key)
        prefs.edit().putStringSet("selected", s).apply()
    }

    // Оставлено для совместимости с MainActivity.load(force=true)
    suspend fun refresh(): List<PiperVoice> = parseAssets()

    private fun parseAssets(): List<PiperVoice> {
        return try {
            val json = context.assets.open("voices.json").bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(json)
            val out = ArrayList<PiperVoice>(arr.length())
            for (i in 0 until arr.length()) {
                val id = arr.optString(i, "")
                if (id.isBlank()) continue
                PiperVoice.fromRepoId(id)?.let(out::add)
            }
            out.sortedWith(compareBy({ it.locale }, { it.name }, { qualityRank(it.quality) }))
        } catch (e: Throwable) {
            FileLogger.error("parseAssets failed", e)
            emptyList()
        }
    }

    private fun qualityRank(q: String) = when (q.lowercase()) {
        "x_low" -> 0; "low" -> 1; "medium" -> 2; "high" -> 3; else -> 4
    }
}