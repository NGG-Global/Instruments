package com.ngg.instruments.calibration

import com.ngg.instruments.math.AttitudeMath
import com.ngg.instruments.math.Quaternion

/**
 * Mounting orientation of the device in the airframe. The tilt part of the
 * mount is fully captured by SET LEVEL; the yaw offset (which edge of the
 * device faces the nose) cannot be observed from a level reference and is an
 * explicit user setting.
 */
enum class MountOrientation(val yawOffsetDeg: Float, val label: String) {
    TOP_FORWARD(0f, "Device top toward nose"),
    RIGHT_FORWARD(-90f, "Device right edge toward nose"),
    LEFT_FORWARD(90f, "Device left edge toward nose"),
    BOTTOM_FORWARD(180f, "Device bottom toward nose"),
}

/**
 * Immutable attitude calibration: the raw device orientation captured at
 * SET LEVEL plus the declared mounting orientation.
 */
data class AttitudeCalibration(
    val reference: Quaternion,
    val mountOrientation: MountOrientation,
) {
    /** Mount-correction quaternion consumed by the estimators. */
    fun mount(): Quaternion =
        AttitudeMath.mountFromReference(reference, mountOrientation.yawOffsetDeg)
}
