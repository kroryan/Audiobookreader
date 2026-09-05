package com.audiobookreader.data

/** Settings stored independently for every book on every supported platform. */
data class BookTtsSettings(
    val modelId: String,
    val speed: Float = 1f,
    val speakerId: Int = 0,
)
