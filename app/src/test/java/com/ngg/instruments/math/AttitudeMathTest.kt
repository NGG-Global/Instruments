package com.ngg.instruments.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Orientation-transform tests: reference calibration, relative rotation,
 * pitch, roll, combined transforms and wraparound.
 *
 * Conventions under test:
 *   world = ENU, body X right / Y forward / Z up,
 *   aircraft rotation A = Rz(yawMath) * Rx(pitch) * Ry(roll),
 *   compass heading h corresponds to yawMath = -h.
 */
class AttitudeMathTest {

    private fun aircraft(headingDeg: Float, pitchDeg: Float, rollDeg: Float): Quaternion =
        (Quaternion.aboutZ(-headingDeg) * Quaternion.aboutX(pitchDeg) * Quaternion.aboutY(rollDeg)).normalized()

    /** Simulates the device sensor output for a device mounted with [mount] (device→body). */
    private fun deviceQuaternion(aircraftRotation: Quaternion, mountDeviceToBody: Quaternion): Quaternion =
        (aircraftRotation * mountDeviceToBody).normalized()

    private fun assertPitchRoll(
        expectedPitch: Float,
        expectedRoll: Float,
        mountDeviceToBody: Quaternion,
        mountYawSetting: Float,
        headingDeg: Float,
        calibrationHeadingDeg: Float = 123f,
    ) {
        val qRef = deviceQuaternion(aircraft(calibrationHeadingDeg, 0f, 0f), mountDeviceToBody)
        val mount = AttitudeMath.mountFromReference(qRef, mountYawSetting)

        val qCur = deviceQuaternion(aircraft(headingDeg, expectedPitch, expectedRoll), mountDeviceToBody)
        val result = AttitudeMath.pitchRoll(AttitudeMath.aircraftRotation(qCur, mount))

        assertEquals("pitch", expectedPitch, result.pitchDeg, 0.05f)
        assertEquals("roll", expectedRoll, result.rollDeg, 0.05f)
    }

    @Test
    fun levelAfterCalibrationIsZero() {
        val cradle = (Quaternion.aboutX(-35f) * Quaternion.aboutY(4f)).normalized()
        assertPitchRoll(0f, 0f, cradle, 0f, headingDeg = 123f)
    }

    @Test
    fun pitchUp10() = assertPitchRoll(10f, 0f, Quaternion.IDENTITY, 0f, headingDeg = 0f)

    @Test
    fun pitchDown10() = assertPitchRoll(-10f, 0f, Quaternion.IDENTITY, 0f, headingDeg = 45f)

    @Test
    fun rollRight30() = assertPitchRoll(0f, 30f, Quaternion.IDENTITY, 0f, headingDeg = 200f)

    @Test
    fun rollLeft30() = assertPitchRoll(0f, -30f, Quaternion.IDENTITY, 0f, headingDeg = 310f)

    @Test
    fun combinedPitchAndRoll() = assertPitchRoll(12f, 25f, Quaternion.IDENTITY, 0f, headingDeg = 77f)

    @Test
    fun combinedWithTiltedCradleMount() {
        // Phone in a cradle pitched back 35 degrees: SET LEVEL must absorb it.
        val cradle = Quaternion.aboutX(-35f)
        assertPitchRoll(12f, -18f, cradle, 0f, headingDeg = 290f)
    }

    @Test
    fun landscapeMountWithYawOffset() {
        // Device rotated -90 about its Z (top toward right wing) and tilted:
        // the LEFT_FORWARD (+90) yaw setting restores correct pitch/roll.
        val landscape = (Quaternion.aboutZ(-90f) * Quaternion.aboutX(20f)).normalized()
        assertPitchRoll(8f, 15f, landscape, 90f, headingDeg = 10f)
    }

    @Test
    fun wraparoundHeadingsDoNotBreakAttitude() {
        assertPitchRoll(5f, -5f, Quaternion.IDENTITY, 0f, headingDeg = 359.5f, calibrationHeadingDeg = 0.5f)
    }

    @Test
    fun headingExtraction() {
        val q = aircraft(45f, 0f, 0f)
        val heading = AttitudeMath.headingDeg(q)
        assertNotNull(heading)
        assertEquals(45f, heading!!, 0.05f)
    }

    @Test
    fun headingExtractionNearWraparound() {
        val q = aircraft(359.5f, 3f, -2f)
        assertEquals(359.5f, AttitudeMath.headingDeg(q)!!, 0.1f)
    }

    @Test
    fun headingSurvivesTiltedMountAfterCalibration() {
        val cradle = Quaternion.aboutX(-40f)
        val qRef = deviceQuaternion(aircraft(200f, 0f, 0f), cradle)
        val mount = AttitudeMath.mountFromReference(qRef, 0f)
        val qCur = deviceQuaternion(aircraft(87f, 5f, 0f), cradle)
        assertEquals(87f, AttitudeMath.headingDeg(AttitudeMath.aircraftRotation(qCur, mount))!!, 0.2f)
    }
}
