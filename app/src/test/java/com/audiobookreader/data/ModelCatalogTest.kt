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
}
