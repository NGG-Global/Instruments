package com.ngg.instruments.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngg.instruments.flight.DataQuality
import com.ngg.instruments.sensor.SensorCapabilities
import com.ngg.instruments.ui.common.SafetyFooter
import com.ngg.instruments.ui.common.ScreenScaffold
import com.ngg.instruments.ui.common.SectionCard
import com.ngg.instruments.ui.common.StatusBadge
import com.ngg.instruments.ui.theme.Palette

/**
 * First-launch flow: mounting guidance, location permission, sensor summary,
 * SET LEVEL, QNH, safety statement. Chrome shared with settings/diagnostics.
 * Not repeated on later launches; recalibration lives in settings.
 */
@Composable
fun OnboardingScreen(
    capabilities: SensorCapabilities,
    hasLocationPermission: Boolean,
    attitudeCalibrated: Boolean,
    onRequestLocationPermission: () -> Unit,
    onSetLevel: () -> Unit,
    onEditQnh: () -> Unit,
    onDone: () -> Unit,
) {
    ScreenScaffold(title = "Flight Instruments", onBack = null) {

        SafetyFooter()

        SectionCard(title = "1 · MOUNT THE DEVICE") {
            Text(
                "Fix the device rigidly in its normal operating position. Attitude readings assume the device does not move relative to the airframe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "2 · LOCATION PERMISSION",
            statusColor = Palette.qualityColor(if (hasLocationPermission) DataQuality.GOOD else DataQuality.DEGRADED),
            statusLabel = if (hasLocationPermission) "GRANTED" else "NOT GRANTED",
        ) {
            Text(
                "Precise location is used only for GNSS ground speed, track and altitude — entirely on this device, offline. This app has no internet permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasLocationPermission) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onRequestLocationPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Grant precise location permission" },
                ) { Text("GRANT PRECISE LOCATION", style = MaterialTheme.typography.labelLarge) }
            }
        }

        SectionCard(title = "3 · DETECTED SENSORS") {
            SensorLine("Attitude (rotation vector)", capabilities.hasAnyAttitudeSource)
            SensorLine("Compass heading", capabilities.hasAnyHeadingSource)
            SensorLine("Barometer", capabilities.hasPressure)
            SensorLine("GNSS", capabilities.hasGnss)
            if (!capabilities.hasPressure) {
                Text(
                    "Pressure sensor unavailable — altitude and vertical speed will use GNSS with reduced responsiveness.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.qualityColor(DataQuality.DEGRADED),
                )
            }
        }

        SectionCard(
            title = "4 · SET LEVEL",
            statusColor = Palette.qualityColor(if (attitudeCalibrated) DataQuality.GOOD else DataQuality.DEGRADED),
            statusLabel = if (attitudeCalibrated) "CALIBRATED" else "NOT CALIBRATED",
        ) {
            Text(
                "With the device mounted and the aircraft level, capture the attitude reference. You can recalibrate any time from settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSetLevel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Capture level attitude reference" },
            ) { Text("SET LEVEL", style = MaterialTheme.typography.labelLarge) }
        }

        SectionCard(title = "5 · QNH") {
            Text(
                "Enter the local altimeter setting for accurate indicated altitude. Defaults to standard pressure 1013.25 hPa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onEditQnh,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Enter QNH altimeter setting" },
            ) { Text("ENTER QNH", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
        }

        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .heightIn(min = 52.dp)
                .semantics { contentDescription = "Finish setup and enter the instrument panel" },
        ) { Text("ENTER INSTRUMENT PANEL", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun SensorLine(name: String, present: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        StatusBadge(
            Palette.qualityColor(if (present) DataQuality.GOOD else DataQuality.POOR),
            if (present) "OK" else "MISSING",
        )
    }
}
