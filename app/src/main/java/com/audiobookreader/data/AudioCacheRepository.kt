package com.audiobookreader.data

import android.content.Context
import java.io.File

data class AudioCacheStatus(
    val bookId: String,
    val modelId: String,
    val generatedChunks: Int,
    val totalChunks: Int,
    val bytes: Long,
) {
    val percentage: Int
        get() = if (totalChunks == 0) 0 else (generatedChunks * 100 / totalChunks).coerceIn(0, 100)
    val sizeLabel: String
        get() = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "${bytes / (1024 * 1024)} MB"
}

class AudioCacheRepository(context: Context) {
    private val root = File(context.cacheDir, "audio")

    fun status(book: Book, model: TtsModelSpec): AudioCacheStatus {
        val expected = book.chapters.sumOf { TextChunker.split(it.text).size }
        val directory = File(root, "${book.id}/${model.id}")
        val generated = directory.listFiles()?.count { it.extension == "wav" } ?: 0
        val bytes = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return AudioCacheStatus(book.id, model.id, generated, expected, bytes)
    }

    fun readyChunks(bookId: String, modelId: String): Set<Int> =
        File(root, "$bookId/$modelId").listFiles().orEmpty()
            .filter { it.extension == "wav" && it.length() > 44L }
            .mapNotNull { it.nameWithoutExtension.substringAfterLast('-').toIntOrNull() }
            .toSet()

    fun filesThrough(bookId: String, modelId: String, lastIndex: Int): List<String>? {
        if (lastIndex < 0) return emptyList()
        val files = File(root, "$bookId/$modelId").listFiles().orEmpty()
        return (0..lastIndex).map { index ->
            files.firstOrNull { it.name.endsWith("-$index.wav") && it.length() > 44L }
                ?: return null
        }.map(File::getAbsolutePath)
    }

    fun durationMs(bookId: String, modelId: String, index: Int): Long {
        val file = File(root, "$bookId/$modelId").listFiles().orEmpty()
            .firstOrNull { it.name.endsWith("-$index.wav") && it.length() > 44L }
            ?: return 0L
        return com.audiobookreader.playback.WavFile.durationMs(file)
    }

    fun clearModel(bookId: String, modelId: String) {
        File(root, "$bookId/$modelId").deleteRecursively()
    }

    fun clearBook(bookId: String) { File(root, bookId).deleteRecursively() }

    fun clearFrom(bookId: String, modelId: String, firstChunk: Int) {
        val directory = File(root, "$bookId/$modelId")
        directory.listFiles().orEmpty()
            .filter { file ->
                val isTemporary = file.name.endsWith(".wav.part")
                val index = file.nameWithoutExtension.substringBeforeLast(".wav").substringAfterLast('-').toIntOrNull()
                (isTemporary || file.extension == "wav") && index?.let { it >= firstChunk } == true
            }
            .forEach(File::delete)
    }

    fun clearTemporary(bookId: String, modelId: String) {
        File(root, "$bookId/$modelId").listFiles().orEmpty()
            .filter { it.name.endsWith(".wav.part") }
            .forEach(File::delete)
    }

    fun clearAll() { root.deleteRecursively(); root.mkdirs() }

    fun canWriteMore(bytesToAdd: Long): Boolean {
        val current = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return current + bytesToAdd <= MAX_BYTES
    }

    companion object { const val MAX_BYTES = 512L * 1024L * 1024L }
}
