package com.ngg.instruments.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngg.instruments.flight.DataQuality
import com.ngg.instruments.flight.FlightState
import com.ngg.instruments.ui.theme.BarlowCondensed
import com.ngg.instruments.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Panel chrome from the mobile design: GPS/IMU status line on top, control
 * bar (portrait) or control rail (landscape) with day/night, brightness,
 * lock, diagnostics and settings, and full-panel night/dim overlays.
 */

private val ChromeDim = Color(0x99E2EAEE)
private val ChromeBright = Color(0xFFE9EEF0)
private val NightRed = Color(0xFF4A1000)
private val LockAmber = Color(0xFFF3A53E)

/** Brightness levels: label and black-overlay alpha, per the design (100/70/45). */
private val BrightnessSteps = listOf("BRT 100" to 0f, "BRT 70" to 0.30f, "BRT 45" to 0.55f)

class PanelChromeState internal constructor(
    night: Boolean,
    brightnessStep: Int,
    locked: Boolean,
) {
    var night by mutableStateOf(night)
    var brightnessStep by mutableIntStateOf(brightnessStep)
    var locked by mutableStateOf(locked)

    val dimAlpha: Float get() = BrightnessSteps[brightnessStep].second
    val brightnessLabel: String get() = BrightnessSteps[brightnessStep].first

    fun cycleBrightness() {
        brightnessStep = (brightnessStep + 1) % BrightnessSteps.size
    }
}

@Composable
fun rememberPanelChromeState(): PanelChromeState =
    remember { PanelChromeState(night = false, brightnessStep = 0, locked = false) }

/**
 * Applies the night-vision (red multiply) and brightness overlays over the
 * panel content. Offscreen compositing is only paid for while an overlay is
 * actually active.
 */
fun Modifier.panelOverlays(state: PanelChromeState): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (state.night) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        if (state.night) {
            drawRect(NightRed.copy(alpha = 0.74f), blendMode = BlendMode.Multiply)
        }
        val dim = state.dimAlpha
        if (dim > 0f) {
            drawRect(Color.Black.copy(alpha = dim))
        }
    }

/* ------------------------------------------------------------- status bar */

@Composable
fun PanelStatusBar(
    flight: State<FlightState>,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val gpsLine by remember {
        derivedStateOf {
            val f = flight.value
            val fix = when (f.gnssQuality) {
                DataQuality.UNAVAILABLE -> "NO FIX"
                DataQuality.POOR -> "2D"
                else -> "3D"
            }
            buildString {
                append("GPS ").append(fix)
                f.satellitesUsed?.let { append(" · ").append(it).append(" SV") }
                f.horizontalAccuracyM?.let { append(" · ±").append(it.roundToInt()).append(" M") }
            }
        }
    }
    val gpsColor by remember { derivedStateOf { Palette.qualityColor(flight.value.gnssQuality) } }
    val imuOk by remember { derivedStateOf { flight.value.attitudeQuality != DataQuality.UNAVAILABLE } }

    val clock = rememberClockText()
    val battery = rememberBatteryPercent()

    Row(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(gpsColor, CircleShape),
        )
        StatusText(gpsLine, Modifier.padding(start = 8.dp))
        Box(Modifier.weight(1f))
        if (locked) {
            Text(
                "LOCKED",
                color = LockAmber,
                fontSize = 9.sp,
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .background(LockAmber.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        StatusText(if (imuOk) "IMU OK" else "IMU —")
        StatusText(clock.value, Modifier.padding(start = 10.dp))
        battery.value?.let { StatusText("$it%", Modifier.padding(start = 10.dp)) }
    }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = ChromeDim,
        fontSize = 9.5.sp,
        fontFamily = BarlowCondensed,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

@Composable
private fun rememberClockText(): State<String> {
    val time = remember { mutableStateOf("--:--") }
    LaunchedEffect(Unit) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            time.value = LocalTime.now().format(fmt)
            delay(15_000)
        }
    }
    return time
}

@Composable
private fun rememberBatteryPercent(): State<Int?> {
    val context = LocalContext.current
    val percent = remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) percent.value = level * 100 / scale
            }
        }
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}

/* ----------------------------------------------------------- control bar */

private data class ControlSpec(
    val label: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val icon: @Composable (Color) -> Unit,
)

@Composable
private fun controls(
    chrome: PanelChromeState,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
): List<ControlSpec> = listOf(
    ControlSpec(
        label = if (chrome.night) "NIGHT" else "DAY",
        active = chrome.night,
        enabled = !chrome.locked,
        onClick = { chrome.night = !chrome.night },
        icon = { tint -> DayNightIcon(tint) },
    ),
    ControlSpec(
        label = chrome.brightnessLabel,
        enabled = !chrome.locked,
        onClick = { chrome.cycleBrightness() },
        icon = { tint -> BrightnessIcon(tint) },
    ),
    ControlSpec(
        label = if (chrome.locked) "LOCKED" else "LOCK",
        active = chrome.locked,
        onClick = { chrome.locked = !chrome.locked },
        icon = { tint -> LockIcon(tint) },
    ),
    ControlSpec(
        label = "DIAG",
        enabled = !chrome.locked,
        onClick = onOpenDiagnostics,
        icon = { tint -> DiagnosticsIcon(tint) },
    ),
    ControlSpec(
        label = "SETTINGS",
        enabled = !chrome.locked,
        onClick = onOpenSettings,
        icon = { tint -> SettingsIcon(tint) },
    ),
)

/* ------------------------------------------------------------------ icons */

@Composable
private fun DayNightIcon(tint: Color) {
    Canvas(Modifier.size(19.dp)) {
        val r = size.minDimension * 0.39f
        drawCircle(tint, radius = r, center = center, style = Stroke(size.minDimension * 0.08f))
        drawArc(
            color = tint,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
        )
    }
}

@Composable
private fun BrightnessIcon(tint: Color) {
    Canvas(Modifier.size(19.dp)) {
        val w = size.minDimension * 0.08f
        drawCircle(tint, radius = size.minDimension * 0.22f, center = center, style = Stroke(w))
        val inner = size.minDimension * 0.34f
        val outer = size.minDimension * 0.47f
        for (i in 0 until 4) {
            val a = Math.toRadians(i * 90.0)
            val dx = kotlin.math.cos(a).toFloat()
            val dy = kotlin.math.sin(a).toFloat()
            drawLine(
                tint,
                Offset(center.x + dx * inner, center.y + dy * inner),
                Offset(center.x + dx * outer, center.y + dy * outer),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun LockIcon(tint: Color) {
    Canvas(Modifier.size(19.dp)) {
        val w = size.minDimension * 0.08f
        val s = size.minDimension
        drawRoundRect(
            tint,
            topLeft = Offset(s * 0.24f, s * 0.45f),
            size = Size(s * 0.52f, s * 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f),
            style = Stroke(w),
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(s * 0.34f, s * 0.16f),
            size = Size(s * 0.32f, s * 0.5f),
            style = Stroke(w),
        )
    }
}

@Composable
private fun DiagnosticsIcon(tint: Color) {
    Canvas(Modifier.size(19.dp)) {
        val s = size.minDimension
        val w = s * 0.08f
        val path = Path().apply {
            moveTo(s * 0.10f, s * 0.55f)
            lineTo(s * 0.34f, s * 0.55f)
            lineTo(s * 0.44f, s * 0.26f)
            lineTo(s * 0.58f, s * 0.76f)
            lineTo(s * 0.68f, s * 0.55f)
            lineTo(s * 0.90f, s * 0.55f)
        }
        drawPath(path, tint, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
private fun SettingsIcon(tint: Color) {
    Canvas(Modifier.size(19.dp)) {
        val s = size.minDimension
        val w = s * 0.08f
        drawLine(tint, Offset(s * 0.14f, s * 0.34f), Offset(s * 0.86f, s * 0.34f), w, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.14f, s * 0.66f), Offset(s * 0.86f, s * 0.66f), w, StrokeCap.Round)
        drawCircle(Color(0xFF0A0C0E), radius = s * 0.13f, center = Offset(s * 0.60f, s * 0.34f))
        drawCircle(tint, radius = s * 0.11f, center = Offset(s * 0.60f, s * 0.34f), style = Stroke(w))
        drawCircle(Color(0xFF0A0C0E), radius = s * 0.13f, center = Offset(s * 0.38f, s * 0.66f))
        drawCircle(tint, radius = s * 0.11f, center = Offset(s * 0.38f, s * 0.66f), style = Stroke(w))
    }
}

/** Horizontal control bar along the bottom edge (portrait). */
@Composable
fun PanelControlBar(
    chrome: PanelChromeState,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFF0A0C0E)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        controls(chrome, onOpenDiagnostics, onOpenSettings).forEach { ControlButton(it) }
    }
}

/** Vertical control rail on the trailing edge (landscape). */
@Composable
fun PanelControlRail(
    chrome: PanelChromeState,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .width(64.dp)
            .background(Color(0xFF0A0C0E)),
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        controls(chrome, onOpenDiagnostics, onOpenSettings).forEach { ControlButton(it) }
    }
}

@Composable
private fun ControlButton(spec: ControlSpec) {
    val tint = when {
        !spec.enabled -> ChromeDim.copy(alpha = 0.35f)
        spec.active -> LockAmber
        else -> ChromeDim
    }
    Column(
        Modifier
            .clickable(enabled = spec.enabled || spec.active, onClick = spec.onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        spec.icon(tint)
        Text(
            spec.label,
            color = tint,
            fontSize = 8.5.sp,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
        )
    }
}
