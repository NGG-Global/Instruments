package com.ngg.instruments

import android.content.Context
import com.ngg.instruments.calibration.CalibrationRepository
import com.ngg.instruments.flight.FlightDataEngine
import com.ngg.instruments.gnss.AndroidDeclinationProvider
import com.ngg.instruments.gnss.GnssRepository
import com.ngg.instruments.recording.SensorRecorder
import com.ngg.instruments.recording.SensorReplay
import com.ngg.instruments.sensor.SensorCapabilities
import com.ngg.instruments.sensor.SensorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

/** How the engine is currently fed. */
data class EngineMode(
    val replayFile: File? = null,
    val recording: Boolean = false,
) {
    val isReplay: Boolean get() = replayFile != null
}

/**
 * Composition root. Owns the repositories and the single engine instance; the
 * UI runs the engine within its lifecycle scope so sensors and GNSS stop when
 * the app is not visible.
 */
class AppContainer(private val context: Context) {

    val capabilities: SensorCapabilities = SensorCapabilities.detect(context)
    val calibrationRepository = CalibrationRepository(context)
    val gnssRepository = GnssRepository(context)
    val recorder = SensorRecorder(context)

    private val attitudeUsesFallback = !capabilities.hasGameRotationVector && capabilities.hasRotationVector

    val engine = FlightDataEngine(
        hasBarometer = capabilities.hasPressure,
        attitudeUsesFallback = attitudeUsesFallback,
        declinationProvider = AndroidDeclinationProvider(),
    )

    private val _mode = MutableStateFlow(EngineMode())
    val mode: StateFlow<EngineMode> = _mode.asStateFlow()

    private val _activeRecordingFile = MutableStateFlow<File?>(null)
    val activeRecordingFile: StateFlow<File?> = _activeRecordingFile.asStateFlow()

    fun startReplay(file: File) {
        _mode.value = EngineMode(replayFile = file)
    }

    fun stopReplay() {
        _mode.value = _mode.value.copy(replayFile = null)
    }

    fun setRecording(enabled: Boolean) {
        _mode.value = _mode.value.copy(recording = enabled)
        if (!enabled) _activeRecordingFile.value = null
    }

    /**
     * Runs the engine until cancellation, wiring the sources dictated by
     * [modeSnapshot]. Called from a lifecycle-aware coroutine.
     */
    suspend fun runEngine(modeSnapshot: EngineMode) {
        engine.resetEstimators()
        val base = if (modeSnapshot.replayFile != null) {
            SensorReplay(modeSnapshot.replayFile)
        } else {
            SensorRepository(context, capabilities, gnssRepository)
        }
        val sources = if (modeSnapshot.recording && !modeSnapshot.isReplay) {
            val file = recorder.newRecordingFile()
            _activeRecordingFile.value = file
            recorder.recordingSources(base, file)
        } else {
            base
        }
        val settingsFlow = calibrationRepository.settings.map { it.toEngineSettings() }
        engine.run(sources, settingsFlow)
    }
}
