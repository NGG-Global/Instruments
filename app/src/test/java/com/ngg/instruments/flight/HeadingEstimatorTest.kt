package com.ngg.instruments.flight

import com.ngg.instruments.flight.heading.HeadingEstimator
import com.ngg.instruments.math.Angles
import com.ngg.instruments.math.Quaternion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HeadingEstimatorTest {

    private fun ns(seconds: Double): Long = (seconds * 1e9).toLong()

    /** Device flat, top pointing at compass heading h: q = Rz(-h). */
    private fun deviceAtHeading(h: Float): Quaternion = Quaternion.aboutZ(-h)

    @Test
    fun convergesToMagneticHeading() {
        val est = HeadingEstimator()
        for (i in 0..100) {
            est.update(deviceAtHeading(90f), Quaternion.IDENTITY, 3, ns(i / 20.0))
        }
        assertEquals(90f, est.magneticHeadingDeg(ns(5.0))!!, 0.5f)
    }

    @Test
    fun trueHeadingAppliesDeclination() {
        val est = HeadingEstimator()
        for (i in 0..100) {
            est.update(deviceAtHeading(90f), Quaternion.IDENTITY, 3, ns(i / 20.0))
        }
        assertEquals(95f, est.trueHeadingDeg(ns(5.0), 5f)!!, 0.5f)
        assertNull(est.trueHeadingDeg(ns(5.0), null))
    }

    @Test
    fun trueHeadingWrapsPast360() {
        val est = HeadingEstimator()
        for (i in 0..100) {
            est.update(deviceAtHeading(358f), Quaternion.IDENTITY, 3, ns(i / 20.0))
        }
        assertEquals(3f, est.trueHeadingDeg(ns(5.0), 5f)!!, 0.5f)
    }

    @Test
    fun wraparoundSmoothingGoesTheShortWay() {
        val est = HeadingEstimator()
        // Settle at 359, then jump to 1: the smoothed value must never pass
        // through headings far from north (e.g. 180).
        for (i in 0..100) est.update(deviceAtHeading(359f), Quaternion.IDENTITY, 3, ns(i / 20.0))
        var previous = est.magneticHeadingDeg(ns(5.0))!!
        for (i in 101..160) {
            est.update(deviceAtHeading(1f), Quaternion.IDENTITY, 3, ns(i / 20.0))
            val v = est.magneticHeadingDeg(ns(i / 20.0))!!
            val step = Angles.shortestDeltaDeg(previous, v)
            assertTrue("step was $step via $v", step >= -0.01f && step < 1.5f)
            previous = v
        }
        assertTrue(abs(Angles.shortestDeltaDeg(previous, 1f)) < 0.5f)
    }

    @Test
    fun qualityFollowsSensorAccuracy() {
        val est = HeadingEstimator()
        est.update(deviceAtHeading(10f), Quaternion.IDENTITY, 3, ns(0.0))
        assertEquals(DataQuality.GOOD, est.quality(ns(0.1)))
        est.update(deviceAtHeading(10f), Quaternion.IDENTITY, 2, ns(0.2))
        assertEquals(DataQuality.DEGRADED, est.quality(ns(0.3)))
        est.update(deviceAtHeading(10f), Quaternion.IDENTITY, 1, ns(0.4))
        assertEquals(DataQuality.POOR, est.quality(ns(0.5)))
    }

    @Test
    fun staleSamplesBecomeUnavailable() {
        val est = HeadingEstimator()
        est.update(deviceAtHeading(10f), Quaternion.IDENTITY, 3, ns(0.0))
        assertNull(est.magneticHeadingDeg(ns(10.0)))
        assertEquals(DataQuality.UNAVAILABLE, est.quality(ns(10.0)))
    }
}
