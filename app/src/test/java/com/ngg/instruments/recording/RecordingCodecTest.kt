package com.ngg.instruments.recording

import com.ngg.instruments.gnss.GnssFix
import com.ngg.instruments.math.Quaternion
import com.ngg.instruments.sensor.GnssSample
import com.ngg.instruments.sensor.PressureSample
import com.ngg.instruments.sensor.RotationKind
import com.ngg.instruments.sensor.RotationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingCodecTest {

    @Test
    fun rotationRoundTrip() {
        val sample = RotationSample(
            kind = RotationKind.GAME,
            quaternion = Quaternion(0.9f, 0.1f, -0.2f, 0.3f),
            accuracy = 3,
            elapsedNanos = 123_456_789L,
        )
        assertEquals(sample, RecordingCodec.decode(RecordingCodec.encode(sample)))
    }

    @Test
    fun magneticRotationRoundTrip() {
        val sample = RotationSample(RotationKind.MAGNETIC, Quaternion(1f, 0f, 0f, 0f), 2, 5L)
        assertEquals(sample, RecordingCodec.decode(RecordingCodec.encode(sample)))
    }

    @Test
    fun pressureRoundTrip() {
        val sample = PressureSample(1008.42f, 3, 42L)
        assertEquals(sample, RecordingCodec.decode(RecordingCodec.encode(sample)))
    }

    @Test
    fun gnssRoundTripWithNulls() {
        val sample = GnssSample(
            fix = GnssFix(
                latitude = 32.109,
                longitude = 34.855,
                altitudeWgs84M = 87.3,
                altitudeMslM = null,
                speedMps = null,
                bearingDeg = 271.5f,
                horizontalAccuracyM = 4.2f,
                verticalAccuracyM = null,
                speedAccuracyMps = 0.3f,
                bearingAccuracyDeg = null,
                elapsedRealtimeNanos = 987_654_321L,
                timeMs = 1_700_000_000_123L,
            ),
            elapsedNanos = 987_654_321L,
        )
        assertEquals(sample, RecordingCodec.decode(RecordingCodec.encode(sample)))
    }

    @Test
    fun gnssStatusRoundTrip() {
        val sample = com.ngg.instruments.sensor.GnssStatusSample(9, 14, 77L)
        assertEquals(sample, RecordingCodec.decode(RecordingCodec.encode(sample)))
    }

    @Test
    fun headerAndGarbageAreIgnored() {
        assertNull(RecordingCodec.decode(RecordingCodec.HEADER))
        assertNull(RecordingCodec.decode(""))
        assertNull(RecordingCodec.decode("bogus,1,2,3"))
        assertNull(RecordingCodec.decode("grv,notanumber"))
    }
}
