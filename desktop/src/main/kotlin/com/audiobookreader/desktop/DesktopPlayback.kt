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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** The same offline sherpa-onnx engine used by Android, hosted by the desktop JVM. */
class DesktopTtsEngine(modelDir: File, spec: TtsModelSpec) : DesktopSpeechEngine {
    private val tts = OfflineTts(offlineConfig(modelDir, spec))

    override fun render(text: String, speakerId: Int, speed: Float): FloatArray =
        tts.generate(
            SpeechText.forOfflineTts(text),
            speakerId,
            speed.coerceIn(0.5f, 2.5f),
        ).samples

    override fun sampleRate(): Int = tts.sampleRate

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
                        .setLexicon(pathList(modelDir, spec.lexicon))
                        .setLang(spec.language)
                        .setDataDir(pathOrEmpty(modelDir, spec.dataDir))
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
