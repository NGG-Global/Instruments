package com.ngg.instruments.math

/**
 * Circular angle helpers. All angles in degrees.
 *
 * These exist so that heading smoothing and interpolation always take the
 * shortest way around the circle (359 -> 1 moves +2, never -358).
 */
object Angles {

    /** Normalizes any angle into [0, 360). */
    fun normalizeDeg(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return if (d == 360f) 0f else d
    }

    /** Signed shortest rotation from [from] to [to], in (-180, 180]. */
    fun shortestDeltaDeg(from: Float, to: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** Interpolates along the shortest arc; t in [0,1]. Result normalized to [0,360). */
    fun lerpAngleDeg(from: Float, to: Float, t: Float): Float {
        return normalizeDeg(from + shortestDeltaDeg(from, to) * t)
    }
}
