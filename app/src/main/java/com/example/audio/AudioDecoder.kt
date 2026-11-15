package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.model.AudioClipInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class AudioDecoder(private val context: Context) {
    private val analyzer = AudioVisemeAnalyzer()

    suspend fun decodeUriToClip(uri: Uri, title: String, sourceLabel: String): AudioClipInfo =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                decodeExtractor(extractor, title, sourceLabel)
            } finally {
                extractor.release()
            }
        }

    suspend fun decodeWavFileToClip(
        wavFile: File,
        title: String,
        sourceLabel: String
    ): AudioClipInfo = withContext(Dispatchers.IO) {
        val (srcRate, rawPcm) = WavHelper.readMono16BitWav(wavFile)
        val pcm16k = if (srcRate != TARGET_SAMPLE_RATE) {
            resampleMono(rawPcm, srcRate, TARGET_SAMPLE_RATE)
        } else {
            rawPcm
        }
        val outFile = if (wavFile.parentFile?.name == "audio" && srcRate == TARGET_SAMPLE_RATE) {
            wavFile
        } else {
            val dest = File(File(context.filesDir, "audio"), "clip_${UUID.randomUUID()}.wav")
            WavHelper.writeMono16BitWav(dest, pcm16k, TARGET_SAMPLE_RATE)
            dest
        }
        buildClipInfo(outFile, pcm16k, TARGET_SAMPLE_RATE, title, sourceLabel)
    }

    private fun decodeExtractor(
        extractor: MediaExtractor,
        title: String,
        sourceLabel: String
    ): AudioClipInfo {
        var audioTrackIdx = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIdx = i
                format = f
                break
            }
        }
        require(audioTrackIdx >= 0 && format != null) { "No audio track found in selected file." }

        extractor.selectTrack(audioTrackIdx)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        val pcmBytes = ByteArrayOutputStream()

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            val maxBytes = 48_000 * 2 * 2 * 90 // Cap at ~90s safety limit

            while (!sawOutputEos && pcmBytes.size() < maxBytes) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(chunk)
                            pcmBytes.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }

        val rawBytes = pcmBytes.toByteArray()
        val shortBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalShorts = shortBuf.remaining()
        val frames = totalShorts / channelCount.coerceAtLeast(1)
        val mono = ShortArray(frames)
        for (i in 0 until frames) {
            var acc = 0
            for (c in 0 until channelCount) {
                acc += shortBuf.get(i * channelCount + c).toInt()
            }
            mono[i] = (acc / channelCount).toShort()
        }

        val resampled = if (sampleRate != TARGET_SAMPLE_RATE) {
            resampleMono(mono, sampleRate, TARGET_SAMPLE_RATE)
        } else {
            mono
        }

        val outFile = File(File(context.filesDir, "audio"), "clip_${UUID.randomUUID()}.wav")
        WavHelper.writeMono16BitWav(outFile, resampled, TARGET_SAMPLE_RATE)
        return buildClipInfo(outFile, resampled, TARGET_SAMPLE_RATE, title, sourceLabel)
    }

    private fun buildClipInfo(
        wavFile: File,
        pcm: ShortArray,
        sampleRate: Int,
        title: String,
        sourceLabel: String
    ): AudioClipInfo {
        val timeline = analyzer.analyzePcmToTimeline(pcm, sampleRate)
        val peaks = analyzer.computeWaveformPeaks(pcm, 96)
        return AudioClipInfo(
            id = UUID.randomUUID().toString(),
            title = title,
            wavFilePath = wavFile.absolutePath,
            durationMs = timeline.durationMs,
            sampleRate = sampleRate,
            waveformPeaks = peaks,
            timeline = timeline,
            sourceLabel = sourceLabel
        )
    }

    private fun resampleMono(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outSize = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outSize)
        for (i in 0 until outSize) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(input.lastIndex)
            val frac = (srcPos - i0).toFloat()
            val sample = input[i0] * (1f - frac) + input[i1] * frac
            out[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16000
    }
}
