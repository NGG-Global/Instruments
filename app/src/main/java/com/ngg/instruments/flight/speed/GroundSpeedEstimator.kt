package com.ngg.instruments.flight.speed

import com.ngg.instruments.math.LowPassFilter
import com.ngg.instruments.math.Units

/**
 * Ground speed from GNSS Doppler speed (Location.speed). Position-difference
 * speed is intentionally NOT derived; the receiver's Doppler speed is far
 * better. A short low-pass (tau = 0.5 s) steadies the needle without masking
 * real acceleration.
 */
class GroundSpeedEstimator(smoothingTauSeconds: Float = 0.5f) {

    private val filter = LowPassFilter(smoothingTauSeconds)
    private var lastSampleNanos = Long.MIN_VALUE

    fun reset() {
        filter.reset()
        lastSampleNanos = Long.MIN_VALUE
    }

    fun onGnssSpeed(speedMps: Float?, elapsedNanos: Long) {
        if (speedMps == null || !speedMps.isFinite() || speedMps < 0f) return
        val dt = if (lastSampleNanos == Long.MIN_VALUE) 0f else (elapsedNanos - lastSampleNanos) / 1e9f
        filter.update(speedMps, dt)
        lastSampleNanos = elapsedNanos
    }

    fun groundSpeedKt(nowNanos: Long): Float? {
        if (!filter.isInitialized || nowNanos - lastSampleNanos > STALE_NS) return null
        return Units.mpsToKnots(filter.value)
    }

    private companion object {
        const val STALE_NS = 5_000_000_000L
    }
}
