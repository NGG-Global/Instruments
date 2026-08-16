package com.ngg.instruments.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable unit quaternion. Represents a rotation; when built from an Android
 * rotation-vector sensor it rotates device-frame vectors into the world
 * (East-North-Up) frame.
 *
 * Convention: q = (w, x, y, z) with w the scalar part.
 */
data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {

    /** Hamilton product: this ⊗ other. Applies [other] first, then this. */
    operator fun times(o: Quaternion): Quaternion = Quaternion(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    /** Inverse of a unit quaternion (its conjugate). */
    fun inverse(): Quaternion = Quaternion(w, -x, -y, -z)

    fun normalized(): Quaternion {
        val n = sqrt(w * w + x * x + y * y + z * z)
        if (n < 1e-9f) return IDENTITY
        return Quaternion(w / n, x / n, y / n, z / n)
    }

    /** Rotates a 3-vector by this quaternion. Returns [x', y', z']. */
    fun rotate(vx: Float, vy: Float, vz: Float): FloatArray {
        // v' = q v q^-1 expanded to avoid allocations of intermediate quaternions.
        val tx = 2f * (y * vz - z * vy)
        val ty = 2f * (z * vx - x * vz)
        val tz = 2f * (x * vy - y * vx)
        return floatArrayOf(
            vx + w * tx + (y * tz - z * ty),
            vy + w * ty + (z * tx - x * tz),
            vz + w * tz + (x * ty - y * tx),
        )
    }

    /**
     * Yaw-only component of this rotation about the world Z (up) axis, i.e. the
     * q_yaw in the decomposition q = q_yaw ⊗ q_tilt. Derived via swing-twist
     * decomposition of the inverse rotation about Z.
     */
    fun yawComponent(): Quaternion {
        val n = sqrt(w * w + z * z)
        if (n < 1e-6f) {
            // Degenerate: the rotation points the device axis straight along Z
            // (gimbal); yaw is undefined, fall back to identity.
            return IDENTITY
        }
        return Quaternion(w / n, 0f, 0f, z / n)
    }

    /** Tilt-only component q_tilt where q = q_yaw ⊗ q_tilt. */
    fun tiltComponent(): Quaternion = (yawComponent().inverse() * this).normalized()

    /** Angle of rotation in degrees between this and [other]. */
    fun angleToDeg(other: Quaternion): Float {
        val d = (this.inverse() * other).normalized()
        val c = abs(d.w).coerceIn(0f, 1f)
        return Math.toDegrees(2.0 * kotlin.math.acos(c.toDouble())).toFloat()
    }

    companion object {
        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)

        /**
         * Builds a quaternion from Android rotation-vector sensor values.
         * values[0..2] = axis * sin(theta/2); values[3] = cos(theta/2) when present.
         */
        fun fromRotationVector(values: FloatArray): Quaternion {
            val x = values[0]
            val y = values[1]
            val z = values[2]
            val w = if (values.size >= 4 && !values[3].isNaN()) {
                values[3]
            } else {
                val s = 1f - x * x - y * y - z * z
                if (s > 0f) sqrt(s) else 0f
            }
            return Quaternion(w, x, y, z).normalized()
        }

        /** Rotation of [angleDeg] about the Z axis. */
        fun aboutZ(angleDeg: Float): Quaternion {
            val h = Math.toRadians(angleDeg.toDouble() / 2.0)
            return Quaternion(cos(h).toFloat(), 0f, 0f, sin(h).toFloat())
        }

        /** Rotation of [angleDeg] about the X axis. */
        fun aboutX(angleDeg: Float): Quaternion {
            val h = Math.toRadians(angleDeg.toDouble() / 2.0)
            return Quaternion(cos(h).toFloat(), sin(h).toFloat(), 0f, 0f)
        }

        /** Rotation of [angleDeg] about the Y axis. */
        fun aboutY(angleDeg: Float): Quaternion {
            val h = Math.toRadians(angleDeg.toDouble() / 2.0)
            return Quaternion(cos(h).toFloat(), 0f, sin(h).toFloat(), 0f)
        }
    }
}
