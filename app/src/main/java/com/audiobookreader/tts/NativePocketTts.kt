package com.audiobookreader.tts

/** Streaming JNI adapter for multilingual PocketTTS ONNX packs. */
internal class NativePocketTts(
    modelsDir: String,
    voicesDir: String,
    precision: String,
    temperature: Float,
    lsdSteps: Int,
    threads: Int,
    sentencePauseMs: Int = 250,
    maxTextTokens: Int = 50,
) : AutoCloseable {
    private var handle = 0L

    init {
        System.loadLibrary("pockettts_jni")
        handle = nativeCreate(
            modelsDir, voicesDir, precision, temperature, lsdSteps, threads,
            sentencePauseMs, maxTextTokens,
        )
        check(handle != 0L) { "No se pudo cargar el modelo PocketTTS" }
    }

    fun synthesize(text: String, voicePath: String, sink: AudioSink): Boolean =
        nativeSynthesize(handle, text, voicePath, sink)

    fun stop() {
        if (handle != 0L) nativeStop(handle)
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    interface AudioSink {
        fun onAudio(samples: FloatArray): Boolean
    }

    private external fun nativeCreate(
        modelsDir: String,
        voicesDir: String,
        precision: String,
        temperature: Float,
        lsdSteps: Int,
        threads: Int,
        sentencePauseMs: Int,
        maxTextTokens: Int,
    ): Long

    private external fun nativeSynthesize(
        handle: Long,
        text: String,
        voicePath: String,
        sink: AudioSink,
    ): Boolean

    private external fun nativeStop(handle: Long)
    private external fun nativeDestroy(handle: Long)
}
