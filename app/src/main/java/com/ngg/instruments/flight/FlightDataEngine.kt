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
import com.ngg.instruments.sensor.GnssStatusSample
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
    private var lastStatus: GnssStatusSample? = null
    private var statusNanos = Long.MIN_VALUE
    private var declinationDeg: Float? = null
    private var declinationFixTimeMs = 0L

    /**
     * Sample-driven monotonic engine clock, in nanoseconds.
     *
     * Raw timestamps cannot be compared across sources: SensorEvent.timestamp
     * is not guaranteed to share the SystemClock.elapsedRealtimeNanos base that
     * Location uses, and on real hardware the two can be hours apart. Feeding
     * both into one max() clock makes whichever source runs "ahead" declare the
     * other permanently stale — which silently kills ground speed, track and
     * GNSS altitude (or, mirrored, attitude and heading).
     *
     * Each source therefore gets a one-time offset that maps its first sample
     * onto the current engine time; afterwards its own deltas advance the
     * shared timeline. Staleness is then measured on a single consistent clock,
     * and a source that stops sending still ages out correctly because the
     * other sources keep the clock moving. Offsets depend only on sample order,
     * so replay stays deterministic.
     */
    private var engineNowNanos = 0L
    private var hasAnySample = false

    /** Time-base offsets: index 0 = SensorManager sources, 1 = GNSS sources. */
    private val sourceOffsets = arrayOfNulls<Long>(2)

    private fun normalize(rawNanos: Long, source: Int): Long {
        val offset = sourceOffsets[source] ?: (rawNanos - engineNowNanos).also {
            sourceOffsets[source] = it
        }
        val t = rawNanos - offset
        if (t > engineNowNanos) engineNowNanos = t
        hasAnySample = true
        return t
    }

    fun resetEstimators() {
        attitudeEstimator.reset()
        headingEstimator.reset()
        altitudeEstimator.reset()
        baroVsi.reset()
        gnssVsi.reset()
        speedEstimator.reset()
        lastFix = null
        lastStatus = null
        statusNanos = Long.MIN_VALUE
        engineNowNanos = 0L
        hasAnySample = false
        sourceOffsets.fill(null)
        declinationDeg = null
        declinationFixTimeMs = 0L
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
                when (sample) {
                    is RotationSample -> onRotation(sample, normalize(sample.elapsedNanos, SENSOR_SOURCE))
                    is PressureSample -> onPressure(sample, normalize(sample.elapsedNanos, SENSOR_SOURCE))
                    is GnssSample -> onGnss(sample, normalize(sample.elapsedNanos, GNSS_SOURCE))
                    is GnssStatusSample -> {
                        statusNanos = normalize(sample.elapsedNanos, GNSS_SOURCE)
                        lastStatus = sample
                    }
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

    private fun onRotation(sample: RotationSample, tNanos: Long) {
        when (sample.kind) {
            RotationKind.GAME -> attitudeEstimator.update(sample.quaternion, tNanos)
            RotationKind.MAGNETIC -> headingEstimator.update(
                sample.quaternion, settings.mount, sample.accuracy, tNanos,
            )
        }
    }

    private fun onPressure(sample: PressureSample, tNanos: Long) {
        val baroAltM = altitudeEstimator.onPressure(sample.pressureHpa, tNanos)
        if (baroAltM != null) baroVsi.addSample(baroAltM, tNanos)
    }

    private fun onGnss(sample: GnssSample, tNanos: Long) {
        val fix = sample.fix
        lastFix = fix
        speedEstimator.onGnssSpeed(fix.speedMps, tNanos)

        val gnssAlt = fix.altitudeMslM ?: fix.altitudeWgs84M
        val filteredGnssAlt = altitudeEstimator.onGnssAltitude(gnssAlt, tNanos)
        if (!hasBarometer && filteredGnssAlt != null) {
            gnssVsi.addSample(filteredGnssAlt, tNanos)
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
        if (!hasAnySample) {
            _state.value = FlightState(qnhHpa = settings.qnhHpa)
            return
        }
        val now = engineNowNanos
        // The quality evaluator reads raw Location timestamps, so give it "now"
        // converted back into the GNSS time base (raw = engine + offset).
        val gnssNow = sourceOffsets[GNSS_SOURCE]?.let { now + it } ?: now

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
        val status = lastStatus?.takeIf { now - statusNanos <= STATUS_STALE_NS }
        val gnssQuality = LocationQualityEvaluator.evaluate(fix, gnssNow)
        val track = if (LocationQualityEvaluator.isTrackUsable(fix, gnssNow)) fix?.bearingDeg else null
        val speedKt = speedEstimator.groundSpeedKt(now)
        val speedQuality = if (speedKt == null) DataQuality.UNAVAILABLE else {
            LocationQualityEvaluator.speedQuality(fix, gnssNow)
        }

        // --- Altitude ---
        val altitude = altitudeEstimator.solution(now)
        val gnssAltQuality = LocationQualityEvaluator.altitudeQuality(fix, gnssNow)
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
            satellitesUsed = status?.satellitesUsed,
            satellitesVisible = status?.satellitesVisible,
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
        const val STATUS_STALE_NS = 6_000_000_000L
        const val SENSOR_SOURCE = 0
        const val GNSS_SOURCE = 1
    }
}
