package com.ngg.instruments.gnss

import android.hardware.GeomagneticField
import com.ngg.instruments.flight.DeclinationProvider

/**
 * Magnetic declination from Android's bundled World Magnetic Model
 * (GeomagneticField). Fully offline.
 */
class AndroidDeclinationProvider : DeclinationProvider {
    override fun declinationDeg(
        latitude: Double,
        longitude: Double,
        altitudeM: Double,
        timeMs: Long,
    ): Float = GeomagneticField(
        latitude.toFloat(),
        longitude.toFloat(),
        altitudeM.toFloat(),
        timeMs,
    ).declination
}
