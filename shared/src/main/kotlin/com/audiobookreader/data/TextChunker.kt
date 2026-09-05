package com.audiobookreader.data

object TextChunker {
    private const val MIN_CHUNK_CHARS = 180

    // Shared by Android and desktop so paragraph boundaries and progress stay compatible.
    // Chunks are cut at sentence/word boundaries and retain paragraph spacing for both
    // display and speech. A short final piece is rebalanced instead of becoming a
    // fragment containing only a few words.
    fun split(text: String, maxChars: Int = 700): List<String> {
        require(maxChars >= MIN_CHUNK_CHARS) { "maxChars is too small" }
        val paragraphs = normalizeDocumentText(text).split("\n\n").filter(String::isNotBlank)
        val result = mutableListOf<String>()
        var current = ""

        paragraphs.forEach { paragraph ->
            val pieces = splitParagraph(paragraph, maxChars)
            pieces.forEachIndexed { index, piece ->
                val separator = if (current.isEmpty()) "" else if (index == 0) "\n\n" else " "
                if (current.isNotEmpty() && current.length + separator.length + piece.length > maxChars) {
                    result += current
                    current = piece
                } else {
                    current += separator + piece
                }
            }
        }
        if (current.isNotBlank()) result += current
        return result.ifEmpty { listOf(" ") }
    }

    /** Normalizes layout noise while retaining meaningful paragraph boundaries. */
    fun normalizeDocumentText(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            val value = current.toString().trim()
            if (value.isNotBlank()) paragraphs += value
            current.clear()
        }
        lines.forEach { rawLine ->
            val line = rawLine.replace('\u00A0', ' ').replace(Regex("[\\t\\f ]+"), " ").trim()
            if (line.isBlank()) {
                flush()
            } else if (current.isEmpty()) {
                current.append(line)
            } else if (current.endsWith("-") && line.firstOrNull()?.isLowerCase() == true) {
                current.deleteCharAt(current.lastIndex)
                current.append(line)
            } else {
                current.append(' ').append(line)
            }
        }
        flush()
        return paragraphs.joinToString("\n\n")
    }

    private fun splitParagraph(paragraph: String, maxChars: Int): List<String> {
        val sentences = paragraph.split(Regex("(?<=[.!?。！？…])\\s+"))
            .map(String::trim)
            .filter(String::isNotBlank)
        val pieces = mutableListOf<String>()
        var current = ""
        sentences.forEach { sentence ->
            if (sentence.length > maxChars) {
                if (current.isNotBlank()) pieces += current
                current = ""
                pieces += splitLongSentence(sentence, maxChars)
            } else if (current.isNotEmpty() && current.length + 1 + sentence.length > maxChars) {
                pieces += current
                current = sentence
            } else {
                current += if (current.isEmpty()) "" else " "
                current += sentence
            }
        }
        if (current.isNotBlank()) pieces += current
        return rebalanceShortTail(pieces, maxChars)
    }

    private fun splitLongSentence(sentence: String, maxChars: Int): List<String> {
        val words = sentence.split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var remaining = words
        while (remaining.isNotEmpty()) {
            if (remaining.joinToString(" ").length <= maxChars) {
                result += remaining.joinToString(" ")
                break
            }
            val groupCount = ((remaining.sumOf(String::length) + remaining.size - 1) + maxChars - 1) / maxChars
            val target = (((remaining.sumOf(String::length) + remaining.size - 1) + groupCount - 1) / groupCount)
                .coerceIn(MIN_CHUNK_CHARS, maxChars)
            var length = 0
            var count = 0
            while (count < remaining.size) {
                val next = remaining[count]
                val nextLength = if (count == 0) next.length else length + 1 + next.length
                if (count > 0 && nextLength > maxChars) break
                length = nextLength
                count++
                if (length >= target && remaining.size - count > 0) break
            }
            if (count == 0) count = 1
            result += remaining.take(count).joinToString(" ")
            remaining = remaining.drop(count)
        }
        return rebalanceShortTail(result, maxChars)
    }

    private fun rebalanceShortTail(pieces: List<String>, maxChars: Int): List<String> {
        if (pieces.size < 2 || pieces.last().length >= MIN_CHUNK_CHARS) return pieces
        val result = pieces.toMutableList()
        val previousWords = result[result.lastIndex - 1].split(' ').toMutableList()
        val tailWords = result.last().split(' ').toMutableList()
        while (previousWords.size > 1 && tailWords.joinToString(" ").length < MIN_CHUNK_CHARS) {
            val moved = previousWords.removeAt(previousWords.lastIndex)
            tailWords.add(0, moved)
            if (previousWords.joinToString(" ").length + 1 + tailWords.joinToString(" ").length > maxChars) {
                tailWords.removeAt(0)
                previousWords.add(moved)
                break
            }
        }
        result[result.lastIndex - 1] = previousWords.joinToString(" ")
        result[result.lastIndex] = tailWords.joinToString(" ")
        return result.filter(String::isNotBlank)
    }
}
