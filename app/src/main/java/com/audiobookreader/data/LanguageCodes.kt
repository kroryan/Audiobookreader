package com.audiobookreader.data

/** Canonicalizes ISO 639-1/639-2/639-3 codes used by model catalogs. */
object LanguageCodes {
    private val iso6393To1 = mapOf(
        "afr" to "af", "ara" to "ar", "cat" to "ca", "ces" to "cs", "cym" to "cy",
        "dan" to "da", "deu" to "de", "ell" to "el", "eng" to "en", "spa" to "es",
        "eus" to "eu", "fas" to "fa", "fin" to "fi", "fra" to "fr", "hin" to "hi",
        "hrv" to "hr", "hun" to "hu", "ind" to "id", "isl" to "is", "ita" to "it",
        "kat" to "ka", "kaz" to "kk", "kur" to "ku", "ltz" to "lb", "lav" to "lv",
        "nep" to "ne", "nld" to "nl", "nor" to "no", "pol" to "pl", "por" to "pt",
        "ron" to "ro", "rus" to "ru", "slk" to "sk", "slv" to "sl", "sqi" to "sq",
        "srp" to "sr", "swe" to "sv", "swa" to "sw", "tur" to "tr", "ukr" to "uk",
        "urd" to "ur", "vie" to "vi", "zho" to "zh",
    )

    fun normalize(code: String): String {
        val clean = code.trim().lowercase().replace('_', '-').substringBefore('-')
        return iso6393To1[clean] ?: clean
    }
}
