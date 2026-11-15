package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SpectralFeatures(
    val rmsEnergy: Float,
    val lowEnergy: Float,      // 250 - 850 Hz (open vowel F1 / fundamental warmth)
    val midEnergy: Float,      // 850 - 2400 Hz (front/spread vowel F2)
    val highEnergy: Float,     // 2600 - 7500 Hz (sibilants / fricatives)
    val zeroCrossingRate: Float,
    val spectralCentroidHz: Float
)

class FftProcessor(private val fftSize: Int = 512) {
    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5f * (1f - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)

    fun analyze(samples: FloatArray, sampleRate: Int): SpectralFeatures {
        var sumSq = 0f
        var zcrCount = 0
        var prevSign = 0
        val count = minOf(samples.size, fftSize)

        for (i in 0 until fftSize) {
            val s = if (i < count) samples[i] else 0f
            if (i < count) {
                sumSq += s * s
                val sign = if (s >= 0f) 1 else -1
                if (i > 0 && sign != prevSign) zcrCount++
                prevSign = sign
            }
            real[i] = s * hannWindow[i]
            imag[i] = 0f
        }

        val rms = sqrt(sumSq / count.coerceAtLeast(1))
        val zcr = zcrCount.toFloat() / count.coerceAtLeast(1)

        fftInPlace(real, imag)

        val binHz = sampleRate.toFloat() / fftSize.toFloat()
        val half = fftSize / 2
        var lowSum = 0f
        var midSum = 0f
        var highSum = 0f
        var magSum = 0f
        var weightedFreqSum = 0f

        for (bin in 1 until half) {
            val freq = bin * binHz
            val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]) / fftSize
            magSum += mag
            weightedFreqSum += freq * mag
            when {
                freq in 220f..850f -> lowSum += mag
                freq in 850f..2450f -> midSum += mag
                freq in 2600f..7800f -> highSum += mag
            }
        }

        val centroid = if (magSum > 1e-6f) weightedFreqSum / magSum else 0f

        return SpectralFeatures(
            rmsEnergy = rms,
            lowEnergy = lowSum,
            midEnergy = midSum,
            highEnergy = highSum,
            zeroCrossingRate = zcr,
            spectralCentroidHz = centroid
        )
    }

    private fun fftInPlace(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = (-2.0 * PI / len).toFloat()
            val wLenRe = cos(ang.toDouble()).toFloat()
            val wLenIm = sin(ang.toDouble()).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                val halfLen = len / 2
                for (k in 0 until halfLen) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + halfLen] * wRe - im[i + k + halfLen] * wIm
                    val vIm = re[i + k + halfLen] * wIm + im[i + k + halfLen] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + halfLen] = uRe - vRe
                    im[i + k + halfLen] = uIm - vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
