package com.ngg.instruments.math

import kotlin.math.exp

/**
 * First-order low-pass filter parameterized by a time constant instead of a
 * fixed per-frame factor, so behaviour is independent of sample rate.
 *
 * alpha = 1 - exp(-dt / tau): after tau seconds the output has covered ~63% of
 * a step change, after 3*tau ~95%.
 */
class LowPassFilter(private val tauSeconds: Float) {
    var value: Float = Float.NaN
        private set

    val isInitialized: Boolean get() = !value.isNaN()

    fun reset() {
        value = Float.NaN
    }

    fun update(sample: Float, dtSeconds: Float): Float {
        if (!sample.isFinite()) return value
        if (!isInitialized || dtSeconds <= 0f) {
            value = sample
            return value
        }
        val alpha = 1f - exp(-dtSeconds / tauSeconds)
        value += (sample - value) * alpha
        return value
    }
}

/**
 * Low-pass filter for circular quantities (headings). Smooths along the
 * shortest arc so 359° -> 1° never swings backwards through 180°.
 */
class CircularLowPassFilter(private val tauSeconds: Float) {
    var valueDeg: Float = Float.NaN
        private set

    val isInitialized: Boolean get() = !valueDeg.isNaN()

    fun reset() {
        valueDeg = Float.NaN
    }

    fun update(sampleDeg: Float, dtSeconds: Float): Float {
        if (!sampleDeg.isFinite()) return valueDeg
        if (!isInitialized || dtSeconds <= 0f) {
            valueDeg = Angles.normalizeDeg(sampleDeg)
            return valueDeg
        }
        val alpha = 1f - exp(-dtSeconds / tauSeconds)
        val delta = Angles.shortestDeltaDeg(valueDeg, sampleDeg)
        valueDeg = Angles.normalizeDeg(valueDeg + delta * alpha)
        return valueDeg
    }
}
