package com.audiobookreader.data

enum class ModelFamily { PIPER, COQUI, MIMIC3, KOKORO, KITTEN, SUPERTONIC }

data class TtsModelSpec(
    val id: String,
    val name: String,
    val family: ModelFamily,
    val language: String,
    val archiveName: String,
    val modelName: String,
    val voices: String = "",
    val lexicon: String = "",
    val dataDir: String = "",
    val experimental: Boolean = false,
)

object ModelCatalog {
    private const val base = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"

    // The names and model metadata are taken from sherpa-onnx's
    // scripts/apk/generate-tts-apk-script.py. More entries can be added without
    // changing the playback engine.
    val models = listOf(
        TtsModelSpec("piper-es-miro", "Piper · Miro (España)", ModelFamily.PIPER, "es", "vits-piper-es_ES-miro-high", "es_ES-miro-high.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("piper-es-carlfm", "Piper · CarlFM (España)", ModelFamily.PIPER, "es", "vits-piper-es_ES-carlfm-x_low", "es_ES-carlfm-x_low.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("piper-es-davefx", "Piper · Davefx (España)", ModelFamily.PIPER, "es", "vits-piper-es_ES-davefx-medium", "es_ES-davefx-medium.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("piper-es-glados", "Piper · Glados (España)", ModelFamily.PIPER, "es", "vits-piper-es_ES-glados-medium", "es_ES-glados-medium.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("piper-es-sharvard", "Piper · Sharvard (España)", ModelFamily.PIPER, "es", "vits-piper-es_ES-sharvard-medium", "es_ES-sharvard-medium.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("piper-mx-ald", "Piper · ALD (México)", ModelFamily.PIPER, "es-MX", "vits-piper-es_MX-ald-medium", "es_MX-ald-medium.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("piper-mx-claude", "Piper · Claude (México)", ModelFamily.PIPER, "es-MX", "vits-piper-es_MX-claude-high", "es_MX-claude-high.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("piper-ar-daniela", "Piper · Daniela (Argentina)", ModelFamily.PIPER, "es-AR", "vits-piper-es_AR-daniela-high", "es_AR-daniela-high.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("coqui-es-css10", "Coqui VITS · CSS10 (español)", ModelFamily.COQUI, "es", "vits-coqui-es-css10", "model.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("mimic3-es-ailabs", "Mimic3 · AILABS (España)", ModelFamily.MIMIC3, "es", "vits-mimic3-es_ES-m-ailabs_low", "es_ES-m-ailabs_low.onnx", dataDir = "espeak-ng-data"),
        TtsModelSpec("kokoro-en", "Kokoro v0.19 · English", ModelFamily.KOKORO, "en", "kokoro-en-v0_19", "model.onnx", voices = "voices.bin", dataDir = "espeak-ng-data"),
        TtsModelSpec("kokoro-multi", "Kokoro v1.1 · English + Chinese", ModelFamily.KOKORO, "en", "kokoro-multi-lang-v1_1", "model.onnx", voices = "voices.bin", lexicon = "lexicon-us-en.txt,lexicon-zh.txt", dataDir = "espeak-ng-data"),
        TtsModelSpec("kokoro-multi-int8", "Kokoro v1.1 INT8 · English + Chinese", ModelFamily.KOKORO, "en", "kokoro-int8-multi-lang-v1_1", "model.int8.onnx", voices = "voices.bin", lexicon = "lexicon-us-en.txt,lexicon-zh.txt", dataDir = "espeak-ng-data"),
        TtsModelSpec("supertonic-es", "Supertonic 3 INT8 · español", ModelFamily.SUPERTONIC, "es", "sherpa-onnx-supertonic-3-tts-int8-2026-05-11", "", experimental = false),
    ).map { it.copy(archiveName = "$base${it.archiveName}.tar.bz2") }
}
