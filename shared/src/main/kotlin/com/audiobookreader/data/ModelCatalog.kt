package com.audiobookreader.data

enum class ModelFamily { PIPER, COQUI, MIMIC3, KOKORO, KITTEN, SUPERTONIC, EDGE }

data class TtsModelSpec(
    val id: String,
    val name: String,
    val family: ModelFamily,
    val language: String,
    val archiveName: String,
    val modelName: String,
    val voices: String = "",
    val lexicon: String = "",
    val ruleFsts: String = "",
    val ruleFars: String = "",
    val dataDir: String = "",
    val experimental: Boolean = false,
    /** Microsoft Edge voice short name, for online Edge TTS entries. */
    val edgeVoice: String = "",
    val licenseSpdx: String = "",
    val licenseUrl: String = "",
    val attribution: String = "",
    val requiresAcceptance: Boolean = false,
)

object ModelCatalog {
    private const val base = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"

    // Sherpa-ONNX's current Android TTS script contains these Piper voices.
    // Only the small catalog entries are bundled; the archives remain remote.
    private val piperDirs = """
vits-piper-ar_JO-kareem-low
vits-piper-ar_JO-kareem-medium
vits-piper-ar_JO-SA_dii-high
vits-piper-ar_JO-SA_miro-high
vits-piper-ar_JO-SA_miro_V2-high
vits-piper-ca_ES-upc_ona-medium
vits-piper-ca_ES-upc_ona-x_low
vits-piper-ca_ES-upc_pau-x_low
vits-piper-cs_CZ-jirka-low
vits-piper-cs_CZ-jirka-medium
vits-piper-cy_GB-bu_tts-medium
vits-piper-cy_GB-gwryw_gogleddol-medium
vits-piper-da_DK-talesyntese-medium
vits-piper-de_DE-eva_k-x_low
vits-piper-de_DE-karlsson-low
vits-piper-de_DE-kerstin-low
vits-piper-de_DE-dii-high
vits-piper-de_DE-miro-high
vits-piper-de_DE-pavoque-low
vits-piper-de_DE-ramona-low
vits-piper-de_DE-thorsten-high
vits-piper-de_DE-thorsten-low
vits-piper-de_DE-thorsten-medium
vits-piper-de_DE-thorsten_emotional-medium
vits-piper-de_DE-glados-high
vits-piper-de_DE-glados-low
vits-piper-de_DE-glados-medium
vits-piper-de_DE-glados_turret-high
vits-piper-de_DE-glados_turret-low
vits-piper-de_DE-glados_turret-medium
vits-piper-el_GR-rapunzelina-low
vits-piper-en_GB-alan-low
vits-piper-en_GB-alan-medium
vits-piper-en_GB-alba-medium
vits-piper-en_GB-aru-medium
vits-piper-en_GB-cori-high
vits-piper-en_GB-cori-medium
vits-piper-en_GB-dii-high
vits-piper-en_GB-jenny_dioco-medium
vits-piper-en_GB-miro-high
vits-piper-en_GB-northern_english_male-medium
vits-piper-en_GB-semaine-medium
vits-piper-en_GB-southern_english_female-low
vits-piper-en_GB-southern_english_female-medium
vits-piper-en_GB-southern_english_male-medium
vits-piper-en_GB-sweetbbak-amy
vits-piper-en_GB-vctk-medium
vits-piper-en_US-amy-low
vits-piper-en_US-amy-medium
vits-piper-en_US-arctic-medium
vits-piper-en_US-bryce-medium
vits-piper-en_US-danny-low
vits-piper-en_US-glados
vits-piper-en_US-glados-high
vits-piper-en_US-hfc_female-medium
vits-piper-en_US-hfc_male-medium
vits-piper-en_US-joe-medium
vits-piper-en_US-john-medium
vits-piper-en_US-kathleen-low
vits-piper-en_US-kristin-medium
vits-piper-en_US-kusal-medium
vits-piper-en_US-l2arctic-medium
vits-piper-en_US-lessac-high
vits-piper-en_US-lessac-low
vits-piper-en_US-lessac-medium
vits-piper-en_US-libritts-high
vits-piper-en_US-libritts_r-medium
vits-piper-en_US-ljspeech-high
vits-piper-en_US-ljspeech-medium
vits-piper-en_US-miro-high
vits-piper-en_US-norman-medium
vits-piper-en_US-ryan-high
vits-piper-en_US-ryan-low
vits-piper-en_US-ryan-medium
vits-piper-es_AR-daniela-high
vits-piper-es_ES-carlfm-x_low
vits-piper-es_ES-davefx-medium
vits-piper-es_ES-glados-medium
vits-piper-es_ES-miro-high
vits-piper-es_ES-sharvard-medium
vits-piper-es_MX-ald-medium
vits-piper-es_MX-claude-high
vits-piper-eu_ES-antton-medium
vits-piper-eu_ES-maider-medium
vits-piper-fa_IR-amir-medium
vits-piper-fa_IR-ganji-medium
vits-piper-fa_IR-ganji_adabi-medium
vits-piper-fa_IR-gyro-medium
vits-piper-fa_IR-reza_ibrahim-medium
vits-piper-fa_en-rezahedayatfar-ibrahimwalk-medium
vits-piper-fi_FI-harri-low
vits-piper-fi_FI-harri-medium
vits-piper-fr_FR-gilles-low
vits-piper-fr_FR-miro-high
vits-piper-fr_FR-siwis-low
vits-piper-fr_FR-siwis-medium
vits-piper-fr_FR-tjiho-model1
vits-piper-fr_FR-tjiho-model2
vits-piper-fr_FR-tjiho-model3
vits-piper-fr_FR-tom-medium
vits-piper-fr_FR-upmc-medium
vits-piper-hi_IN-pratham-medium
vits-piper-hi_IN-priyamvada-medium
vits-piper-hi_IN-rohan-medium
vits-piper-hu_HU-anna-medium
vits-piper-hu_HU-berta-medium
vits-piper-hu_HU-imre-medium
vits-piper-id_ID-news_tts-medium
vits-piper-is_IS-bui-medium
vits-piper-is_IS-salka-medium
vits-piper-is_IS-steinn-medium
vits-piper-is_IS-ugla-medium
vits-piper-it_IT-dii-high
vits-piper-it_IT-miro-high
vits-piper-it_IT-paola-medium
vits-piper-it_IT-riccardo-x_low
vits-piper-ka_GE-natia-medium
vits-piper-kk_KZ-iseke-x_low
vits-piper-kk_KZ-issai-high
vits-piper-kk_KZ-raya-x_low
vits-piper-lv_LV-aivars-medium
vits-piper-lb_LU-marylux-medium
vits-piper-ne_NP-chitwan-medium
vits-piper-ne_NP-google-medium
vits-piper-ne_NP-google-x_low
vits-piper-nl_BE-nathalie-medium
vits-piper-nl_BE-nathalie-x_low
vits-piper-nl_BE-rdh-medium
vits-piper-nl_BE-rdh-x_low
vits-piper-nl_NL-miro-high
vits-piper-nl_NL-dii-high
vits-piper-nl_NL-alex-medium
vits-piper-no_NO-talesyntese-medium
vits-piper-pl_PL-darkman-medium
vits-piper-pl_PL-bass-high
vits-piper-pl_PL-gosia-medium
vits-piper-pl_PL-jarvis_wg_glos-medium
vits-piper-pl_PL-justyna_wg_glos-medium
vits-piper-pl_PL-mc_speech-medium
vits-piper-pl_PL-meski_wg_glos-medium
vits-piper-pl_PL-zenski_wg_glos-medium
vits-piper-pt_BR-cadu-medium
vits-piper-pt_BR-dii-high
vits-piper-pt_BR-edresson-low
vits-piper-pt_BR-faber-medium
vits-piper-pt_BR-jeff-medium
vits-piper-pt_BR-miro-high
vits-piper-pt_PT-dii-high
vits-piper-pt_PT-miro-high
vits-piper-pt_PT-tugao-medium
vits-piper-ro_RO-mihai-medium
vits-piper-ru_RU-denis-medium
vits-piper-ru_RU-dmitri-medium
vits-piper-ru_RU-irina-medium
vits-piper-ru_RU-ruslan-medium
vits-piper-sk_SK-lili-medium
vits-piper-sl_SI-artur-medium
vits-piper-sq_AL-edon-medium
vits-piper-sr_RS-serbski_institut-medium
vits-piper-sv_SE-lisa-medium
vits-piper-sv_SE-nst-medium
vits-piper-sv_SE-alma-medium
vits-piper-sw_CD-lanfrica-medium
vits-piper-tr_TR-dfki-medium
vits-piper-tr_TR-fahrettin-medium
vits-piper-tr_TR-fettah-medium
vits-piper-ku_TR-berfin_renas-medium
vits-piper-uk_UA-lada-x_low
vits-piper-uk_UA-ukrainian_tts-medium
vits-piper-ur_PK-fasih-medium
vits-piper-vi_VN-25hours_single-low
vits-piper-vi_VN-vais1000-medium
vits-piper-vi_VN-vivos-x_low
vits-piper-zh_CN-huayan-medium
vits-piper-zh_CN-xiao_ya-medium
vits-piper-zh_CN-chaowen-medium
""".trimIndent().lines()

    private val legacyIds = mapOf(
        "vits-piper-es_ES-miro-high" to "piper-es-miro",
        "vits-piper-es_ES-carlfm-x_low" to "piper-es-carlfm",
        "vits-piper-es_ES-davefx-medium" to "piper-es-davefx",
        "vits-piper-es_ES-glados-medium" to "piper-es-glados",
        "vits-piper-es_ES-sharvard-medium" to "piper-es-sharvard",
        "vits-piper-es_MX-ald-medium" to "piper-mx-ald",
        "vits-piper-es_MX-claude-high" to "piper-mx-claude",
        "vits-piper-es_AR-daniela-high" to "piper-ar-daniela",
    )

    private fun languageOf(suffix: String) = suffix.substringBefore('-').substringBefore('_').lowercase()

    private fun piper(dir: String): TtsModelSpec {
        val suffix = dir.removePrefix("vits-piper-")
        val chinese = dir.contains("zh_CN-xiao_ya") || dir.contains("zh_CN-chaowen")
        val restrictedOpenVoiceOs = suffix.contains("miro", ignoreCase = true) || suffix.contains("dii", ignoreCase = true)
        val slug = suffix.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return TtsModelSpec(
            id = legacyIds[dir] ?: "piper-$slug",
            name = "Piper · $suffix",
            family = ModelFamily.PIPER,
            language = languageOf(suffix),
            archiveName = "$base$dir.tar.bz2",
            modelName = "$suffix.onnx",
            lexicon = if (chinese) "lexicon.txt" else "",
            ruleFsts = if (chinese) "phone.fst,date.fst,number.fst" else "",
            dataDir = if (chinese) "" else "espeak-ng-data",
            licenseSpdx = if (restrictedOpenVoiceOs) "CC BY-NC-SA 4.0" else "Per-voice license",
            licenseUrl = if (restrictedOpenVoiceOs) "https://creativecommons.org/licenses/by-nc-sa/4.0/" else "https://huggingface.co/rhasspy/piper-voices/tree/main",
            attribution = if (restrictedOpenVoiceOs) "OpenVoiceOS / Piper voice contributors" else "Piper voice contributors",
            requiresAcceptance = restrictedOpenVoiceOs,
        )
    }

    private val coquiDirs = listOf(
        "vits-coqui-en-ljspeech", "vits-coqui-en-ljspeech-neon", "vits-coqui-en-vctk",
        "vits-coqui-bg-cv", "vits-coqui-bn-custom_female", "vits-coqui-cs-cv", "vits-coqui-da-cv",
        "vits-coqui-de-css10", "vits-coqui-es-css10", "vits-coqui-et-cv", "vits-coqui-fi-css10",
        "vits-coqui-fr-css10", "vits-coqui-ga-cv", "vits-coqui-hr-cv", "vits-coqui-lt-cv",
        "vits-coqui-lv-cv", "vits-coqui-mt-cv", "vits-coqui-nl-css10", "vits-coqui-pl-mai_female",
        "vits-coqui-pt-cv", "vits-coqui-ro-cv", "vits-coqui-sk-cv", "vits-coqui-sl-cv",
        "vits-coqui-sv-cv", "vits-coqui-uk-mai",
    )

    private val mimic3Dirs = listOf(
        "vits-mimic3-af_ZA-google-nwu_low", "vits-mimic3-bn-multi_low", "vits-mimic3-es_ES-m-ailabs_low",
        "vits-mimic3-fa-haaniye_low", "vits-mimic3-fi_FI-harri-tapani-ylilammi_low", "vits-mimic3-gu_IN-cmu-indic_low",
        "vits-mimic3-hu_HU-diana-majlinger_low", "vits-mimic3-ko_KO-kss_low", "vits-mimic3-ne_NP-ne-google_low",
        "vits-mimic3-pl_PL-m-ailabs_low", "vits-mimic3-tn_ZA-google-nwu_low", "vits-mimic3-vi_VN-vais1000_low",
    )

    private fun vitsModel(dir: String, family: ModelFamily): TtsModelSpec {
        val prefix = if (family == ModelFamily.COQUI) "vits-coqui-" else "vits-mimic3-"
        val suffix = dir.removePrefix(prefix)
        // Coqui archives always contain model.onnx. Mimic3 archives use the
        // model directory suffix as the ONNX filename.
        val modelName = if (family == ModelFamily.COQUI) "model.onnx" else "$suffix.onnx"
        return TtsModelSpec(
            id = dir.replace('_', '-'),
            name = "${if (family == ModelFamily.COQUI) "Coqui VITS" else "Mimic3"} · $suffix",
            family = family,
            language = languageOf(suffix),
            archiveName = "$base$dir.tar.bz2",
            modelName = modelName,
            dataDir = if (family == ModelFamily.COQUI) "" else "espeak-ng-data",
            licenseSpdx = if (family == ModelFamily.COQUI) "BSD-3-Clause" else "CC BY-SA 4.0",
            licenseUrl = if (family == ModelFamily.COQUI) "https://github.com/coqui-ai/TTS/blob/dev/TTS/.models.json" else "https://github.com/MycroftAI/mimic3-voices/blob/master/LICENSE",
            attribution = if (family == ModelFamily.COQUI) "Coqui TTS / NeonGeckoCom" else "MycroftAI Mimic 3 Voices contributors",
            requiresAcceptance = family == ModelFamily.MIMIC3,
        )
    }

    val models: List<TtsModelSpec> = piperDirs.map(::piper) +
        coquiDirs.map { vitsModel(it, ModelFamily.COQUI) } +
        mimic3Dirs.map { vitsModel(it, ModelFamily.MIMIC3) } + listOf(
            TtsModelSpec("kokoro-en", "Kokoro v0.19 · English", ModelFamily.KOKORO, "en", "${base}kokoro-en-v0_19.tar.bz2", "model.onnx", voices = "voices.bin", dataDir = "espeak-ng-data", licenseSpdx = "Apache-2.0", licenseUrl = "https://huggingface.co/hexgrad/Kokoro-82M/blob/main/LICENSE", attribution = "hexgrad Kokoro-82M contributors"),
            TtsModelSpec("kokoro-multi-v1-0", "Kokoro v1.0 · English + Chinese", ModelFamily.KOKORO, "en", "${base}kokoro-multi-lang-v1_0.tar.bz2", "model.onnx", voices = "voices.bin", lexicon = "lexicon-us-en.txt,lexicon-zh.txt", ruleFsts = "phone-zh.fst,date-zh.fst,number-zh.fst", dataDir = "espeak-ng-data", licenseSpdx = "Apache-2.0", licenseUrl = "https://huggingface.co/hexgrad/Kokoro-82M/blob/main/LICENSE", attribution = "hexgrad Kokoro-82M contributors"),
            TtsModelSpec("kokoro-multi", "Kokoro v1.1 · English + Chinese", ModelFamily.KOKORO, "en", "${base}kokoro-multi-lang-v1_1.tar.bz2", "model.onnx", voices = "voices.bin", lexicon = "lexicon-us-en.txt,lexicon-zh.txt", ruleFsts = "phone-zh.fst,date-zh.fst,number-zh.fst", dataDir = "espeak-ng-data", licenseSpdx = "Apache-2.0", licenseUrl = "https://huggingface.co/hexgrad/Kokoro-82M/blob/main/LICENSE", attribution = "hexgrad Kokoro-82M contributors"),
            TtsModelSpec("kokoro-multi-int8", "Kokoro v1.1 INT8 · English + Chinese", ModelFamily.KOKORO, "en", "${base}kokoro-int8-multi-lang-v1_1.tar.bz2", "model.int8.onnx", voices = "voices.bin", lexicon = "lexicon-us-en.txt,lexicon-zh.txt", ruleFsts = "phone-zh.fst,date-zh.fst,number-zh.fst", dataDir = "espeak-ng-data", licenseSpdx = "Apache-2.0", licenseUrl = "https://huggingface.co/hexgrad/Kokoro-82M/blob/main/LICENSE", attribution = "hexgrad Kokoro-82M contributors"),
            TtsModelSpec("supertonic-es", "Supertonic 3 INT8 · Spanish", ModelFamily.SUPERTONIC, "es", "${base}sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2", "", experimental = false, licenseSpdx = "OpenRAIL-M", licenseUrl = "https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE", attribution = "Supertone Inc.", requiresAcceptance = true),
        )

    val languages: List<String> = models.map { it.language }.distinct().sorted()

    fun edgeVoice(shortName: String, displayName: String, language: String): TtsModelSpec =
        TtsModelSpec(
            id = "edge-" + shortName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
            name = "Edge · $displayName",
            family = ModelFamily.EDGE,
            language = language,
            archiveName = "",
            modelName = "",
            edgeVoice = shortName,
        )
}
