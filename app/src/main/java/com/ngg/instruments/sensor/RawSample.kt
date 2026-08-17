package com.ngg.instruments.sensor

import com.ngg.instruments.gnss.GnssFix
import com.ngg.instruments.math.Quaternion
import kotlinx.coroutines.flow.Flow

/**
 * Raw input samples flowing into the estimation pipeline. Live sensors,
 * recordings and replays all speak this type, which is what makes replay
 * deterministic: the engine cannot tell the difference.
 */
sealed interface RawSample {
    /** Monotonic timestamp (SystemClock.elapsedRealtimeNanos domain). */
    val elapsedNanos: Long
}

enum class RotationKind {
    /** TYPE_GAME_ROTATION_VECTOR: gyro+accel fusion, arbitrary yaw reference. */
    GAME,
    /** TYPE_ROTATION_VECTOR (or geomagnetic RV): yaw referenced to magnetic north. */
    MAGNETIC,
}

data class RotationSample(
    val kind: RotationKind,
    val quaternion: Quaternion,
    /** SensorManager.SENSOR_STATUS_* accuracy of the underlying sensor. */
    val accuracy: Int,
    override val elapsedNanos: Long,
) : RawSample

data class PressureSample(
    val pressureHpa: Float,
    val accuracy: Int,
    override val elapsedNanos: Long,
) : RawSample

data class GnssSample(
    val fix: GnssFix,
    override val elapsedNanos: Long,
) : RawSample

/** Constellation status: satellites used in fix / visible. */
data class GnssStatusSample(
    val satellitesUsed: Int,
    val satellitesVisible: Int,
    override val elapsedNanos: Long,
) : RawSample

/**
 * A source of raw flight data. [LiveFlightDataSources] merges real sensors and
 * GNSS; [com.ngg.instruments.recording.SensorReplay] plays back a recording.
 */
interface FlightDataSources {
    val samples: Flow<RawSample>
}
