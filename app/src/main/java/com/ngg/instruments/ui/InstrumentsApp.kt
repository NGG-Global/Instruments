package com.ngg.instruments.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ngg.instruments.AppContainer
import com.ngg.instruments.calibration.AltimeterCalibration
import com.ngg.instruments.calibration.AppSettings
import com.ngg.instruments.calibration.MountOrientation
import kotlinx.coroutines.launch

private enum class Screen { PANEL, SETTINGS, DIAGNOSTICS, ONBOARDING }

/**
 * Navigation shell. Also owns the engine lifecycle: the engine (and therefore
 * all sensor and GNSS registrations) runs only while the app is STARTED, and
 * restarts when the mode (live/replay/recording) or permission changes.
 */
@Composable
fun InstrumentsApp(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val settingsState = container.calibrationRepository.settings
        .collectAsState(initial = null)
    val settings = settingsState.value

    var hasLocationPermission by remember { mutableStateOf(container.gnssRepository.hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    val mode by container.mode.collectAsState()

    // Engine lifecycle: rebuild sources when mode or permission changes.
    LaunchedEffect(mode, hasLocationPermission) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.runEngine(mode)
        }
    }

    if (settings == null) return // settings still loading from DataStore

    var screen by remember(settings.onboardingComplete) {
        mutableStateOf(if (settings.onboardingComplete) Screen.PANEL else Screen.ONBOARDING)
    }
    var showQnhDialog by remember { mutableStateOf(false) }

    val flight = container.engine.state.collectAsStateWithLifecycle()
    val useTrueHeading = remember(settings.useTrueHeading) { mutableStateOf(settings.useTrueHeading) }

    fun setLevel() {
        val reference = container.engine.latestDeviceOrientation ?: return
        scope.launch { container.calibrationRepository.setAttitudeReference(reference) }
    }

    when (screen) {
        Screen.ONBOARDING -> OnboardingHost(
            container = container,
            settings = settings,
            hasLocationPermission = hasLocationPermission,
            onRequestPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            onSetLevel = ::setLevel,
            onEditQnh = { showQnhDialog = true },
            onDone = {
                scope.launch { container.calibrationRepository.setOnboardingComplete() }
                screen = Screen.PANEL
            },
        )

        Screen.PANEL -> {
            // Ask for permission on entry if it was never granted.
            LaunchedEffect(Unit) {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            InstrumentPanelScreen(
                flight = flight,
                useTrueHeading = useTrueHeading,
                recording = mode.recording,
                replaying = mode.isReplay,
                onOpenSettings = { screen = Screen.SETTINGS },
                onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                onAltimeterTapped = { showQnhDialog = true },
            )
        }

        Screen.SETTINGS -> com.ngg.instruments.ui.settings.SettingsScreen(
            settings = settings,
            onSetLevel = ::setLevel,
            onClearCalibration = { scope.launch { container.calibrationRepository.clearAttitudeReference() } },
            onMountSelected = { mount: MountOrientation ->
                scope.launch { container.calibrationRepository.setMountOrientation(mount) }
            },
            onUseTrueHeading = { scope.launch { container.calibrationRepository.setUseTrueHeading(it) } },
            onEditQnh = { showQnhDialog = true },
            onBack = { screen = Screen.PANEL },
        )

        Screen.DIAGNOSTICS -> com.ngg.instruments.ui.diagnostics.DiagnosticsScreen(
            flight = flight.value,
            capabilities = container.capabilities,
            recordings = container.recorder.listRecordings(),
            isRecording = mode.recording,
            replayingFile = mode.replayFile,
            hasLocationPermission = hasLocationPermission,
            onToggleRecording = { container.setRecording(!mode.recording) },
            onReplay = { container.startReplay(it) },
            onStopReplay = { container.stopReplay() },
            onBack = { screen = Screen.PANEL },
        )
    }

    if (showQnhDialog) {
        QnhDialog(
            currentQnh = settings.qnhHpa,
            onDismiss = { showQnhDialog = false },
            onConfirm = { qnh ->
                scope.launch { container.calibrationRepository.setQnh(qnh) }
                showQnhDialog = false
            },
        )
    }
}

@Composable
private fun OnboardingHost(
    container: AppContainer,
    settings: AppSettings,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSetLevel: () -> Unit,
    onEditQnh: () -> Unit,
    onDone: () -> Unit,
) {
    com.ngg.instruments.ui.onboarding.OnboardingScreen(
        capabilities = container.capabilities,
        hasLocationPermission = hasLocationPermission,
        attitudeCalibrated = settings.attitudeCalibration != null,
        onRequestLocationPermission = onRequestPermission,
        onSetLevel = onSetLevel,
        onEditQnh = onEditQnh,
        onDone = onDone,
    )
}

@Composable
private fun QnhDialog(
    currentQnh: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(currentQnh.toString()) }
    val parsed = text.toFloatOrNull()
    val valid = parsed != null && AltimeterCalibration.isValid(parsed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QNH (hPa)") },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("850 – 1100 hPa") },
                )
                Text(
                    "Standard pressure is 1013.25 hPa. Use the local altimeter setting for indicated altitude.",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) { Text("SET") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        },
    )
}
