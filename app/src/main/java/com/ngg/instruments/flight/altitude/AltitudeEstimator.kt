package com.ngg.instruments.flight.altitude

import com.ngg.instruments.flight.AltitudeSource
import com.ngg.instruments.math.BarometricFormula
import com.ngg.instruments.math.LowPassFilter

/**
 * Altitude solution combining barometric and GNSS sources.
 *
 * With a pressure sensor: barometric altitude (against the user QNH) is the
 * primary, responsive source. GNSS altitude is exposed alongside it but does
 * not jitter the needle.
 *
 * Without a pressure sensor: GNSS MSL altitude with a stronger low-pass
 * (tau = 2.5 s) is used and labeled as GNSS-sourced.
 */
data class AltitudeSolution(
    val selectedM: Float?,
    val barometricM: Float?,
    val gnssM: Float?,
    val source: AltitudeSource,
)

class AltitudeEstimator(private val hasBarometer: Boolean) {

    // Light smoothing to remove pressure-sensor quantization noise (~0.1 hPa)
    // while keeping ~real-time response; tau = 0.3 s.
    private val pressureFilter = LowPassFilter(0.3f)

    // GNSS vertical noise is meters-scale; stronger filtering, tau = 2.5 s.
    private val gnssAltFilter = LowPassFilter(2.5f)

    private var lastPressureNanos = Long.MIN_VALUE
    private var lastGnssNanos = Long.MIN_VALUE
    private var qnhHpa: Float = BarometricFormula.STANDARD_PRESSURE_HPA

    fun reset() {
        pressureFilter.reset()
        gnssAltFilter.reset()
        lastPressureNanos = Long.MIN_VALUE
        lastGnssNanos = Long.MIN_VALUE
    }

    fun setQnh(qnh: Float) {
        if (BarometricFormula.isValidQnh(qnh)) qnhHpa = qnh
    }

    /** Returns the new barometric altitude in meters, for feeding the VSI. */
    fun onPressure(pressureHpa: Float, elapsedNanos: Long): Float? {
        if (!pressureHpa.isFinite() || pressureHpa <= 0f) return null
        val dt = if (lastPressureNanos == Long.MIN_VALUE) 0f else (elapsedNanos - lastPressureNanos) / 1e9f
        pressureFilter.update(pressureHpa, dt)
        lastPressureNanos = elapsedNanos
        return barometricAltitudeM()
    }

    /** Returns the filtered GNSS altitude in meters, for a possible VSI fallback. */
    fun onGnssAltitude(altitudeMslOrWgs84M: Double?, elapsedNanos: Long): Float? {
        val alt = altitudeMslOrWgs84M ?: return null
        val dt = if (lastGnssNanos == Long.MIN_VALUE) 0f else (elapsedNanos - lastGnssNanos) / 1e9f
        gnssAltFilter.update(alt.toFloat(), dt)
        lastGnssNanos = elapsedNanos
        return gnssAltFilter.value
    }

    fun barometricAltitudeM(): Float? {
        if (!pressureFilter.isInitialized) return null
        return BarometricFormula.altitudeMeters(pressureFilter.value, qnhHpa)
    }

    fun gnssAltitudeM(): Float? = if (gnssAltFilter.isInitialized) gnssAltFilter.value else null

    fun solution(nowNanos: Long): AltitudeSolution {
        val baroFresh = hasBarometer && lastPressureNanos != Long.MIN_VALUE &&
            nowNanos - lastPressureNanos <= BARO_STALE_NS
        val gnssFresh = lastGnssNanos != Long.MIN_VALUE &&
            nowNanos - lastGnssNanos <= GNSS_STALE_NS

        val baro = if (baroFresh) barometricAltitudeM() else null
        val gnss = if (gnssFresh) gnssAltitudeM() else null

        return when {
            baro != null -> AltitudeSolution(baro, baro, gnss, AltitudeSource.BAROMETRIC)
            gnss != null -> AltitudeSolution(gnss, null, gnss, AltitudeSource.GNSS)
            else -> AltitudeSolution(null, null, null, AltitudeSource.NONE)
        }
    }

    private companion object {
        const val BARO_STALE_NS = 2_000_000_000L
        const val GNSS_STALE_NS = 6_000_000_000L
    }
}
