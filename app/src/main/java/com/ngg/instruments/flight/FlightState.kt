package com.ngg.instruments.flight

/**
 * Explicit data quality. Unavailable data is represented by null values plus
 * an UNAVAILABLE quality — never by zero.
 */
enum class DataQuality { GOOD, DEGRADED, POOR, UNAVAILABLE }

enum class AltitudeSource { BAROMETRIC, GNSS, NONE }

/**
 * The single normalized application state. Produced only by [FlightDataEngine];
 * the UI never interprets raw sensor values.
 */
data class FlightState(
    val pitchDeg: Float? = null,
    val rollDeg: Float? = null,

    val magneticHeadingDeg: Float? = null,
    val trueHeadingDeg: Float? = null,
    val trackDeg: Float? = null,

    val altitudeFt: Float? = null,
    val gnssAltitudeFt: Float? = null,
    val barometricAltitudeFt: Float? = null,

    val verticalSpeedFpm: Float? = null,
    val groundSpeedKt: Float? = null,

    val horizontalAccuracyM: Float? = null,
    val verticalAccuracyM: Float? = null,
    val speedAccuracyMps: Float? = null,
    val bearingAccuracyDeg: Float? = null,

    val attitudeQuality: DataQuality = DataQuality.UNAVAILABLE,
    val headingQuality: DataQuality = DataQuality.UNAVAILABLE,
    val altitudeQuality: DataQuality = DataQuality.UNAVAILABLE,
    val verticalSpeedQuality: DataQuality = DataQuality.UNAVAILABLE,
    val speedQuality: DataQuality = DataQuality.UNAVAILABLE,
    val gnssQuality: DataQuality = DataQuality.UNAVAILABLE,

    val altitudeSource: AltitudeSource = AltitudeSource.NONE,
    val verticalSpeedSource: AltitudeSource = AltitudeSource.NONE,

    /** True while the attitude reference has been calibrated ("SET LEVEL"). */
    val attitudeCalibrated: Boolean = false,
    val qnhHpa: Float = com.ngg.instruments.math.BarometricFormula.STANDARD_PRESSURE_HPA,
)
