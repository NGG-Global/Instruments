package com.ngg.instruments.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.ngg.instruments.math.Quaternion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Cold flows over Android sensors. Listeners are registered when the flow is
 * collected and unregistered when collection stops, so sensor power follows
 * the app lifecycle automatically.
 */
private const val SAMPLING_PERIOD_US = 20_000 // 50 Hz; decoupled from display rate

private fun sensorFlow(
    context: Context,
    sensorType: Int,
    transform: (SensorEvent) -> RawSample?,
): Flow<RawSample> {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sm.getDefaultSensor(sensorType) ?: return emptyFlow()
    return callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                transform(event)?.let { trySend(it) }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SAMPLING_PERIOD_US)
        awaitClose { sm.unregisterListener(listener) }
    }
}

/**
 * Attitude source. Prefers TYPE_GAME_ROTATION_VECTOR (gyro-based, immune to
 * magnetic disturbance); falls back to TYPE_ROTATION_VECTOR when absent.
 */
class AttitudeSensorSource(private val context: Context, capabilities: SensorCapabilities) {
    val usesFallback: Boolean = !capabilities.hasGameRotationVector && capabilities.hasRotationVector

    private val sensorType: Int? = when {
        capabilities.hasGameRotationVector -> Sensor.TYPE_GAME_ROTATION_VECTOR
        capabilities.hasRotationVector -> Sensor.TYPE_ROTATION_VECTOR
        else -> null
    }

    val samples: Flow<RawSample> = sensorType?.let { type ->
        sensorFlow(context, type) { event ->
            RotationSample(
                kind = RotationKind.GAME,
                quaternion = Quaternion.fromRotationVector(event.values),
                accuracy = event.accuracy,
                elapsedNanos = event.timestamp,
            )
        }
    } ?: emptyFlow()
}

/**
 * Heading source referenced to magnetic north. Prefers TYPE_ROTATION_VECTOR;
 * falls back to TYPE_GEOMAGNETIC_ROTATION_VECTOR on gyro-less hardware.
 */
class HeadingSensorSource(private val context: Context, capabilities: SensorCapabilities) {
    private val sensorType: Int? = when {
        capabilities.hasRotationVector -> Sensor.TYPE_ROTATION_VECTOR
        capabilities.hasGeomagneticRotationVector -> Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
        else -> null
    }

    val samples: Flow<RawSample> = sensorType?.let { type ->
        sensorFlow(context, type) { event ->
            RotationSample(
                kind = RotationKind.MAGNETIC,
                quaternion = Quaternion.fromRotationVector(event.values),
                accuracy = event.accuracy,
                elapsedNanos = event.timestamp,
            )
        }
    } ?: emptyFlow()
}

/** Static pressure in hPa from TYPE_PRESSURE when present. */
class PressureSensorSource(private val context: Context, capabilities: SensorCapabilities) {
    val available: Boolean = capabilities.hasPressure

    val samples: Flow<RawSample> = if (available) {
        sensorFlow(context, Sensor.TYPE_PRESSURE) { event ->
            PressureSample(
                pressureHpa = event.values[0],
                accuracy = event.accuracy,
                elapsedNanos = event.timestamp,
            )
        }
    } else {
        emptyFlow()
    }
}
