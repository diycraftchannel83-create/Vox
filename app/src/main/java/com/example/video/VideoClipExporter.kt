package com.example.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.audio.WavHelper
import com.example.avatar.AvatarModel3D
import com.example.data.db.ExportedClipEntity
import com.example.model.LightingPreset
import com.example.model.StageBackdrop
import com.example.model.VideoAspectRatio
import com.example.render.AvatarGlRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class ExportProgress(
    val fraction: Float,
    val renderedFrames: Int,
    val totalFrames: Int,
    val stageLabel: String
)

class VideoClipExporter(private val context: Context) {

    private class EncodedSample(
        val data: ByteArray,
        val size: Int,
        val presentationTimeUs: Long,
        val flags: Int
    )

    suspend fun exportMp4(
        title: String,
        model: AvatarModel3D,
        wavFilePath: String,
        durationMs: Long,
        timeline: com.example.model.VisemeTimeline,
        aspectRatio: VideoAspectRatio,
        lightingPreset: LightingPreset,
        stageBackdrop: StageBackdrop,
        fps: Int = 30,
        onProgress: (ExportProgress) -> Unit
    ): ExportedClipEntity = withContext(Dispatchers.Default) {
        val width = aspectRatio.width
        val height = aspectRatio.height
        val safeDurationMs = durationMs.coerceAtLeast(500L)
        val totalFrames = ((safeDurationMs * fps) / 1000L).toInt().coerceAtLeast(15)

        onProgress(ExportProgress(0.03f, 0, totalFrames, "Encoding AAC audio track..."))
        val (audioFormat, audioSamples) = encodeWavToAacSamples(File(wavFilePath))
        currentCoroutineContext().ensureActive()

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val clipId = UUID.randomUUID().toString()
        val outFile = File(exportsDir, "voxsculpt_${clipId.take(8)}.mp4")

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoCodec.createInputSurface()
        videoCodec.start()

        val egl = EglOffscreenSurface(inputSurface)
        egl.makeCurrent()

        val renderer = AvatarGlRenderer(model).apply {
            this.lightingPreset = lightingPreset
            this.stageBackdrop = stageBackdrop
        }
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, width, height)

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIdx = -1
        var audioTrackIdx = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        fun drainVideo(endOfStream: Boolean) {
            if (endOfStream) {
                videoCodec.signalEndOfInputStream()
            }
            while (true) {
                val outIdx = videoCodec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) break
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            videoTrackIdx = muxer.addTrack(videoCodec.outputFormat)
                            audioTrackIdx = muxer.addTrack(audioFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val encodedBuf = videoCodec.getOutputBuffer(outIdx)
                        if (encodedBuf != null && bufferInfo.size > 0 &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                            muxerStarted
                        ) {
                            encodedBuf.position(bufferInfo.offset)
                            encodedBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIdx, encodedBuf, bufferInfo)
                        }
                        videoCodec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            }
        }

        try {
            for (frame in 0 until totalFrames) {
                currentCoroutineContext().ensureActive()
                val ptsMs = (frame * 1000L) / fps
                val ptsNs = ptsMs * 1_000_000L
                val rig = timeline.sampleAt(ptsMs)

                renderer.renderFrame(rig)
                egl.setPresentationTime(ptsNs)
                egl.swapBuffers()

                drainVideo(endOfStream = false)

                if (frame % 3 == 0 || frame == totalFrames - 1) {
                    val frac = 0.08f + 0.84f * ((frame + 1).toFloat() / totalFrames.toFloat())
                    onProgress(
                        ExportProgress(
                            fraction = frac.coerceIn(0f, 0.94f),
                            renderedFrames = frame + 1,
                            totalFrames = totalFrames,
                            stageLabel = "Rendering 3D frames (${frame + 1}/$totalFrames)..."
                        )
                    )
                }
            }

            onProgress(ExportProgress(0.95f, totalFrames, totalFrames, "Finalizing MP4 & audio mux..."))
            drainVideo(endOfStream = true)

            if (muxerStarted && audioTrackIdx >= 0) {
                val sampleInfo = MediaCodec.BufferInfo()
                for (sample in audioSamples) {
                    val buf = ByteBuffer.wrap(sample.data, 0, sample.size)
                    sampleInfo.set(0, sample.size, sample.presentationTimeUs, sample.flags)
                    muxer.writeSampleData(audioTrackIdx, buf, sampleInfo)
                }
            }
        } finally {
            renderer.release()
            egl.release()
            try {
                videoCodec.stop()
            } catch (_: Exception) {
            }
            videoCodec.release()
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            muxer.release()
        }

        onProgress(ExportProgress(0.98f, totalFrames, totalFrames, "Saving to Movies/VoxSculpt..."))
        val galleryUri = saveToMediaStoreGallery(outFile, title)
        onProgress(ExportProgress(1f, totalFrames, totalFrames, "Export complete"))

        ExportedClipEntity(
            id = clipId,
            title = title,
            videoPath = outFile.absolutePath,
            galleryUri = galleryUri,
            avatarName = model.name,
            durationMs = safeDurationMs,
            width = width,
            height = height,
            fps = fps,
            fileSizeBytes = outFile.length(),
            createdAtMs = System.currentTimeMillis()
        )
    }

    private fun encodeWavToAacSamples(wavFile: File): Pair<MediaFormat, List<EncodedSample>> {
        val (srcRate, rawPcm) = WavHelper.readMono16BitWav(wavFile)
        val targetRate = 44100
        val pcm44k = resampleTo44100(rawPcm, srcRate, targetRate)

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, targetRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val samples = ArrayList<EncodedSample>()
        var outputFormat: MediaFormat = format
        val bufferInfo = MediaCodec.BufferInfo()

        var sampleCursor = 0
        var inputDone = false
        var outputDone = false
        val chunkShorts = 1024

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        if (sampleCursor >= pcm44k.size) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0,
                                (sampleCursor.toLong() * 1_000_000L) / targetRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val remaining = pcm44k.size - sampleCursor
                            val maxBufShorts = inBuf.remaining() / 2
                            val count = minOf(chunkShorts, remaining, maxBufShorts)
                            val bb = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until count) {
                                bb.putShort(pcm44k[sampleCursor + i])
                            }
                            inBuf.put(bb.array())
                            val ptsUs = (sampleCursor.toLong() * 1_000_000L) / targetRate
                            codec.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                            sampleCursor += count
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(bytes)
                            samples.add(
                                EncodedSample(
                                    data = bytes,
                                    size = bufferInfo.size,
                                    presentationTimeUs = bufferInfo.presentationTimeUs,
                                    flags = bufferInfo.flags
                                )
                            )
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }

        return outputFormat to samples
    }

    private fun resampleTo44100(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty()) return ShortArray(dstRate / 2)
        if (srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLen = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceIn(0, input.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(input.lastIndex)
            val frac = (pos - i0).toFloat()
            val v = input[i0] * (1f - frac) + input[i1] * frac
            out[i] = v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun saveToMediaStoreGallery(mp4File: File, title: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifEmpty { "VoxSculpt" }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${safeTitle}_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VoxSculpt")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                mp4File.inputStream().use { inp -> inp.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    private class EglOffscreenSurface(surface: android.view.Surface) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT_KHR
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0] ?: throw IllegalStateException("No EGLConfig for video export")

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfAttribs, 0)
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
