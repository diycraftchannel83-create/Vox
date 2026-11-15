package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.model.FacialRigState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class LiveMicRecorder(private val context: Context) {
    private val sampleRate = 16000
    private val analyzer = AudioVisemeAnalyzer(fftSize = 512)
    private var recordingJob: Job? = null
    private var currentFile: File? = null

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onLiveRig: (FacialRigState, Float) -> Unit,
        onError: (String) -> Unit
    ) {
        stopInternal()
        analyzer.reset()

        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufBytes * 2
            )
        } catch (e: Exception) {
            onError(e.message ?: "Unable to open microphone")
            return
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onError("Microphone initialization failed")
            return
        }

        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val outFile = File(audioDir, "mic_${UUID.randomUUID()}.wav")
        currentFile = outFile

        recordingJob = scope.launch(Dispatchers.IO) {
            val readShorts = ShortArray(320) // ~20ms chunks = 50Hz live lip-sync
            val fftWindow = FloatArray(512)
            var startTimeNs = System.nanoTime()
            var lastFrameNs = startTimeNs

            FileOutputStream(outFile).use { fos ->
                WavHelper.writeWavHeaderPlaceholder(fos, sampleRate)
                try {
                    audioRecord.startRecording()
                    while (isActive) {
                        val read = audioRecord.read(readShorts, 0, readShorts.size)
                        if (read > 0) {
                            val bb = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until read) {
                                bb.putShort(readShorts[i])
                            }
                            fos.write(bb.array())

                            // Shift sliding FFT buffer and populate latest samples
                            val keep = (fftWindow.size - read).coerceAtLeast(0)
                            if (keep > 0) {
                                System.arraycopy(fftWindow, read, fftWindow, 0, keep)
                            }
                            val copyStart = keep
                            val srcStart = (read - fftWindow.size).coerceAtLeast(0)
                            val count = minOf(read, fftWindow.size)
                            for (i in 0 until count) {
                                fftWindow[copyStart + i] = readShorts[srcStart + i] / 32768f
                            }

                            val nowNs = System.nanoTime()
                            val elapsedSec = (nowNs - startTimeNs) / 1_000_000_000f
                            val dtSec = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.005f, 0.08f)
                            lastFrameNs = nowNs

                            val rig = analyzer.processFrame(fftWindow, sampleRate, elapsedSec, dtSec)
                            onLiveRig(rig, rig.vocalEnergy)
                        }
                    }
                } catch (e: Exception) {
                    onError(e.message ?: "Recording interrupted")
                } finally {
                    try {
                        audioRecord.stop()
                    } catch (_: Exception) {
                    }
                    audioRecord.release()
                }
            }
            WavHelper.finalizeWavHeader(outFile, sampleRate)
        }
    }

    suspend fun stopAndGetFile(): File? {
        val job = recordingJob
        recordingJob = null
        job?.cancel()
        job?.join()
        val file = currentFile
        currentFile = null
        return file?.takeIf { it.exists() && it.length() > 128 }
    }

    private fun stopInternal() {
        recordingJob?.cancel()
        recordingJob = null
    }
}
