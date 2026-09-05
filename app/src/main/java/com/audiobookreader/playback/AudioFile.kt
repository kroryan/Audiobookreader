package com.audiobookreader.playback

import android.media.MediaMetadataRetriever
import java.io.File

object AudioFile {
    fun durationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            retriever.release()
        }
    }
}
