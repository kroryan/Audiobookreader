package com.audiobookreader.tts

import com.audiobookreader.data.ModelFamily
import com.audiobookreader.data.TtsModelSpec
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.audiobookreader.data.ModelCatalog
import java.io.File

/** Thin adapter around the same OfflineTts API used by sherpa-onnx's Android demo. */
class SherpaTtsEngine(
    private val modelDir: File,
    private val spec: TtsModelSpec,
    private val speakerId: Int = 0,
) : AutoCloseable {
    private val tts = OfflineTts(config = createConfig())

    fun sampleRate(): Int = tts.sampleRate()

    fun generate(text: String, speakerId: Int = 0, speed: Float = 1f): FloatArray =
        tts.generateWithConfig(
            text,
            GenerationConfig(sid = speakerId, speed = speed.coerceIn(0.5f, 2.5f))
        ).samples

    override fun close() { tts.release() }

    private fun createConfig(): OfflineTtsConfig {
        val dataDir = spec.dataDir.takeIf { it.isNotBlank() }?.let { File(modelDir, it).absolutePath }.orEmpty()
        val model = if (spec.family == ModelFamily.KOKORO) {
            OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = File(modelDir, spec.modelName).absolutePath,
                    voices = File(modelDir, spec.voices).absolutePath,
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    dataDir = dataDir,
                    lexicon = spec.lexicon.split(',').filter(String::isNotBlank)
                        .joinToString(",") { File(modelDir, it).absolutePath },
                    // sherpa's convenience helper currently omits this field.
                    // It is required to select Spanish, French, etc. correctly.
                    lang = ModelCatalog.kokoroLanguage(speakerId),
                ),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                debug = false,
                provider = "cpu",
            )
        } else {
            OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, spec.modelName).absolutePath,
                    lexicon = spec.lexicon.split(',').filter(String::isNotBlank)
                        .joinToString(",") { File(modelDir, it).absolutePath },
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    dataDir = dataDir,
                    // Keep Piper's upstream defaults; these affect variation,
                    // not the document language or the selected speaker.
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                ),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                debug = false,
                provider = "cpu",
            )
        }
        return OfflineTtsConfig(
            model = model,
            ruleFsts = spec.ruleFsts.split(',').filter(String::isNotBlank)
                .joinToString(",") { File(modelDir, it).absolutePath },
            ruleFars = spec.ruleFars.split(',').filter(String::isNotBlank)
                .joinToString(",") { File(modelDir, it).absolutePath },
            maxNumSentences = 1,
            silenceScale = 0.2f,
        )
    }
}
