package com.audiobookreader.tts

import com.audiobookreader.data.ModelFamily
import com.audiobookreader.data.TtsModelSpec
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File

/** Thin adapter around the same OfflineTts API used by sherpa-onnx's Android demo. */
class SherpaTtsEngine(
    private val modelDir: File,
    private val spec: TtsModelSpec,
) : AutoCloseable {
    private val tts = OfflineTts(
        config = getOfflineTtsConfig(
            modelDir = modelDir.absolutePath,
            modelName = spec.modelName,
            acousticModelName = "",
            vocoder = "",
            voices = spec.voices,
            lexicon = spec.lexicon,
            dataDir = if (spec.dataDir.isBlank()) "" else File(modelDir, spec.dataDir).absolutePath,
            dictDir = "",
            ruleFsts = spec.ruleFsts,
            ruleFars = spec.ruleFars,
            numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
            isKitten = spec.family == ModelFamily.KITTEN,
            isSupertonic = spec.family == ModelFamily.SUPERTONIC,
            durationPredictor = "duration_predictor.int8.onnx",
            textEncoder = "text_encoder.int8.onnx",
            vectorEstimator = "vector_estimator.int8.onnx",
            supertonicVocoder = "vocoder.int8.onnx",
            ttsJson = "tts.json",
            unicodeIndexer = "unicode_indexer.bin",
            voiceStyle = "voice.bin",
        )
    )

    fun sampleRate(): Int = tts.sampleRate()

    fun generate(text: String, speakerId: Int = 0, speed: Float = 1f): FloatArray =
        tts.generateWithConfig(
            text,
            GenerationConfig(sid = speakerId, speed = speed.coerceIn(0.5f, 2.5f))
        ).samples

    override fun close() { tts.release() }
}
