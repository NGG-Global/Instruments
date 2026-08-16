package com.ngg.instruments.flight

import com.ngg.instruments.flight.altitude.AltitudeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AltitudeEstimatorTest {

    private fun ns(seconds: Double): Long = (seconds * 1e9).toLong()

    @Test
    fun barometricAltitudeAtStandardPressure() {
        val est = AltitudeEstimator(hasBarometer = true)
        for (i in 0..50) est.onPressure(1013.25f, ns(i / 10.0))
        val solution = est.solution(ns(5.0))
        assertEquals(AltitudeSource.BAROMETRIC, solution.source)
        assertEquals(0f, solution.selectedM!!, 0.5f)
    }

    @Test
    fun qnhChangeShiftsBarometricAltitude() {
        val est = AltitudeEstimator(hasBarometer = true)
        for (i in 0..50) est.onPressure(1013.25f, ns(i / 10.0))
        est.setQnh(1023.25f)
        // Near sea level +10 hPa QNH is ~+83 m of indicated altitude.
        assertEquals(83f, est.barometricAltitudeM()!!, 3f)
    }

    @Test
    fun gnssFallbackWithoutBarometer() {
        val est = AltitudeEstimator(hasBarometer = false)
        for (i in 0..100) est.onGnssAltitude(500.0, ns(i / 2.0)) // 2 Hz
        val solution = est.solution(ns(50.0))
        assertEquals(AltitudeSource.GNSS, solution.source)
        assertEquals(500f, solution.selectedM!!, 1f)
        assertNull(solution.barometricM)
    }

    @Test
    fun staleBarometerFallsBackToGnss() {
        val est = AltitudeEstimator(hasBarometer = true)
        for (i in 0..20) est.onPressure(1013.25f, ns(i / 10.0))
        for (i in 0..100) est.onGnssAltitude(500.0, ns(i / 2.0))
        // 50 s later, the last pressure sample (t=2 s) is long stale.
        val solution = est.solution(ns(50.0))
        assertEquals(AltitudeSource.GNSS, solution.source)
        assertEquals(500f, solution.selectedM!!, 1f)
    }

    @Test
    fun noSourcesMeansNoAltitude() {
        val est = AltitudeEstimator(hasBarometer = true)
        val solution = est.solution(ns(10.0))
        assertEquals(AltitudeSource.NONE, solution.source)
        assertNull(solution.selectedM)
    }

    @Test
    fun invalidQnhIsIgnored() {
        val est = AltitudeEstimator(hasBarometer = true)
        for (i in 0..50) est.onPressure(1013.25f, ns(i / 10.0))
        est.setQnh(Float.NaN)
        est.setQnh(2000f)
        assertEquals(0f, est.barometricAltitudeM()!!, 0.5f)
    }
}
