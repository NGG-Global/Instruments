package com.ngg.instruments.math

import org.junit.Assert.assertEquals
import org.junit.Test

class AnglesTest {

    @Test
    fun normalization() {
        assertEquals(0f, Angles.normalizeDeg(0f), 1e-5f)
        assertEquals(0f, Angles.normalizeDeg(360f), 1e-5f)
        assertEquals(1f, Angles.normalizeDeg(361f), 1e-4f)
        assertEquals(359f, Angles.normalizeDeg(-1f), 1e-4f)
        assertEquals(180f, Angles.normalizeDeg(-180f), 1e-4f)
        assertEquals(5f, Angles.normalizeDeg(725f), 1e-3f)
    }

    @Test
    fun shortestDeltaWraparound() {
        // 359 -> 1 must be +2, never -358.
        assertEquals(2f, Angles.shortestDeltaDeg(359f, 1f), 1e-4f)
        assertEquals(-2f, Angles.shortestDeltaDeg(1f, 359f), 1e-4f)
        assertEquals(180f, Angles.shortestDeltaDeg(0f, 180f), 1e-4f)
        assertEquals(-90f, Angles.shortestDeltaDeg(45f, 315f), 1e-4f)
        assertEquals(0f, Angles.shortestDeltaDeg(123f, 123f), 1e-4f)
    }

    @Test
    fun shortestAngleInterpolation() {
        assertEquals(0f, Angles.lerpAngleDeg(359f, 1f, 0.5f), 1e-3f)
        assertEquals(359.5f, Angles.lerpAngleDeg(359f, 1f, 0.25f), 1e-3f)
        assertEquals(90f, Angles.lerpAngleDeg(45f, 135f, 0.5f), 1e-3f)
    }

    @Test
    fun circularFilterTakesShortWay() {
        val f = CircularLowPassFilter(tauSeconds = 1f)
        f.update(359f, 0f)
        // A sample at 1 degree must move the value forward past 360, not backwards.
        val v = f.update(1f, 0.5f)
        val delta = Angles.shortestDeltaDeg(359f, v)
        org.junit.Assert.assertTrue("moved forward, was $v", delta > 0f && delta < 2f)
    }
}
