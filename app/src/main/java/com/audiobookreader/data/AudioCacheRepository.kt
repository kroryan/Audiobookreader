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
        val generated = directory.listFiles()?.count { it.isAudioChunk() } ?: 0
        val bytes = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return AudioCacheStatus(book.id, model.id, generated, expected, bytes)
    }

    fun readyChunks(bookId: String, modelId: String): Set<Int> =
        File(root, "$bookId/$modelId").listFiles().orEmpty()
            .filter { it.isAudioChunk() && it.length() > MIN_AUDIO_BYTES }
            .mapNotNull { it.nameWithoutExtension.substringAfterLast('-').toIntOrNull() }
            .toSet()

    fun filesThrough(bookId: String, modelId: String, lastIndex: Int): List<String>? {
        if (lastIndex < 0) return emptyList()
        val files = File(root, "$bookId/$modelId").listFiles().orEmpty()
        return (0..lastIndex).map { index ->
            files.firstOrNull { it.isAudioChunk(index) && it.length() > MIN_AUDIO_BYTES }
                ?: return null
        }.map(File::getAbsolutePath)
    }

    fun durationMs(bookId: String, modelId: String, index: Int): Long {
        val file = File(root, "$bookId/$modelId").listFiles().orEmpty()
            .firstOrNull { it.isAudioChunk(index) && it.length() > MIN_AUDIO_BYTES }
            ?: return 0L
        return if (file.extension == "wav") {
            com.audiobookreader.playback.WavFile.durationMs(file)
        } else {
            com.audiobookreader.playback.AudioFile.durationMs(file)
        }
    }

    fun clearModel(bookId: String, modelId: String) {
        File(root, "$bookId/$modelId").deleteRecursively()
    }

    fun clearBook(bookId: String) { File(root, bookId).deleteRecursively() }

    fun clearFrom(bookId: String, modelId: String, firstChunk: Int) {
        val directory = File(root, "$bookId/$modelId")
        directory.listFiles().orEmpty()
            .filter { file ->
                val isTemporary = file.name.endsWith(".wav.part") || file.name.endsWith(".mp3.part")
                val index = file.nameWithoutExtension.substringBeforeLast('.').substringAfterLast('-').toIntOrNull()
                (isTemporary || file.isAudioChunk()) && index?.let { it >= firstChunk } == true
            }
            .forEach(File::delete)
    }

    fun clearTemporary(bookId: String, modelId: String) {
        File(root, "$bookId/$modelId").listFiles().orEmpty()
            .filter { it.name.endsWith(".wav.part") || it.name.endsWith(".mp3.part") }
            .forEach(File::delete)
    }

    fun clearAll() { root.deleteRecursively(); root.mkdirs() }

    fun canWriteMore(bytesToAdd: Long): Boolean {
        val current = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return current + bytesToAdd <= MAX_BYTES
    }

    private fun File.isAudioChunk(index: Int? = null): Boolean {
        val extensionMatches = extension == "wav" || extension == "mp3"
        val indexMatches = index == null || nameWithoutExtension.substringAfterLast('-').toIntOrNull() == index
        return extensionMatches && indexMatches
    }

    companion object {
        const val MAX_BYTES = 512L * 1024L * 1024L
        private const val MIN_AUDIO_BYTES = 44L
    }
}
