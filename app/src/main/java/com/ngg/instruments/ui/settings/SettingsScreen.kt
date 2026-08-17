package com.ngg.instruments.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ngg.instruments.calibration.AppSettings
import com.ngg.instruments.calibration.MountOrientation
import com.ngg.instruments.flight.DataQuality
import com.ngg.instruments.ui.common.SafetyFooter
import com.ngg.instruments.ui.common.ScreenScaffold
import com.ngg.instruments.ui.common.SectionCard
import com.ngg.instruments.ui.theme.Palette

/**
 * Settings, grouped by task with progressive disclosure:
 * in-flight adjustments (SET LEVEL, QNH) first, one-time installation choices
 * (mounting orientation, heading reference) second, destructive maintenance
 * last, and the safety statement as a persistent footer.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    declinationAvailable: Boolean,
    onSetLevel: () -> Unit,
    onClearCalibration: () -> Unit,
    onMountSelected: (MountOrientation) -> Unit,
    onUseTrueHeading: (Boolean) -> Unit,
    onEditQnh: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val calibrated = settings.attitudeCalibration != null

    ScreenScaffold(title = "Settings", onBack = onBack) {

        // ---------------- Flight: adjusted before or during a flight ------
        SectionCard(
            title = "FLIGHT",
            statusColor = Palette.qualityColor(if (calibrated) DataQuality.GOOD else DataQuality.DEGRADED),
            statusLabel = if (calibrated) "CALIBRATED" else "NOT CALIBRATED",
        ) {
            Button(
                onClick = onSetLevel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("SET LEVEL", style = MaterialTheme.typography.labelLarge)
            }
            HintLine("Press with the aircraft level and the device rigidly mounted.")
            WhyThisMatters(
                "All pitch and roll are measured relative to this reference. Without it, the horizon " +
                    "shows the raw device orientation, including the mounting tilt. Recalibrate whenever " +
                    "the mount moves.",
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("QNH ${formatQnh(settings.qnhHpa)} hPa", style = MaterialTheme.typography.titleMedium)
                    HintLine("Local altimeter setting; 1013.25 shows pressure altitude.")
                }
                OutlinedButton(onClick = onEditQnh, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("ADJUST", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // ---------------- Installation: set once when mounting ------------
        SectionCard(
            title = "INSTALLATION",
            statusColor = Palette.qualityColor(if (declinationAvailable) DataQuality.GOOD else DataQuality.DEGRADED),
            statusLabel = if (declinationAvailable) "DECLINATION OK" else "NO DECLINATION FIX",
        ) {
            Text("Mounting orientation", style = MaterialTheme.typography.titleSmall)
            HintLine("Which edge of the device points toward the nose.")
            Spacer(Modifier.size(4.dp))
            MountOrientation.entries.forEach { mount ->
                val selected = settings.mountOrientation == mount
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onMountSelected(mount) },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    MountGlyph(
                        mount = mount,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        mount.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = settings.useTrueHeading,
                        onValueChange = onUseTrueHeading,
                        role = Role.Switch,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (settings.useTrueHeading) "TRUE heading" else "MAGNETIC heading",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    HintLine("True heading needs one GNSS fix for the declination model.")
                }
                Switch(checked = settings.useTrueHeading, onCheckedChange = null)
            }
        }

        // ---------------- Maintenance: destructive, de-emphasised ---------
        SectionCard(title = "CALIBRATION DATA") {
            TextButton(
                onClick = { confirmClear = true },
                enabled = calibrated,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    "Clear attitude calibration…",
                    color = if (calibrated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HintLine("Removes the SET LEVEL reference. Requires confirmation.")
        }

        SafetyFooter()
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear attitude calibration?") },
            text = {
                Text(
                    "The SET LEVEL reference will be deleted. Until you calibrate again, pitch and roll " +
                        "will show the raw device orientation, including any mounting tilt.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearCalibration()
                        confirmClear = false
                    },
                ) {
                    Text("CLEAR", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("CANCEL") }
            },
        )
    }
}

private fun formatQnh(v: Float): String =
    if (v % 1f == 0f) "%.0f".format(v) else "%.2f".format(v)

/** One line of guidance next to a control. */
@Composable
private fun HintLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Longer explanations live behind an expandable affordance. */
@Composable
private fun WhyThisMatters(text: String) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(
            if (expanded) "Hide details" else "Why this matters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    if (expanded) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Recognition over recall: a phone silhouette with the aircraft-nose arrow on
 * the edge the option names, so orientation is understood at a glance.
 */
@Composable
private fun MountGlyph(mount: MountOrientation, tint: Color) {
    Canvas(Modifier.size(40.dp).padding(2.dp)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.055f)

        // Phone body, always drawn upright, with a small speaker notch on top.
        val phoneW = s * 0.36f
        val phoneH = s * 0.58f
        val topLeft = Offset(center.x - phoneW / 2f, center.y - phoneH / 2f)
        drawRoundRect(
            color = tint,
            topLeft = topLeft,
            size = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(s * 0.07f),
            style = stroke,
        )
        drawLine(
            color = tint,
            start = Offset(center.x - phoneW * 0.18f, topLeft.y + phoneH * 0.12f),
            end = Offset(center.x + phoneW * 0.18f, topLeft.y + phoneH * 0.12f),
            strokeWidth = s * 0.05f,
        )

        // Nose arrow at the edge that faces the aircraft nose.
        val arrowBase = s * 0.11f
        val arrowLen = s * 0.16f
        val gap = s * 0.06f
        val arrow = Path()
        when (mount) {
            MountOrientation.TOP_FORWARD -> {
                val y = topLeft.y - gap
                arrow.moveTo(center.x, y - arrowLen)
                arrow.lineTo(center.x - arrowBase, y)
                arrow.lineTo(center.x + arrowBase, y)
            }
            MountOrientation.BOTTOM_FORWARD -> {
                val y = topLeft.y + phoneH + gap
                arrow.moveTo(center.x, y + arrowLen)
                arrow.lineTo(center.x - arrowBase, y)
                arrow.lineTo(center.x + arrowBase, y)
            }
            MountOrientation.RIGHT_FORWARD -> {
                val x = topLeft.x + phoneW + gap
                arrow.moveTo(x + arrowLen, center.y)
                arrow.lineTo(x, center.y - arrowBase)
                arrow.lineTo(x, center.y + arrowBase)
            }
            MountOrientation.LEFT_FORWARD -> {
                val x = topLeft.x - gap
                arrow.moveTo(x - arrowLen, center.y)
                arrow.lineTo(x, center.y - arrowBase)
                arrow.lineTo(x, center.y + arrowBase)
            }
        }
        arrow.close()
        drawPath(arrow, tint)
    }
}
