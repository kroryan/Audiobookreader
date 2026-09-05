package com.audiobookreader.desktop

import com.audiobookreader.data.SpeechText
import com.audiobookreader.data.TtsModelSpec
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** The same offline sherpa-onnx engine used by Android, hosted by the desktop JVM. */
class DesktopTtsEngine(modelDir: File, spec: TtsModelSpec) : AutoCloseable {
    private val tts = OfflineTts(offlineConfig(modelDir, spec))

    fun render(text: String, speakerId: Int, speed: Float): FloatArray =
        tts.generate(
            SpeechText.forOfflineTts(text),
            speakerId,
            speed.coerceIn(0.5f, 2.5f),
        ).samples

    fun sampleRate(): Int = tts.sampleRate

    override fun close() = tts.release()

    private fun offlineConfig(modelDir: File, spec: TtsModelSpec): OfflineTtsConfig {
        require(spec.modelName.isNotBlank()) { "This voice does not have a local downloadable model" }
        val model = if (spec.family.name == "KOKORO") {
            OfflineTtsModelConfig.builder()
                .setKokoro(
                    OfflineTtsKokoroModelConfig.builder()
                        .setModel(find(modelDir, spec.modelName).absolutePath)
                        .setVoices(find(modelDir, spec.voices).absolutePath)
                        .setTokens(find(modelDir, "tokens.txt").absolutePath)
                        .setLang(spec.language)
                        .setDataDir(find(modelDir, spec.dataDir).absolutePath)
                        .build()
                )
        } else {
            OfflineTtsModelConfig.builder()
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

    private fun find(root: File, name: String): File {
        require(name.isNotBlank()) { "Missing model file configuration" }
        return root.walkTopDown().firstOrNull { it.isFile && it.name == name }
            ?: File(root, name).also { require(it.isFile) { "Model file not found: $name" } }
    }

    private fun pathOrEmpty(root: File, name: String): String =
        if (name.isBlank()) "" else root.walkTopDown().firstOrNull { it.isDirectory && it.name == name }?.absolutePath.orEmpty()

    private fun pathList(root: File, value: String): String = value.split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { find(root, it).absolutePath }
        .joinToString(",")
}

object DesktopWavFile {
    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        val pcmSize = samples.size * 2
        file.parentFile?.mkdirs()
        DataOutputStream(FileOutputStream(file)).use { output ->
            fun text(value: String) = output.writeBytes(value)
            fun int(value: Int) = output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
            fun short(value: Int) = output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
            text("RIFF"); int(36 + pcmSize); text("WAVE")
            text("fmt "); int(16); short(1); short(1); int(sampleRate); int(sampleRate * 2); short(2); short(16)
            text("data"); int(pcmSize)
            samples.forEach { short((it.coerceIn(-1f, 1f) * 32767f).toInt()) }
        }
    }
}

class DesktopAudioPlayer {
    private var clip: Clip? = null

    fun play(file: File, positionMs: Long = 0L) {
        stop()
        val stream: AudioInputStream = AudioSystem.getAudioInputStream(file)
        clip = AudioSystem.getClip().also {
            stream.use { input -> it.open(input) }
            it.microsecondPosition = positionMs.coerceAtLeast(0L) * 1_000L
            it.start()
        }
    }

    fun stop() {
        clip?.stop()
        clip?.close()
        clip = null
    }
}
