package com.ngg.instruments.flight

import com.ngg.instruments.gnss.GnssFix
import com.ngg.instruments.math.Quaternion
import com.ngg.instruments.sensor.FlightDataSources
import com.ngg.instruments.sensor.GnssSample
import com.ngg.instruments.sensor.PressureSample
import com.ngg.instruments.sensor.RawSample
import com.ngg.instruments.sensor.RotationKind
import com.ngg.instruments.sensor.RotationSample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end verification of the whole estimation pipeline: synthetic raw
 * samples are pushed through the real [FlightDataEngine] and the resulting
 * [FlightState] is asserted per instrument. This is the closest thing to a
 * bench test of the panel without a physical device.
 *
 * Timestamps deliberately exercise the fact that Android sensor timestamps
 * and GNSS timestamps do not always share a time base on real hardware.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlightDataEngineTest {

    private fun ns(seconds: Double): Long = (seconds * 1e9).toLong()

    private fun fix(
        tSeconds: Double,
        speedMps: Float? = 51.44444f, // 100 kt
        bearing: Float? = 90f,
        altMsl: Double? = 300.0,
        hAcc: Float = 4f,
    ) = GnssSample(
        fix = GnssFix(
            latitude = 32.0,
            longitude = 34.8,
            altitudeWgs84M = altMsl?.plus(17.0),
            altitudeMslM = altMsl,
            speedMps = speedMps,
            bearingDeg = bearing,
            horizontalAccuracyM = hAcc,
            verticalAccuracyM = 6f,
            speedAccuracyMps = 0.4f,
            bearingAccuracyDeg = 2f,
            elapsedRealtimeNanos = ns(tSeconds),
            timeMs = 1_770_000_000_000L + (tSeconds * 1000).toLong(),
        ),
        elapsedNanos = ns(tSeconds),
    )

    /** Device level and pointing north. */
    private fun rotation(tSeconds: Double, kind: RotationKind, headingDeg: Float = 0f) =
        RotationSample(
            kind = kind,
            quaternion = Quaternion.aboutZ(-headingDeg),
            accuracy = 3,
            elapsedNanos = ns(tSeconds),
        )

    private fun pressure(tSeconds: Double, hPa: Float) =
        PressureSample(pressureHpa = hPa, accuracy = 3, elapsedNanos = ns(tSeconds))

    private fun sourcesOf(samples: List<RawSample>) = object : FlightDataSources {
        override val samples: Flow<RawSample> = flow {
            samples.forEach { emit(it) }
            awaitCancellation()
        }
    }

    private val settings = flowOf(
        EngineSettings(qnhHpa = 1013.25f, mount = Quaternion.IDENTITY, attitudeCalibrated = true),
    )

    /** Runs the engine over [samples] and returns the published state. */
    private suspend fun kotlinx.coroutines.test.TestScope.stateFor(
        engine: FlightDataEngine,
        samples: List<RawSample>,
    ): FlightState {
        val job = launch { engine.run(sourcesOf(samples), settings) }
        advanceTimeBy(500)
        runCurrent()
        val state = engine.state.value
        job.cancel()
        return state
    }

    private fun engine(hasBarometer: Boolean = true) = FlightDataEngine(
        hasBarometer = hasBarometer,
        attitudeUsesFallback = false,
        declinationProvider = { _, _, _, _ -> 5f },
    )

    // ------------------------------------------------------------------ speed

    @Test
    fun groundSpeedFollowsGnssDopplerSpeed() = runTest {
        val samples = buildList {
            // 10 s of GNSS at 1 Hz plus 50 Hz attitude, as on a real device.
            for (i in 0..10) add(fix(i.toDouble()))
            for (i in 0..500) add(rotation(i / 50.0, RotationKind.GAME))
        }.sortedBy { it.elapsedNanos }

        val state = stateFor(engine(), samples)
        assertNotNull("ground speed must be reported", state.groundSpeedKt)
        assertEquals(100f, state.groundSpeedKt!!, 1f)
        assertEquals(DataQuality.GOOD, state.speedQuality)
    }

    @Test
    fun groundSpeedSurvivesSensorAndGnssTimestampsInDifferentTimeBases() = runTest {
        // Real hardware caveat: SensorEvent.timestamp is not guaranteed to be
        // in the SystemClock.elapsedRealtimeNanos base that Location uses.
        // A device that reports sensor time far ahead of GNSS time must not
        // cause every GNSS-derived value to be treated as stale.
        // Interleaved as on a device: 50 Hz attitude and 1 Hz GNSS arriving
        // together, but with sensor timestamps 50 000 s ahead of GNSS ones.
        val samples = buildList {
            for (i in 0..500) {
                add(rotation(50_000.0 + i / 50.0, RotationKind.GAME))
                if (i % 50 == 0) add(fix(i / 50.0))
            }
        }

        val state = stateFor(engine(), samples)
        assertNotNull("ground speed lost when sensor clock runs ahead", state.groundSpeedKt)
        assertEquals(100f, state.groundSpeedKt!!, 1f)
        assertNotNull("track lost when sensor clock runs ahead", state.trackDeg)
        assertNotNull("GNSS altitude lost when sensor clock runs ahead", state.gnssAltitudeFt)
    }

    @Test
    fun attitudeSurvivesGnssTimestampsRunningAheadOfSensors() = runTest {
        // The mirrored case: GNSS time ahead of the sensor base must not make
        // attitude and heading disappear.
        val samples = buildList {
            for (i in 0..500) {
                add(rotation(i / 50.0, RotationKind.GAME))
                add(rotation(i / 50.0, RotationKind.MAGNETIC, headingDeg = 90f))
                if (i % 50 == 0) add(fix(50_000.0 + i / 50.0)) // GNSS base far ahead
            }
        }

        val state = stateFor(engine(), samples)
        assertNotNull("attitude lost when GNSS clock runs ahead", state.pitchDeg)
        assertNotNull("heading lost when GNSS clock runs ahead", state.magneticHeadingDeg)
    }

    @Test
    fun stationaryReceiverReportsSpeedButNoTrack() = runTest {
        val samples = buildList {
            for (i in 0..10) add(fix(i.toDouble(), speedMps = 0.2f)) // below track threshold
            for (i in 0..500) add(rotation(i / 50.0, RotationKind.GAME))
        }.sortedBy { it.elapsedNanos }

        val state = stateFor(engine(), samples)
        assertNotNull(state.groundSpeedKt)
        assertTrue("stationary speed should be ~0", state.groundSpeedKt!! < 1f)
        assertNull("track must not be fabricated while stationary", state.trackDeg)
    }

    @Test
    fun missingGnssSpeedLeavesGroundSpeedUnavailable() = runTest {
        val samples = buildList {
            for (i in 0..10) add(fix(i.toDouble(), speedMps = null))
            for (i in 0..500) add(rotation(i / 50.0, RotationKind.GAME))
        }.sortedBy { it.elapsedNanos }

        val state = stateFor(engine(), samples)
        assertNull(state.groundSpeedKt)
        assertEquals(DataQuality.UNAVAILABLE, state.speedQuality)
    }

    // --------------------------------------------------------------- attitude

    @Test
    fun attitudeReportsPitchAndRoll() = runTest {
        val q = (Quaternion.aboutZ(-30f) * Quaternion.aboutX(10f) * Quaternion.aboutY(-20f)).normalized()
        val samples = (0..200).map {
            RotationSample(RotationKind.GAME, q, 3, ns(it / 50.0))
        }

        val state = stateFor(engine(), samples)
        assertEquals(10f, state.pitchDeg!!, 0.5f)
        assertEquals(-20f, state.rollDeg!!, 0.5f)
        assertEquals(DataQuality.GOOD, state.attitudeQuality)
    }

    // ---------------------------------------------------------------- heading

    @Test
    fun headingAndTrackStaySeparateValues() = runTest {
        // Nose pointing 090, but moving on a 180 track (crabbing).
        val samples = buildList {
            for (i in 0..500) {
                add(rotation(i / 50.0, RotationKind.MAGNETIC, headingDeg = 90f))
                if (i % 50 == 0) add(fix(i / 50.0, bearing = 180f))
            }
        }

        val state = stateFor(engine(), samples)
        assertEquals("magnetic heading", 90f, state.magneticHeadingDeg!!, 1f)
        assertEquals("true heading = magnetic + declination", 95f, state.trueHeadingDeg!!, 1f)
        assertEquals("track is course over ground", 180f, state.trackDeg!!, 0.1f)
    }

    // --------------------------------------------------------------- altitude

    @Test
    fun barometricAltitudeAndVerticalSpeedFromPressure() = runTest {
        // Climb: pressure falling steadily for 10 s at 10 Hz.
        val samples = buildList {
            for (i in 0..100) {
                val t = i / 10.0
                // -0.6 hPa/s ≈ 5 m/s climb near sea level; over 10 s that is
                // ~50 m (~165 ft) gained and ~985 fpm on the VSI.
                add(pressure(t, 1013.25f - 0.6f * t.toFloat()))
            }
        }

        val state = stateFor(engine(hasBarometer = true), samples)
        assertEquals(AltitudeSource.BAROMETRIC, state.altitudeSource)
        assertNotNull(state.barometricAltitudeFt)
        assertTrue("altitude should have climbed, was ${state.altitudeFt}", state.altitudeFt!! > 140f)
        assertNotNull(state.verticalSpeedFpm)
        assertTrue("VSI should show a climb, was ${state.verticalSpeedFpm}", state.verticalSpeedFpm!! > 500f)
        assertEquals(AltitudeSource.BAROMETRIC, state.verticalSpeedSource)
    }

    @Test
    fun gnssAltitudeFallbackWhenNoBarometer() = runTest {
        val samples = buildList {
            for (i in 0..20) add(fix(i.toDouble(), altMsl = 300.0))
            for (i in 0..500) add(rotation(i / 50.0, RotationKind.GAME))
        }.sortedBy { it.elapsedNanos }

        val state = stateFor(engine(hasBarometer = false), samples)
        assertEquals(AltitudeSource.GNSS, state.altitudeSource)
        // 300 m = 984 ft
        assertEquals(984f, state.altitudeFt!!, 20f)
        assertNull(state.barometricAltitudeFt)
    }

    @Test
    fun gnssVerticalSpeedFallbackWhenNoBarometer() = runTest {
        // 2 m/s climb sampled at 1 Hz for 30 s -> ~394 fpm, degraded quality.
        val samples = (0..30).map { fix(it.toDouble(), altMsl = 300.0 + 2.0 * it) }

        val state = stateFor(engine(hasBarometer = false), samples)
        assertNotNull(state.verticalSpeedFpm)
        assertEquals(AltitudeSource.GNSS, state.verticalSpeedSource)
        assertTrue("expected a climb, was ${state.verticalSpeedFpm}", state.verticalSpeedFpm!! > 150f)
        assertEquals(DataQuality.DEGRADED, state.verticalSpeedQuality)
    }

    // ------------------------------------------------------------ degradation

    @Test
    fun noSamplesMeansEverythingUnavailableNotZero() = runTest {
        val state = stateFor(engine(), emptyList())
        assertNull(state.pitchDeg)
        assertNull(state.magneticHeadingDeg)
        assertNull(state.trackDeg)
        assertNull(state.altitudeFt)
        assertNull(state.verticalSpeedFpm)
        assertNull(state.groundSpeedKt)
        assertEquals(DataQuality.UNAVAILABLE, state.gnssQuality)
    }

    @Test
    fun sensorsOnlyStillDrivesAttitudeWithoutGnss() = runTest {
        // Indoors: no GNSS at all. Attitude must still work; GNSS values null.
        val samples = (0..200).map { rotation(it / 50.0, RotationKind.GAME) }

        val state = stateFor(engine(), samples)
        assertNotNull("attitude must work without GNSS", state.pitchDeg)
        assertNull(state.groundSpeedKt)
        assertNull(state.trackDeg)
        assertEquals(DataQuality.UNAVAILABLE, state.gnssQuality)
    }

    @Test
    fun staleGnssStopsBeingReported() = runTest {
        // A fix at t=0 then 60 s of sensor-only data: GNSS must age out.
        val samples = buildList {
            add(fix(0.0))
            for (i in 0..600) add(rotation(i / 10.0, RotationKind.GAME))
        }

        val state = stateFor(engine(), samples)
        assertNull("60 s old fix must not be reported as speed", state.groundSpeedKt)
        assertEquals(DataQuality.UNAVAILABLE, state.gnssQuality)
    }
}
