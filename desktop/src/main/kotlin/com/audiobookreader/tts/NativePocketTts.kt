package com.audiobookreader.tts

import java.io.File
import java.nio.file.Files

internal class NativePocketTts(modelsDir: String) : AutoCloseable {
    private var handle: Long

    init {
        loadRuntime()
        handle = nativeCreate(modelsDir, modelsDir, "int8", 0.7f, 1,
            Runtime.getRuntime().availableProcessors().coerceIn(2, 4), 250, 50)
        check(handle != 0L) { "Could not initialize PocketTTS; check the model files" }
    }

    fun synthesize(text: String, voice: String, sink: AudioSink): Boolean =
        nativeSynthesize(handle, text, voice, sink)

    interface AudioSink { fun onAudio(samples: FloatArray): Boolean }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(models: String, voices: String, precision: String,
        temperature: Float, steps: Int, threads: Int, pause: Int, tokens: Int): Long
    private external fun nativeSynthesize(handle: Long, text: String, voice: String, sink: AudioSink): Boolean
    private external fun nativeDestroy(handle: Long)

    companion object {
        private var loaded = false

        @Synchronized private fun loadRuntime() {
            if (loaded) return
            val platform = when {
                System.getProperty("os.name").startsWith("Linux") &&
                    System.getProperty("os.arch") in setOf("amd64", "x86_64") -> "linux-x64"
                else -> error("PocketTTS native runtime is not packaged for this platform")
            }
            val directory = Files.createTempDirectory("bookreader-pocket-").toFile()
            directory.deleteOnExit()
            for (name in listOf("libonnxruntime.so", "libpockettts_jni.so")) {
                val file = File(directory, name)
                val resource = NativePocketTts::class.java.getResourceAsStream("/pocket/$platform/$name")
                    ?: error("PocketTTS runtime missing from this build: $name")
                resource.use { input -> file.outputStream().use(input::copyTo) }
                file.deleteOnExit()
                System.load(file.absolutePath)
            }
            loaded = true
        }
    }
}
