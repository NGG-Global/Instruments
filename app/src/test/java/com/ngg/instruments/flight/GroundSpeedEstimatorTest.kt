package com.ngg.instruments.flight

import com.ngg.instruments.flight.speed.GroundSpeedEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroundSpeedEstimatorTest {

    private fun ns(seconds: Double): Long = (seconds * 1e9).toLong()

    @Test
    fun convertsDopplerSpeedToKnots() {
        val est = GroundSpeedEstimator()
        for (i in 0..20) est.onGnssSpeed(51.44444f, ns(i.toDouble())) // 100 kt
        assertEquals(100f, est.groundSpeedKt(ns(20.0))!!, 0.2f)
    }

    @Test
    fun ignoresInvalidSamples() {
        val est = GroundSpeedEstimator()
        for (i in 0..20) est.onGnssSpeed(10f, ns(i.toDouble()))
        est.onGnssSpeed(Float.NaN, ns(21.0))
        est.onGnssSpeed(-5f, ns(22.0))
        est.onGnssSpeed(null, ns(23.0))
        assertEquals(19.44f, est.groundSpeedKt(ns(23.0))!!, 0.2f)
    }

    @Test
    fun staleSpeedBecomesNull() {
        val est = GroundSpeedEstimator()
        est.onGnssSpeed(10f, ns(0.0))
        assertNull(est.groundSpeedKt(ns(30.0)))
    }

    @Test
    fun neverReportsBeforeFirstSample() {
        assertNull(GroundSpeedEstimator().groundSpeedKt(ns(1.0)))
    }
}
