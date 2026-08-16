package com.ngg.instruments.math

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class QuaternionTest {

    @Test
    fun identityRotationLeavesVectorsAlone() {
        val v = Quaternion.IDENTITY.rotate(1f, 2f, 3f)
        assertEquals(1f, v[0], 1e-5f)
        assertEquals(2f, v[1], 1e-5f)
        assertEquals(3f, v[2], 1e-5f)
    }

    @Test
    fun aboutZRotatesXTowardY() {
        // +90 degrees about Z takes X to Y (right-handed).
        val q = Quaternion.aboutZ(90f)
        val v = q.rotate(1f, 0f, 0f)
        assertEquals(0f, v[0], 1e-5f)
        assertEquals(1f, v[1], 1e-5f)
        assertEquals(0f, v[2], 1e-5f)
    }

    @Test
    fun aboutXRotatesYTowardZ() {
        val q = Quaternion.aboutX(90f)
        val v = q.rotate(0f, 1f, 0f)
        assertEquals(0f, v[0], 1e-5f)
        assertEquals(0f, v[1], 1e-5f)
        assertEquals(1f, v[2], 1e-5f)
    }

    @Test
    fun compositionAppliesRightFirst() {
        val first = Quaternion.aboutX(90f)
        val then = Quaternion.aboutZ(90f)
        val v = (then * first).rotate(0f, 1f, 0f) // Y -> Z (by X-rot), Z unaffected by Z-rot
        assertEquals(0f, v[0], 1e-5f)
        assertEquals(0f, v[1], 1e-5f)
        assertEquals(1f, v[2], 1e-5f)
    }

    @Test
    fun inverseUndoesRotation() {
        val q = (Quaternion.aboutZ(37f) * Quaternion.aboutX(21f) * Quaternion.aboutY(-63f)).normalized()
        val r = q * q.inverse()
        assertEquals(1f, kotlin.math.abs(r.w), 1e-5f)
    }

    @Test
    fun fromRotationVectorWithAndWithoutW() {
        // 90 degrees about Z: axis (0,0,1), sin(45)=cos(45)=sqrt(2)/2
        val half = (sqrt(2.0) / 2.0).toFloat()
        val withW = Quaternion.fromRotationVector(floatArrayOf(0f, 0f, half, half))
        val withoutW = Quaternion.fromRotationVector(floatArrayOf(0f, 0f, half))
        val v1 = withW.rotate(1f, 0f, 0f)
        val v2 = withoutW.rotate(1f, 0f, 0f)
        assertEquals(v1[1], v2[1], 1e-4f)
        assertEquals(1f, v1[1], 1e-4f)
    }

    @Test
    fun yawTiltDecompositionRecombines() {
        val q = (Quaternion.aboutZ(140f) * Quaternion.aboutX(30f) * Quaternion.aboutY(-20f)).normalized()
        val yaw = q.yawComponent()
        val tilt = q.tiltComponent()
        val recombined = (yaw * tilt).normalized()
        assertEquals(0f, q.angleToDeg(recombined), 1e-2f)
        // Yaw component must be a pure Z rotation.
        assertEquals(0f, yaw.x, 1e-6f)
        assertEquals(0f, yaw.y, 1e-6f)
    }
}
