package com.audiobookreader.desktop

import com.audiobookreader.data.SpeechText
import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.TtsModelSpec
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTtsCallback
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** The same offline sherpa-onnx engine used by Android, hosted by the desktop JVM. */
class DesktopTtsEngine(
    modelDir: File,
    private val spec: TtsModelSpec,
    referenceAudioPath: String = "",
    private val referenceText: String = "",
) : DesktopSpeechEngine {
    private val referenceAudio: ReferenceAudio? = referenceAudioPath.takeIf { it.isNotBlank() }?.let(::readReferenceAudio)
    private val tts = OfflineTts(offlineConfig(modelDir, spec))

    override fun render(text: String, speakerId: Int, speed: Float): FloatArray {
        if (spec.referenceAudioRequired) {
            check(referenceAudio != null) { "Choose a reference WAV before using this voice" }
        }
        if (spec.referenceTextRequired) {
            check(referenceText.isNotBlank()) { "Enter the exact reference transcript before using this voice" }
        }
        val config = GenerationConfig().apply {
            setSid(when {
                spec.family == com.audiobookreader.data.ModelFamily.SUPERTONIC -> speakerId.coerceIn(0, 9)
                spec.family == com.audiobookreader.data.ModelFamily.KOKORO && spec.voiceId.isNotBlank() ->
                    ModelCatalog.kokoroVoices.firstOrNull { it.id == spec.voiceId }?.speakerId ?: 0
                else -> 0
            })
            setSpeed(speed.coerceIn(0.5f, 2.5f))
            setReferenceAudio(this@DesktopTtsEngine.referenceAudio?.samples)
            setReferenceSampleRate(this@DesktopTtsEngine.referenceAudio?.sampleRate ?: 0)
            setReferenceText(this@DesktopTtsEngine.referenceText.takeIf { it.isNotBlank() })
            setNumSteps(if (spec.family == com.audiobookreader.data.ModelFamily.ZIPVOICE) 4 else if (spec.family == com.audiobookreader.data.ModelFamily.POCKET) 2 else 8)
            setExtra(when (spec.family) {
                com.audiobookreader.data.ModelFamily.SUPERTONIC -> mapOf("lang" to spec.language)
                com.audiobookreader.data.ModelFamily.POCKET -> mapOf("max_reference_audio_len" to "10")
                com.audiobookreader.data.ModelFamily.ZIPVOICE -> mapOf("min_char_in_sentence" to "10")
                else -> emptyMap()
            })
        }
        return tts.generateWithConfigAndCallback(
            SpeechText.forOfflineTts(text), config, OfflineTtsCallback { 1 }
        ).samples
    }

    override fun sampleRate(): Int = tts.sampleRate

    override fun close() = tts.release()

    private fun offlineConfig(modelDir: File, spec: TtsModelSpec): OfflineTtsConfig {
        require(spec.modelName.isNotBlank() || spec.family in setOf(
            com.audiobookreader.data.ModelFamily.POCKET,
            com.audiobookreader.data.ModelFamily.SUPERTONIC,
        )) { "This voice does not have a local downloadable model" }
        val model = when (spec.family) {
            com.audiobookreader.data.ModelFamily.KOKORO -> OfflineTtsModelConfig.builder()
                .setKokoro(
                    OfflineTtsKokoroModelConfig.builder()
                        .setModel(find(modelDir, spec.modelName).absolutePath)
                        .setVoices(find(modelDir, spec.voices).absolutePath)
                        .setTokens(find(modelDir, "tokens.txt").absolutePath)
                        .setLexicon(pathList(modelDir, spec.lexicon))
                        .setLang(spec.language)
                        .setDataDir(pathOrEmpty(modelDir, spec.dataDir))
                        .build()
                )
            com.audiobookreader.data.ModelFamily.POCKET -> OfflineTtsModelConfig.builder()
                .setPocket(
                    OfflineTtsPocketModelConfig.builder()
                        .setLmFlow(find(modelDir, "lm_flow.int8.onnx").absolutePath)
                        .setLmMain(find(modelDir, "lm_main.int8.onnx").absolutePath)
                        .setEncoder(find(modelDir, "encoder.onnx").absolutePath)
                        .setDecoder(find(modelDir, "decoder.int8.onnx").absolutePath)
                        .setTextConditioner(find(modelDir, "text_conditioner.onnx").absolutePath)
                        .setVocabJson(find(modelDir, "vocab.json").absolutePath)
                        .setTokenScoresJson(find(modelDir, "token_scores.json").absolutePath)
                        .build()
                )
            com.audiobookreader.data.ModelFamily.ZIPVOICE -> OfflineTtsModelConfig.builder()
                .setZipvoice(
                    OfflineTtsZipVoiceModelConfig.builder()
                        .setTokens(find(modelDir, "tokens.txt").absolutePath)
                        .setEncoder(find(modelDir, "encoder.int8.onnx").absolutePath)
                        .setDecoder(find(modelDir, "decoder.int8.onnx").absolutePath)
                        .setVocoder(find(modelDir, spec.auxiliaryName).absolutePath)
                        .setDataDir(pathOrEmpty(modelDir, spec.dataDir))
                        .setLexicon(pathList(modelDir, spec.lexicon))
                        .build()
                )
            com.audiobookreader.data.ModelFamily.SUPERTONIC -> OfflineTtsModelConfig.builder()
                .setSupertonic(
                    OfflineTtsSupertonicModelConfig.builder()
                        .setDurationPredictor(find(modelDir, "duration_predictor.int8.onnx").absolutePath)
                        .setTextEncoder(find(modelDir, "text_encoder.int8.onnx").absolutePath)
                        .setVectorEstimator(find(modelDir, "vector_estimator.int8.onnx").absolutePath)
                        .setVocoder(find(modelDir, "vocoder.int8.onnx").absolutePath)
                        .setTtsJson(find(modelDir, "tts.json").absolutePath)
                        .setUnicodeIndexer(find(modelDir, "unicode_indexer.bin").absolutePath)
                        .setVoiceStyle(find(modelDir, "voice.bin").absolutePath)
                        .build()
                )
            else -> OfflineTtsModelConfig.builder()
                .setVits(
                    OfflineTtsVitsModelConfig.builder()
                        .setModel(find(modelDir, spec.modelName).absolutePath)
                        .setTokens(find(modelDir, "tokens.txt").absolutePath)
                        .setLexicon(pathList(modelDir, spec.lexicon))
                        .setDataDir(pathOrEmpty(modelDir, spec.dataDir))
                        .build()
                )
        }
        return OfflineTtsConfig.builder()
            .setModel(model.setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4)).build())
            .setRuleFsts(pathList(modelDir, spec.ruleFsts))
            .setRuleFars(pathList(modelDir, spec.ruleFars))
            .build()
    }

    private data class ReferenceAudio(val samples: FloatArray, val sampleRate: Int)

    private fun readReferenceAudio(path: String): ReferenceAudio {
        val source = AudioSystem.getAudioInputStream(File(path))
        val sourceFormat = source.format
        val targetFormat = javax.sound.sampled.AudioFormat(
            javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
            sourceFormat.sampleRate,
            16,
            1,
            2,
            sourceFormat.sampleRate,
            false,
        )
        val decoded = if (sourceFormat.matches(targetFormat)) source else AudioSystem.getAudioInputStream(targetFormat, source)
        decoded.use { stream ->
            val bytes = stream.readBytes()
            val samples = FloatArray(bytes.size / 2)
            for (index in samples.indices) {
                val low = bytes[index * 2].toInt() and 0xff
                val high = bytes[index * 2 + 1].toInt()
                samples[index] = ((high shl 8) or low).toShort() / 32768f
            }
            return ReferenceAudio(samples, targetFormat.sampleRate.toInt())
        }
    }

    private fun find(root: File, name: String): File {
        require(name.isNotBlank()) { "Missing model file configuration" }
        return root.walkTopDown().firstOrNull { it.isFile && it.name == name }
            ?: File(root, name).also { require(it.isFile) { "Model file not found: $name" } }
    }

    private fun pathOrEmpty(root: File, name: String): String =
        if (name.isBlank()) "" else requireNotNull(root.walkTopDown().firstOrNull { it.isDirectory && it.name == name }) {
            "Model data directory not found: $name"
        }.absolutePath

    private fun pathList(root: File, value: String): String = value.split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { find(root, it).absolutePath }
        .joinToString(",")
}

object DesktopWavFile {
    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        require(samples.isNotEmpty() && sampleRate > 0) { "The model generated no audio" }
        val pcmSize = samples.size * 2
        file.parentFile?.mkdirs()
        DataOutputStream(FileOutputStream(file).buffered(64 * 1024)).use { output ->
            fun text(value: String) = output.writeBytes(value)
            fun int(value: Int) = output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
            fun short(value: Int) = output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
            text("RIFF"); int(36 + pcmSize); text("WAVE")
            text("fmt "); int(16); short(1); short(1); int(sampleRate); int(sampleRate * 2); short(2); short(16)
            text("data"); int(pcmSize)
            val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { pcm.putShort((it.coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
            output.write(pcm.array())
        }
    }
}

interface DesktopSpeechEngine : AutoCloseable {
    fun render(text: String, speakerId: Int, speed: Float): FloatArray
    fun sampleRate(): Int
}

interface DesktopAudioOutput {
    suspend fun playToEnd(file: File, positionMs: Long, onStarted: () -> Unit, onPosition: (Long) -> Unit)
    fun stop()
}

class DesktopAudioPlayer : DesktopAudioOutput {
    private var clip: Clip? = null

    override suspend fun playToEnd(file: File, positionMs: Long, onStarted: () -> Unit, onPosition: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val current = AudioSystem.getClip()
        try {
            AudioSystem.getAudioInputStream(file).use { current.open(it) }
            val context = kotlinx.coroutines.currentCoroutineContext()
            synchronized(this@DesktopAudioPlayer) {
                context.ensureActive()
                clip = current
                current.microsecondPosition = (positionMs.coerceAtLeast(0L) * 1_000L).coerceAtMost(current.microsecondLength)
                current.start()
            }
            onStarted()
            var lastFrame = current.framePosition
            var lastAdvance = System.nanoTime()
            while (current.isOpen && current.framePosition < current.frameLength) {
                context.ensureActive()
                if (current.framePosition != lastFrame) {
                    lastFrame = current.framePosition
                    lastAdvance = System.nanoTime()
                }
                check(System.nanoTime() - lastAdvance < 10_000_000_000L) { "The audio device stopped responding. Check the system audio output and try Play again." }
                onPosition(current.microsecondPosition / 1_000L)
                delay(100)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
        } finally {
            synchronized(this@DesktopAudioPlayer) {
                current.stop()
                current.close()
                if (clip === current) clip = null
            }
        }
    }

    @Synchronized
    override fun stop() {
        clip?.stop()
        clip?.close()
        clip = null
    }
}
