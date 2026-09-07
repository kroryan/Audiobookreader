package com.audiobookreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LanguageCodesTest {
    @Test
    fun importUsesIso6392AndStoresTheCanonicalCode() {
        assertEquals("es", LanguageCodes.normalizeImportCode("spa"))
        assertEquals("en", LanguageCodes.normalizeImportCode("ENG"))
    }

    @Test
    fun importRejectsTwoLetterCodes() {
        assertThrows(IllegalArgumentException::class.java) {
            LanguageCodes.normalizeImportCode("es")
        }
    }
}
