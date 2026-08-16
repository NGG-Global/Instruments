package com.ngg.instruments.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngg.instruments.calibration.AppSettings
import com.ngg.instruments.calibration.MountOrientation
import com.ngg.instruments.ui.theme.Palette

/**
 * Settings: SET LEVEL calibration, mounting orientation, QNH, heading
 * reference, safety statement.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSetLevel: () -> Unit,
    onClearCalibration: () -> Unit,
    onMountSelected: (MountOrientation) -> Unit,
    onUseTrueHeading: (Boolean) -> Unit,
    onEditQnh: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.panelBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< BACK", color = Color(0x99EEEFEB)) }
            Spacer(Modifier.width(8.dp))
            Text("SETTINGS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        SectionTitle("ATTITUDE CALIBRATION")
        Text(
            "Mount the device rigidly, hold the aircraft level, then press SET LEVEL. " +
                "All pitch and roll values are measured relative to this reference.",
            color = Color(0x99EEEFEB), fontSize = 13.sp,
        )
        Button(
            onClick = onSetLevel,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.referenceOrange, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("SET LEVEL", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Text(
            if (settings.attitudeCalibration != null) "Status: calibrated" else "Status: not calibrated — attitude is shown relative to the raw device orientation",
            color = if (settings.attitudeCalibration != null) Color(0xFF4FBF6B) else Color(0xFFE0A83C),
            fontSize = 12.sp,
        )
        OutlinedButton(onClick = onClearCalibration, modifier = Modifier.fillMaxWidth()) {
            Text("CLEAR CALIBRATION", color = Color(0x99EEEFEB), fontSize = 12.sp)
        }

        SectionTitle("MOUNTING ORIENTATION")
        Text(
            "Which edge of the device points toward the aircraft nose. Needed for heading and for correct pitch/roll separation.",
            color = Color(0x99EEEFEB), fontSize = 13.sp,
        )
        MountOrientation.entries.forEach { mount ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(
                    selected = settings.mountOrientation == mount,
                    onClick = { onMountSelected(mount) },
                )
                Text(mount.label, color = Color(0xCCEEEFEB), fontSize = 13.sp)
            }
        }

        SectionTitle("HEADING REFERENCE")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (settings.useTrueHeading) "TRUE heading (magnetic + local declination)" else "MAGNETIC heading",
                color = Color(0xCCEEEFEB), fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            Switch(checked = settings.useTrueHeading, onCheckedChange = onUseTrueHeading)
        }
        Text(
            "True heading requires at least one GNSS fix for the declination model.",
            color = Color(0x77EEEFEB), fontSize = 11.sp,
        )

        SectionTitle("ALTIMETER")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("QNH: ${settings.qnhHpa} hPa", color = Color(0xCCEEEFEB), fontSize = 13.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onEditQnh) { Text("ADJUST", fontSize = 12.sp, color = Palette.inkCream) }
        }
        Text(
            "Accurate indicated altitude requires the current local QNH. With the default 1013.25 hPa the altimeter shows pressure altitude.",
            color = Color(0x77EEEFEB), fontSize = 11.sp,
        )

        HorizontalDivider(color = Color(0x22FFFFFF))
        Text(
            "For supplemental / experimental use only. Not a certified flight instrument and not a substitute for approved aircraft instrumentation.",
            color = Color(0xFFE0A83C), fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Palette.inkCream, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}
