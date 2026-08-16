package com.ngg.instruments.ui.instruments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.ngg.instruments.ui.theme.BarlowCondensed
import com.ngg.instruments.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Vertical speed indicator, thousands of ft/min, ±6,000 fpm.
 * Zero sits at the left (9 o'clock); climb sweeps clockwise over the top,
 * descent counterclockwise under the bottom. The scale is non-linear like a
 * real VSI (expanded near zero) and ticks, labels and needle all share the
 * same mapping.
 */
@Composable
fun VerticalSpeedIndicator(
    verticalSpeedFpm: State<Float>,
    available: State<Boolean>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Spacer(
        modifier.drawWithCache {
            val s = min(size.width, size.height)
            val c = Offset(size.width / 2f, size.height / 2f)
            val dialR = s * 0.278f // bezel inner 0.296 * 0.94

            val background = renderStaticLayer {
                drawHousing(cornerFraction = 0.065f)
                drawBezel()
                drawVsiDial(textMeasurer, dialR)
            }

            onDrawBehind {
                drawImage(background)

                if (available.value) {
                    val fpm = verticalSpeedFpm.value.coerceIn(-6_000f, 6_000f)
                    drawVsiNeedle(c, dialR, s, fpm)
                }
                drawHub()
                drawGlass(dialR)
            }
        },
    )
}

/**
 * Non-linear VSI scale: sweep in degrees from the left zero point for a rate
 * in fpm. Piecewise-linear through the printed calibration points.
 */
internal fun vsiSweepDeg(fpm: Float): Float {
    val points = floatArrayOf(0f, 500f, 1_000f, 2_000f, 4_000f, 6_000f)
    val sweeps = floatArrayOf(0f, 45f, 75f, 112f, 150f, 180f)
    val v = abs(fpm).coerceAtMost(6_000f)
    var i = 1
    while (i < points.size && v > points[i]) i++
    if (i >= points.size) return if (fpm >= 0f) 180f else -180f
    val f = (v - points[i - 1]) / (points[i] - points[i - 1])
    val sweep = sweeps[i - 1] + f * (sweeps[i] - sweeps[i - 1])
    return if (fpm >= 0f) sweep else -sweep
}

private fun DrawScope.drawVsiDial(textMeasurer: TextMeasurer, dialR: Float) {
    val s = instrumentSizePx()
    val c = center
    drawDialFace(dialR)

    fun tickAt(fpm: Float, major: Boolean) {
        val angle = Math.toRadians(180.0 + vsiSweepDeg(fpm).toDouble())
        val outer = dialR * 0.91f
        val inner = dialR * if (major) 0.74f else 0.82f
        drawLine(
            color = Palette.inkWhite,
            start = Offset(c.x + (cos(angle) * inner).toFloat(), c.y + (sin(angle) * inner).toFloat()),
            end = Offset(c.x + (cos(angle) * outer).toFloat(), c.y + (sin(angle) * outer).toFloat()),
            strokeWidth = s * if (major) 0.005f else 0.0025f,
        )
    }

    // Minor ticks: 100s to 1,000, 500s to 2,000, 1,000s to 6,000 — both signs.
    val minors = buildList {
        for (v in 100..900 step 100) add(v.toFloat())
        add(1_500f)
        add(3_000f)
        add(5_000f)
    }
    val majors = listOf(0f, 500f, 1_000f, 2_000f, 4_000f, 6_000f)
    for (v in minors) {
        tickAt(v, major = false)
        tickAt(-v, major = false)
    }
    for (v in majors) {
        tickAt(v, major = true)
        if (v > 0f) tickAt(-v, major = true)
    }

    // Labels, using the same mapping as the ticks and needle.
    val bigStyle = TextStyle(fontFamily = BarlowCondensed, color = Palette.inkWhite, fontSize = (s * 0.052f).toSp(), fontWeight = FontWeight.Bold)
    val smallStyle = TextStyle(fontFamily = BarlowCondensed, color = Palette.inkWhite, fontSize = (s * 0.036f).toSp(), fontWeight = FontWeight.Bold)

    fun label(text: String, fpm: Float, style: TextStyle) {
        val angle = Math.toRadians(180.0 + vsiSweepDeg(fpm).toDouble())
        val rr = dialR * 0.58f
        val layout = textMeasurer.measure(AnnotatedString(text), style)
        drawText(
            layout,
            topLeft = Offset(
                c.x + (cos(angle) * rr).toFloat() - layout.size.width / 2f,
                c.y + (sin(angle) * rr).toFloat() - layout.size.height / 2f,
            ),
        )
    }

    label("0", 0f, bigStyle)
    label(".5", 500f, smallStyle)
    label(".5", -500f, smallStyle)
    label("1", 1_000f, bigStyle)
    label("1", -1_000f, bigStyle)
    label("2", 2_000f, bigStyle)
    label("2", -2_000f, bigStyle)
    label("4", 4_000f, bigStyle)
    label("4", -4_000f, bigStyle)
    label("6", 6_000f, bigStyle)

    // Center wording.
    val t1 = textMeasurer.measure(
        AnnotatedString("VERTICAL SPEED"),
        TextStyle(fontFamily = BarlowCondensed, color = Palette.inkWhite, fontSize = (s * 0.024f).toSp(), fontWeight = FontWeight.Bold),
    )
    drawText(t1, topLeft = Offset(c.x - t1.size.width / 2f, c.y - dialR * 0.30f - t1.size.height / 2f))
    val t2 = textMeasurer.measure(
        AnnotatedString("THOUSANDS FT / MIN"),
        TextStyle(fontFamily = BarlowCondensed, color = Palette.inkWhite, fontSize = (s * 0.019f).toSp(), fontWeight = FontWeight.Bold),
    )
    drawText(t2, topLeft = Offset(c.x - t2.size.width / 2f, c.y - dialR * 0.19f - t2.size.height / 2f))

    // UP / DOWN arrows near the zero point.
    val arrowStyle = TextStyle(fontFamily = BarlowCondensed, color = Palette.inkCream, fontSize = (s * 0.020f).toSp(), fontWeight = FontWeight.Bold)
    val up = textMeasurer.measure(AnnotatedString("UP"), arrowStyle)
    drawText(up, topLeft = Offset(c.x - dialR * 0.72f, c.y - dialR * 0.30f))
    val dn = textMeasurer.measure(AnnotatedString("DN"), arrowStyle)
    drawText(dn, topLeft = Offset(c.x - dialR * 0.72f, c.y + dialR * 0.22f))

    // Zero reference bug at the left.
    val bug = Path().apply {
        moveTo(c.x - dialR * 0.93f, c.y)
        lineTo(c.x - dialR * 0.84f, c.y - s * 0.018f)
        lineTo(c.x - dialR * 0.84f, c.y + s * 0.018f)
        close()
    }
    drawPath(bug, Palette.inkWhite)
}

private fun DrawScope.drawVsiNeedle(c: Offset, dialR: Float, s: Float, fpm: Float) {
    val angleDeg = 180f + vsiSweepDeg(fpm)
    rotate(degrees = angleDeg, pivot = c) {
        // Needle drawn pointing along +X, rotation handles direction.
        val needle = Path().apply {
            moveTo(c.x - s * 0.035f, c.y - s * 0.012f)
            lineTo(c.x + dialR * 0.76f, c.y - s * 0.010f)
            lineTo(c.x + dialR * 0.86f, c.y)
            lineTo(c.x + dialR * 0.76f, c.y + s * 0.010f)
            lineTo(c.x - s * 0.035f, c.y + s * 0.012f)
            close()
        }
        drawPath(needle, Color(0xFFF0F0EC))
        drawPath(needle, Color(0x8C141414), style = Stroke(width = s * 0.002f))
    }
}
