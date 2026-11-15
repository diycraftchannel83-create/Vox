package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDecoder
import com.example.audio.AudioPlayerController
import com.example.audio.LiveMicRecorder
import com.example.audio.TtsSpeechGenerator
import com.example.avatar.AvatarImporter
import com.example.avatar.AvatarModel3D
import com.example.avatar.ProceduralAvatarFactory
import com.example.data.db.StudioDatabase
import com.example.data.db.StudioPreferences
import com.example.model.AudioClipInfo
import com.example.model.AvatarSpec
import com.example.model.ExportedClip
import com.example.model.FacialRigState
import com.example.model.LightingPreset
import com.example.model.StageBackdrop
import com.example.model.VideoAspectRatio
import com.example.video.ExportProgress
import com.example.video.VideoClipExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sin

enum class AudioInputTab(val label: String) {
    MIC_LIVE("Mic Live"),
    AUDIO_FILE("Audio File"),
    VOICE_SCRIPT("Script")
}

data class StudioUiState(
    val avatars: List<AvatarSpec> = ProceduralAvatarFactory.builtInSpecs,
    val selectedAvatar: AvatarSpec = ProceduralAvatarFactory.builtInSpecs.first(),
    val activeModel: AvatarModel3D = ProceduralAvatarFactory.buildAvatar("nova"),
    val lightingPreset: LightingPreset = LightingPreset.CINEMA_KEY,
    val stageBackdrop: StageBackdrop = StageBackdrop.OBSIDIAN_STUDIO,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.PORTRAIT_9_16,
    val activeInputTab: AudioInputTab = AudioInputTab.MIC_LIVE,
    val isRecordingMic: Boolean = false,
    val micLevel: Float = 0f,
    val micElapsedMs: Long = 0L,
    val isProcessingAudio: Boolean = false,
    val activeAudioClip: AudioClipInfo? = null,
    val isPlayingAudio: Boolean = false,
    val playbackPosMs: Long = 0L,
    val rigState: FacialRigState = FacialRigState(),
    val viewportFps: Int = 60,
    val cameraResetCounter: Int = 0,
    val exportProgress: ExportProgress? = null,
    val exportedClips: List<ExportedClip> = emptyList(),
    val latestExportedClip: ExportedClip? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = StudioDatabase.getInstance(application).studioDao()
    private val prefs = StudioPreferences(application)
    private val avatarImporter = AvatarImporter(application)
    private val audioDecoder = AudioDecoder(application)
    private val micRecorder = LiveMicRecorder(application)
    private val ttsGenerator = TtsSpeechGenerator(application)
    private val player = AudioPlayerController()
    private val videoExporter = VideoClipExporter(application)

    private val _selectedAvatarSpec = MutableStateFlow(ProceduralAvatarFactory.builtInSpecs.first())
    private val _activeModel = MutableStateFlow(ProceduralAvatarFactory.buildAvatar("nova"))
    private val _activeInputTab = MutableStateFlow(AudioInputTab.MIC_LIVE)
    private val _isRecordingMic = MutableStateFlow(false)
    private val _micLevel = MutableStateFlow(0f)
    private val _micElapsedMs = MutableStateFlow(0L)
    private val _isProcessingAudio = MutableStateFlow(false)
    private val _activeAudioClip = MutableStateFlow<AudioClipInfo?>(null)
    private val _rigState = MutableStateFlow(FacialRigState())
    private val _viewportFps = MutableStateFlow(60)
    private val _cameraResetCounter = MutableStateFlow(0)
    private val _exportProgress = MutableStateFlow<ExportProgress?>(null)
    private val _latestExportedClip = MutableStateFlow<ExportedClip?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private var exportJob: Job? = null
    private var micTimerJob: Job? = null

    private val allAvatarsFlow = dao.observeImportedAvatars().map { entities ->
        ProceduralAvatarFactory.builtInSpecs + entities.map { it.toDomain() }
    }

    private val exportedClipsFlow = dao.observeExportedClips().map { entities ->
        entities.map { it.toDomain() }
    }

    val uiState: StateFlow<StudioUiState> = combine(
        allAvatarsFlow,
        prefs.settingsFlow,
        _activeModel,
        _activeInputTab,
        _isRecordingMic,
        _micLevel,
        _micElapsedMs,
        _isProcessingAudio,
        _activeAudioClip,
        player.isPlaying,
        player.positionMs,
        _rigState,
        _viewportFps,
        _cameraResetCounter,
        _exportProgress,
        exportedClipsFlow,
        _latestExportedClip,
        _statusMessage,
        _errorMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val avatars = values[0] as List<AvatarSpec>
        val settings = values[1] as com.example.data.db.StudioSettings
        val model = values[2] as AvatarModel3D
        val inputTab = values[3] as AudioInputTab
        val recMic = values[4] as Boolean
        val micLvl = values[5] as Float
        val micMs = values[6] as Long
        val procAudio = values[7] as Boolean
        val audioClip = values[8] as AudioClipInfo?
        val playing = values[9] as Boolean
        val posMs = values[10] as Long
        val rig = values[11] as FacialRigState
        val fps = values[12] as Int
        val resetCnt = values[13] as Int
        val expProg = values[14] as ExportProgress?
        @Suppress("UNCHECKED_CAST")
        val clips = values[15] as List<ExportedClip>
        val latestClip = values[16] as ExportedClip?
        val statusMsg = values[17] as String?
        val errMsg = values[18] as String?

        val selected = avatars.firstOrNull { it.id == settings.selectedAvatarId } ?: avatars.first()
        StudioUiState(
            avatars = avatars,
            selectedAvatar = selected,
            activeModel = model,
            lightingPreset = settings.lightingPreset,
            stageBackdrop = settings.stageBackdrop,
            aspectRatio = settings.aspectRatio,
            activeInputTab = inputTab,
            isRecordingMic = recMic,
            micLevel = micLvl,
            micElapsedMs = micMs,
            isProcessingAudio = procAudio,
            activeAudioClip = audioClip,
            isPlayingAudio = playing,
            playbackPosMs = posMs,
            rigState = rig,
            viewportFps = fps,
            cameraResetCounter = resetCnt,
            exportProgress = expProg,
            exportedClips = clips,
            latestExportedClip = latestClip,
            statusMessage = statusMsg,
            errorMessage = errMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioUiState()
    )

    init {
        // Load persisted avatar selection on startup
        viewModelScope.launch {
            combine(allAvatarsFlow, prefs.settingsFlow) { avatars, settings ->
                avatars.firstOrNull { it.id == settings.selectedAvatarId } ?: avatars.first()
            }.collect { spec ->
                if (_selectedAvatarSpec.value.id != spec.id) {
                    _selectedAvatarSpec.value = spec
                    val loaded = withContext(Dispatchers.Default) {
                        avatarImporter.loadModelFromSpec(spec)
                    }
                    _activeModel.value = loaded
                }
            }
        }

        // Subtle idle breathing & blinking loop when idle
        viewModelScope.launch {
            var t = 0f
            while (isActive) {
                if (!_isRecordingMic.value && !player.isPlaying.value) {
                    val blinkCycle = t % 4.1f
                    val blink = when {
                        blinkCycle < 0.08f -> blinkCycle / 0.08f
                        blinkCycle < 0.18f -> 1f - (blinkCycle - 0.08f) / 0.10f
                        else -> 0f
                    }.coerceIn(0f, 1f)
                    _rigState.value = FacialRigState(
                        eyeBlink = blink,
                        headPitch = 0.025f * sin(t * 1.1f),
                        headYaw = 0.04f * sin(t * 0.65f),
                        headRoll = 0.015f * sin(t * 0.85f)
                    )
                }
                delay(33L)
                t += 0.033f
            }
        }
    }

    fun selectInputTab(tab: AudioInputTab) {
        _activeInputTab.value = tab
    }

    fun selectAvatar(spec: AvatarSpec) {
        viewModelScope.launch {
            prefs.setSelectedAvatar(spec.id)
            _selectedAvatarSpec.value = spec
            val loaded = withContext(Dispatchers.Default) {
                avatarImporter.loadModelFromSpec(spec)
            }
            _activeModel.value = loaded
        }
    }

    fun setLightingPreset(preset: LightingPreset) {
        viewModelScope.launch { prefs.setLightingPreset(preset) }
    }

    fun setStageBackdrop(backdrop: StageBackdrop) {
        viewModelScope.launch { prefs.setStageBackdrop(backdrop) }
    }

    fun setAspectRatio(ratio: VideoAspectRatio) {
        viewModelScope.launch { prefs.setAspectRatio(ratio) }
    }

    fun resetCamera() {
        _cameraResetCounter.value += 1
    }

    fun updateViewportFps(fps: Int) {
        _viewportFps.value = fps
    }

    fun startLiveMic() {
        if (_isRecordingMic.value) return
        player.pause()
        _isRecordingMic.value = true
        _micElapsedMs.value = 0L
        micTimerJob?.cancel()
        micTimerJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            while (isActive && _isRecordingMic.value) {
                _micElapsedMs.value = System.currentTimeMillis() - startMs
                delay(50L)
            }
        }
        micRecorder.start(
            scope = viewModelScope,
            onLiveRig = { rig, level ->
                _rigState.value = rig
                _micLevel.value = level
            },
            onError = { msg ->
                _isRecordingMic.value = false
                _micLevel.value = 0f
                micTimerJob?.cancel()
                _errorMessage.value = msg
            }
        )
    }

    fun stopLiveMic() {
        if (!_isRecordingMic.value) return
        _isRecordingMic.value = false
        _micLevel.value = 0f
        micTimerJob?.cancel()
        viewModelScope.launch {
            _isProcessingAudio.value = true
            try {
                val wav = micRecorder.stopAndGetFile()
                if (wav != null) {
                    val clip = audioDecoder.decodeWavFileToClip(
                        wavFile = wav,
                        title = "Voice Recording",
                        sourceLabel = "Live Mic"
                    )
                    activateAudioClip(clip, autoPlay = true)
                    _statusMessage.value = "Captured ${formatMs(clip.durationMs)} voice clip with lip-sync"
                } else {
                    _errorMessage.value = "Recording was too short to analyze."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to finalize recording."
            } finally {
                _isProcessingAudio.value = false
            }
        }
    }

    fun importAudioUri(uri: Uri) {
        player.pause()
        viewModelScope.launch {
            _isProcessingAudio.value = true
            try {
                val name = resolveFileName(uri).substringBeforeLast('.').ifEmpty { "Imported Audio" }
                val clip = audioDecoder.decodeUriToClip(
                    uri = uri,
                    title = name,
                    sourceLabel = "Audio File"
                )
                activateAudioClip(clip, autoPlay = true)
                _statusMessage.value = "Synchronized \"$name\" (${formatMs(clip.durationMs)})"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not decode selected audio file."
            } finally {
                _isProcessingAudio.value = false
            }
        }
    }

    fun synthesizeScript(text: String, pitch: Float = 1.0f, rate: Float = 1.0f) {
        player.pause()
        viewModelScope.launch {
            _isProcessingAudio.value = true
            try {
                val wav = ttsGenerator.synthesizeToWav(text, pitch = pitch, rate = rate)
                val shortTitle = text.trim().take(28).let { if (text.trim().length > 28) "$it..." else it }
                val clip = audioDecoder.decodeWavFileToClip(
                    wavFile = wav,
                    title = shortTitle,
                    sourceLabel = "Voice Script"
                )
                activateAudioClip(clip, autoPlay = true)
                _statusMessage.value = "Synthesized speech & synchronized ${clip.timeline.frames.size} facial frames"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Speech synthesis failed."
            } finally {
                _isProcessingAudio.value = false
            }
        }
    }

    fun togglePlayPause() {
        val clip = _activeAudioClip.value ?: return
        if (player.isPlaying.value) {
            player.pause()
        } else {
            player.load(clip.wavFilePath)
            player.play(
                scope = viewModelScope,
                onPositionUpdate = { posMs ->
                    _rigState.value = clip.timeline.sampleAt(posMs)
                },
                onCompleted = {
                    _rigState.value = FacialRigState()
                }
            )
        }
    }

    fun seekAudio(positionMs: Long) {
        val clip = _activeAudioClip.value ?: return
        player.load(clip.wavFilePath)
        player.seekTo(positionMs) { pos ->
            _rigState.value = clip.timeline.sampleAt(pos)
        }
    }

    fun importCustomAvatar(uri: Uri) {
        viewModelScope.launch {
            _isProcessingAudio.value = true
            try {
                val (entity, model) = avatarImporter.importFromUri(uri)
                dao.insertImportedAvatar(entity)
                prefs.setSelectedAvatar(entity.id)
                _activeModel.value = model
                _statusMessage.value =
                    "Imported ${entity.name} (${entity.triangleCount} tris" +
                        if (entity.morphTargetCount > 0) ", ${entity.morphTargetCount} morphs)" else ", auto-rigged)"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to import 3D model."
            } finally {
                _isProcessingAudio.value = false
            }
        }
    }

    fun deleteCustomAvatar(spec: AvatarSpec) {
        if (spec.isBuiltIn) return
        viewModelScope.launch {
            dao.deleteImportedAvatar(spec.id)
            spec.filePath?.let { path -> File(path).delete() }
            if (_selectedAvatarSpec.value.id == spec.id) {
                selectAvatar(ProceduralAvatarFactory.builtInSpecs.first())
            }
            _statusMessage.value = "Removed ${spec.name}"
        }
    }

    fun exportVideoClip(customTitle: String? = null) {
        val clip = _activeAudioClip.value
        if (clip == null) {
            _errorMessage.value = "Record or import audio first before generating a video clip."
            return
        }
        if (_exportProgress.value != null) return

        player.pause()
        val settings = uiState.value
        val model = settings.activeModel
        val title = customTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${model.name} - ${clip.title}"

        exportJob = viewModelScope.launch {
            try {
                val entity = videoExporter.exportMp4(
                    title = title,
                    model = model,
                    wavFilePath = clip.wavFilePath,
                    durationMs = clip.durationMs,
                    timeline = clip.timeline,
                    aspectRatio = settings.aspectRatio,
                    lightingPreset = settings.lightingPreset,
                    stageBackdrop = settings.stageBackdrop,
                    fps = 30,
                    onProgress = { progress ->
                        _exportProgress.value = progress
                    }
                )
                dao.insertExportedClip(entity)
                _latestExportedClip.value = entity.toDomain()
                _statusMessage.value = "Saved MP4 video (${entity.width}x${entity.height})"
            } catch (e: kotlinx.coroutines.CancellationException) {
                _statusMessage.value = "Video export cancelled"
            } catch (e: Exception) {
                _errorMessage.value = "Video export failed: ${e.message ?: "encoder error"}"
            } finally {
                _exportProgress.value = null
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _exportProgress.value = null
    }

    fun deleteExportedClip(clip: ExportedClip) {
        viewModelScope.launch {
            dao.deleteExportedClip(clip.id)
            File(clip.videoPath).delete()
            if (_latestExportedClip.value?.id == clip.id) {
                _latestExportedClip.value = null
            }
            _statusMessage.value = "Deleted ${clip.title}"
        }
    }

    fun dismissLatestExportedClip() {
        _latestExportedClip.value = null
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun consumeErrorMessage() {
        _errorMessage.value = null
    }

    private fun activateAudioClip(clip: AudioClipInfo, autoPlay: Boolean) {
        _activeAudioClip.value = clip
        player.load(clip.wavFilePath)
        if (autoPlay) {
            player.play(
                scope = viewModelScope,
                onPositionUpdate = { posMs ->
                    _rigState.value = clip.timeline.sampleAt(posMs)
                },
                onCompleted = {
                    _rigState.value = FacialRigState()
                }
            )
        }
    }

    private fun resolveFileName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "audio"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
    }

    private fun formatMs(ms: Long): String {
        val sec = ms / 1000L
        val tenths = (ms % 1000L) / 100L
        return "${sec}.${tenths}s"
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
