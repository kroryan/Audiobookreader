package com.audiobookreader.playback

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFile {
    data class Audio(val samples: FloatArray, val sampleRate: Int)

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

    fun durationMs(file: File, sampleRate: Int): Long {
        if (sampleRate <= 0 || file.length() <= 44L) return 0L
        val sampleCount = (file.length() - 44L) / 2L
        return sampleCount * 1000L / sampleRate
    }

    fun durationMs(file: File): Long {
        if (file.length() <= 44L) return 0L
        RandomAccessFile(file, "r").use { input ->
            input.seek(24L)
            val b0 = input.read()
            val b1 = input.read()
            val b2 = input.read()
            val b3 = input.read()
            if (listOf(b0, b1, b2, b3).any { it < 0 }) return 0L
            val sampleRate = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            return durationMs(file, sampleRate)
        }
    }

    fun read(file: File): Audio {
        RandomAccessFile(file, "r").use { input ->
            require(input.readInt() == 0x52494646) { "No es un archivo WAV" }
            input.skipBytes(4)
            require(input.readInt() == 0x57415645) { "No es un archivo WAV" }
            var format = 0
            var channels = 0
            var sampleRate = 0
            var bits = 0
            var dataOffset = -1L
            var dataSize = 0L
            while (input.filePointer + 8 <= input.length()) {
                val id = input.readInt()
                val size = java.lang.Integer.reverseBytes(input.readInt()).toLong()
                val chunkStart = input.filePointer
                when (id) {
                    0x666D7420 -> {
                        format = java.lang.Short.reverseBytes(input.readShort()).toInt() and 0xffff
                        channels = java.lang.Short.reverseBytes(input.readShort()).toInt() and 0xffff
                        sampleRate = java.lang.Integer.reverseBytes(input.readInt())
                        input.skipBytes(6)
                        bits = java.lang.Short.reverseBytes(input.readShort()).toInt() and 0xffff
                    }
                    0x64617461 -> {
                        dataOffset = input.filePointer
                        dataSize = size
                    }
                }
                input.seek((chunkStart + size + (size and 1L)).coerceAtMost(input.length()))
            }
            require(format == 1 && channels > 0 && sampleRate > 0 && bits == 16 && dataOffset >= 0) {
                "El audio debe ser WAV PCM de 16 bits"
            }
            val frames = (dataSize / (channels * 2L)).toInt()
            val samples = FloatArray(frames)
            input.seek(dataOffset)
            repeat(frames) { frame ->
                var sum = 0f
                repeat(channels) {
                    sum += java.lang.Short.reverseBytes(input.readShort()).toFloat() / 32768f
                }
                samples[frame] = sum / channels
            }
            return Audio(samples, sampleRate)
        }
    }
}
