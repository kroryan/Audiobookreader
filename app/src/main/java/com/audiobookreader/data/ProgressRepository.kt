package com.audiobookreader.data

import android.content.Context

data class ReadingProgress(
    val bookId: String,
    val itemIndex: Int = 0,
    val positionMs: Long = 0L,
    val itemCount: Int = 1,
) {
    val percentage: Int
        get() = if (itemCount <= 0) 0 else ((itemIndex.toDouble() / itemCount) * 100).toInt().coerceIn(0, 99)
}

data class Bookmark(
    val bookId: String,
    val label: String,
    val itemIndex: Int,
    val positionMs: Long,
    val percentage: Int,
)

class ProgressRepository(context: Context) {
    private val prefs = context.getSharedPreferences("reading-progress", Context.MODE_PRIVATE)

    fun load(bookId: String): ReadingProgress = ReadingProgress(
        bookId = bookId,
        itemIndex = prefs.getInt("$bookId.item", 0),
        positionMs = prefs.getLong("$bookId.position", 0L),
        itemCount = prefs.getInt("$bookId.count", 1),
    )

    fun save(progress: ReadingProgress) {
        prefs.edit()
            .putInt("${progress.bookId}.item", progress.itemIndex)
            .putLong("${progress.bookId}.position", progress.positionMs)
            .putInt("${progress.bookId}.count", progress.itemCount)
            .apply()
    }

    fun addBookmark(bookmark: Bookmark) {
        val key = "${bookmark.bookId}.bookmarks"
        val existing = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        existing += listOf(bookmark.label, bookmark.itemIndex, bookmark.positionMs, bookmark.percentage).joinToString("|")
        prefs.edit().putStringSet(key, existing).apply()
    }

    fun bookmarks(bookId: String): List<Bookmark> = prefs.getStringSet("$bookId.bookmarks", emptySet()).orEmpty()
        .mapNotNull { value ->
            val parts = value.split('|')
            if (parts.size != 4) return@mapNotNull null
            Bookmark(bookId, parts[0], parts[1].toIntOrNull() ?: return@mapNotNull null, parts[2].toLongOrNull() ?: return@mapNotNull null, parts[3].toIntOrNull() ?: return@mapNotNull null)
        }.sortedBy { it.itemIndex }
}
