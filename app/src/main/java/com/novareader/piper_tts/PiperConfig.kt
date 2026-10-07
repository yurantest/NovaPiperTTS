package com.novareader.piper_tts

import org.json.JSONObject

data class PiperConfig(
    val sampleRate: Int,
    val noiseScale: Float,
    val lengthScale: Float,
    val noiseW: Float,
) {
    companion object {
        fun parse(text: String): PiperConfig {
            val root = JSONObject(text)
            val audio = root.optJSONObject("audio")
            val inference = root.optJSONObject("inference")
            return PiperConfig(
                sampleRate = audio?.optInt("sample_rate", 22050) ?: 22050,
                noiseScale = inference?.optDouble("noise_scale", 0.667)?.toFloat() ?: 0.667f,
                lengthScale = inference?.optDouble("length_scale", 1.0)?.toFloat() ?: 1.0f,
                noiseW = inference?.optDouble("noise_w", 0.8)?.toFloat() ?: 0.8f,
            )
        }
    }
}
