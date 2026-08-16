package com.ngg.instruments.gnss

/**
 * Pure snapshot of a GNSS fix, decoupled from android.location.Location so the
 * estimation layer is JVM-testable and recordings are replayable.
 *
 * Null fields mean the receiver did not provide that value — they are never
 * substituted with zero.
 */
data class GnssFix(
    val latitude: Double,
    val longitude: Double,
    /** WGS84 ellipsoid altitude in meters as delivered by the GNSS provider. */
    val altitudeWgs84M: Double?,
    /** Mean-sea-level altitude in meters (AltitudeConverter, API 34+), when available. */
    val altitudeMslM: Double?,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val horizontalAccuracyM: Float?,
    val verticalAccuracyM: Float?,
    val speedAccuracyMps: Float?,
    val bearingAccuracyDeg: Float?,
    /** Monotonic timestamp of the fix (SystemClock.elapsedRealtimeNanos domain). */
    val elapsedRealtimeNanos: Long,
    /** UTC wall-clock time of the fix in milliseconds (used for declination). */
    val timeMs: Long,
)
