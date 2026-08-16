package com.ngg.instruments.math

import kotlin.math.pow

/**
 * International barometric formula for the ISA troposphere, the same formula
 * implemented by Android's SensorManager.getAltitude():
 *
 *   h = 44330 * (1 - (p / p0)^(1/5.255))
 *
 * where p is static pressure in hPa and p0 the reference (QNH) pressure. It is
 * re-implemented here (rather than calling SensorManager) so the altitude and
 * VSI pipeline is testable on the JVM.
 */
object BarometricFormula {
    const val STANDARD_PRESSURE_HPA = 1013.25f
    const val MIN_QNH_HPA = 850f
    const val MAX_QNH_HPA = 1100f

    private const val SCALE_M = 44330.0
    private const val EXPONENT = 1.0 / 5.255

    /** Pressure altitude in meters for [pressureHpa] against reference [qnhHpa]. */
    fun altitudeMeters(pressureHpa: Float, qnhHpa: Float = STANDARD_PRESSURE_HPA): Float {
        if (pressureHpa <= 0f || qnhHpa <= 0f) return Float.NaN
        val ratio = (pressureHpa / qnhHpa).toDouble()
        return (SCALE_M * (1.0 - ratio.pow(EXPONENT))).toFloat()
    }

    /** Inverse: pressure in hPa at [altitudeM] with reference [qnhHpa]. Used by tests. */
    fun pressureHpa(altitudeM: Float, qnhHpa: Float = STANDARD_PRESSURE_HPA): Float {
        val base = 1.0 - altitudeM.toDouble() / SCALE_M
        return (qnhHpa.toDouble() * base.pow(5.255)).toFloat()
    }

    fun isValidQnh(qnhHpa: Float): Boolean =
        qnhHpa.isFinite() && qnhHpa in MIN_QNH_HPA..MAX_QNH_HPA
}
