package com.ngg.instruments.calibration

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ngg.instruments.flight.EngineSettings
import com.ngg.instruments.math.Quaternion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "instruments_settings")

/** All persisted user preferences and calibrations. Local storage only. */
data class AppSettings(
    val qnhHpa: Float,
    val useTrueHeading: Boolean,
    val mountOrientation: MountOrientation,
    val attitudeCalibration: AttitudeCalibration?,
    val onboardingComplete: Boolean,
) {
    fun toEngineSettings(): EngineSettings = EngineSettings(
        qnhHpa = qnhHpa,
        mount = attitudeCalibration?.mount() ?: Quaternion.IDENTITY,
        attitudeCalibrated = attitudeCalibration != null,
    )
}

/**
 * DataStore-backed persistence for calibration and settings. No cloud, no
 * network — the store lives in app-private storage.
 */
class CalibrationRepository(private val context: Context) {

    private object Keys {
        val QNH = floatPreferencesKey("qnh_hpa")
        val TRUE_HEADING = booleanPreferencesKey("use_true_heading")
        val MOUNT = stringPreferencesKey("mount_orientation")
        val CAL_W = floatPreferencesKey("cal_w")
        val CAL_X = floatPreferencesKey("cal_x")
        val CAL_Y = floatPreferencesKey("cal_y")
        val CAL_Z = floatPreferencesKey("cal_z")
        val CAL_SET = booleanPreferencesKey("cal_set")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val mount = prefs[Keys.MOUNT]?.let { stored ->
            MountOrientation.entries.firstOrNull { it.name == stored }
        } ?: MountOrientation.TOP_FORWARD

        val calibration = if (prefs[Keys.CAL_SET] == true) {
            val w = prefs[Keys.CAL_W]
            val x = prefs[Keys.CAL_X]
            val y = prefs[Keys.CAL_Y]
            val z = prefs[Keys.CAL_Z]
            if (w != null && x != null && y != null && z != null) {
                AttitudeCalibration(Quaternion(w, x, y, z).normalized(), mount)
            } else null
        } else null

        AppSettings(
            qnhHpa = prefs[Keys.QNH] ?: AltimeterCalibration.DEFAULT_QNH_HPA,
            useTrueHeading = prefs[Keys.TRUE_HEADING] ?: false,
            mountOrientation = mount,
            attitudeCalibration = calibration,
            onboardingComplete = prefs[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun setQnh(qnhHpa: Float) {
        if (!AltimeterCalibration.isValid(qnhHpa)) return
        context.dataStore.edit { it[Keys.QNH] = qnhHpa }
    }

    suspend fun setUseTrueHeading(value: Boolean) {
        context.dataStore.edit { it[Keys.TRUE_HEADING] = value }
    }

    suspend fun setMountOrientation(mount: MountOrientation) {
        context.dataStore.edit { it[Keys.MOUNT] = mount.name }
    }

    /** Captures the SET LEVEL reference orientation. */
    suspend fun setAttitudeReference(reference: Quaternion) {
        context.dataStore.edit {
            it[Keys.CAL_W] = reference.w
            it[Keys.CAL_X] = reference.x
            it[Keys.CAL_Y] = reference.y
            it[Keys.CAL_Z] = reference.z
            it[Keys.CAL_SET] = true
        }
    }

    suspend fun clearAttitudeReference() {
        context.dataStore.edit { it[Keys.CAL_SET] = false }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[Keys.ONBOARDED] = true }
    }
}
