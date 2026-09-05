package com.audiobookreader.data

object TextChunker {
    fun split(text: String, maxChars: Int = 1800): List<String> {
        val sentences = text.replace(Regex("\\s+"), " ").trim()
            .split(Regex("(?<=[.!?。！？])\\s+"))
        val result = mutableListOf<String>()
        var current = StringBuilder()
        sentences.filter { it.isNotBlank() }.forEach { sentence ->
            if (current.length + sentence.length + 1 > maxChars && current.isNotEmpty()) {
                result += current.toString()
                current = StringBuilder()
            }
            current.append(if (current.isEmpty()) "" else " ").append(sentence)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result.ifEmpty { listOf(" ") }
    }
}
