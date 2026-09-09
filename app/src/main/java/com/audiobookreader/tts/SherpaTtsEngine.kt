package com.audiobookreader.tts

import com.audiobookreader.data.ModelFamily
import com.audiobookreader.data.TtsModelSpec
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import com.audiobookreader.data.ModelCatalog
import java.io.File

/** Thin adapter around the same OfflineTts API used by sherpa-onnx's Android demo. */
class SherpaTtsEngine(
    private val modelDir: File,
    private val spec: TtsModelSpec,
    private val speakerId: Int = 0,
    private val referenceAudio: FloatArray? = null,
    private val referenceSampleRate: Int = 0,
    private val referenceText: String? = null,
    private val referenceAudioPath: String = "",
    private val numThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
) : AutoCloseable {
    private val nativePocket = if (spec.family == ModelFamily.POCKET && spec.remoteFiles.isNotEmpty()) {
        NativePocketTts(
            modelsDir = modelDir.absolutePath,
            voicesDir = modelDir.absolutePath,
            precision = "int8",
            temperature = 0.7f,
            lsdSteps = 1,
            threads = 2,
        )
    } else null
    private val tts: OfflineTts? = if (nativePocket == null) OfflineTts(config = createConfig()) else null

    fun sampleRate(): Int = nativePocket?.let { 24_000 } ?: checkNotNull(tts).sampleRate()

    fun generate(text: String, speakerId: Int = 0, speed: Float = 1f, isActive: () -> Boolean = { true }): FloatArray {
        if (spec.referenceAudioRequired) {
            check(referenceAudio != null && referenceAudio.isNotEmpty()) { "Selecciona un audio de referencia para esta voz" }
            check(referenceSampleRate > 0) { "El audio de referencia no tiene una frecuencia válida" }
        }
        if (spec.referenceTextRequired) {
            check(!referenceText.isNullOrBlank()) { "Escribe la transcripción exacta del audio de referencia" }
        }
        if (nativePocket != null) {
            check(referenceAudioPath.isNotBlank()) { "Selecciona un audio de referencia o una voz predefinida" }
            val chunks = ArrayList<FloatArray>()
            var sampleCount = 0
            val completed = nativePocket.synthesize(
                text,
                referenceAudioPath,
                object : NativePocketTts.AudioSink {
                    override fun onAudio(chunk: FloatArray): Boolean {
                        if (!isActive()) return false
                        chunks.add(chunk)
                        sampleCount += chunk.size
                        return isActive()
                    }
                },
            )
            check(completed && sampleCount > 0 && isActive()) { "PocketTTS no completó la generación de audio" }
            return FloatArray(sampleCount).also { samples ->
                var offset = 0
                chunks.forEach { chunk -> chunk.copyInto(samples, offset); offset += chunk.size }
            }
        }
        return checkNotNull(tts).generateWithConfig(text, GenerationConfig(
            sid = speakerId,
            speed = speed.coerceIn(0.5f, 2.5f),
            referenceAudio = referenceAudio,
            referenceSampleRate = referenceSampleRate,
            referenceText = referenceText,
            numSteps = if (spec.family == ModelFamily.ZIPVOICE) 4 else if (spec.family == ModelFamily.POCKET) 2 else 8,
            extra = when (spec.family) {
                ModelFamily.SUPERTONIC -> mapOf("lang" to spec.language)
                ModelFamily.POCKET -> mapOf("max_reference_audio_len" to "10")
                ModelFamily.ZIPVOICE -> mapOf("min_char_in_sentence" to "10")
                else -> emptyMap()
            },
        )).samples
    }

    override fun close() {
        nativePocket?.close()
        tts?.release()
    }

    private fun createConfig(): OfflineTtsConfig {
        val dataDir = spec.dataDir.takeIf { it.isNotBlank() }?.let { File(modelDir, it).absolutePath }.orEmpty()
        val model = when (spec.family) {
            ModelFamily.KOKORO -> OfflineTtsModelConfig(
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
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            ModelFamily.POCKET -> OfflineTtsModelConfig(
                pocket = OfflineTtsPocketModelConfig(
                    lmFlow = find("lm_flow.int8.onnx").absolutePath,
                    lmMain = find("lm_main.int8.onnx").absolutePath,
                    encoder = find("encoder.onnx").absolutePath,
                    decoder = find("decoder.int8.onnx").absolutePath,
                    textConditioner = find("text_conditioner.onnx").absolutePath,
                    vocabJson = find("vocab.json").absolutePath,
                    tokenScoresJson = find("token_scores.json").absolutePath,
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            ModelFamily.ZIPVOICE -> OfflineTtsModelConfig(
                zipvoice = OfflineTtsZipVoiceModelConfig(
                    tokens = find("tokens.txt").absolutePath,
                    encoder = find("encoder.int8.onnx").absolutePath,
                    decoder = find("decoder.int8.onnx").absolutePath,
                    vocoder = find(spec.auxiliaryName).absolutePath,
                    dataDir = dataDir,
                    lexicon = find("lexicon.txt").absolutePath,
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            ModelFamily.SUPERTONIC -> OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = find("duration_predictor.int8.onnx").absolutePath,
                    textEncoder = find("text_encoder.int8.onnx").absolutePath,
                    vectorEstimator = find("vector_estimator.int8.onnx").absolutePath,
                    vocoder = find("vocoder.int8.onnx").absolutePath,
                    ttsJson = find("tts.json").absolutePath,
                    unicodeIndexer = find("unicode_indexer.bin").absolutePath,
                    voiceStyle = find("voice.bin").absolutePath,
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            else -> OfflineTtsModelConfig(
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
                numThreads = numThreads,
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

    private fun find(name: String): File {
        require(name.isNotBlank()) { "Falta configurar un archivo del modelo" }
        return modelDir.walkTopDown().firstOrNull { it.isFile && it.name == name }
            ?: File(modelDir, name).also { require(it.isFile) { "No se encuentra el archivo del modelo: $name" } }
    }
}
