package com.ngg.instruments.math

import kotlin.math.asin
import kotlin.math.atan2

/**
 * Aircraft attitude mathematics.
 *
 * World frame: Android sensor world frame, East-North-Up (X east, Y north, Z up).
 * Aircraft body frame after mount correction: X = right wing, Y = forward
 * (out the nose), Z = up. This body frame is defined by the calibration, not by
 * how the device happens to be mounted, which is what makes an arbitrary rigid
 * mount work.
 */
data class PitchRoll(val pitchDeg: Float, val rollDeg: Float)

object AttitudeMath {

    /**
     * Computes the mount-correction quaternion from a "SET LEVEL" reference.
     *
     * [qRef] is the device→world rotation captured while the aircraft is level.
     * Decomposing qRef = q_yaw ⊗ q_tilt (yaw about world up), the correction is
     * q_mount = q_tilt⁻¹ ⊗ Rz(mountYawDeg): it removes the device's tilt in the
     * airframe and then rotates the body frame so "forward" matches the chosen
     * mounting orientation (0 = device top points forward when upright).
     *
     * The corrected aircraft rotation at any later time is q_cur ⊗ q_mount,
     * which equals a pure yaw at the calibration instant (level, by definition).
     */
    fun mountFromReference(qRef: Quaternion, mountYawDeg: Float = 0f): Quaternion {
        val tilt = qRef.tiltComponent()
        return (tilt.inverse() * Quaternion.aboutZ(mountYawDeg)).normalized()
    }

    /** Applies the mount correction: aircraft body → world rotation. */
    fun aircraftRotation(qDevice: Quaternion, qMount: Quaternion): Quaternion =
        (qDevice * qMount).normalized()

    /**
     * Extracts aircraft pitch and roll from a body→world rotation.
     * Positive pitch = nose up. Positive roll = right bank.
     */
    fun pitchRoll(qAircraft: Quaternion): PitchRoll {
        val f = qAircraft.rotate(0f, 1f, 0f)   // body forward in world
        val r = qAircraft.rotate(1f, 0f, 0f)   // body right in world
        val u = qAircraft.rotate(0f, 0f, 1f)   // body up in world

        val pitch = Math.toDegrees(asin(f[2].coerceIn(-1f, 1f).toDouble())).toFloat()
        val roll = Math.toDegrees(atan2(-r[2].toDouble(), u[2].toDouble())).toFloat()
        return PitchRoll(pitch, roll)
    }

    /**
     * Extracts the heading (yaw) of the aircraft forward axis, in degrees
     * clockwise from north, from a body→world rotation whose world frame is
     * referenced to magnetic north (TYPE_ROTATION_VECTOR).
     * Returns null when the forward axis is too close to vertical for a
     * meaningful heading.
     */
    fun headingDeg(qAircraft: Quaternion): Float? {
        val f = qAircraft.rotate(0f, 1f, 0f)
        val horiz = f[0] * f[0] + f[1] * f[1]
        if (horiz < 1e-6f) return null
        // atan2(east, north) gives compass bearing.
        return Angles.normalizeDeg(Math.toDegrees(atan2(f[0].toDouble(), f[1].toDouble())).toFloat())
    }
}
