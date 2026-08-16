package com.ngg.instruments.flight

import com.ngg.instruments.flight.altitude.AltitudeEstimator
import com.ngg.instruments.flight.attitude.AttitudeEstimator
import com.ngg.instruments.flight.heading.HeadingEstimator
import com.ngg.instruments.flight.speed.GroundSpeedEstimator
import com.ngg.instruments.flight.verticalspeed.VerticalSpeedEstimator
import com.ngg.instruments.gnss.GnssFix
import com.ngg.instruments.gnss.LocationQualityEvaluator
import com.ngg.instruments.math.Quaternion
import com.ngg.instruments.math.Units
import com.ngg.instruments.sensor.FlightDataSources
import com.ngg.instruments.sensor.GnssSample
import com.ngg.instruments.sensor.PressureSample
import com.ngg.instruments.sensor.RotationKind
import com.ngg.instruments.sensor.RotationSample
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/** Settings the engine needs; provided as a flow so changes apply live. */
data class EngineSettings(
    val qnhHpa: Float,
    val mount: Quaternion,
    val attitudeCalibrated: Boolean,
)

/** Provides magnetic declination; injected so the engine is JVM-testable. */
fun interface DeclinationProvider {
    fun declinationDeg(latitude: Double, longitude: Double, altitudeM: Double, timeMs: Long): Float
}

/**
 * The estimation core. Consumes one raw-sample stream, runs the per-quantity
 * estimators and publishes [FlightState].
 *
 * Time base: the engine's "now" is the newest sample timestamp it has seen
 * (monotonic elapsed-realtime domain). This makes staleness decisions
 * deterministic under replay while behaving identically live.
 */
class FlightDataEngine(
    private val hasBarometer: Boolean,
    private val attitudeUsesFallback: Boolean,
    private val declinationProvider: DeclinationProvider,
) {
    private val _state = MutableStateFlow(FlightState())
    val state: StateFlow<FlightState> = _state.asStateFlow()

    private val attitudeEstimator = AttitudeEstimator()
    private val headingEstimator = HeadingEstimator()
    private val altitudeEstimator = AltitudeEstimator(hasBarometer)
    private val baroVsi = VerticalSpeedEstimator(rateTauSeconds = 1.2f)
    private val gnssVsi = VerticalSpeedEstimator(rateTauSeconds = 4.0f, maxSampleGapSeconds = 6f)
    private val speedEstimator = GroundSpeedEstimator()

    /** Latest raw device orientation, for the SET LEVEL action. */
    val latestDeviceOrientation: Quaternion? get() = attitudeEstimator.deviceOrientation

    private var settings = EngineSettings(
        qnhHpa = com.ngg.instruments.math.BarometricFormula.STANDARD_PRESSURE_HPA,
        mount = Quaternion.IDENTITY,
        attitudeCalibrated = false,
    )

    private var lastFix: GnssFix? = null
    private var declinationDeg: Float? = null
    private var declinationFixTimeMs = 0L

    /** Sample-driven monotonic clock (max observed elapsedNanos). */
    private var sampleClockNanos = Long.MIN_VALUE

    fun resetEstimators() {
        attitudeEstimator.reset()
        headingEstimator.reset()
        altitudeEstimator.reset()
        baroVsi.reset()
        gnssVsi.reset()
        speedEstimator.reset()
        lastFix = null
        sampleClockNanos = Long.MIN_VALUE
    }

    /**
     * Runs the engine against [sources] until cancelled. Sensor registration
     * follows this call's lifetime because the source flows are cold.
     */
    suspend fun run(sources: FlightDataSources, settingsFlow: Flow<EngineSettings>) = coroutineScope {
        launch {
            settingsFlow.collect {
                settings = it
                altitudeEstimator.setQnh(it.qnhHpa)
            }
        }
        launch {
            sources.samples.collect { sample ->
                sampleClockNanos = max(sampleClockNanos, sample.elapsedNanos)
                when (sample) {
                    is RotationSample -> onRotation(sample)
                    is PressureSample -> onPressure(sample)
                    is GnssSample -> onGnss(sample)
                }
            }
        }
        launch {
            // Publication cadence is decoupled from sensor rate.
            while (true) {
                publish()
                delay(PUBLISH_PERIOD_MS)
            }
        }
    }

    private fun onRotation(sample: RotationSample) {
        when (sample.kind) {
            RotationKind.GAME -> attitudeEstimator.update(sample.quaternion, sample.elapsedNanos)
            RotationKind.MAGNETIC -> headingEstimator.update(
                sample.quaternion, settings.mount, sample.accuracy, sample.elapsedNanos,
            )
        }
    }

    private fun onPressure(sample: PressureSample) {
        val baroAltM = altitudeEstimator.onPressure(sample.pressureHpa, sample.elapsedNanos)
        if (baroAltM != null) baroVsi.addSample(baroAltM, sample.elapsedNanos)
    }

    private fun onGnss(sample: GnssSample) {
        val fix = sample.fix
        lastFix = fix
        speedEstimator.onGnssSpeed(fix.speedMps, sample.elapsedNanos)

        val gnssAlt = fix.altitudeMslM ?: fix.altitudeWgs84M
        val filteredGnssAlt = altitudeEstimator.onGnssAltitude(gnssAlt, sample.elapsedNanos)
        if (!hasBarometer && filteredGnssAlt != null) {
            gnssVsi.addSample(filteredGnssAlt, sample.elapsedNanos)
        }

        // Refresh declination when we have moved or enough time has passed.
        if (declinationDeg == null || fix.timeMs - declinationFixTimeMs > DECLINATION_REFRESH_MS) {
            declinationDeg = declinationProvider.declinationDeg(
                fix.latitude, fix.longitude, gnssAlt ?: 0.0, fix.timeMs,
            )
            declinationFixTimeMs = fix.timeMs
        }
    }

    private fun publish() {
        val now = sampleClockNanos
        if (now == Long.MIN_VALUE) {
            _state.value = FlightState(qnhHpa = settings.qnhHpa)
            return
        }

        // --- Attitude ---
        val pitchRoll = if (attitudeEstimator.isFresh(now)) {
            attitudeEstimator.pitchRoll(settings.mount)
        } else null
        val attitudeQuality = when {
            pitchRoll == null -> DataQuality.UNAVAILABLE
            attitudeUsesFallback -> DataQuality.DEGRADED
            !settings.attitudeCalibrated -> DataQuality.DEGRADED
            else -> DataQuality.GOOD
        }

        // --- Heading (never GPS track) ---
        val magneticHeading = headingEstimator.magneticHeadingDeg(now)
        val trueHeading = headingEstimator.trueHeadingDeg(now, declinationDeg)
        val headingQuality = headingEstimator.quality(now)

        // --- GNSS-derived quantities ---
        val fix = lastFix
        val gnssQuality = LocationQualityEvaluator.evaluate(fix, now)
        val track = if (LocationQualityEvaluator.isTrackUsable(fix, now)) fix?.bearingDeg else null
        val speedKt = speedEstimator.groundSpeedKt(now)
        val speedQuality = if (speedKt == null) DataQuality.UNAVAILABLE else {
            LocationQualityEvaluator.speedQuality(fix, now)
        }

        // --- Altitude ---
        val altitude = altitudeEstimator.solution(now)
        val gnssAltQuality = LocationQualityEvaluator.altitudeQuality(fix, now)
        val altitudeQuality = when (altitude.source) {
            AltitudeSource.BAROMETRIC -> DataQuality.GOOD
            AltitudeSource.GNSS -> gnssAltQuality
            AltitudeSource.NONE -> DataQuality.UNAVAILABLE
        }

        // --- Vertical speed ---
        val vsiFpm: Float?
        val vsiSource: AltitudeSource
        val vsiQuality: DataQuality
        if (hasBarometer && baroVsi.isFresh(now)) {
            vsiFpm = baroVsi.verticalSpeedFpm
            vsiSource = AltitudeSource.BAROMETRIC
            vsiQuality = DataQuality.GOOD
        } else if (!hasBarometer && gnssVsi.isFresh(now, maxAgeNanos = 6_000_000_000L)) {
            vsiFpm = gnssVsi.verticalSpeedFpm
            vsiSource = AltitudeSource.GNSS
            vsiQuality = if (gnssAltQuality == DataQuality.POOR) DataQuality.POOR else DataQuality.DEGRADED
        } else {
            vsiFpm = null
            vsiSource = AltitudeSource.NONE
            vsiQuality = DataQuality.UNAVAILABLE
        }

        _state.value = FlightState(
            pitchDeg = pitchRoll?.pitchDeg,
            rollDeg = pitchRoll?.rollDeg,
            magneticHeadingDeg = magneticHeading,
            trueHeadingDeg = trueHeading,
            trackDeg = track,
            altitudeFt = altitude.selectedM?.let(Units::metersToFeet),
            gnssAltitudeFt = altitude.gnssM?.let(Units::metersToFeet),
            barometricAltitudeFt = altitude.barometricM?.let(Units::metersToFeet),
            verticalSpeedFpm = vsiFpm,
            groundSpeedKt = speedKt,
            horizontalAccuracyM = fix?.horizontalAccuracyM,
            verticalAccuracyM = fix?.verticalAccuracyM,
            speedAccuracyMps = fix?.speedAccuracyMps,
            bearingAccuracyDeg = fix?.bearingAccuracyDeg,
            attitudeQuality = attitudeQuality,
            headingQuality = headingQuality,
            altitudeQuality = altitudeQuality,
            verticalSpeedQuality = vsiQuality,
            speedQuality = speedQuality,
            gnssQuality = gnssQuality,
            altitudeSource = altitude.source,
            verticalSpeedSource = vsiSource,
            attitudeCalibrated = settings.attitudeCalibrated,
            qnhHpa = settings.qnhHpa,
        )
    }

    private companion object {
        const val PUBLISH_PERIOD_MS = 33L // ~30 Hz state publication
        const val DECLINATION_REFRESH_MS = 10 * 60 * 1000L
    }
}
