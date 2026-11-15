package com.example.audio

import com.example.model.FacialRigState
import com.example.model.VisemeFrame
import com.example.model.VisemeTimeline
import com.example.model.VisemeType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class AudioVisemeAnalyzer(private val fftSize: Int = 512) {
    private val fft = FftProcessor(fftSize)
    private var smoothJaw = 0f
    private var smoothWide = 0f
    private var smoothPucker = 0f
    private var smoothPress = 0f
    private var smoothUpperLip = 0f
    private var smoothTongue = 0f
    private var smoothEnergy = 0f
    private var smoothBrow = 0f
    private var prevRms = 0f
    private var blinkClock = 0f

    fun reset() {
        smoothJaw = 0f
        smoothWide = 0f
        smoothPucker = 0f
        smoothPress = 0f
        smoothUpperLip = 0f
        smoothTongue = 0f
        smoothEnergy = 0f
        smoothBrow = 0f
        prevRms = 0f
        blinkClock = 0f
    }

    fun processFrame(
        samples: FloatArray,
        sampleRate: Int,
        timeSec: Float,
        dtSec: Float
    ): FacialRigState {
        val feat = fft.analyze(samples, sampleRate)

        // Normalize RMS into speech activity [0..1]
        val energy = ((feat.rmsEnergy - 0.012f) / 0.19f).coerceIn(0f, 1f)
        val onset = (energy - prevRms).coerceAtLeast(0f)
        prevRms = energy

        val totalBand = (feat.lowEnergy + feat.midEnergy + feat.highEnergy).coerceAtLeast(1e-5f)
        val lowRatio = feat.lowEnergy / totalBand
        val midRatio = feat.midEnergy / totalBand
        val highRatio = feat.highEnergy / totalBand

        var targetViseme = VisemeType.SIL
        var targetJaw = 0f
        var targetWide = 0f
        var targetPucker = 0f
        var targetPress = 0f
        var targetUpperLip = 0f
        var targetTongue = 0f

        if (energy > 0.05f) {
            when {
                // Sharp low-energy onset spike -> bilabial plosive P / B / M
                onset > 0.28f && lowRatio > 0.45f -> {
                    targetViseme = VisemeType.PP
                    targetPress = 0.85f
                    targetJaw = 0.08f
                }
                // High-frequency fricative/sibilant energy -> SS / CH / FF
                highRatio > 0.34f || feat.zeroCrossingRate > 0.24f -> {
                    if (feat.spectralCentroidHz > 3400f) {
                        targetViseme = VisemeType.SS
                        targetWide = 0.55f
                        targetJaw = 0.16f + energy * 0.18f
                        targetTongue = 0.65f
                    } else if (midRatio > 0.26f) {
                        targetViseme = VisemeType.CH
                        targetPucker = 0.48f
                        targetJaw = 0.24f + energy * 0.2f
                    } else {
                        targetViseme = VisemeType.FF
                        targetUpperLip = 0.62f
                        targetJaw = 0.12f
                    }
                }
                // Dominant low formant -> AA / OH / OU
                lowRatio > 0.56f -> {
                    if (midRatio < 0.18f) {
                        targetViseme = VisemeType.OU
                        targetPucker = 0.78f * (0.6f + energy * 0.4f)
                        targetJaw = 0.28f + energy * 0.32f
                    } else if (energy > 0.46f) {
                        targetViseme = VisemeType.AA
                        targetJaw = (0.45f + energy * 0.52f).coerceIn(0f, 1f)
                        targetWide = 0.18f
                    } else {
                        targetViseme = VisemeType.OH
                        targetPucker = 0.52f
                        targetJaw = 0.36f + energy * 0.34f
                    }
                }
                // Strong mid formant -> EE / IH
                midRatio > 0.35f -> {
                    if (energy > 0.4f) {
                        targetViseme = VisemeType.EE
                        targetWide = 0.78f
                        targetJaw = 0.32f + energy * 0.34f
                    } else {
                        targetViseme = VisemeType.IH
                        targetWide = 0.58f
                        targetJaw = 0.22f + energy * 0.24f
                    }
                }
                // Balanced consonant/vowel transition -> DD / NN / RR
                else -> {
                    targetViseme = if (onset > 0.14f) VisemeType.DD else VisemeType.NN
                    targetJaw = 0.24f + energy * 0.36f
                    targetTongue = 0.55f
                    targetWide = 0.20f
                }
            }
        }

        // Critically damped co-articulation smoothing (fast attack, smooth release)
        smoothJaw = smoothTowards(smoothJaw, targetJaw, dtSec, attackHz = 28f, releaseHz = 16f)
        smoothWide = smoothTowards(smoothWide, targetWide, dtSec, attackHz = 22f, releaseHz = 14f)
        smoothPucker = smoothTowards(smoothPucker, targetPucker, dtSec, attackHz = 22f, releaseHz = 14f)
        smoothPress = smoothTowards(smoothPress, targetPress, dtSec, attackHz = 30f, releaseHz = 18f)
        smoothUpperLip = smoothTowards(smoothUpperLip, targetUpperLip, dtSec, attackHz = 24f, releaseHz = 15f)
        smoothTongue = smoothTowards(smoothTongue, targetTongue, dtSec, attackHz = 24f, releaseHz = 15f)
        smoothEnergy = smoothTowards(smoothEnergy, energy, dtSec, attackHz = 18f, releaseHz = 9f)

        val browTarget = (energy * 0.55f + onset * 0.8f).coerceIn(0f, 0.85f)
        smoothBrow = smoothTowards(smoothBrow, browTarget, dtSec, attackHz = 8f, releaseHz = 4f)

        // Natural blink cycle (~every 3.8s)
        blinkClock += dtSec
        val blinkCycle = blinkClock % 3.8f
        val eyeBlink = when {
            blinkCycle < 0.09f -> blinkCycle / 0.09f
            blinkCycle < 0.19f -> 1f - (blinkCycle - 0.09f) / 0.10f
            else -> 0f
        }.coerceIn(0f, 1f)

        // Natural organic speech head motion
        val headPitch = (-0.035f * sin(timeSec * 3.1f) + smoothEnergy * 0.075f * sin(timeSec * 5.2f))
        val headYaw = 0.065f * sin(timeSec * 1.45f) + smoothEnergy * 0.045f * cos(timeSec * 2.3f)
        val headRoll = 0.03f * sin(timeSec * 1.9f)

        return FacialRigState(
            viseme = targetViseme,
            jawOpen = smoothJaw.coerceIn(0f, 1f),
            mouthWide = smoothWide.coerceIn(0f, 1f),
            mouthPucker = smoothPucker.coerceIn(0f, 1f),
            lipPress = smoothPress.coerceIn(0f, 1f),
            upperLipRaise = smoothUpperLip.coerceIn(0f, 1f),
            tongueLift = smoothTongue.coerceIn(0f, 1f),
            eyeBlink = eyeBlink,
            browRaise = smoothBrow.coerceIn(0f, 1f),
            smile = (0.12f + smoothWide * 0.25f).coerceIn(0f, 1f),
            headPitch = headPitch,
            headYaw = headYaw,
            headRoll = headRoll,
            vocalEnergy = smoothEnergy
        )
    }

    fun analyzePcmToTimeline(pcm: ShortArray, sampleRate: Int): VisemeTimeline {
        reset()
        val stepMs = 16L
        val stepSamples = ((sampleRate * stepMs) / 1000L).toInt().coerceAtLeast(1)
        val durationMs = ((pcm.size.toLong() * 1000L) / sampleRate.coerceAtLeast(1)).coerceAtLeast(100L)
        val window = FloatArray(fftSize)
        val frames = ArrayList<VisemeFrame>((pcm.size / stepSamples) + 2)

        var pos = 0
        var frameIdx = 0
        while (pos < pcm.size) {
            val start = (pos - fftSize / 2).coerceAtLeast(0)
            for (i in 0 until fftSize) {
                val idx = start + i
                window[i] = if (idx < pcm.size) pcm[idx] / 32768f else 0f
            }
            val timeMs = frameIdx * stepMs
            val rig = processFrame(
                samples = window,
                sampleRate = sampleRate,
                timeSec = timeMs / 1000f,
                dtSec = stepMs / 1000f
            )
            frames.add(VisemeFrame(timeMs = timeMs, rig = rig))
            pos += stepSamples
            frameIdx++
        }

        return VisemeTimeline(
            durationMs = durationMs,
            stepMs = stepMs,
            frames = frames
        )
    }

    fun computeWaveformPeaks(pcm: ShortArray, barCount: Int = 96): List<Float> {
        if (pcm.isEmpty() || barCount <= 0) return List(barCount.coerceAtLeast(1)) { 0.08f }
        val chunkSize = (pcm.size / barCount).coerceAtLeast(1)
        val peaks = FloatArray(barCount)
        var globalMax = 1e-4f
        for (b in 0 until barCount) {
            val start = b * chunkSize
            val end = minOf(pcm.size, start + chunkSize)
            var sumSq = 0.0
            for (i in start until end) {
                val s = pcm[i] / 32768.0
                sumSq += s * s
            }
            val rms = sqrt(sumSq / (end - start).coerceAtLeast(1)).toFloat()
            peaks[b] = rms
            globalMax = max(globalMax, rms)
        }
        return peaks.map { (it / globalMax).coerceIn(0.06f, 1f) }
    }

    private fun smoothTowards(
        current: Float,
        target: Float,
        dt: Float,
        attackHz: Float,
        releaseHz: Float
    ): Float {
        val rate = if (target > current) attackHz else releaseHz
        val alpha = (1f - kotlin.math.exp(-rate * dt)).coerceIn(0f, 1f)
        return current + (target - current) * alpha
    }
}
