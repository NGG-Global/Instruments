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
 *
 * Dial art follows the reference exactly: 60 evenly spaced divisions around
 * the full circle, numerals 0 / 2 / 4 / 6 at the left, top, upper-right and
 * right (mirrored below), ".5" marks flanking the zero, the two centre
 * captions and the white zero bug at 9 o'clock.
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

                // The needle is always solid, as on the reference panel; with
                // no data it simply rests at zero.
                val fpm = if (available.value) {
                    verticalSpeedFpm.value.coerceIn(-6_000f, 6_000f)
                } else {
                    0f
                }
                drawVsiNeedle(c, dialR, s, fpm)
                drawHub()
                drawGlass(dialR)
            }
        },
    )
}

/**
 * Sweep in degrees from the zero point at 9 o'clock, positive toward the top
 * (climb). Calibrated on the reference dial's own printed numerals: 0 at the
 * left, ".5" a little above it, 2 at the top, 4 upper-right and 6 at the
 * right, interpolated linearly in between.
 */
internal fun vsiSweepDeg(fpm: Float): Float {
    val points = floatArrayOf(0f, 500f, 2_000f, 4_000f, 6_000f)
    val sweeps = floatArrayOf(0f, 25.7f, 90f, 135f, 180f)
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

    // 60 evenly spaced divisions around the circle, as in the reference.
    for (i in 0 until 60) {
        val a = i / 60.0 * 2.0 * Math.PI
        val major = i % 5 == 0
        val mid = !major && i % 2 == 0
        val outer = dialR * 0.91f
        val inner = dialR * if (major) 0.74f else if (mid) 0.79f else 0.82f
        drawLine(
            color = Palette.inkWhite,
            start = Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
            end = Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
            strokeWidth = s * if (major) 0.005f else 0.0025f,
        )
    }

    // Numerals at the reference's fixed angles.
    val bigStyle = TextStyle(
        fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.058f).toSp(),
        fontWeight = FontWeight.Bold,
    )
    val labels = listOf(
        "0" to Math.PI,
        "2" to Math.PI * 1.5,
        "4" to Math.PI * 1.75,
        "6" to 0.0,
        "4" to Math.PI * 0.25,
        "2" to Math.PI * 0.5,
    )
    for ((text, a) in labels) {
        val rr = dialR * 0.60f
        val layout = textMeasurer.measure(AnnotatedString(text), bigStyle)
        drawText(
            layout,
            topLeft = Offset(
                c.x + (cos(a) * rr).toFloat() - layout.size.width / 2f,
                c.y + (sin(a) * rr).toFloat() - layout.size.height / 2f,
            ),
        )
    }

    // ".5" marks flanking zero, at the reference's hard-coded positions.
    val smallStyle = TextStyle(
        fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.039f).toSp(),
        fontWeight = FontWeight.Bold,
    )
    val half = textMeasurer.measure(AnnotatedString(".5"), smallStyle)
    drawText(
        half,
        topLeft = Offset(c.x - dialR * 0.52f - half.size.width / 2f, c.y - dialR * 0.25f - half.size.height / 2f),
    )
    drawText(
        half,
        topLeft = Offset(c.x - dialR * 0.52f - half.size.width / 2f, c.y + dialR * 0.25f - half.size.height / 2f),
    )

    // Centre captions.
    val t1 = textMeasurer.measure(
        AnnotatedString("VERTICAL SPEED"),
        TextStyle(fontFamily = BarlowCondensed, color = Color(0xFFF2F2EE), fontSize = (s * 0.024f).toSp(), fontWeight = FontWeight.Bold),
    )
    drawText(t1, topLeft = Offset(c.x - t1.size.width / 2f, c.y - dialR * 0.26f - t1.size.height / 2f))
    val t2 = textMeasurer.measure(
        AnnotatedString("THOUSANDS FT / MIN"),
        TextStyle(fontFamily = BarlowCondensed, color = Color(0xFFF2F2EE), fontSize = (s * 0.019f).toSp(), fontWeight = FontWeight.Bold),
    )
    drawText(t2, topLeft = Offset(c.x - t2.size.width / 2f, c.y - dialR * 0.165f - t2.size.height / 2f))

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
    rotate(degrees = 180f + vsiSweepDeg(fpm), pivot = c) {
        // Tapered white needle, drawn along +X; rotation sets the direction.
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
