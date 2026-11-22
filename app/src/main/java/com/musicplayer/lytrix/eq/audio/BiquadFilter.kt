package com.musicplayer.lytrix.eq.audio

import com.musicplayer.lytrix.eq.data.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class BiquadCoeffs(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double
)

/**
 * Shared Audio EQ Cookbook biquad math used by both real-time DSP filtering and the response graph.
 */
object BiquadMath {
    fun computeCoefficients(
        sampleRate: Int,
        frequency: Double,
        gainDb: Double,
        q: Double,
        filterType: FilterType
    ): BiquadCoeffs {
        val safeSampleRate = sampleRate.coerceAtLeast(8000)
        val safeFreq = frequency.coerceIn(5.0, safeSampleRate * 0.495)
        val safeQ = q.coerceAtLeast(0.05)

        val omega = 2.0 * PI * safeFreq / safeSampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)

        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double

        when (filterType) {
            FilterType.PK -> {
                val amp = 10.0.pow(gainDb / 40.0)
                val alpha = sinOmega / (2.0 * safeQ)
                b0 = 1.0 + alpha * amp
                b1 = -2.0 * cosOmega
                b2 = 1.0 - alpha * amp
                a0 = 1.0 + alpha / amp
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha / amp
            }
            FilterType.LSC -> {
                val amp = sqrt(10.0.pow(gainDb / 20.0))
                val slope = 1.0
                val alpha = sinOmega / 2.0 * sqrt((amp + 1.0 / amp) * (1.0 / slope - 1.0) + 2.0)
                val twoSqrtAAlpha = 2.0 * sqrt(amp) * alpha
                val aPlusOne = amp + 1.0
                val aMinusOne = amp - 1.0
                b0 = amp * (aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha)
                b1 = 2.0 * amp * (aMinusOne - aPlusOne * cosOmega)
                b2 = amp * (aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha)
                a0 = aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha
                a1 = -2.0 * (aMinusOne + aPlusOne * cosOmega)
                a2 = aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha
            }
            FilterType.HSC -> {
                val amp = sqrt(10.0.pow(gainDb / 20.0))
                val slope = 1.0
                val alpha = sinOmega / 2.0 * sqrt((amp + 1.0 / amp) * (1.0 / slope - 1.0) + 2.0)
                val twoSqrtAAlpha = 2.0 * sqrt(amp) * alpha
                val aPlusOne = amp + 1.0
                val aMinusOne = amp - 1.0
                b0 = amp * (aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha)
                b1 = -2.0 * amp * (aMinusOne + aPlusOne * cosOmega)
                b2 = amp * (aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha)
                a0 = aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha
                a1 = 2.0 * (aMinusOne - aPlusOne * cosOmega)
                a2 = aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha
            }
            FilterType.LPQ -> {
                val alpha = sinOmega / (2.0 * safeQ)
                b0 = (1.0 - cosOmega) / 2.0
                b1 = 1.0 - cosOmega
                b2 = (1.0 - cosOmega) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha
            }
            FilterType.HPQ -> {
                val alpha = sinOmega / (2.0 * safeQ)
                b0 = (1.0 + cosOmega) / 2.0
                b1 = -(1.0 + cosOmega)
                b2 = (1.0 + cosOmega) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha
            }
        }

        return BiquadCoeffs(
            b0 = b0 / a0,
            b1 = b1 / a0,
            b2 = b2 / a0,
            a1 = a1 / a0,
            a2 = a2 / a0
        )
    }

    fun magnitudeResponseDb(coeffs: BiquadCoeffs, freq: Double, sampleRate: Int): Double {
        val omega = 2.0 * PI * freq / sampleRate
        val cosW = cos(omega)
        val cos2W = cos(2.0 * omega)
        val sinW = sin(omega)
        val sin2W = sin(2.0 * omega)

        val numReal = coeffs.b0 + coeffs.b1 * cosW + coeffs.b2 * cos2W
        val numImag = coeffs.b1 * sinW + coeffs.b2 * sin2W
        val denReal = 1.0 + coeffs.a1 * cosW + coeffs.a2 * cos2W
        val denImag = coeffs.a1 * sinW + coeffs.a2 * sin2W

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        return if (denMagSq > 0.0) {
            10.0 * log10(max(numMagSq / denMagSq, 1e-10))
        } else {
            0.0
        }
    }
}

/**
 * Per-band stereo/mono biquad filter implementing Direct Form I difference equation.
 */
class BiquadFilter(
    sampleRate: Int,
    frequency: Double,
    gain: Double,
    q: Double = 1.41,
    filterType: FilterType = FilterType.PK
) {
    private val coeffs: BiquadCoeffs =
        BiquadMath.computeCoefficients(sampleRate, frequency, gain, q, filterType)

    private var x1L = 0.0
    private var x2L = 0.0
    private var y1L = 0.0
    private var y2L = 0.0

    private var x1R = 0.0
    private var x2R = 0.0
    private var y1R = 0.0
    private var y2R = 0.0

    fun processMono(input: Double): Double {
        val output = coeffs.b0 * input +
            coeffs.b1 * x1L +
            coeffs.b2 * x2L -
            coeffs.a1 * y1L -
            coeffs.a2 * y2L

        x2L = x1L
        x1L = input
        y2L = y1L
        y1L = output
        return output
    }

    fun processLeft(input: Double): Double = processMono(input)

    fun processRight(input: Double): Double {
        val output = coeffs.b0 * input +
            coeffs.b1 * x1R +
            coeffs.b2 * x2R -
            coeffs.a1 * y1R -
            coeffs.a2 * y2R

        x2R = x1R
        x1R = input
        y2R = y1R
        y1R = output
        return output
    }

    fun reset() {
        x1L = 0.0
        x2L = 0.0
        y1L = 0.0
        y2L = 0.0
        x1R = 0.0
        x2R = 0.0
        y1R = 0.0
        y2R = 0.0
    }
}
