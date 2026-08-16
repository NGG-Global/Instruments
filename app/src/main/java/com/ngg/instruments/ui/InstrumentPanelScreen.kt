package com.ngg.instruments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngg.instruments.flight.DataQuality
import com.ngg.instruments.flight.FlightState
import com.ngg.instruments.ui.instruments.Altimeter
import com.ngg.instruments.ui.instruments.AttitudeIndicator
import com.ngg.instruments.ui.instruments.GroundSpeedIndicator
import com.ngg.instruments.ui.instruments.HeadingIndicator
import com.ngg.instruments.ui.instruments.VerticalSpeedIndicator
import com.ngg.instruments.ui.theme.Palette

/**
 * The instrument panel: ATTITUDE full width on top, then
 * HEADING | ALTIMETER and VERTICAL SPEED | GROUND SPEED — the reference layout.
 */
@Composable
fun InstrumentPanelScreen(
    flight: State<FlightState>,
    useTrueHeading: State<Boolean>,
    recording: Boolean,
    replaying: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onAltimeterTapped: () -> Unit,
) {
    // Display-animation layer: eases the estimator output onto the needles.
    // NaN targets freeze the display (used when data is unavailable).
    val pitch = rememberDisplayValue(tauMillis = 60f) { flight.value.pitchDeg ?: Float.NaN }
    val roll = rememberDisplayValue(tauMillis = 60f) { flight.value.rollDeg ?: Float.NaN }
    val heading = rememberDisplayValue(tauMillis = 150f, angular = true) {
        val f = flight.value
        val h = if (useTrueHeading.value) f.trueHeadingDeg ?: f.magneticHeadingDeg else f.magneticHeadingDeg
        h ?: Float.NaN
    }
    val altitude = rememberDisplayValue(tauMillis = 280f) { flight.value.altitudeFt ?: Float.NaN }
    val vsi = rememberDisplayValue(tauMillis = 220f) { flight.value.verticalSpeedFpm ?: Float.NaN }
    val speed = rememberDisplayValue(tauMillis = 250f) { flight.value.groundSpeedKt ?: Float.NaN }

    val attitudeAvailable = remember { derivedStateOf { flight.value.pitchDeg != null } }
    val headingAvailable = remember {
        derivedStateOf { flight.value.magneticHeadingDeg != null || flight.value.trueHeadingDeg != null }
    }
    val altitudeAvailable = remember { derivedStateOf { flight.value.altitudeFt != null } }
    val vsiAvailable = remember { derivedStateOf { flight.value.verticalSpeedFpm != null } }
    val speedAvailable = remember { derivedStateOf { flight.value.groundSpeedKt != null } }
    val track = remember { derivedStateOf { flight.value.trackDeg } }
    val qnh = remember { derivedStateOf { flight.value.qnhHpa } }

    val attitudeQuality by remember { derivedStateOf { flight.value.attitudeQuality } }
    val headingQuality by remember { derivedStateOf { flight.value.headingQuality } }
    val altitudeQuality by remember { derivedStateOf { flight.value.altitudeQuality } }
    val vsiQuality by remember { derivedStateOf { flight.value.verticalSpeedQuality } }
    val speedQuality by remember { derivedStateOf { flight.value.speedQuality } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.panelBackground)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "FLIGHT INSTRUMENTS",
                color = Color(0x66EEEFEB),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
            Box(Modifier.weight(1f))
            if (recording) StatusChip("REC", Color(0xFFD65045))
            if (replaying) StatusChip("REPLAY", Color(0xFF4E9AD1))
            IconButton(onClick = onOpenDiagnostics, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Info, contentDescription = "Diagnostics", tint = Color(0x88EEEFEB))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0x88EEEFEB))
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().weight(0.4f)) {
                InstrumentSlot(caption = "ATTITUDE", quality = attitudeQuality) {
                    AttitudeIndicator(
                        pitchDeg = pitch,
                        rollDeg = roll,
                        available = attitudeAvailable,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().weight(0.3f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                InstrumentSlot(caption = "HEADING", quality = headingQuality) {
                    HeadingIndicator(
                        headingDeg = heading,
                        headingAvailable = headingAvailable,
                        trackDeg = track,
                        isTrueHeading = useTrueHeading,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                InstrumentSlot(
                    caption = "ALTIMETER",
                    quality = altitudeQuality,
                    onClick = onAltimeterTapped,
                ) {
                    Altimeter(
                        altitudeFt = altitude,
                        available = altitudeAvailable,
                        qnhHpa = qnh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().weight(0.3f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                InstrumentSlot(caption = "VERTICAL SPEED", quality = vsiQuality) {
                    VerticalSpeedIndicator(
                        verticalSpeedFpm = vsi,
                        available = vsiAvailable,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                InstrumentSlot(caption = "GROUND SPEED", quality = speedQuality) {
                    GroundSpeedIndicator(
                        groundSpeedKt = speed,
                        available = speedAvailable,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun RowScope.InstrumentSlot(
    caption: String,
    quality: DataQuality,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .clip(RoundedCornerShape(7.dp))
        .background(Palette.slotBackground)
        .border(1.dp, Palette.slotBorder, RoundedCornerShape(7.dp))
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)

    Box(modifier) {
        content()
        Text(
            caption,
            color = Palette.caption,
            fontSize = 7.sp,
            letterSpacing = 0.7.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 5.dp, bottom = 4.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 5.dp)
                .size(5.dp)
                .background(Palette.qualityColor(quality), CircleShape),
        )
    }
}
