package com.audiobookreader.desktop

import com.audiobookreader.data.KokoroVoice
import com.audiobookreader.data.ModelCatalog

/** Voices belong to the shared Kokoro package and are selected per book. */
object DesktopKokoroVoiceCatalog {
    val voices: List<KokoroVoice> = ModelCatalog.kokoroVoices
}
