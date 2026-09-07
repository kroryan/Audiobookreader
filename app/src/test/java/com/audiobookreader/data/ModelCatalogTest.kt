package com.audiobookreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun coquiArchivesUseTheModelFilenamePublishedBySherpa() {
        val coqui = ModelCatalog.models.filter { it.family == ModelFamily.COQUI }

        assertTrue(coqui.isNotEmpty())
        assertTrue(coqui.all { it.archiveName.endsWith(".tar.bz2") })
        assertTrue(coqui.all { it.modelName == "model.onnx" })
        assertTrue(coqui.all { it.dataDir.isBlank() })
    }

    @Test
    fun mimic3ArchivesKeepTheirDirectoryBasedModelFilename() {
        val mimic3 = ModelCatalog.models.filter { it.family == ModelFamily.MIMIC3 }

        assertTrue(mimic3.isNotEmpty())
        mimic3.forEach { model ->
            val directory = model.archiveName.substringAfterLast('/').removeSuffix(".tar.bz2")
            val suffix = directory.removePrefix("vits-mimic3-")
            assertEquals("$suffix.onnx", model.modelName)
            assertEquals("espeak-ng-data", model.dataDir)
        }
    }

    @Test
    fun kokoroV10ExposesTheOfficialVoiceOrderAndSpanishLanguageCodes() {
        val voices = ModelCatalog.kokoroVoices

        assertEquals(54, voices.size)
        assertEquals("ef_dora", voices.first { it.language == "es" }.id)
        assertEquals(listOf("ef_dora", "em_alex", "em_santa"), voices.filter { it.language == "es" }.map { it.id })
        assertEquals("es", ModelCatalog.kokoroVoices[28].language)
        assertEquals("es", ModelCatalog.kokoroVoices[29].language)
        assertEquals(53, ModelCatalog.kokoroVoices[53].speakerId)
        assertEquals(true, ModelCatalog.kokoroVoices[53].available)
        assertEquals("es", ModelCatalog.kokoroLanguage(28).take(2))
        assertEquals("es", ModelCatalog.kokoroLanguage(29))
        assertEquals("es", ModelCatalog.kokoroLanguage(53))
    }

    @Test
    fun kokoroPackageIsAvailableForEveryLanguageFilter() {
        val packageSpec = ModelCatalog.models.first { it.id == "kokoro-multi-v1-0" }
        assertEquals("all", packageSpec.language)
        assertTrue(packageSpec.archiveName.endsWith("kokoro-multi-lang-v1_0-em-santa.tar.bz2"))
        assertEquals("Apache-2.0", packageSpec.licenseSpdx)
    }

    @Test
    fun multilingualAndCloningFamiliesDeclareTheirInstallRequirements() {
        val supertonic = ModelCatalog.models.filter { it.family == ModelFamily.SUPERTONIC }
        assertEquals(31, supertonic.size)
        assertTrue(supertonic.map { it.language }.containsAll(listOf("en", "es", "ja")))
        assertEquals(1, supertonic.map { it.storageId }.distinct().size)
        assertTrue(supertonic.all { it.requiredFiles.contains("voice.bin") && it.requiresAcceptance })

        val pocket = ModelCatalog.models.first { it.family == ModelFamily.POCKET }
        assertEquals("en", pocket.language)
        assertTrue(pocket.referenceAudioRequired)
        assertTrue(pocket.requiredFiles.contains("text_conditioner.onnx"))

        val zipVoice = ModelCatalog.models.first { it.family == ModelFamily.ZIPVOICE }
        assertTrue(zipVoice.referenceAudioRequired)
        assertTrue(zipVoice.referenceTextRequired)
        assertTrue(zipVoice.auxiliaryName.endsWith(".onnx"))
        assertTrue(zipVoice.requiredFiles.contains("decoder.int8.onnx"))
    }
}
