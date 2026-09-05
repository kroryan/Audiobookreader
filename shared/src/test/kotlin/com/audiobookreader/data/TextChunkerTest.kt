package com.audiobookreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    @Test
    fun normalizesLayoutWithoutFlatteningParagraphs() {
        val normalized = TextChunker.normalizeDocumentText("Primera línea\nsegunda línea.\n\n  Nuevo párrafo. ")

        assertEquals("Primera línea segunda línea.\n\nNuevo párrafo.", normalized)
    }

    @Test
    fun keepsWordsTogetherAndRebalancesShortTail() {
        val source = (1..180).joinToString(" ") { "palabra$it" }
        val chunks = TextChunker.split(source, maxChars = 700)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 700 })
        assertTrue(chunks.dropLast(1).all { it.length >= 180 })
        assertTrue(chunks.all { !it.startsWith("bra") && !it.endsWith("abra") })
        assertFalse(chunks.any { it.contains("palab ra") })
        assertEquals(source, chunks.joinToString(" ").replace("\n\n", " "))
    }

    @Test
    fun keepsSeparatePdfParagraphsAsSeparateBlocks() {
        val normalized = TextChunker.normalizeDocumentText("Diálogo primero.\n\n— Acotación después.")

        assertEquals("Diálogo primero.\n\n— Acotación después.", normalized)
        assertEquals(2, TextChunker.split(normalized, maxChars = 700).single().split("\n\n").size)
    }

    @Test
    fun convertsEllipsisAndLongDashesForOfflineSpeech() {
        val speech = SpeechText.forOfflineTts("Espera... — dijo ella.\n\nLuego continuó.")

        assertTrue(speech.contains("…"))
        assertFalse(speech.contains("—"))
        assertFalse(speech.contains("..."))
        assertFalse(speech.contains("\n\n"))
    }
}
