package com.ngg.instruments.flight

import com.ngg.instruments.flight.verticalspeed.VerticalSpeedEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class VerticalSpeedEstimatorTest {

    private fun ns(seconds: Double): Long = (seconds * 1e9).toLong()

    @Test
    fun stationaryNoisyAltitudeStaysNearZero() {
        val vsi = VerticalSpeedEstimator()
        // 10 Hz for 20 s, deterministic noise of ±5 cm (typical filtered baro noise).
        for (i in 0..200) {
            val t = i / 10.0
            val noise = 0.05f * sin(i * 2.399f)
            vsi.addSample(100f + noise, ns(t))
        }
        val fpm = vsi.verticalSpeedFpm
        assertNotNull(fpm)
        assertTrue("expected near zero, was $fpm", abs(fpm!!) < 30f)
    }

    @Test
    fun constantClimbConverges() {
        val vsi = VerticalSpeedEstimator()
        // 5 m/s climb = 984.25 fpm, 10 Hz for 10 s (>> tau of 1.2 s).
        for (i in 0..100) {
            val t = i / 10.0
            vsi.addSample(100f + 5f * t.toFloat(), ns(t))
        }
        assertEquals(984.25f, vsi.verticalSpeedFpm!!, 20f)
    }

    @Test
    fun constantDescentConverges() {
        val vsi = VerticalSpeedEstimator()
        for (i in 0..100) {
            val t = i / 10.0
            vsi.addSample(500f - 3f * t.toFloat(), ns(t))
        }
        assertEquals(-590.55f, vsi.verticalSpeedFpm!!, 15f)
    }

    @Test
    fun noisyClimbStillConverges() {
        val vsi = VerticalSpeedEstimator()
        for (i in 0..200) {
            val t = i / 10.0
            val noise = 0.08f * sin(i * 1.7f)
            vsi.addSample(100f + 2f * t.toFloat() + noise, ns(t))
        }
        assertEquals(393.7f, vsi.verticalSpeedFpm!!, 60f)
    }

    @Test
    fun singleOutlierIsRejected() {
        val vsi = VerticalSpeedEstimator()
        for (i in 0..50) vsi.addSample(100f, ns(i / 10.0))
        // A 500 m spike in one 100 ms sample implies 5000 m/s: rejected.
        vsi.addSample(600f, ns(5.1))
        for (i in 52..80) vsi.addSample(100f, ns(i / 10.0))
        assertTrue("outlier leaked: ${vsi.verticalSpeedFpm}", abs(vsi.verticalSpeedFpm!!) < 30f)
    }

    @Test
    fun dataGapReseedsInsteadOfSpiking() {
        val vsi = VerticalSpeedEstimator()
        for (i in 0..20) vsi.addSample(100f, ns(i / 10.0))
        // 30 s gap while the altitude changed by 300 m: a naive derivative
        // would show 10 m/s; the estimator must re-seed to zero instead.
        vsi.addSample(400f, ns(32.0))
        assertEquals(0f, vsi.verticalSpeedFpm!!, 1f)
    }

    @Test
    fun freshnessExpires() {
        val vsi = VerticalSpeedEstimator()
        vsi.addSample(100f, ns(0.0))
        vsi.addSample(100f, ns(0.1))
        assertTrue(vsi.isFresh(ns(1.0)))
        assertTrue(!vsi.isFresh(ns(10.0)))
    }
}
