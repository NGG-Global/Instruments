package com.ngg.instruments.flight.attitude

import com.ngg.instruments.math.AttitudeMath
import com.ngg.instruments.math.PitchRoll
import com.ngg.instruments.math.Quaternion

/**
 * Turns rotation-vector quaternions into aircraft pitch/roll using the mount
 * correction from the "SET LEVEL" calibration.
 *
 * The rotation vector is already a fused, low-noise orientation, so no extra
 * smoothing is applied here; display animation is a separate UI concern.
 */
class AttitudeEstimator {

    var lastSampleNanos: Long = Long.MIN_VALUE
        private set

    private var lastQuaternion: Quaternion? = null

    fun reset() {
        lastQuaternion = null
        lastSampleNanos = Long.MIN_VALUE
    }

    fun update(qDeviceToWorld: Quaternion, elapsedNanos: Long) {
        lastQuaternion = qDeviceToWorld
        lastSampleNanos = elapsedNanos
    }

    /** Latest raw device orientation; used to capture the calibration reference. */
    val deviceOrientation: Quaternion? get() = lastQuaternion

    fun pitchRoll(mount: Quaternion): PitchRoll? {
        val q = lastQuaternion ?: return null
        return AttitudeMath.pitchRoll(AttitudeMath.aircraftRotation(q, mount))
    }

    fun isFresh(nowNanos: Long, maxAgeNanos: Long = 1_000_000_000L): Boolean =
        lastQuaternion != null && nowNanos - lastSampleNanos <= maxAgeNanos
}
