package com.audiobookreader.playback

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFile {
    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        val pcmSize = samples.size * 2
        DataOutputStream(FileOutputStream(file)).use { out ->
            fun ascii(value: String) = out.writeBytes(value)
            fun leInt(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
            fun leShort(value: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
            ascii("RIFF"); leInt(36 + pcmSize); ascii("WAVE")
            ascii("fmt "); leInt(16); leShort(1); leShort(1); leInt(sampleRate)
            leInt(sampleRate * 2); leShort(2); leShort(16)
            ascii("data"); leInt(pcmSize)
            samples.forEach { sample ->
                val value = (sample.coerceIn(-1f, 1f) * 32767f).toInt()
                leShort(value)
            }
        }
    }
}
