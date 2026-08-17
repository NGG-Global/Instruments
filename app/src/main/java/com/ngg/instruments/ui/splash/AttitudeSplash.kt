package com.ngg.instruments.ui.splash

/*
 * Winged attitude-indicator splash loader.
 *
 *  - Everything is drawn on a Canvas in a 1000x1000 design space centred on the
 *    logo, so it scales to any screen without assets.
 *  - Phases: bezel snaps in -> wings unfold -> indeterminate 2.4s horizon roll
 *    -> resolve (horizon levels, shimmer sweep) -> exit.
 *  - Flip `loading` to false when your app is ready; the loop finishes its
 *    current pass, resolves, and calls onFinished().
 *
 * Usage:
 *   setContent {
 *       var ready by remember { mutableStateOf(false) }
 *       LaunchedEffect(Unit) { viewModel.warmUp(); ready = true }
 *       if (showSplash) AttitudeSplash(loading = !ready) { showSplash = false }
 *       else AppNavHost()
 *   }
 *
 * On Android 12+ pair this with the SplashScreen API: keep the system splash on
 * the icon, call installSplashScreen() in onCreate, and hand off to this
 * composable via setKeepOnScreenCondition { false } once the window is drawn.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* ---------------------------------------------------------------- palette */

private val Navy = Color(0xFF080D16)
private val GlowBlue = Color(0xFF3A84C9)
private val SkyTop = Color(0xFF4A9EDE)
private val SkyBottom = Color(0xFF2A6EA8)
private val GroundTop = Color(0xFF181D22)
private val GroundBottom = Color(0xFF04060A)
private val Chalk = Color(0xFFF2F6FA)
private val FaceDark = Color(0xFF06090E)
private val RingShadow = Color(0xFF0D1219)

private val SilverStops = arrayOf(
    0.00f to Color(0xFFFFFFFF),
    0.34f to Color(0xFFE2E8EE),
    0.52f to Color(0xFFAAB4BF),
    0.72f to Color(0xFFDFE6EC),
    1.00f to Color(0xFF7D8892),
)

private val BezelStops = arrayOf(
    0.00f to Color(0xFFF4F7FA),
    0.26f to Color(0xFF9AA4AE),
    0.50f to Color(0xFFE6ECF1),
    0.76f to Color(0xFF79838D),
    1.00f to Color(0xFFD3DAE1),
)

/* ------------------------------------------------------------------ timing */

private const val LOOP_MS = 2400          // one indeterminate pass
private const val BEZEL_MS = 700
private const val FEATHER_MS = 620
private const val FEATHER_STAGGER = 100
private const val RESOLVE_MS = 850
private const val SHIMMER_MS = 950
private const val EXIT_MS = 520

// Gentler overshoot than the original 1.56 — the unfold should feel rounded,
// not snappy, to match the soft feather shapes of the logo.
private val Overshoot = CubicBezierEasing(0.34f, 1.24f, 0.64f, 1f)
private val Glide = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)   // easeInOutSine-ish

/* ---------------------------------------------------------------- geometry */

/** One swept feather, in design units, rooted at (0,0) and pointing +x. */
private data class Feather(
    val length: Float,
    val thickness: Float,
    val rise: Float,
    val offsetY: Float,
    val restAngle: Float,
)

private val Feathers = listOf(
    Feather(340f, 58f, 132f, -44f, -5f),
    Feather(296f, 52f, 74f, 2f, -2f),
    Feather(250f, 46f, 32f, 46f, 0f),
    Feather(192f, 38f, 8f, 86f, 3f),
)

/**
 * Fuller, rounded feather like the logo artwork: the outer end is a soft
 * rounded cap (a small cubic loop around the tip) instead of the original
 * sharp point.
 */
private fun bladePath(f: Feather): Path = Path().apply {
    val l = f.length
    val h = f.thickness
    val r = f.rise
    moveTo(0f, -h / 2f)
    // Top edge sweeping out toward the tip.
    cubicTo(l * 0.36f, -h * 0.55f - r * 0.36f, l * 0.70f, -r * 0.80f, l * 0.93f, -r * 0.94f)
    // Rounded tip cap: loop past the tip and back instead of a sharp corner.
    cubicTo(l * 1.07f, -r * 1.02f - h * 0.10f, l * 1.06f, -r * 0.66f + h * 0.16f, l * 0.90f, -r * 0.62f)
    // Bottom edge back to the root.
    cubicTo(l * 0.62f, -r * 0.24f, l * 0.34f, h * 0.34f, 0f, h / 2f)
    close()
}

/* ------------------------------------------------------------------- entry */

@Composable
fun AttitudeSplash(
    loading: Boolean,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    val finish by rememberUpdatedState(onFinished)

    val bezel = remember { Animatable(0.24f) }
    val wings = remember { List(Feathers.size) { Animatable(0f) } }
    val roll = remember { Animatable(0f) }
    val pitch = remember { Animatable(0f) }
    val shimmer = remember { Animatable(-1.3f) }   // -1.3 .. 1.4 across the logo
    val exit = remember { Animatable(0f) }         // 0 = present, 1 = gone

    // Intro: bezel, then the feathers unfold outward with a stagger.
    LaunchedEffect(Unit) {
        launch { bezel.animateTo(1f, tween(BEZEL_MS, easing = Overshoot)) }
        wings.forEachIndexed { i, a ->
            launch {
                a.animateTo(
                    1f,
                    tween(FEATHER_MS, delayMillis = 260 + i * FEATHER_STAGGER, easing = Overshoot),
                )
            }
        }
    }

    // Indeterminate loop while loading, then resolve to a level horizon.
    LaunchedEffect(loading) {
        if (loading) {
            launch {
                while (isActive) {
                    roll.animateTo(
                        0f,
                        keyframes {
                            durationMillis = LOOP_MS
                            0f at 0 using Glide
                            -26f at 600 using Glide
                            21f at 1200 using Glide
                            -17f at 1800 using Glide
                            8f at 2150 using Glide
                            0f at LOOP_MS
                        },
                    )
                }
            }
            launch {
                while (isActive) {
                    pitch.animateTo(
                        0f,
                        keyframes {
                            durationMillis = LOOP_MS
                            0f at 0 using Glide
                            26f at 700 using Glide
                            -20f at 1400 using Glide
                            11f at 2000 using Glide
                            0f at LOOP_MS
                        },
                    )
                }
            }
            launch {
                while (isActive) {
                    shimmer.snapTo(-1.3f)
                    shimmer.animateTo(1.4f, tween(SHIMMER_MS, easing = LinearEasing))
                    shimmer.animateTo(1.4f, tween(LOOP_MS - SHIMMER_MS))  // rest offscreen
                }
            }
        } else {
            launch { pitch.animateTo(0f, tween(RESOLVE_MS, easing = FastOutSlowInEasing)) }
            launch {
                shimmer.snapTo(-1.3f)
                shimmer.animateTo(1.4f, tween(SHIMMER_MS, easing = Glide))
            }
            roll.animateTo(0f, tween(RESOLVE_MS, easing = FastOutSlowInEasing))
            exit.animateTo(1f, tween(EXIT_MS, easing = FastOutSlowInEasing))
            finish()
        }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.47f
            // Logo spans ~900 design units; leave a comfortable margin.
            val k = min(size.width / 1040f, size.height / 1500f) * (1f + 0.14f * exit.value)
            val alpha = 1f - exit.value

            // Backdrop fades with the exit phase so the panel is revealed
            // underneath instead of a hard cut.
            drawRect(Navy, alpha = alpha)
            drawGlow(cx, cy, k, bezel.value, alpha)

            withTransform({
                translate(cx, cy)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawWings(wings.map { it.value }, alpha)
                drawGauge(roll.value, pitch.value, bezel.value, alpha)
                drawShimmer(shimmer.value, alpha)
            }
        }
    }
}

/* ---------------------------------------------------------------- painting */

private fun DrawScope.drawGlow(cx: Float, cy: Float, k: Float, bezel: Float, alpha: Float) {
    val r = 760f * k
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to GlowBlue.copy(alpha = 0.42f * bezel),
            0.34f to Color(0xFF142C4A).copy(alpha = 0.28f * bezel),
            1.00f to Color.Transparent,
            center = Offset(cx, cy),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
        alpha = alpha,
    )
}

private fun DrawScope.drawWings(progress: List<Float>, alpha: Float) {
    for (side in intArrayOf(1, -1)) {
        Feathers.forEachIndexed { i, f ->
            val p = progress[i]
            if (p <= 0.001f) return@forEachIndexed
            val grow = 0.08f + 0.92f * p
            val angle = 30f + (f.restAngle - 30f) * p
            val brush = Brush.linearGradient(
                colorStops = SilverStops,
                start = Offset(0f, -f.rise - f.thickness),
                end = Offset(0f, f.thickness),
            )
            withTransform({
                scale(side.toFloat(), 1f, pivot = Offset.Zero)
                translate(112f, f.offsetY)
                rotate(angle, pivot = Offset.Zero)
                scale(grow, 0.7f + 0.3f * grow, pivot = Offset.Zero)
            }) {
                val path = bladePath(f)
                drawPath(path, brush, alpha = alpha * min(1f, p * 3f))
                drawPath(
                    path,
                    Color.White.copy(alpha = 0.5f),
                    alpha = alpha * min(1f, p * 3f),
                    style = Stroke(width = 1.6f),
                )
            }
        }
    }
}

private fun DrawScope.drawGauge(roll: Float, pitch: Float, bezel: Float, alpha: Float) {
    withTransform({ scale(bezel, bezel, pivot = Offset.Zero) }) {
        val a = alpha * min(1f, bezel * 1.4f)

        drawCircle(RingShadow.copy(alpha = 0.9f), radius = 172f, center = Offset.Zero, alpha = a)
        drawCircle(
            brush = Brush.linearGradient(
                colorStops = BezelStops,
                start = Offset(-158f, -158f),
                end = Offset(60f, 158f),
            ),
            radius = 158f,
            center = Offset.Zero,
            alpha = a,
            style = Stroke(width = 30f),
        )
        drawCircle(RingShadow, radius = 142f, center = Offset.Zero, alpha = a, style = Stroke(8f))
        drawCircle(FaceDark, radius = 133f, center = Offset.Zero, alpha = a)

        val face = Path().apply { addOval(Rect(Offset(-132f, -132f), Size(264f, 264f))) }
        clipPath(face) {
            withTransform({
                rotate(roll, pivot = Offset.Zero)
                translate(0f, pitch)
            }) {
                drawRect(
                    Brush.verticalGradient(listOf(SkyTop, SkyBottom), startY = -460f, endY = 0f),
                    topLeft = Offset(-260f, -460f),
                    size = Size(520f, 460f),
                    alpha = a,
                )
                drawRect(
                    Brush.verticalGradient(listOf(GroundTop, GroundBottom), startY = 0f, endY = 460f),
                    topLeft = Offset(-260f, 0f),
                    size = Size(520f, 460f),
                    alpha = a,
                )
                drawRect(Chalk, topLeft = Offset(-260f, -3f), size = Size(520f, 6f), alpha = a)
                ladder(-64f, 112f, 7f, a)   // pitch ladder rungs
                ladder(52f, 136f, 8f, a)
                ladder(96f, 96f, 7f, a)
            }
            // inner shading, keeps the face from reading flat
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.16f),
                    0.7f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.45f),
                    center = Offset(0f, -42f),
                    radius = 206f,
                ),
                radius = 133f,
                center = Offset.Zero,
                alpha = a,
            )
        }

        // roll scale — the tick nearest the pointer lights up
        var deg = -75f
        while (deg <= 75f) {
            val major = abs(deg.toInt() % 30) == 0
            val near = max(0f, 1f - abs(deg - roll) / 26f)
            val rad = ((deg - 90f) * Math.PI / 180f).toFloat()
            val r2 = if (major) 106f else 114f
            drawLine(
                color = Color.White,
                start = Offset(cos(rad) * 128f, sin(rad) * 128f),
                end = Offset(cos(rad) * r2, sin(rad) * r2),
                strokeWidth = if (major) 6f else 4f,
                alpha = a * (0.42f + 0.58f * near),
            )
            deg += 15f
        }

        // roll pointer, sweeping with the horizon
        withTransform({ rotate(roll, pivot = Offset.Zero) }) {
            val pointer = Path().apply {
                moveTo(0f, -118f); lineTo(-15f, -92f); lineTo(15f, -92f); close()
            }
            drawPath(pointer, Color.White, alpha = a)
        }

        // fixed aircraft symbol
        drawRect(Chalk, topLeft = Offset(-98f, -5f), size = Size(64f, 10f), alpha = a)
        drawRect(Chalk, topLeft = Offset(34f, -5f), size = Size(64f, 10f), alpha = a)
        val delta = Path().apply {
            moveTo(-34f, 4f); lineTo(0f, -14f); lineTo(34f, 4f); lineTo(0f, -4f); close()
        }
        drawPath(delta, Chalk, alpha = a)
        drawCircle(Color.White, radius = 10f, center = Offset(0f, -6f), alpha = a)
    }
}

private fun DrawScope.ladder(y: Float, width: Float, height: Float, alpha: Float) {
    drawRect(Chalk, topLeft = Offset(-width / 2f, y), size = Size(width, height), alpha = alpha)
}

/** Diagonal highlight travelling across the metal; clipped to the wing shapes. */
private fun DrawScope.drawShimmer(x: Float, alpha: Float) {
    if (x <= -1.25f || x >= 1.35f) return
    val cxUnits = x * 470f
    val brush = Brush.linearGradient(
        0.0f to Color.Transparent,
        0.5f to Color.White.copy(alpha = 0.85f),
        1.0f to Color.Transparent,
        start = Offset(cxUnits - 210f, -240f),
        end = Offset(cxUnits + 210f, 240f),
    )
    for (side in intArrayOf(1, -1)) {
        Feathers.forEach { f ->
            withTransform({
                scale(side.toFloat(), 1f, pivot = Offset.Zero)
                translate(112f, f.offsetY)
                rotate(f.restAngle, pivot = Offset.Zero)
            }) {
                clipPath(bladePath(f)) {
                    drawRect(
                        brush,
                        topLeft = Offset(-600f, -400f),
                        size = Size(1200f, 800f),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- preview */

@Preview(widthDp = 360, heightDp = 640, backgroundColor = 0xFF080D16, showBackground = true)
@Composable
private fun AttitudeSplashPreview() {
    AttitudeSplash(loading = true, modifier = Modifier.fillMaxSize().background(Navy))
}
