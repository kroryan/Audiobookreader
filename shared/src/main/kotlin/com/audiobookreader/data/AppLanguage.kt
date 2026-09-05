package com.audiobookreader.data

enum class AppLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}
