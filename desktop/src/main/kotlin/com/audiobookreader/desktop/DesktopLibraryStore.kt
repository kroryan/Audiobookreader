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
)

/** Keeps the desktop shelf across launches, like Android's persisted library. */
object DesktopLibraryStore {
    private val preferences = Preferences.userRoot().node("com.audiobookreader.library")

    fun load(): List<DesktopBook> = runCatching {
        preferences.keys()
            .filter { it.startsWith("book-") }
            .sortedBy { it.removePrefix("book-").toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { key ->
                val file = File(preferences.get(key, ""))
                if (!file.isFile) return@mapNotNull null
                runCatching { DesktopBook(file.absolutePath, file.nameWithoutExtension, DesktopBookReader.read(file)) }.getOrNull()
            }
    }.getOrDefault(emptyList())

    fun save(books: List<DesktopBook>) {
        runCatching {
            preferences.clear()
            books.forEachIndexed { index, book -> preferences.put("book-$index", book.path) }
            preferences.flush()
        }
    }
}
