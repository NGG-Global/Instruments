package com.ngg.instruments.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    // Tap-to-enlarge: a tapped instrument opens full-screen for legibility
    // (all drawing is size-relative, so text scales up with it). LOCK blocks
    // opening, which is its purpose: a locked panel ignores accidental taps.
    var focusedName by rememberSaveable { mutableStateOf("") }
    val focused = FocusedInstrument.entries.firstOrNull { it.name == focusedName }
    fun focus(target: FocusedInstrument) {
        if (!locked) focusedName = target.name
    }

    @Composable
    fun attitudeSlot(modifier: Modifier) = InstrumentSlot(
        caption = "ATTITUDE",
        quality = attitudeQuality,
        chipText = attitudeChip,
        chipTop = true,
        onClick = { focus(FocusedInstrument.ATTITUDE) },
        modifier = modifier,
    ) {
        AttitudeIndicator(pitch, roll, attitudeAvailable, Modifier.fillMaxSize())
    }

    @Composable
    fun dialGrid(modifier: Modifier) = Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            InstrumentSlot(
                "HEADING", headingQuality,
                onClick = { focus(FocusedInstrument.HEADING) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                HeadingIndicator(heading, headingAvailable, track, useTrueHeading, Modifier.fillMaxSize())
            }
            InstrumentSlot(
                "ALTIMETER", altitudeQuality,
                onClick = { focus(FocusedInstrument.ALTIMETER) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Altimeter(altitude, altitudeAvailable, qnh, Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            InstrumentSlot(
                "VERTICAL SPEED", vsiQuality, chipText = vsiChip,
                onClick = { focus(FocusedInstrument.VERTICAL_SPEED) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                VerticalSpeedIndicator(vsi, vsiAvailable, Modifier.fillMaxSize())
            }
            InstrumentSlot(
                "GROUND SPEED", speedQuality, chipText = speedChip,
                onClick = { focus(FocusedInstrument.GROUND_SPEED) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
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
                        Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().weight(0.4f)) {
                            attitudeSlot(Modifier.weight(1f).fillMaxHeight())
                        }
                        dialGrid(Modifier.fillMaxWidth().weight(0.6f).padding(bottom = 2.dp))
                    }
                    PanelControlBar(chrome, onOpenDiagnostics, onOpenSettings)
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        PanelStatusBar(flight, locked)
                        StatusChipsRow(recording, replaying)
                        Row(
                            Modifier
                                .weight(1f)
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom),
                                )
                                .padding(start = 3.dp, end = 3.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            attitudeSlot(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                            dialGrid(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    PanelControlRail(chrome, onOpenDiagnostics, onOpenSettings)
                }
            }

            // Enlarged single-instrument view. Rendered inside the overlay box
            // so night mode and dimming apply to it as well.
            if (focused != null) {
                FocusedInstrumentOverlay(
                    focused = focused,
                    onClose = { focusedName = "" },
                    onAdjustQnh = onAltimeterTapped,
                ) { instrumentModifier ->
                    when (focused) {
                        FocusedInstrument.ATTITUDE ->
                            AttitudeIndicator(pitch, roll, attitudeAvailable, instrumentModifier)
                        FocusedInstrument.HEADING ->
                            HeadingIndicator(heading, headingAvailable, track, useTrueHeading, instrumentModifier)
                        FocusedInstrument.ALTIMETER ->
                            Altimeter(altitude, altitudeAvailable, qnh, instrumentModifier)
                        FocusedInstrument.VERTICAL_SPEED ->
                            VerticalSpeedIndicator(vsi, vsiAvailable, instrumentModifier)
                        FocusedInstrument.GROUND_SPEED ->
                            GroundSpeedIndicator(speed, speedAvailable, instrumentModifier)
                    }
                }
            }
        }
    }
}

private enum class FocusedInstrument(val title: String) {
    ATTITUDE("ATTITUDE"),
    HEADING("HEADING"),
    ALTIMETER("ALTIMETER"),
    VERTICAL_SPEED("VERTICAL SPEED"),
    GROUND_SPEED("GROUND SPEED"),
}

/**
 * Full-screen enlarged view of one instrument. The instrument composables are
 * reused as-is; because all their geometry and typography scale with the
 * drawn size, the enlarged face is fully legible. Tap anywhere or press the
 * system back button to return to the panel.
 */
@Composable
private fun FocusedInstrumentOverlay(
    focused: FocusedInstrument,
    onClose: () -> Unit,
    onAdjustQnh: () -> Unit,
    instrument: @Composable (Modifier) -> Unit,
) {
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF5030405))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Close enlarged instrument",
                onClick = onClose,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { contentDescription = "Enlarged ${focused.title} instrument" },
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                focused.title,
                color = Color(0x99E9EEF0),
                fontSize = 13.sp,
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                instrument(Modifier.fillMaxSize())
            }
            if (focused == FocusedInstrument.ALTIMETER) {
                OutlinedButton(
                    onClick = onAdjustQnh,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(top = 4.dp),
                ) {
                    Text(
                        "ADJUST QNH",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        color = Palette.inkCream,
                    )
                }
            }
            Text(
                "TAP TO CLOSE",
                color = Color(0x66E9EEF0),
                fontSize = 10.sp,
                fontFamily = BarlowCondensed,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
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
    // The visual caption was removed with the reference's mobile design; the
    // label is kept for accessibility services and UI tests via semantics.
    var m = modifier
        .clip(RoundedCornerShape(1.dp))
        .background(Palette.slotBackground)
        .border(1.dp, Palette.slotBorder, RoundedCornerShape(1.dp))
        .semantics { contentDescription = caption }
    if (onClick != null) m = m.clickable(onClick = onClick)

    Box(m) {
        content()
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
