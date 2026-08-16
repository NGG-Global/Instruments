package com.ngg.instruments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.ngg.instruments.ui.theme.BarlowCondensed
import com.ngg.instruments.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The instrument panel, per the mobile design:
 *
 * Portrait — status bar, ATTITUDE full width, HEADING | ALTIMETER,
 * VERTICAL SPEED | GROUND SPEED, control bar along the bottom.
 *
 * Landscape — status bar over attitude (left, square) with the 2×2 dial grid
 * beside it and the control rail on the trailing edge.
 *
 * Night mode, brightness and lock come from [PanelChromeState]; lock freezes
 * all panel interaction except the lock control itself.
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
    val chrome = rememberPanelChromeState()

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

    // Corner data chips (rounded so they only invalidate when the text changes).
    val attitudeChip = remember {
        derivedStateOf {
            val p = flight.value.pitchDeg
            val r = flight.value.rollDeg
            if (p == null || r == null) {
                "PITCH ---  BANK ---"
            } else {
                val bank = r.roundToInt()
                val side = if (bank > 0) "R" else if (bank < 0) "L" else ""
                "PITCH %+d°  BANK %d°%s".format(p.roundToInt(), abs(bank), side)
            }
        }
    }
    val vsiChip = remember {
        derivedStateOf {
            flight.value.verticalSpeedFpm?.let { fpm ->
                val rounded = (fpm / 10f).roundToInt() * 10
                if (rounded > 0) "+$rounded" else "$rounded"
            }
        }
    }
    val speedChip = remember {
        derivedStateOf { flight.value.groundSpeedKt?.let { "${it.roundToInt()} KT" } }
    }

    val locked = chrome.locked

    @Composable
    fun attitudeSlot(modifier: Modifier) = InstrumentSlot(
        caption = "ATTITUDE",
        quality = attitudeQuality,
        chipText = attitudeChip,
        chipTop = true,
        modifier = modifier,
    ) {
        AttitudeIndicator(pitch, roll, attitudeAvailable, Modifier.fillMaxSize())
    }

    @Composable
    fun dialGrid(modifier: Modifier) = Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            InstrumentSlot("HEADING", headingQuality, modifier = Modifier.weight(1f).fillMaxHeight()) {
                HeadingIndicator(heading, headingAvailable, track, useTrueHeading, Modifier.fillMaxSize())
            }
            InstrumentSlot(
                "ALTIMETER", altitudeQuality,
                onClick = if (locked) null else onAltimeterTapped,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Altimeter(altitude, altitudeAvailable, qnh, Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            InstrumentSlot("VERTICAL SPEED", vsiQuality, chipText = vsiChip, modifier = Modifier.weight(1f).fillMaxHeight()) {
                VerticalSpeedIndicator(vsi, vsiAvailable, Modifier.fillMaxSize())
            }
            InstrumentSlot("GROUND SPEED", speedQuality, chipText = speedChip, modifier = Modifier.weight(1f).fillMaxHeight()) {
                GroundSpeedIndicator(speed, speedAvailable, Modifier.fillMaxSize())
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.panelBackground)) {
        val landscape = maxWidth > maxHeight

        Box(Modifier.fillMaxSize().panelOverlays(chrome)) {
            if (!landscape) {
                Column(Modifier.fillMaxSize()) {
                    PanelStatusBar(flight, locked)
                    StatusChipsRow(recording, replaying)
                    Column(
                        Modifier.weight(1f).padding(horizontal = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().weight(0.4f)) {
                            attitudeSlot(Modifier.weight(1f).fillMaxHeight())
                        }
                        dialGrid(Modifier.fillMaxWidth().weight(0.6f).padding(bottom = 5.dp))
                    }
                    PanelControlBar(chrome, onOpenDiagnostics, onOpenSettings)
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        PanelStatusBar(flight, locked)
                        StatusChipsRow(recording, replaying)
                        Row(
                            Modifier.weight(1f).padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            attitudeSlot(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                            dialGrid(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    PanelControlRail(chrome, onOpenDiagnostics, onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun StatusChipsRow(recording: Boolean, replaying: Boolean) {
    if (!recording && !replaying) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp)) {
        if (recording) {
            Text(
                "● REC", color = Color(0xFFD65045), fontSize = 9.sp,
                fontFamily = BarlowCondensed, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp,
            )
        }
        if (replaying) {
            Text(
                "▶ REPLAY", color = Color(0xFF4E9AD1), fontSize = 9.sp,
                fontFamily = BarlowCondensed, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun InstrumentSlot(
    caption: String,
    quality: DataQuality,
    modifier: Modifier,
    chipText: State<String?>? = null,
    chipTop: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var m = modifier
        .clip(RoundedCornerShape(8.dp))
        .background(Palette.slotBackground)
        .border(1.dp, Palette.slotBorder, RoundedCornerShape(8.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)

    Box(m) {
        content()
        Text(
            caption,
            color = Palette.caption,
            fontSize = 8.sp,
            fontFamily = BarlowCondensed,
            letterSpacing = 1.6.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 7.dp, bottom = 5.dp)
                .background(Color(0xC7050709), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        val chip = chipText?.value
        if (chip != null) {
            Text(
                chip,
                color = Color(0xDBEEF3F5),
                fontSize = 11.sp,
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                modifier = Modifier
                    .align(if (chipTop) Alignment.TopEnd else Alignment.BottomEnd)
                    .padding(horizontal = 7.dp, vertical = if (chipTop) 5.dp else 22.dp)
                    .background(Color(0xC7050709), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 7.dp, bottom = 7.dp)
                .size(5.dp)
                .background(Palette.qualityColor(quality), CircleShape),
        )
    }
}
