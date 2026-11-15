package com.example.audio

import com.example.model.FacialRigState
import com.example.model.VisemeTimeline
import com.example.model.VisemeType
import java.util.Locale
import kotlin.math.min

/** A phoneme label with its aligned interval on the audio timeline. */
data class TimedPhoneme(
    val symbol: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Converts externally aligned phoneme timestamps into deterministic facial keyframes.
 *
 * This is the accurate-alignment path for TTS providers or speech-alignment services.
 * It intentionally does not infer phonemes from FFT energy; the caller must supply
 * timestamped phoneme labels from a real aligner.
 */
object PhonemeVisemeTimelineBuilder {
    private data class Shape(
        val viseme: VisemeType,
        val jaw: Float = 0f,
        val wide: Float = 0f,
        val pucker: Float = 0f,
        val press: Float = 0f,
        val upperLip: Float = 0f,
        val tongue: Float = 0f
    ) {
        fun toRig(weight: Float) = FacialRigState(
            viseme = viseme,
            jawOpen = jaw * weight,
            mouthWide = wide * weight,
            mouthPucker = pucker * weight,
            lipPress = press * weight,
            upperLipRaise = upperLip * weight,
            tongueLift = tongue * weight,
            vocalEnergy = weight
        )
    }

    private val shapes = mapOf(
        "P" to Shape(VisemeType.PP, jaw = 0.05f, press = 1f),
        "B" to Shape(VisemeType.PP, jaw = 0.05f, press = 0.95f),
        "M" to Shape(VisemeType.PP, jaw = 0.04f, press = 0.85f),
        "F" to Shape(VisemeType.FF, jaw = 0.10f, upperLip = 0.6f),
        "V" to Shape(VisemeType.FF, jaw = 0.12f, upperLip = 0.5f),
        "TH" to Shape(VisemeType.TH, jaw = 0.16f, tongue = 0.9f),
        "DH" to Shape(VisemeType.TH, jaw = 0.14f, tongue = 0.75f),
        "T" to Shape(VisemeType.DD, jaw = 0.16f, tongue = 0.55f),
        "D" to Shape(VisemeType.DD, jaw = 0.18f, tongue = 0.5f),
        "N" to Shape(VisemeType.NN, jaw = 0.13f, tongue = 0.65f),
        "L" to Shape(VisemeType.NN, jaw = 0.16f, tongue = 0.7f),
        "K" to Shape(VisemeType.KK, jaw = 0.15f, tongue = 0.7f),
        "G" to Shape(VisemeType.KK, jaw = 0.17f, tongue = 0.65f),
        "NG" to Shape(VisemeType.KK, jaw = 0.12f, tongue = 0.8f),
        "CH" to Shape(VisemeType.CH, jaw = 0.22f, pucker = 0.38f, tongue = 0.4f),
        "JH" to Shape(VisemeType.CH, jaw = 0.2f, pucker = 0.3f, tongue = 0.4f),
        "SH" to Shape(VisemeType.CH, jaw = 0.16f, pucker = 0.32f, tongue = 0.5f),
        "ZH" to Shape(VisemeType.CH, jaw = 0.16f, pucker = 0.28f, tongue = 0.45f),
        "S" to Shape(VisemeType.SS, jaw = 0.1f, wide = 0.25f, tongue = 0.55f),
        "Z" to Shape(VisemeType.SS, jaw = 0.1f, wide = 0.22f, tongue = 0.5f),
        "R" to Shape(VisemeType.RR, jaw = 0.16f, pucker = 0.18f, tongue = 0.35f),
        "AA" to Shape(VisemeType.AA, jaw = 0.82f),
        "AE" to Shape(VisemeType.AA, jaw = 0.72f, wide = 0.12f),
        "AH" to Shape(VisemeType.AA, jaw = 0.68f),
        "AO" to Shape(VisemeType.OH, jaw = 0.42f, pucker = 0.48f),
        "OW" to Shape(VisemeType.OH, jaw = 0.38f, pucker = 0.58f),
        "EH" to Shape(VisemeType.EE, jaw = 0.38f, wide = 0.35f),
        "EY" to Shape(VisemeType.EE, jaw = 0.32f, wide = 0.4f),
        "IY" to Shape(VisemeType.IH, jaw = 0.24f, wide = 0.62f),
        "IH" to Shape(VisemeType.IH, jaw = 0.3f, wide = 0.48f),
        "AY" to Shape(VisemeType.AA, jaw = 0.58f, wide = 0.28f),
        "OY" to Shape(VisemeType.OH, jaw = 0.4f, pucker = 0.42f),
        "UH" to Shape(VisemeType.OU, jaw = 0.25f, pucker = 0.62f),
        "UW" to Shape(VisemeType.OU, jaw = 0.2f, pucker = 0.82f),
        "W" to Shape(VisemeType.OU, jaw = 0.16f, pucker = 0.78f),
        "Y" to Shape(VisemeType.IH, jaw = 0.22f, wide = 0.45f)
    )

    fun build(
        durationMs: Long,
        phonemes: List<TimedPhoneme>,
        stepMs: Long = 20L,
        transitionMs: Long = 35L
    ): VisemeTimeline {
        require(durationMs >= 0L) { "Audio duration cannot be negative." }
        require(stepMs > 0L) { "Timeline step must be greater than zero." }
        require(transitionMs >= 0L) { "Transition duration cannot be negative." }
        phonemes.forEach {
            require(it.startMs >= 0L && it.endMs >= it.startMs) {
                "Phoneme intervals must have non-negative, ordered timestamps."
            }
        }

        val sorted = phonemes
            .filter { it.endMs > it.startMs && it.startMs < durationMs }
            .sortedBy { it.startMs }
        val lastFrame = (durationMs / stepMs).toInt()
        val frames = (0..lastFrame).map { frameIndex ->
            val timeMs = (frameIndex * stepMs).coerceAtMost(durationMs)
            val active = sorted.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }
            val shape = active?.let { shapeFor(it.symbol) }
            val weight = if (active == null || shape == null) 0f else envelope(
                timeMs, active, transitionMs
            )
            com.example.model.VisemeFrame(
                timeMs = timeMs,
                rig = shape?.toRig(weight) ?: FacialRigState()
            )
        }
        return VisemeTimeline(durationMs = durationMs, stepMs = stepMs, frames = frames)
    }

    fun visemeFor(symbol: String): VisemeType = shapeFor(symbol)?.viseme ?: VisemeType.SIL

    private fun shapeFor(raw: String): Shape? {
        val key = raw.trim().uppercase(Locale.ROOT).replace(Regex("[0-2]$"), "")
        if (key.isEmpty() || key == "SIL" || key == "SP" || key == "PAUSE") return null
        return shapes[key]
    }

    private fun envelope(timeMs: Long, phoneme: TimedPhoneme, transitionMs: Long): Float {
        if (transitionMs == 0L) return 1f
        val attack = ((timeMs - phoneme.startMs).toFloat() / transitionMs).coerceIn(0f, 1f)
        val release = ((phoneme.endMs - timeMs).toFloat() / transitionMs).coerceIn(0f, 1f)
        return min(attack, release).coerceIn(0f, 1f)
    }
}
