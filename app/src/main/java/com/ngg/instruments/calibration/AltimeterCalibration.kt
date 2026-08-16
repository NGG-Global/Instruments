package com.ngg.instruments.calibration

import com.ngg.instruments.math.BarometricFormula

/**
 * Altimeter (QNH) calibration rules. The default is the ISA standard pressure;
 * the UI makes clear that indicated altitude needs the local QNH.
 */
object AltimeterCalibration {
    const val DEFAULT_QNH_HPA = BarometricFormula.STANDARD_PRESSURE_HPA
    const val STEP_HPA = 1f

    fun coerce(qnhHpa: Float): Float =
        qnhHpa.coerceIn(BarometricFormula.MIN_QNH_HPA, BarometricFormula.MAX_QNH_HPA)

    fun isValid(qnhHpa: Float): Boolean = BarometricFormula.isValidQnh(qnhHpa)
}
