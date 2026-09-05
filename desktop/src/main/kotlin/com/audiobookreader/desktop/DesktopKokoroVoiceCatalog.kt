package com.audiobookreader.desktop

import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.TtsModelSpec

/**
 * Kokoro-82M's full desktop voice catalogue. These are voice choices inside
 * the Kokoro package, not duplicate model downloads. The original model card
 * lists English, Japanese, Chinese, Spanish, French, Hindi, Italian and
 * Brazilian Portuguese voices.
 */
object DesktopKokoroVoiceCatalog {
    private val voiceIds = listOf(
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky",
        "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa",
        "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis",
        "ef_dora", "em_alex", "em_santa", "ff_siwis", "hf_alpha", "hf_beta", "hm_omega", "hm_psi",
        "if_sara", "im_nicola", "jf_alpha", "jf_gongitsune", "jf_nezumi", "jf_tebukuro", "jm_kumo",
        "pf_dora", "pm_alex", "pm_santa", "zf_xiaobei", "zf_xiaoni", "zf_xiaoxiao", "zf_xiaoyi", "zm_yunjian", "zm_yunxi", "zm_yunxia", "zm_yunyang",
    )

    val voices: List<TtsModelSpec> = voiceIds.map { voice ->
        TtsModelSpec(
            id = "desktop-kokoro-voice-$voice",
            name = "Kokoro voice · $voice",
            family = com.audiobookreader.data.ModelFamily.KOKORO,
            language = languageOf(voice),
            archiveName = "",
            modelName = "",
            voiceId = voice,
            licenseSpdx = "Apache-2.0",
            licenseUrl = "https://huggingface.co/hexgrad/Kokoro-82M/blob/main/LICENSE",
            attribution = "hexgrad Kokoro-82M contributors",
        )
    }

    private fun languageOf(voice: String): String = when (voice.take(2)) {
        "af", "am", "bf", "bm" -> "en"
        "ef", "em" -> "es"
        "ff" -> "fr"
        "hf", "hm" -> "hi"
        "if", "im" -> "it"
        "jf", "jm" -> "ja"
        "pf", "pm" -> "pt"
        "zf", "zm" -> "zh"
        else -> "all"
    }
}
