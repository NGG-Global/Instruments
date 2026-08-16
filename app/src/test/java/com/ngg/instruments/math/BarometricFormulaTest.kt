package com.ngg.instruments.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarometricFormulaTest {

    @Test
    fun standardPressureIsZeroAltitude() {
        assertEquals(0f, BarometricFormula.altitudeMeters(1013.25f, 1013.25f), 0.01f)
    }

    @Test
    fun knownIsaPoint850hPa() {
        // ISA: 850 hPa is approximately 1,457 m.
        assertEquals(1457f, BarometricFormula.altitudeMeters(850f, 1013.25f), 5f)
    }

    @Test
    fun roundTripThroughInverse() {
        val p = BarometricFormula.pressureHpa(1000f, 1013.25f)
        assertEquals(1000f, BarometricFormula.altitudeMeters(p, 1013.25f), 0.5f)
    }

    @Test
    fun qnhChangeShiftsAltitude() {
        // Near sea level, +10 hPa of QNH raises indicated altitude by ~83 m.
        val at1013 = BarometricFormula.altitudeMeters(1013.25f, 1013.25f)
        val at1023 = BarometricFormula.altitudeMeters(1013.25f, 1023.25f)
        assertEquals(83f, at1023 - at1013, 3f)
        assertTrue(at1023 > at1013)
    }

    @Test
    fun qnhValidation() {
        assertTrue(BarometricFormula.isValidQnh(1013.25f))
        assertTrue(BarometricFormula.isValidQnh(950f))
        assertFalse(BarometricFormula.isValidQnh(500f))
        assertFalse(BarometricFormula.isValidQnh(1500f))
        assertFalse(BarometricFormula.isValidQnh(Float.NaN))
    }
}
