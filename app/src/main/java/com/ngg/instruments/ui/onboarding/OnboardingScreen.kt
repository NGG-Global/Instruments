package com.ngg.instruments.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngg.instruments.sensor.SensorCapabilities
import com.ngg.instruments.ui.theme.Palette

/**
 * First-launch flow: mounting guidance, location permission, sensor summary,
 * SET LEVEL, QNH, safety statement. Not repeated on later launches;
 * recalibration lives in settings.
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.panelBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "FLIGHT INSTRUMENTS",
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
        )
        Text(
            "For supplemental / experimental use only. Not a certified flight instrument and not a substitute for approved aircraft instrumentation.",
            color = Color(0xFFE0A83C), fontSize = 13.sp,
        )

        Step("1", "MOUNT THE DEVICE") {
            Text(
                "Fix the device rigidly in its normal operating position. Attitude readings assume the device does not move relative to the airframe.",
                color = Color(0x99EEEFEB), fontSize = 13.sp,
            )
        }

        Step("2", "LOCATION PERMISSION") {
            Text(
                "Precise location is used only for GNSS ground speed, track and altitude — entirely on this device, offline. This app has no internet permission.",
                color = Color(0x99EEEFEB), fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (hasLocationPermission) {
                Text("Granted", color = Color(0xFF4FBF6B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Button(
                    onClick = onRequestLocationPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3033), contentColor = Color.White),
                ) { Text("GRANT PRECISE LOCATION") }
            }
        }

        Step("3", "DETECTED SENSORS") {
            SensorLine("Attitude (rotation vector)", capabilities.hasAnyAttitudeSource)
            SensorLine("Compass heading", capabilities.hasAnyHeadingSource)
            SensorLine("Barometer", capabilities.hasPressure)
            SensorLine("GNSS", capabilities.hasGnss)
            if (!capabilities.hasPressure) {
                Text(
                    "Pressure sensor unavailable — altitude and vertical speed will use GNSS with reduced responsiveness.",
                    color = Color(0xFFE0A83C), fontSize = 12.sp,
                )
            }
        }

        Step("4", "SET LEVEL") {
            Text(
                "With the device mounted and the aircraft level, capture the attitude reference. You can recalibrate any time from settings.",
                color = Color(0x99EEEFEB), fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSetLevel,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.referenceOrange, contentColor = Color.Black),
            ) { Text("SET LEVEL", fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
            if (attitudeCalibrated) {
                Text("Calibrated", color = Color(0xFF4FBF6B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Step("5", "QNH") {
            Text(
                "Enter the local altimeter setting for accurate indicated altitude. Defaults to standard pressure 1013.25 hPa.",
                color = Color(0x99EEEFEB), fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEditQnh) { Text("ENTER QNH", color = Palette.inkCream) }
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3033), contentColor = Color.White),
        ) { Text("ENTER INSTRUMENT PANEL", letterSpacing = 1.5.sp) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Step(number: String, title: String, content: @Composable () -> Unit) {
    Column {
        Row {
            Text(number, color = Palette.referenceOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(title, color = Palette.inkCream, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun SensorLine(name: String, present: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, color = Color(0xCCEEEFEB), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            if (present) "OK" else "MISSING",
            color = if (present) Color(0xFF4FBF6B) else Color(0xFFD65045),
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
    }
}
