package com.novareader.piper_tts

import java.util.Locale

data class PiperVoice(
    val repoId: String,      // "csukuangfj/vits-piper-ru_RU-irina-medium"
    val key: String,         // "ru_RU-irina-medium"
    val name: String,        // "irina"
    val locale: String,      // "ru_RU"
    val quality: String,     // "medium"
) {
    val friendlyName: String
        get() = name.replace('_', ' ').replaceFirstChar { it.uppercase() } + " · " + quality

    val modelUrl: String
        get() = "https://huggingface.co/$repoId/resolve/main/$key.onnx"

    val tokensUrl: String
        get() = "https://huggingface.co/$repoId/resolve/main/tokens.txt"

    /** Язык/страна, приведённые к тому, что реально знает Android (ISO-639 / ISO-3166). */
    private val langCode: String
        get() {
            val l = locale.replace('-', '_').substringBefore('_').lowercase(Locale.ROOT)
            return if (l in ISO_LANGS) l else l
        }

    private val countryCode: String
        get() {
            val c = locale.replace('-', '_').substringAfter('_', "").uppercase(Locale.ROOT)
            if (c in ISO_COUNTRIES) return c
            // "fa", "fa_en", "es_419" и т.п.: Android-настройки разбирают тег "lang-COUNTRY-variant"
            // и падают, если страны нет, поэтому подставляем страну по умолчанию.
            return DEFAULT_COUNTRY[langCode] ?: langCode.uppercase(Locale.ROOT).takeIf { it in ISO_COUNTRIES } ?: "US"
        }

    /**
     * Уникальный variant: только буквы/цифры, без '_' и '-'. Системные Настройки разбирают тег
     * "lang-COUNTRY-variant" и режут variant по разделителям — иначе в списке языков появляется
     * пустая запись и окно падает с NullPointerException (ListPreference.findIndexOfValue).
     */
    private val variant: String
        get() = camel(name) + camel(quality)

    private fun camel(src: String): String =
        src.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
            .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

    /** Старый формат variant (без качества) — Настройки Android могли его запомнить раньше. */
    private val legacyVariant: String
        get() = name.replace('-', '_').replace(Regex("[^A-Za-z0-9_]"), "_")

    /** Промежуточный формат (name_quality) из предыдущей сборки. */
    private val legacyVariant2: String
        get() = (name + "_" + quality).replace('-', '_').replace(Regex("[^A-Za-z0-9_]"), "_")

    /** Совпадает ли имя голоса (в т.ч. старого формата без качества). */
    fun hasVoiceName(n: String?): Boolean =
        !n.isNullOrBlank() && (ttsKey().equals(n, true) ||
            ttsKey().removeSuffix(variant).plus(legacyVariant).equals(n, true) ||
            ttsKey().removeSuffix(variant).plus(legacyVariant2).equals(n, true))

    /** Совпадение с тегом, который присылает система: lang/country/variant (ISO-3). */
    fun matchesTag(lang: String?, country: String?, variantIn: String?): Boolean {
        if (variantIn.isNullOrBlank()) return false
        return iso3Lang().equals(lang, true) &&
            iso3Country().equals(country ?: "", true) &&
            (variant.equals(variantIn, true) || legacyVariant.equals(variantIn, true) ||
                legacyVariant2.equals(variantIn, true))
    }

    private fun iso3Lang(): String =
        try { Locale(langCode).isO3Language } catch (_: Throwable) { langCode }

    private fun iso3Country(): String =
        try { Locale("", countryCode).isO3Country } catch (_: Throwable) { "" }

    fun ttsKey(): String = buildString {
        append(iso3Lang())
        val c = iso3Country()
        if (c.isNotEmpty()) append('-').append(c)
        append('-').append(variant)
    }

    /** Locale в формате, который ожидает TextToSpeechService: ISO-3 язык/страна + variant. */
    fun androidLocale(): Locale = Locale(iso3Lang(), iso3Country(), variant)

    /** Обычная locale (2-буквенная) — для отображения в UI. */
    fun displayLocale(): Locale = Locale(langCode, countryCode)

    companion object {
        private val DEFAULT_COUNTRY = mapOf("fa" to "IR", "ar" to "SA", "en" to "US", "es" to "ES", "pt" to "PT",
            "zh" to "CN", "sw" to "KE", "ne" to "NP", "hi" to "IN", "uk" to "UA", "sr" to "RS", "ca" to "ES")
        private val ISO_LANGS: Set<String> = Locale.getISOLanguages().toHashSet()
        private val ISO_COUNTRIES: Set<String> = Locale.getISOCountries().toHashSet()

        /** Квантизованные копии (fp16/int8) грузим не будем: файл модели у них назван иначе. */
        fun isUnsupportedVariant(id: String) =
            id.endsWith("-fp16") || id.endsWith("-int8")

        /** Парсит id вида "csukuangfj/vits-piper-ru_RU-irina-medium". */
        fun fromRepoId(id: String): PiperVoice? {
            if (isUnsupportedVariant(id)) return null
            val short = id.substringAfter('/')      // vits-piper-ru_RU-irina-medium
            if (!short.startsWith("vits-piper-")) return null
            val rest = short.removePrefix("vits-piper-")   // ru_RU-irina-medium
            val parts = rest.split('-')
            if (parts.size < 2) return null
            val locale = parts[0]
            val quality = parts.last()
            val voiceName = parts.drop(1).dropLast(1).joinToString("-")
            return PiperVoice(
                repoId = id,
                key = rest,
                name = voiceName,
                locale = locale,
                quality = quality
            )
        }
    }
}