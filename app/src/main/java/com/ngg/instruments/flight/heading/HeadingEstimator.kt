package com.ngg.instruments.flight.heading

import com.ngg.instruments.flight.DataQuality
import com.ngg.instruments.math.Angles
import com.ngg.instruments.math.AttitudeMath
import com.ngg.instruments.math.CircularLowPassFilter
import com.ngg.instruments.math.Quaternion

/**
 * Magnetic heading of the aircraft nose from the north-referenced rotation
 * vector, tilt-compensated through the same mount correction as attitude.
 *
 * Smoothing is circular (359 -> 1 takes the short way) and the time constant is
 * deliberately short (0.25 s) so turns are not lagged away.
 */
class HeadingEstimator(smoothingTauSeconds: Float = 0.25f) {

    private val filter = CircularLowPassFilter(smoothingTauSeconds)

    private var lastSampleNanos: Long = Long.MIN_VALUE
    private var sensorAccuracy: Int = -1
    private var hasSample = false

    fun reset() {
        filter.reset()
        hasSample = false
        lastSampleNanos = Long.MIN_VALUE
    }

    fun update(qDeviceToWorld: Quaternion, mount: Quaternion, accuracy: Int, elapsedNanos: Long) {
        val heading = AttitudeMath.headingDeg(AttitudeMath.aircraftRotation(qDeviceToWorld, mount))
            ?: return
        val dt = if (lastSampleNanos == Long.MIN_VALUE) 0f else (elapsedNanos - lastSampleNanos) / 1e9f
        filter.update(heading, dt)
        lastSampleNanos = elapsedNanos
        sensorAccuracy = accuracy
        hasSample = true
    }

    /** Smoothed magnetic heading in [0, 360), or null before the first sample. */
    fun magneticHeadingDeg(nowNanos: Long): Float? {
        if (!hasSample || nowNanos - lastSampleNanos > STALE_NS) return null
        return filter.valueDeg
    }

    /** True heading = magnetic heading + local declination (east positive). */
    fun trueHeadingDeg(nowNanos: Long, declinationDeg: Float?): Float? {
        val mag = magneticHeadingDeg(nowNanos) ?: return null
        if (declinationDeg == null || !declinationDeg.isFinite()) return null
        return Angles.normalizeDeg(mag + declinationDeg)
    }

    /**
     * Quality from the platform's magnetometer calibration status:
     * SENSOR_STATUS_ACCURACY_HIGH(3) -> GOOD, MEDIUM(2) -> DEGRADED,
     * LOW(1)/UNRELIABLE(0) -> POOR. Poor heading is shown as poor, not hidden.
     */
    fun quality(nowNanos: Long): DataQuality {
        if (magneticHeadingDeg(nowNanos) == null) return DataQuality.UNAVAILABLE
        return when {
            sensorAccuracy >= 3 -> DataQuality.GOOD
            sensorAccuracy == 2 -> DataQuality.DEGRADED
            else -> DataQuality.POOR
        }
    }

    private companion object {
        const val STALE_NS = 2_000_000_000L
    }
}
