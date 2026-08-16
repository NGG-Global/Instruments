package com.ngg.instruments.math

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun metersToFeet() {
        assertEquals(3.28084f, Units.metersToFeet(1f), 1e-4f)
        assertEquals(32808.4f, Units.metersToFeet(10_000f), 0.5f)
        assertEquals(-328.084f, Units.metersToFeet(-100f), 0.01f)
    }

    @Test
    fun feetToMetersRoundTrip() {
        assertEquals(1234.5f, Units.feetToMeters(Units.metersToFeet(1234.5f)), 1e-2f)
    }

    @Test
    fun mpsToKnots() {
        // 1852 m per nautical mile exactly: 1 m/s = 1.9438445 kt
        assertEquals(1.9438445f, Units.mpsToKnots(1f), 1e-5f)
        assertEquals(100f, Units.mpsToKnots(51.44444f), 1e-2f)
    }

    @Test
    fun knotsRoundTrip() {
        assertEquals(250f, Units.mpsToKnots(Units.knotsToMps(250f)), 1e-3f)
    }

    @Test
    fun mpsToFpm() {
        // 1 m/s = 196.8504 ft/min
        assertEquals(196.8504f, Units.mpsToFpm(1f), 1e-3f)
        assertEquals(-984.252f, Units.mpsToFpm(-5f), 1e-2f)
    }
}
