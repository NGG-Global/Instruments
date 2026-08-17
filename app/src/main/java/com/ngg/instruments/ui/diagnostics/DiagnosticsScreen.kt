package com.ngg.instruments.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngg.instruments.flight.AltitudeSource
import com.ngg.instruments.flight.DataQuality
import com.ngg.instruments.flight.FlightState
import com.ngg.instruments.sensor.SensorCapabilities
import com.ngg.instruments.ui.common.SafetyFooter
import com.ngg.instruments.ui.common.ScreenScaffold
import com.ngg.instruments.ui.common.SectionCard
import com.ngg.instruments.ui.common.StatusBadge
import com.ngg.instruments.ui.theme.Palette
import java.io.File

/**
 * Diagnostics: per-channel quality, hardware inventory with consequences of
 * missing sensors, GNSS accuracy detail, recording/replay controls. Chrome
 * (scaffold, cards, status language) shared with settings and onboarding.
 */
@Composable
fun DiagnosticsScreen(
    flight: FlightState,
    capabilities: SensorCapabilities,
    recordings: List<File>,
    isRecording: Boolean,
    replayingFile: File?,
    hasLocationPermission: Boolean,
    onToggleRecording: () -> Unit,
    onReplay: (File) -> Unit,
    onStopReplay: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = "Diagnostics", onBack = onBack) {

        SectionCard(title = "DATA QUALITY") {
            QualityRow("ATTITUDE", flight.attitudeQuality, if (flight.attitudeCalibrated) "calibrated" else "uncalibrated")
            QualityRow("HEADING", flight.headingQuality, null)
            QualityRow("GNSS", flight.gnssQuality, if (!hasLocationPermission) "location permission not granted" else null)
            QualityRow("ALTITUDE", flight.altitudeQuality, flight.altitudeSource.label())
            QualityRow("VSI", flight.verticalSpeedQuality, flight.verticalSpeedSource.label())
            QualityRow("GROUND SPEED", flight.speedQuality, null)
        }

        // Live numbers behind each instrument, so a panel that looks "stuck"
        // can be diagnosed on the aircraft: a dash means the estimator has no
        // valid data, not that the needle is broken.
        SectionCard(title = "LIVE VALUES") {
            DetailRow("Pitch / roll", if (flight.pitchDeg != null && flight.rollDeg != null) {
                "%+.1f° / %+.1f°".format(flight.pitchDeg, flight.rollDeg)
            } else "—")
            DetailRow("Magnetic heading", flight.magneticHeadingDeg?.let { "%.0f°".format(it) } ?: "—")
            DetailRow("True heading", flight.trueHeadingDeg?.let { "%.0f°".format(it) } ?: "—")
            DetailRow("GNSS track", flight.trackDeg?.let { "%.0f°".format(it) } ?: "— (needs motion)")
            DetailRow("Altitude", flight.altitudeFt?.let { "%.0f ft".format(it) } ?: "—")
            DetailRow("Vertical speed", flight.verticalSpeedFpm?.let { "%+.0f fpm".format(it) } ?: "—")
            DetailRow("Ground speed", flight.groundSpeedKt?.let { "%.1f kt".format(it) } ?: "—")
        }

        SectionCard(title = "GNSS DETAIL") {
            DetailRow("Satellites used", flight.satellitesUsed?.toString() ?: "—")
            DetailRow("Horizontal accuracy", flight.horizontalAccuracyM?.let { "±%.1f m".format(it) } ?: "—")
            DetailRow("Vertical accuracy", flight.verticalAccuracyM?.let { "±%.1f m".format(it) } ?: "—")
            DetailRow("Speed accuracy", flight.speedAccuracyMps?.let { "±%.1f m/s".format(it) } ?: "—")
            DetailRow("Bearing accuracy", flight.bearingAccuracyDeg?.let { "±%.1f°".format(it) } ?: "—")
        }

        SectionCard(title = "HARDWARE") {
            HardwareRow("Accelerometer", capabilities.hasAccelerometer, null)
            HardwareRow("Gyroscope", capabilities.hasGyroscope, "Attitude will rely on magnetic fusion and be less stable in turns.")
            HardwareRow("Magnetometer", capabilities.hasMagnetometer, "Magnetic heading unavailable; only GNSS track can be shown.")
            HardwareRow("Rotation vector", capabilities.hasRotationVector, "Heading unavailable.")
            HardwareRow("Game rotation vector", capabilities.hasGameRotationVector, "Attitude falls back to the magnetic rotation vector (degraded).")
            HardwareRow("Pressure sensor", capabilities.hasPressure, "Altitude and VSI will use GNSS with reduced responsiveness.")
            HardwareRow("GNSS", capabilities.hasGnss, "Ground speed, track and GNSS altitude unavailable.")
        }

        SectionCard(
            title = "RECORDING / REPLAY",
            statusColor = when {
                isRecording -> MaterialTheme.colorScheme.error
                replayingFile != null -> Color(0xFF4E9AD1)
                else -> null
            },
            statusLabel = when {
                isRecording -> "RECORDING"
                replayingFile != null -> "REPLAYING"
                else -> null
            },
        ) {
            Text(
                "Records raw sensor and GNSS samples to local app storage for deterministic replay through the same estimation pipeline. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = onToggleRecording,
                enabled = replayingFile == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = if (isRecording) "Stop recording" else "Start recording" },
            ) {
                Text(if (isRecording) "STOP RECORDING" else "START RECORDING", style = MaterialTheme.typography.labelLarge)
            }

            if (replayingFile != null) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Replaying: ${replayingFile.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4E9AD1),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = onStopReplay,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("STOP", style = MaterialTheme.typography.labelLarge) }
                }
            }

            recordings.forEach { file ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "%.1f kB".format(file.length() / 1024f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { onReplay(file) },
                        enabled = replayingFile == null && !isRecording,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Replay recording ${file.name}" },
                    ) { Text("REPLAY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
                }
            }
            if (recordings.isEmpty()) {
                Text(
                    "No recordings yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SafetyFooter()
    }
}

private fun AltitudeSource.label(): String = when (this) {
    AltitudeSource.BAROMETRIC -> "BARO"
    AltitudeSource.GNSS -> "GNSS"
    AltitudeSource.NONE -> "no source"
}

@Composable
private fun QualityRow(name: String, quality: DataQuality, note: String?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (note != null) {
            Text(
                "$note · ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusBadge(Palette.qualityColor(quality), quality.name)
    }
}

@Composable
private fun DetailRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HardwareRow(name: String, present: Boolean, consequenceWhenMissing: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            StatusBadge(
                Palette.qualityColor(if (present) DataQuality.GOOD else DataQuality.POOR),
                if (present) "PRESENT" else "MISSING",
            )
        }
        if (!present && consequenceWhenMissing != null) {
            Text(
                consequenceWhenMissing,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.qualityColor(DataQuality.DEGRADED),
            )
        }
    }
}
