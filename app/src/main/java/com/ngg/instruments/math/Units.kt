package com.ngg.instruments.math

/**
 * Exact unit conversion constants used across the whole application.
 *
 * METERS_PER_FOOT is the international foot definition (0.3048 m exactly).
 * MPS_TO_KNOTS follows from the international nautical mile (1852 m exactly):
 * 3600 / 1852 = 1.9438444924...
 */
object Units {
    const val METERS_TO_FEET = 1.0f / 0.3048f          // 3.2808399...
    const val MPS_TO_KNOTS = 3600.0f / 1852.0f          // 1.9438445...
    const val MPS_TO_FPM = 60.0f / 0.3048f              // 196.850394...

    fun metersToFeet(m: Float): Float = m * METERS_TO_FEET
    fun feetToMeters(ft: Float): Float = ft / METERS_TO_FEET
    fun mpsToKnots(mps: Float): Float = mps * MPS_TO_KNOTS
    fun knotsToMps(kt: Float): Float = kt / MPS_TO_KNOTS
    fun mpsToFpm(mps: Float): Float = mps * MPS_TO_FPM
    fun fpmToMps(fpm: Float): Float = fpm / MPS_TO_FPM
}
