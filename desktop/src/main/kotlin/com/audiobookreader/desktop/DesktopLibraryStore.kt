package com.audiobookreader.desktop

import java.io.File
import java.util.prefs.Preferences

data class DesktopBook(
    val path: String,
    val title: String,
    val text: String,
    val progress: Int = 0,
    val currentFragment: Int = 0,
    val bookmarks: List<Int> = emptyList(),
    val positionMs: Long = 0,
    val modelId: String = "",
    val speed: Float = 1f,
)

/** Keeps the desktop shelf across launches, like Android's persisted library. */
object DesktopLibraryStore {
    private val preferences = Preferences.userRoot().node("com.audiobookreader.library")
    private val positions = Preferences.userRoot().node("com.audiobookreader.book-state")
    private fun state(path: String) = positions.node(path.hashCode().toUInt().toString(16))

    fun load(): List<DesktopBook> = runCatching {
        preferences.keys()
            .filter { it.startsWith("book-") }
            .sortedBy { it.removePrefix("book-").toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { key ->
                val file = File(preferences.get(key, ""))
                if (!file.isFile) return@mapNotNull null
                runCatching {
                    val saved = state(file.absolutePath)
                    DesktopBook(file.absolutePath, file.nameWithoutExtension, DesktopBookReader.read(file),
                        progress = saved.getInt("progress", 0),
                        currentFragment = saved.getInt("fragment", 0),
                        bookmarks = saved.get("bookmarks", "").split(',').mapNotNull(String::toIntOrNull),
                        positionMs = saved.getLong("position-ms", 0),
                        modelId = saved.get("model", ""), speed = saved.getFloat("speed", 1f))
                }.getOrNull()
            }
    }.getOrDefault(emptyList())

    fun save(books: List<DesktopBook>) {
        runCatching {
            preferences.clear()
            books.forEachIndexed { index, book ->
                preferences.put("book-$index", book.path)
                state(book.path).apply {
                    putInt("progress", book.progress)
                    putInt("fragment", book.currentFragment)
                    put("bookmarks", book.bookmarks.joinToString(","))
                    putLong("position-ms", book.positionMs)
                    put("model", book.modelId)
                    putFloat("speed", book.speed)
                }
            }
            preferences.flush()
            positions.flush()
        }
    }
}
