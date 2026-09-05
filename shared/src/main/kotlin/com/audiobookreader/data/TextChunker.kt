package com.audiobookreader.data

object TextChunker {
    // Shared by Android and desktop so paragraph boundaries and progress stay compatible.
    fun split(text: String, maxChars: Int = 700): List<String> {
        val sentences = text.replace(Regex("\\s+"), " ").trim()
            .split(Regex("(?<=[.!?。！？])\\s+"))
        val result = mutableListOf<String>()
        var current = StringBuilder()
        sentences.filter { it.isNotBlank() }.forEach { sentence ->
            if (sentence.length > maxChars) {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current = StringBuilder()
                }
                sentence.chunked(maxChars).forEach { part -> result += part }
            } else if (current.length + sentence.length + 1 > maxChars && current.isNotEmpty()) {
                result += current.toString()
                current = StringBuilder()
                current.append(sentence)
            } else {
                current.append(if (current.isEmpty()) "" else " ").append(sentence)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result.ifEmpty { listOf(" ") }
    }
}
