package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.render.AvatarViewport
import com.example.ui.AudioInputTab
import com.example.ui.StudioUiState
import com.example.ui.components.RenderSettingsSheet
import com.example.ui.components.RigTelemetryStrip
import com.example.ui.components.WaveformTimelineCard
import com.example.ui.components.formatTimecode
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.IrisViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StudioScreen(
    state: StudioUiState,
    onSelectInputTab: (AudioInputTab) -> Unit,
    onStartLiveMic: () -> Unit,
    onStopLiveMic: () -> Unit,
    onImportAudioUri: (Uri) -> Unit,
    onSynthesizeScript: (String, Float, Float, String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekAudio: (Long) -> Unit,
    onExportVideo: () -> Unit,
    onResetCamera: () -> Unit,
    onFpsUpdate: (Int) -> Unit,
    onSelectLighting: (com.example.model.LightingPreset) -> Unit,
    onSelectBackdrop: (com.example.model.StageBackdrop) -> Unit,
    onSelectAspectRatio: (com.example.model.VideoAspectRatio) -> Unit,
    onNavigateToAvatars: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRenderSheet by rememberSaveable { mutableStateOf(false) }
    var micPermissionDenied by remember { mutableStateOf(false) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportAudioUri)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
            onStartLiveMic()
        } else {
            micPermissionDenied = true
        }
    }

    val onMicTap = {
        if (state.isRecordingMic) {
            onStopLiveMic()
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                micPermissionDenied = false
                onStartLiveMic()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isWide = maxWidth >= 700.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioTopBar(
                        state = state,
                        onOpenRenderSheet = { showRenderSheet = true },
                        onNavigateToAvatars = onNavigateToAvatars,
                        onExportVideo = onExportVideo
                    )
                    ViewportCard(
                        state = state,
                        onResetCamera = onResetCamera,
                        onFpsUpdate = onFpsUpdate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AudioAndTimelineSection(
                        state = state,
                        micPermissionDenied = micPermissionDenied,
                        onSelectInputTab = onSelectInputTab,
                        onMicTap = onMicTap,
                        onPickAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                        onSynthesizeScript = onSynthesizeScript,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekAudio = onSeekAudio
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudioTopBar(
                    state = state,
                    onOpenRenderSheet = { showRenderSheet = true },
                    onNavigateToAvatars = onNavigateToAvatars,
                    onExportVideo = onExportVideo
                )

                ViewportCard(
                    state = state,
                    onResetCamera = onResetCamera,
                    onFpsUpdate = onFpsUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                )

                AudioAndTimelineSection(
                    state = state,
                    micPermissionDenied = micPermissionDenied,
                    onSelectInputTab = onSelectInputTab,
                    onMicTap = onMicTap,
                    onPickAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                    onSynthesizeScript = onSynthesizeScript,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekAudio = onSeekAudio
                )
            }
        }
    }

    if (showRenderSheet) {
        RenderSettingsSheet(
            lightingPreset = state.lightingPreset,
            stageBackdrop = state.stageBackdrop,
            aspectRatio = state.aspectRatio,
            onSelectLighting = onSelectLighting,
            onSelectBackdrop = onSelectBackdrop,
            onSelectAspectRatio = onSelectAspectRatio,
            onDismiss = { showRenderSheet = false }
        )
    }
}

@Composable
private fun StudioTopBar(
    state: StudioUiState,
    onOpenRenderSheet: () -> Unit,
    onNavigateToAvatars: () -> Unit,
    onExportVideo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onNavigateToAvatars)
                .testTag("active_avatar_pill"),
            shape = RoundedCornerShape(14.dp),
            color = StudioSurfaceHigh,
            border = BorderStroke(1.dp, StudioBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Face,
                    contentDescription = "Switch 3D avatar",
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.selectedAvatar.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.lightingPreset.displayName} · ${state.aspectRatio.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        IconButton(
            onClick = onOpenRenderSheet,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(StudioSurfaceHigh)
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                .testTag("render_settings_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Studio lighting and aspect ratio settings",
                tint = TextPrimary
            )
        }

        Button(
            onClick = onExportVideo,
            enabled = state.activeAudioClip != null && !state.isRecordingMic && state.exportProgress == null,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CoralPulse,
                contentColor = Color.White,
                disabledContainerColor = StudioSurfaceHigh,
                disabledContentColor = TextMuted
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier
                .height(48.dp)
                .testTag("export_mp4_button")
        ) {
            Icon(
                imageVector = Icons.Filled.MovieCreation,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export MP4", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ViewportCard(
    state: StudioUiState,
    onResetCamera: () -> Unit,
    onFpsUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = StudioSurface,
        border = BorderStroke(
            1.dp,
            if (state.isRecordingMic) CoralPulse.copy(alpha = 0.8f) else StudioBorder
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AvatarViewport(
                model = state.activeModel,
                rigState = state.rigState,
                lightingPreset = state.lightingPreset,
                stageBackdrop = state.stageBackdrop,
                resetTrigger = state.cameraResetCounter,
                onFpsUpdate = onFpsUpdate,
                modifier = Modifier.fillMaxSize()
            )

            // Top-left live HUD badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianBg.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, StudioBorder.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        state.isRecordingMic -> CoralPulse
                                        state.isPlayingAudio -> EmeraldReady
                                        else -> ElectricCyan
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                state.isRecordingMic -> "LIVE SYNC · ${formatTimecode(state.micElapsedMs)}"
                                state.isPlayingAudio -> "SYNCED · ${state.rigState.viseme.code}"
                                else -> "3D STAGE · ${state.viewportFps} FPS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Top-right Camera Reset button
            IconButton(
                onClick = onResetCamera,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp)
                    .testTag("reset_camera_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg.copy(alpha = 0.68f))
                        .border(1.dp, StudioBorder.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "Reset 3D camera",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom subtle orbit hint
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(8.dp),
                color = ObsidianBg.copy(alpha = 0.62f)
            ) {
                Text(
                    text = "Drag to orbit · Pinch to zoom · Double-tap reset",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioAndTimelineSection(
    state: StudioUiState,
    micPermissionDenied: Boolean,
    onSelectInputTab: (AudioInputTab) -> Unit,
    onMicTap: () -> Unit,
    onPickAudio: () -> Unit,
    onSynthesizeScript: (String, Float, Float, String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekAudio: (Long) -> Unit
) {
    // Loading indicator when decoding or synthesizing
    AnimatedVisibility(visible = state.isProcessingAudio) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = ElectricCyan.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = ElectricCyan
                )
                Text(
                    text = "Analyzing speech formants & building viseme rig...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricCyan
                )
            }
        }
    }

    // Active Audio Timeline Scrubber
    if (state.activeAudioClip != null && !state.isRecordingMic) {
        WaveformTimelineCard(
            clip = state.activeAudioClip,
            isPlaying = state.isPlayingAudio,
            positionMs = state.playbackPosMs,
            rigState = state.rigState,
            onTogglePlay = onTogglePlayPause,
            onSeekMs = onSeekAudio
        )
    } else if (state.isRecordingMic) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = StudioSurfaceHigh,
            border = BorderStroke(1.dp, CoralPulse.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RigTelemetryStrip(rigState = state.rigState)
            }
        }
    }

    // 3-Tab Audio Input Dock
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Segmented Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSurfaceHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AudioInputTab.entries.forEach { tab ->
                    val selected = state.activeInputTab == tab
                    val icon = when (tab) {
                        AudioInputTab.MIC_LIVE -> Icons.Filled.Mic
                        AudioInputTab.AUDIO_FILE -> Icons.Filled.AudioFile
                        AudioInputTab.VOICE_SCRIPT -> Icons.Filled.RecordVoiceOver
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) ElectricCyan.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .clickable { onSelectInputTab(tab) }
                            .testTag("tab_${tab.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) ElectricCyan else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            when (state.activeInputTab) {
                AudioInputTab.MIC_LIVE -> {
                    MicLiveControls(
                        isRecording = state.isRecordingMic,
                        micLevel = state.micLevel,
                        elapsedMs = state.micElapsedMs,
                        permissionDenied = micPermissionDenied,
                        onMicTap = onMicTap
                    )
                }

                AudioInputTab.AUDIO_FILE -> {
                    AudioFileControls(onPickAudio = onPickAudio)
                }

                AudioInputTab.VOICE_SCRIPT -> {
                    VoiceScriptControls(
                        isBusy = state.isProcessingAudio,
                        onSynthesize = onSynthesizeScript
                    )
                }
            }
        }
    }
}

@Composable
private fun MicLiveControls(
    isRecording: Boolean,
    micLevel: Float,
    elapsedMs: Long,
    permissionDenied: Boolean,
    onMicTap: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (permissionDenied) {
            Text(
                text = "Microphone permission is needed for live voice capture. Grant mic access or switch to Audio File / Script.",
                style = MaterialTheme.typography.bodyMedium,
                color = CoralPulse
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .scale(pulseScale + micLevel * 0.25f)
                        .clip(CircleShape)
                        .background(CoralPulse.copy(alpha = 0.22f))
                )
            }

            Button(
                onClick = onMicTap,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) CoralPulse else ElectricCyan,
                    contentColor = if (isRecording) Color.White else ObsidianBg
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(68.dp)
                    .testTag("mic_record_button")
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isRecording) "Stop recording" else "Start live mic lip-sync",
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Text(
            text = if (isRecording) {
                "Speaking live (${formatTimecode(elapsedMs)}) · Tap Stop to lock clip"
            } else {
                "Tap Mic to speak & synchronize the 3D avatar in real time"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isRecording) CoralPulse else TextSecondary
        )
    }
}

@Composable
private fun AudioFileControls(onPickAudio: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Import any speech recording (.mp3, .wav, .m4a, .aac, .ogg) to automatically extract formants & viseme keyframes.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Button(
            onClick = onPickAudio,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = ObsidianBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("pick_audio_file_button")
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose Audio File", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceScriptControls(
    isBusy: Boolean,
    onSynthesize: (String, Float, Float, String) -> Unit
) {
    var scriptText by rememberSaveable {
        mutableStateOf("Welcome to VoxSculpt. Every spoken syllable drives my 3D facial rig in real time.")
    }
    var pitch by rememberSaveable { mutableFloatStateOf(1.0f) }
    var rate by rememberSaveable { mutableFloatStateOf(1.0f) }
    var languageTag by rememberSaveable { mutableStateOf("id-ID") }

    val speechLanguages = listOf(
        "id-ID" to "Indonesia",
        "en-US" to "English",
        "ar-SA" to "العربية",
        "ja-JP" to "日本語",
        "ko-KR" to "한국어"
    )

    val quickScripts = listOf(
        "Studio Intro" to "Welcome to VoxSculpt. Every spoken syllable drives my 3D facial rig in real time.",
        "Product Pitch" to "Import any custom three-D avatar, drop in your voice track, and render crisp sixty-FPS video.",
        "Character Line" to "All systems online. Vocal synchronization locked and ready for broadcast."
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickScripts.forEach { (label, line) ->
                AssistChip(
                    onClick = { scriptText = line },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = StudioSurfaceHigh,
                        labelColor = ElectricCyan
                    ),
                    border = BorderStroke(1.dp, StudioBorder)
                )
            }
        }

        Text(
            text = "Speech language · requires an installed device voice",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            speechLanguages.forEach { (tag, label) ->
                AssistChip(
                    onClick = { languageTag = tag },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (languageTag == tag) ElectricCyan.copy(alpha = 0.2f) else StudioSurfaceHigh,
                        labelColor = if (languageTag == tag) ElectricCyan else TextSecondary
                    ),
                    border = BorderStroke(1.dp, if (languageTag == tag) ElectricCyan else StudioBorder)
                )
            }
        }

        OutlinedTextField(
            value = scriptText,
            onValueChange = { scriptText = it },
            label = { Text("Spoken Script") },
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = StudioBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("script_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pitch ${String.format("%.1fx", pitch)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pace ${String.format("%.1fx", rate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Slider(
                    value = rate,
                    onValueChange = { rate = it },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = IrisViolet, activeTrackColor = IrisViolet)
                )
            }
        }

        Button(
            onClick = { onSynthesize(scriptText, pitch, rate, languageTag) },
            enabled = !isBusy && scriptText.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = ObsidianBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("synthesize_speech_button")
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate Speech & Lip-Sync", style = MaterialTheme.typography.labelLarge)
        }
    }
}
