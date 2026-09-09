package com.audiobookreader.playback

import java.io.DataOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFile {
    data class Audio(val samples: FloatArray, val sampleRate: Int)

    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        require(sampleRate > 0 && samples.isNotEmpty() && samples.all { it.isFinite() }) { "Audio vacío o inválido" }
        val pcmSize = samples.size * 2
        DataOutputStream(BufferedOutputStream(FileOutputStream(file), 64 * 1024)).use { out ->
            fun ascii(value: String) = out.writeBytes(value)
            fun leInt(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
            fun leShort(value: Int) { out.write(value and 0xff); out.write((value ushr 8) and 0xff) }
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
            if (input.readInt() != 0x52494646) return 0L
            input.seek(8L)
            if (input.readInt() != 0x57415645) return 0L
            input.seek(40L)
            val size = java.lang.Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            if (size <= 0L || size + 44L != file.length()) return 0L
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
                val size = java.lang.Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                val chunkStart = input.filePointer
                require(size <= input.length() - chunkStart) { "El archivo WAV está incompleto" }
                when (id) {
                    0x666D7420 -> {
                        require(size >= 16) { "Cabecera WAV inválida" }
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
            require(format == 1 && channels in 1..8 && sampleRate in 8000..192000 && bits == 16 && dataOffset >= 0 && dataSize > 0) {
                "El audio debe ser WAV PCM de 16 bits"
            }
            require(dataSize % (channels * 2L) == 0L) { "El archivo WAV está incompleto" }
            val frameCount = dataSize / (channels * 2L)
            require(frameCount <= sampleRate * 30L) { "Selecciona una muestra de voz de entre 3 y 30 segundos" }
            val frames = frameCount.toInt()
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
