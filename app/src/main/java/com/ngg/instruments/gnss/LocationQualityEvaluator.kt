package com.ngg.instruments.gnss

import com.ngg.instruments.flight.DataQuality

/**
 * Grades GNSS fixes so low-quality data is never used silently.
 * All thresholds are documented and unit tested.
 */
object LocationQualityEvaluator {

    // Fix age thresholds (a 1 Hz receiver plus processing jitter should stay
    // well under 3 s; beyond 15 s the fix is history, not data).
    private const val FRESH_NS = 3_000_000_000L
    private const val USABLE_NS = 6_000_000_000L
    private const val STALE_NS = 15_000_000_000L

    // Horizontal accuracy thresholds in meters (1-sigma, as reported by Android).
    private const val GOOD_HACC_M = 15f
    private const val USABLE_HACC_M = 40f

    /** Minimum ground speed for GNSS course-over-ground to be meaningful. */
    const val MIN_SPEED_FOR_TRACK_MPS = 1.5f

    /** Speed accuracy above which speed is only shown as degraded. */
    private const val GOOD_SACC_MPS = 2.0f

    fun evaluate(fix: GnssFix?, nowElapsedNanos: Long): DataQuality {
        if (fix == null) return DataQuality.UNAVAILABLE
        val age = nowElapsedNanos - fix.elapsedRealtimeNanos
        if (age > STALE_NS || age < -1_000_000_000L) return DataQuality.UNAVAILABLE
        val hAcc = fix.horizontalAccuracyM
        return when {
            age <= FRESH_NS && hAcc != null && hAcc <= GOOD_HACC_M -> DataQuality.GOOD
            age <= USABLE_NS && hAcc != null && hAcc <= USABLE_HACC_M -> DataQuality.DEGRADED
            else -> DataQuality.POOR
        }
    }

    /**
     * GNSS bearing is track over ground, only meaningful with a valid bearing,
     * acceptable fix quality and enough speed. A stationary receiver has no track.
     */
    fun isTrackUsable(fix: GnssFix?, nowElapsedNanos: Long): Boolean {
        if (fix?.bearingDeg == null) return false
        val speed = fix.speedMps ?: return false
        if (speed < MIN_SPEED_FOR_TRACK_MPS) return false
        val q = evaluate(fix, nowElapsedNanos)
        return q == DataQuality.GOOD || q == DataQuality.DEGRADED
    }

    /** Quality of the GNSS ground speed value itself. */
    fun speedQuality(fix: GnssFix?, nowElapsedNanos: Long): DataQuality {
        if (fix?.speedMps == null) return DataQuality.UNAVAILABLE
        val base = evaluate(fix, nowElapsedNanos)
        if (base == DataQuality.UNAVAILABLE) return DataQuality.UNAVAILABLE
        val sAcc = fix.speedAccuracyMps
        return when {
            base == DataQuality.GOOD && (sAcc == null || sAcc <= GOOD_SACC_MPS) -> DataQuality.GOOD
            base == DataQuality.POOR -> DataQuality.POOR
            else -> DataQuality.DEGRADED
        }
    }

    /** Quality of the GNSS altitude value. */
    fun altitudeQuality(fix: GnssFix?, nowElapsedNanos: Long): DataQuality {
        if (fix == null || (fix.altitudeMslM == null && fix.altitudeWgs84M == null)) {
            return DataQuality.UNAVAILABLE
        }
        val base = evaluate(fix, nowElapsedNanos)
        if (base == DataQuality.UNAVAILABLE) return DataQuality.UNAVAILABLE
        val vAcc = fix.verticalAccuracyM
        return when {
            base == DataQuality.GOOD && vAcc != null && vAcc <= 10f -> DataQuality.GOOD
            base != DataQuality.POOR && (vAcc == null || vAcc <= 30f) -> DataQuality.DEGRADED
            else -> DataQuality.POOR
        }
    }
}
