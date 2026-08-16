package com.ngg.instruments.sensor

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * Snapshot of the hardware available on this device, detected once at startup.
 * Drives graceful degradation and the diagnostics screen.
 */
data class SensorCapabilities(
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val hasMagnetometer: Boolean,
    val hasRotationVector: Boolean,
    val hasGameRotationVector: Boolean,
    val hasGeomagneticRotationVector: Boolean,
    val hasPressure: Boolean,
    val hasGnss: Boolean,
) {
    val hasAnyAttitudeSource: Boolean
        get() = hasGameRotationVector || hasRotationVector

    val hasAnyHeadingSource: Boolean
        get() = hasRotationVector || hasGeomagneticRotationVector

    companion object {
        fun detect(context: Context): SensorCapabilities {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            fun has(type: Int) = sm.getDefaultSensor(type) != null
            return SensorCapabilities(
                hasAccelerometer = has(Sensor.TYPE_ACCELEROMETER),
                hasGyroscope = has(Sensor.TYPE_GYROSCOPE),
                hasMagnetometer = has(Sensor.TYPE_MAGNETIC_FIELD),
                hasRotationVector = has(Sensor.TYPE_ROTATION_VECTOR),
                hasGameRotationVector = has(Sensor.TYPE_GAME_ROTATION_VECTOR),
                hasGeomagneticRotationVector = has(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR),
                hasPressure = has(Sensor.TYPE_PRESSURE),
                hasGnss = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
            )
        }
    }
}
