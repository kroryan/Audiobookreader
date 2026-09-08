package com.audiobookreader.data

/**
 * Keeps the document text untouched for display while making layout punctuation
 * predictable for the different TTS backends.
 */
object SpeechText {
    fun forOfflineTts(text: String): String = text
        // Some offline voices barely pause for typographic punctuation. An
        // ellipsis is a reliable pause marker without changing displayed text.
        .replace(Regex("\\.{3,}|…"), " … ")
        .replace(Regex("\\s*[\\u2013\\u2014]\\s*"), " … ")
        .replace(Regex("\\n{2,}"), " … ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()

    fun forEdgeTts(text: String): String = text
        .replace(Regex("\\.{3,}"), "…")
        .replace(Regex("\\n{2,}"), "\n\n")

    /**
     * PocketTTS performs its own tokenization and text preparation. Only
     * collapse layout whitespace here; punctuation and paragraph boundaries
     * must remain available to its sentence splitter.
     */
    fun forPocketTts(text: String): String = text
        .replace('\r', '\n')
        .replace(Regex("[ \\t]+"), " ")
        .trim()

    fun forSupertonicTts(text: String): String = text
        .replace('\r', '\n')
        .replace(Regex("[\\n\\t ]+"), " ")
        .replace(Regex("\\s*([–‑—])\\s*"), " - ")
        .replace(Regex("\\s+([,.;:!?])"), "$1")
        .trim()
}
