package com.example.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavHelper {
    fun writeMono16BitWav(outFile: File, pcm: ShortArray, sampleRate: Int) {
        outFile.parentFile?.mkdirs()
        val dataSize = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
            putShort(1) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // Byte rate
            putShort(2) // Block align
            putShort(16) // Bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }
        FileOutputStream(outFile).use { fos ->
            fos.write(header.array())
            val byteBuf = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            byteBuf.asShortBuffer().put(pcm)
            fos.write(byteBuf.array())
        }
    }

    fun writeWavHeaderPlaceholder(fos: FileOutputStream, sampleRate: Int) {
        val emptyHeader = ByteArray(44)
        fos.write(emptyHeader)
    }

    fun finalizeWavHeader(file: File, sampleRate: Int) {
        val totalLength = file.length().toInt()
        val dataSize = (totalLength - 44).coerceAtLeast(0)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }

    fun readMono16BitWav(file: File): Pair<Int, ShortArray> {
        FileInputStream(file).use { fis ->
            val bytes = fis.readBytes()
            if (bytes.size < 44) return 16000 to ShortArray(0)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val sampleRate = bb.getInt(24).coerceIn(8000, 48000)
            val channels = bb.getShort(22).toInt().coerceAtLeast(1)
            // Locate "data" chunk robustly
            var offset = 12
            var dataStart = 44
            var dataLength = bytes.size - 44
            while (offset + 8 <= bytes.size) {
                val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = bb.getInt(offset + 4)
                if (chunkId == "data") {
                    dataStart = offset + 8
                    dataLength = minOf(chunkSize, bytes.size - dataStart)
                    break
                }
                offset += 8 + chunkSize
            }
            val sampleCount = (dataLength / (2 * channels)).coerceAtLeast(0)
            val out = ShortArray(sampleCount)
            var idx = dataStart
            for (i in 0 until sampleCount) {
                var sum = 0
                for (c in 0 until channels) {
                    if (idx + 1 < bytes.size) {
                        val lo = bytes[idx].toInt() and 0xFF
                        val hi = bytes[idx + 1].toInt()
                        sum += (hi shl 8) or lo
                    }
                    idx += 2
                }
                out[i] = (sum / channels).toShort()
            }
            return sampleRate to out
        }
    }
}
