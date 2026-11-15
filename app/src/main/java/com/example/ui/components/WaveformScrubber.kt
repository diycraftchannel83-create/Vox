package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AudioClipInfo
import com.example.model.FacialRigState
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IrisViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WaveformTimelineCard(
    clip: AudioClipInfo,
    isPlaying: Boolean,
    positionMs: Long,
    rigState: FacialRigState,
    onTogglePlay: () -> Unit,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (clip.durationMs > 0L) {
        (positionMs.toFloat() / clip.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = StudioSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(ElectricCyan, IrisViolet))
                        )
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause synced audio" else "Play synced audio",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clip.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${clip.sourceLabel} · ${clip.timeline.frames.size} rig frames",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Text(
                    text = "${formatTimecode(positionMs)} / ${formatTimecode(clip.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Waveform Scrubber
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioSurfaceHighest.copy(alpha = 0.7f))
                    .pointerInput(clip.id, clip.durationMs) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekMs((frac * clip.durationMs).toLong())
                        }
                    }
                    .pointerInput(clip.id, clip.durationMs) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekMs((frac * clip.durationMs).toLong())
                        }
                    }
                    .testTag("waveform_scrubber")
            ) {
                val peaks = clip.waveformPeaks
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    val count = peaks.size.coerceAtLeast(1)
                    val stepW = size.width / count.toFloat()
                    val barW = (stepW * 0.58f).coerceAtLeast(2f)
                    val midY = size.height / 2f

                    for (i in 0 until count) {
                        val barFrac = i.toFloat() / count.toFloat()
                        val amp = peaks[i].coerceIn(0.08f, 1f)
                        val barH = size.height * amp
                        val x = i * stepW + (stepW - barW) * 0.5f
                        val isPast = barFrac <= progress
                        drawRoundRect(
                            color = if (isPast) ElectricCyan else TextMuted.copy(alpha = 0.45f),
                            topLeft = Offset(x, midY - barH * 0.5f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f)
                        )
                    }

                    // Playhead cursor line
                    val headX = (progress * size.width).coerceIn(0f, size.width)
                    drawLine(
                        color = CoralPulse,
                        start = Offset(headX, 0f),
                        end = Offset(headX, size.height),
                        strokeWidth = 3f
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Real-time Viseme & Facial Rig Telemetry Bars
            RigTelemetryStrip(rigState = rigState)
        }
    }
}

@Composable
fun RigTelemetryStrip(
    rigState: FacialRigState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ElectricCyan.copy(alpha = 0.14f))
                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "VIS ${rigState.viseme.code}",
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan
            )
        }

        RigMeterChip(label = "JAW", value = rigState.jawOpen, modifier = Modifier.weight(1f))
        RigMeterChip(label = "WIDE", value = rigState.mouthWide, modifier = Modifier.weight(1f))
        RigMeterChip(label = "PUCK", value = rigState.mouthPucker, modifier = Modifier.weight(1f))
        RigMeterChip(label = "BROW", value = rigState.browRaise, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RigMeterChip(
    label: String,
    value: Float,
    modifier: Modifier = Modifier
) {
    val clamped = value.coerceIn(0f, 1f)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                text = "${(clamped * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(StudioSurfaceHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped.coerceAtLeast(0.04f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(listOf(ElectricCyan, CoralPulse))
                    )
            )
        }
    }
}

fun formatTimecode(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (ms % 1000L) / 100L
    return String.format("%d:%02d.%d", mins, secs, tenths)
}
