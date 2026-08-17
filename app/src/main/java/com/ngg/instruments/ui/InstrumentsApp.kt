package com.ngg.instruments.ui

import android.Manifest
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    // Designer splash: plays its intro + one loading loop minimum, resolves
    // once persisted settings are loaded, then fades into the app.
    com.ngg.instruments.ui.splash.SplashHost(ready = settings != null) {
        if (settings != null) {
            MainContent(
                container = container,
                settings = settings,
                mode = mode,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun MainContent(
    container: AppContainer,
    settings: AppSettings,
    mode: com.ngg.instruments.EngineMode,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var screen by remember(settings.onboardingComplete) {
        mutableStateOf(if (settings.onboardingComplete) Screen.PANEL else Screen.ONBOARDING)
    }
    var showQnhDialog by remember { mutableStateOf(false) }

    val flight = container.engine.state.collectAsStateWithLifecycle()
    val headingSource = remember(settings.headingSource) { mutableStateOf(settings.headingSource) }

    fun setLevel() {
        val reference = container.engine.latestDeviceOrientation ?: return
        scope.launch { container.calibrationRepository.setAttitudeReference(reference) }
    }

    when (screen) {
        Screen.ONBOARDING -> OnboardingHost(
            container = container,
            settings = settings,
            hasLocationPermission = hasLocationPermission,
            onRequestPermission = onRequestPermission,
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
                if (!hasLocationPermission) onRequestPermission()
            }
            InstrumentPanelScreen(
                flight = flight,
                headingSource = headingSource,
                recording = mode.recording,
                replaying = mode.isReplay,
                onOpenSettings = { screen = Screen.SETTINGS },
                onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                onAltimeterTapped = { showQnhDialog = true },
            )
        }

        Screen.SETTINGS -> {
            // System back mirrors the on-screen back control.
            BackHandler { screen = Screen.PANEL }
            com.ngg.instruments.ui.settings.SettingsScreen(
                settings = settings,
                declinationAvailable = flight.value.trueHeadingDeg != null,
                onSetLevel = ::setLevel,
                onClearCalibration = { scope.launch { container.calibrationRepository.clearAttitudeReference() } },
                onMountSelected = { mount: MountOrientation ->
                    scope.launch { container.calibrationRepository.setMountOrientation(mount) }
                },
                onHeadingSourceSelected = { source ->
                    scope.launch { container.calibrationRepository.setHeadingSource(source) }
                },
                onEditQnh = { showQnhDialog = true },
                onBack = { screen = Screen.PANEL },
            )
        }

        Screen.DIAGNOSTICS -> {
            BackHandler { screen = Screen.PANEL }
            com.ngg.instruments.ui.diagnostics.DiagnosticsScreen(
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

/**
 * QNH entry: decimal keyboard, pre-selected value, ±1 hPa steppers for the
 * common small correction, inline validation naming the valid range, and a
 * one-tap reset to standard pressure. AltimeterCalibration.isValid stays the
 * single source of truth for the range.
 */
@Composable
private fun QnhDialog(
    currentQnh: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    fun format(v: Float): String = if (v % 1f == 0f) "%.0f".format(v) else "%.2f".format(v)

    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var field by remember {
        val text = format(currentQnh)
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = text,
                selection = androidx.compose.ui.text.TextRange(0, text.length),
            ),
        )
    }
    val parsed = field.text.toFloatOrNull()
    val valid = parsed != null && AltimeterCalibration.isValid(parsed)

    fun setValue(v: Float) {
        val coerced = AltimeterCalibration.coerce(v)
        val text = format(coerced)
        field = androidx.compose.ui.text.input.TextFieldValue(
            text = text,
            selection = androidx.compose.ui.text.TextRange(text.length),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Altimeter setting — QNH") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { setValue((parsed ?: currentQnh) - AltimeterCalibration.STEP_HPA) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Decrease QNH by 1 hectopascal" },
                    ) { Text("−1") }
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        isError = !valid,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                        supportingText = {
                            Text(
                                if (valid) "hPa" else "Enter a value between 850 and 1100 hPa",
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "QNH in hectopascals" },
                    )
                    TextButton(
                        onClick = { setValue((parsed ?: currentQnh) + AltimeterCalibration.STEP_HPA) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Increase QNH by 1 hectopascal" },
                    ) { Text("+1") }
                }
                TextButton(
                    onClick = { setValue(AltimeterCalibration.DEFAULT_QNH_HPA) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Reset to standard 1013.25 hPa")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = valid,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("SET") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("CANCEL") }
        },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
