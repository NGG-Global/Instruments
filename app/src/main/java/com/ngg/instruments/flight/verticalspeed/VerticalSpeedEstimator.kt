package com.ngg.instruments.flight.verticalspeed

import com.ngg.instruments.math.Units
import kotlin.math.abs
import kotlin.math.exp

/**
 * Vertical speed from altitude change over monotonic time.
 *
 * The rate (not the altitude) is low-pass filtered with a time constant chosen
 * per source: 1.2 s for barometric input keeps genuine climbs visible within a
 * second while suppressing sensor noise; GNSS fallback uses a longer constant
 * set by the caller. Samples implying an impossible rate (> 75 m/s vertically,
 * ~15,000 fpm) are rejected as outliers; three consecutive rejections re-seed
 * the estimator so a genuine step (e.g. QNH change) is not ignored forever.
 *
 * The internal value is never clamped; the instrument face clamps only its
 * displayed needle.
 */
class VerticalSpeedEstimator(
    private val rateTauSeconds: Float = 1.2f,
    private val maxSampleGapSeconds: Float = 2.5f,
    private val maxPlausibleRateMps: Float = 75f,
) {
    private var lastAltitudeM = Float.NaN
    private var lastTimestampNanos = Long.MIN_VALUE
    private var rateMps = 0f
    private var seeded = false
    private var consecutiveRejects = 0

    fun reset() {
        lastAltitudeM = Float.NaN
        lastTimestampNanos = Long.MIN_VALUE
        rateMps = 0f
        seeded = false
        consecutiveRejects = 0
    }

    fun addSample(altitudeM: Float, elapsedNanos: Long) {
        if (!altitudeM.isFinite()) return

        if (!seeded) {
            seed(altitudeM, elapsedNanos)
            return
        }

        val dt = (elapsedNanos - lastTimestampNanos) / 1e9f
        if (dt <= 0f) return
        if (dt > maxSampleGapSeconds) {
            // Data gap: a derivative across it would be meaningless.
            seed(altitudeM, elapsedNanos)
            return
        }

        val rawRate = (altitudeM - lastAltitudeM) / dt
        if (abs(rawRate) > maxPlausibleRateMps) {
            consecutiveRejects++
            if (consecutiveRejects >= 3) seed(altitudeM, elapsedNanos)
            return
        }
        consecutiveRejects = 0

        val alpha = 1f - exp(-dt / rateTauSeconds)
        rateMps += (rawRate - rateMps) * alpha
        lastAltitudeM = altitudeM
        lastTimestampNanos = elapsedNanos
    }

    private fun seed(altitudeM: Float, elapsedNanos: Long) {
        lastAltitudeM = altitudeM
        lastTimestampNanos = elapsedNanos
        rateMps = 0f
        seeded = true
        consecutiveRejects = 0
    }

    fun isFresh(nowNanos: Long, maxAgeNanos: Long = 3_000_000_000L): Boolean =
        seeded && nowNanos - lastTimestampNanos <= maxAgeNanos

    val verticalSpeedMps: Float? get() = if (seeded) rateMps else null
    val verticalSpeedFpm: Float? get() = verticalSpeedMps?.let { Units.mpsToFpm(it) }
}
