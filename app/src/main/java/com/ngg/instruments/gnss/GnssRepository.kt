package com.ngg.instruments.gnss

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.ngg.instruments.sensor.GnssSample
import com.ngg.instruments.sensor.GnssStatusSample
import com.ngg.instruments.sensor.RawSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * GNSS fixes from the platform LocationManager GPS provider. Deliberately does
 * NOT use fused/network location so the application works fully offline.
 *
 * MSL altitude is derived on-device with android.location.altitude.AltitudeConverter
 * on API 34+ (bundled geoid model, no network). On older releases only the
 * WGS84 ellipsoid altitude is available and is labeled accordingly.
 */
class GnssRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    val samples: Flow<RawSample>
        get() {
            if (!hasPermission()) return emptyFlow()
            val fixes = rawLocations()
                .map { location ->
                    val msl = tryConvertToMsl(location)
                    GnssSample(fix = location.toGnssFix(msl), elapsedNanos = location.elapsedRealtimeNanos)
                }
                .flowOn(Dispatchers.IO) // AltitudeConverter performs disk I/O
            return merge(fixes, constellationStatus())
        }

    /** Satellite counts for the panel status line. */
    private fun constellationStatus(): Flow<RawSample> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                trySend(
                    GnssStatusSample(
                        satellitesUsed = used,
                        satellitesVisible = status.satelliteCount,
                        elapsedNanos = SystemClock.elapsedRealtimeNanos(),
                    ),
                )
            }
        }
        val registered = try {
            @Suppress("DEPRECATION") // Executor overload requires API 30; minSdk is 26
            lm.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        } catch (se: SecurityException) {
            false
        }
        if (!registered) close()
        awaitClose { lm.unregisterGnssStatusCallback(callback) }
    }

    private fun rawLocations(): Flow<Location> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Deprecated in API 29, still required on minSdk 26")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        } catch (se: SecurityException) {
            close()
        } catch (iae: IllegalArgumentException) {
            // GPS provider missing on this device.
            close()
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    /**
     * Adds MSL altitude via AltitudeConverter where the platform supports it.
     * Never crashes when the API or its geoid data is unavailable.
     */
    private fun tryConvertToMsl(location: Location): Double? {
        if (Build.VERSION.SDK_INT < 34 || !location.hasAltitude()) return null
        return try {
            val converter = android.location.altitude.AltitudeConverter()
            converter.addMslAltitudeToLocation(context, location)
            if (location.hasMslAltitude()) location.mslAltitudeMeters else null
        } catch (t: Throwable) {
            null
        }
    }

    companion object {
        private const val MIN_UPDATE_INTERVAL_MS = 500L

        private const val MAX_REASONABLE_ACCURACY_M = 10_000f

        fun Location.toGnssFix(mslAltitudeM: Double?): GnssFix = GnssFix(
            latitude = latitude,
            longitude = longitude,
            altitudeWgs84M = if (hasAltitude()) altitude else null,
            altitudeMslM = mslAltitudeM,
            speedMps = if (hasSpeed() && speed >= 0f) speed else null,
            bearingDeg = if (hasBearing()) bearing else null,
            horizontalAccuracyM = if (hasAccuracy() && accuracy in 0f..MAX_REASONABLE_ACCURACY_M) accuracy else null,
            verticalAccuracyM = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
            speedAccuracyMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
            bearingAccuracyDeg = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            timeMs = time,
        )
    }
}
