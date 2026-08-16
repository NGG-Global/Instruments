package com.ngg.instruments.gnss

import com.ngg.instruments.flight.DataQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationQualityEvaluatorTest {

    private fun fix(
        ageSeconds: Double = 0.5,
        nowNanos: Long = 100_000_000_000L,
        hAcc: Float? = 5f,
        vAcc: Float? = 8f,
        sAcc: Float? = 0.5f,
        speed: Float? = 30f,
        bearing: Float? = 123f,
    ) = GnssFix(
        latitude = 32.0,
        longitude = 34.8,
        altitudeWgs84M = 150.0,
        altitudeMslM = 130.0,
        speedMps = speed,
        bearingDeg = bearing,
        horizontalAccuracyM = hAcc,
        verticalAccuracyM = vAcc,
        speedAccuracyMps = sAcc,
        bearingAccuracyDeg = 2f,
        elapsedRealtimeNanos = nowNanos - (ageSeconds * 1e9).toLong(),
        timeMs = 1_700_000_000_000L,
    )

    private val now = 100_000_000_000L

    @Test
    fun freshAccurateFixIsGood() {
        assertEquals(DataQuality.GOOD, LocationQualityEvaluator.evaluate(fix(), now))
    }

    @Test
    fun noFixIsUnavailable() {
        assertEquals(DataQuality.UNAVAILABLE, LocationQualityEvaluator.evaluate(null, now))
    }

    @Test
    fun staleFixIsUnavailable() {
        assertEquals(DataQuality.UNAVAILABLE, LocationQualityEvaluator.evaluate(fix(ageSeconds = 20.0), now))
    }

    @Test
    fun agingFixDegrades() {
        assertEquals(DataQuality.DEGRADED, LocationQualityEvaluator.evaluate(fix(ageSeconds = 5.0), now))
        assertEquals(DataQuality.POOR, LocationQualityEvaluator.evaluate(fix(ageSeconds = 10.0), now))
    }

    @Test
    fun poorAccuracyIsPoor() {
        assertEquals(DataQuality.POOR, LocationQualityEvaluator.evaluate(fix(hAcc = 100f), now))
        assertEquals(DataQuality.DEGRADED, LocationQualityEvaluator.evaluate(fix(hAcc = 30f), now))
    }

    @Test
    fun missingAccuracyIsNeverGood() {
        assertEquals(DataQuality.POOR, LocationQualityEvaluator.evaluate(fix(hAcc = null), now))
    }

    @Test
    fun trackRequiresBearingSpeedAndQuality() {
        assertTrue(LocationQualityEvaluator.isTrackUsable(fix(), now))
        assertFalse("no bearing", LocationQualityEvaluator.isTrackUsable(fix(bearing = null), now))
        assertFalse("no speed", LocationQualityEvaluator.isTrackUsable(fix(speed = null), now))
        assertFalse("stationary", LocationQualityEvaluator.isTrackUsable(fix(speed = 0.5f), now))
        assertFalse("stale", LocationQualityEvaluator.isTrackUsable(fix(ageSeconds = 20.0), now))
        assertFalse("poor", LocationQualityEvaluator.isTrackUsable(fix(hAcc = 200f), now))
    }

    @Test
    fun speedQuality() {
        assertEquals(DataQuality.GOOD, LocationQualityEvaluator.speedQuality(fix(), now))
        assertEquals(DataQuality.UNAVAILABLE, LocationQualityEvaluator.speedQuality(fix(speed = null), now))
        assertEquals(DataQuality.DEGRADED, LocationQualityEvaluator.speedQuality(fix(sAcc = 5f), now))
        assertEquals(DataQuality.UNAVAILABLE, LocationQualityEvaluator.speedQuality(null, now))
    }

    @Test
    fun altitudeQuality() {
        assertEquals(DataQuality.GOOD, LocationQualityEvaluator.altitudeQuality(fix(), now))
        assertEquals(DataQuality.DEGRADED, LocationQualityEvaluator.altitudeQuality(fix(vAcc = 20f), now))
        assertEquals(DataQuality.POOR, LocationQualityEvaluator.altitudeQuality(fix(vAcc = 80f), now))
    }
}
