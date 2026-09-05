package com.audiobookreader.data

import android.net.Uri

data class Book(
    val id: String,
    val title: String,
    val sourceUri: Uri,
    val chapters: List<Chapter>,
    val language: String = "es",
    val coverPath: String? = null,
)

data class Chapter(
    val id: String,
    val title: String,
    val text: String,
)
