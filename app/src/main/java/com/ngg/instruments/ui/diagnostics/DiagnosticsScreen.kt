package com.ngg.instruments.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngg.instruments.flight.AltitudeSource
import com.ngg.instruments.flight.DataQuality
import com.ngg.instruments.flight.FlightState
import com.ngg.instruments.sensor.SensorCapabilities
import com.ngg.instruments.ui.theme.Palette
import java.io.File

/**
 * Diagnostics: per-channel quality, hardware inventory with consequences of
 * missing sensors, GNSS accuracy detail, recording/replay controls.
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.panelBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< BACK", color = Color(0x99EEEFEB)) }
            Spacer(Modifier.width(8.dp))
            Text("DIAGNOSTICS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        SectionTitle("DATA QUALITY")
        QualityRow("ATTITUDE", flight.attitudeQuality, if (flight.attitudeCalibrated) "calibrated" else "uncalibrated")
        QualityRow("HEADING", flight.headingQuality, null)
        QualityRow("GNSS", flight.gnssQuality, if (!hasLocationPermission) "location permission not granted" else null)
        QualityRow("ALTITUDE", flight.altitudeQuality, flight.altitudeSource.label())
        QualityRow("VSI", flight.verticalSpeedQuality, flight.verticalSpeedSource.label())
        QualityRow("GROUND SPEED", flight.speedQuality, null)

        SectionTitle("GNSS DETAIL")
        DetailRow("Horizontal accuracy", flight.horizontalAccuracyM?.let { "±%.1f m".format(it) } ?: "—")
        DetailRow("Vertical accuracy", flight.verticalAccuracyM?.let { "±%.1f m".format(it) } ?: "—")
        DetailRow("Speed accuracy", flight.speedAccuracyMps?.let { "±%.1f m/s".format(it) } ?: "—")
        DetailRow("Bearing accuracy", flight.bearingAccuracyDeg?.let { "±%.1f°".format(it) } ?: "—")

        SectionTitle("HARDWARE")
        HardwareRow("Accelerometer", capabilities.hasAccelerometer, null)
        HardwareRow("Gyroscope", capabilities.hasGyroscope, "Attitude will rely on magnetic fusion and be less stable in turns.")
        HardwareRow("Magnetometer", capabilities.hasMagnetometer, "Magnetic heading unavailable; only GNSS track can be shown.")
        HardwareRow("Rotation vector", capabilities.hasRotationVector, "Heading unavailable.")
        HardwareRow("Game rotation vector", capabilities.hasGameRotationVector, "Attitude falls back to the magnetic rotation vector (degraded).")
        HardwareRow("Pressure sensor", capabilities.hasPressure, "Altitude and VSI will use GNSS with reduced responsiveness.")
        HardwareRow("GNSS", capabilities.hasGnss, "Ground speed, track and GNSS altitude unavailable.")

        SectionTitle("RECORDING / REPLAY")
        Text(
            "Records raw sensor and GNSS samples to local app storage for deterministic replay through the same estimation pipeline. Nothing is uploaded.",
            color = Color(0x77EEEFEB), fontSize = 11.sp,
        )
        Button(
            onClick = onToggleRecording,
            enabled = replayingFile == null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color(0xFFD65045) else Color(0xFF2B3033),
                contentColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isRecording) "STOP RECORDING" else "START RECORDING", letterSpacing = 1.5.sp)
        }

        if (replayingFile != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Replaying: ${replayingFile.name}", color = Color(0xFF4E9AD1), fontSize = 12.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onStopReplay) { Text("STOP", fontSize = 12.sp, color = Color.White) }
            }
        }

        recordings.forEach { file ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(file.name, color = Color(0xCCEEEFEB), fontSize = 12.sp)
                    Text("%.1f kB".format(file.length() / 1024f), color = Color(0x66EEEFEB), fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = { onReplay(file) },
                    enabled = replayingFile == null && !isRecording,
                ) { Text("REPLAY", fontSize = 12.sp, color = Palette.inkCream) }
            }
        }
        if (recordings.isEmpty()) {
            Text("No recordings yet.", color = Color(0x66EEEFEB), fontSize = 12.sp)
        }

        HorizontalDivider(color = Color(0x22FFFFFF))
        Text(
            "For supplemental / experimental use only. Not a certified flight instrument.",
            color = Color(0x77E0A83C), fontSize = 11.sp,
        )
    }
}

private fun AltitudeSource.label(): String = when (this) {
    AltitudeSource.BAROMETRIC -> "BARO"
    AltitudeSource.GNSS -> "GNSS"
    AltitudeSource.NONE -> "no source"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Palette.inkCream, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}

@Composable
private fun QualityRow(name: String, quality: DataQuality, note: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(8.dp)
                .background(Palette.qualityColor(quality), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(name, color = Color(0xCCEEEFEB), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            buildString {
                if (note != null) append("$note · ")
                append(quality.name)
            },
            color = Palette.qualityColor(quality),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, color = Color(0x99EEEFEB), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color(0xCCEEEFEB), fontSize = 12.sp)
    }
}

@Composable
private fun HardwareRow(name: String, present: Boolean, consequenceWhenMissing: String?) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color(0xCCEEEFEB), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                if (present) "PRESENT" else "MISSING",
                color = if (present) Color(0xFF4FBF6B) else Color(0xFFD65045),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!present && consequenceWhenMissing != null) {
            Text(consequenceWhenMissing, color = Color(0xFFE0A83C), fontSize = 11.sp)
        }
    }
}
