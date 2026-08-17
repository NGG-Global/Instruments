package com.ngg.instruments.ui.instruments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Three-pointer altimeter in feet, drawn to the reference dial:
 * long needle = hundreds (1 turn / 1,000 ft), short wide needle = thousands
 * (1 turn / 10,000 ft), slender needle with an inward triangle =
 * ten-thousands (1 turn / 100,000 ft), numerals 0..9, and the digital
 * altitude window below the hub.
 *
 * Needle geometry is transcribed from the reference, which draws each needle
 * along +X and rotates it; here the same shapes are drawn pointing up (-Y),
 * so a reference point (px, py) maps to (centre.x + py, centre.y - px).
 */
@Composable
fun Altimeter(
    altitudeFt: State<Float>,
    available: State<Boolean>,
    qnhHpa: State<Float>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Spacer(
        modifier.drawWithCache {
            val s = min(size.width, size.height)
            val c = Offset(size.width / 2f, size.height / 2f)
            val dialR = s * Chrome.DIAL_RADIUS

            val background = renderStaticLayer {
                drawHousing(cornerFraction = 0.05f)
                drawBezel()
                drawAltimeterDial(textMeasurer, dialR)
            }

            val readoutStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Color.White,
                fontSize = (s * 0.028f).toSp(),
                fontWeight = FontWeight.Bold,
            )
            val qnhStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Palette.inkCream,
                fontSize = (s * 0.024f).toSp(),
                fontWeight = FontWeight.Bold,
            )

            onDrawBehind {
                drawImage(background)

                val isAvailable = available.value
                val feet = if (isAvailable) altitudeFt.value else 0f

                // Digital window, drawn before the needles so they pass over it.
                val boxW = s * 0.18f
                val boxH = s * 0.048f
                val boxTop = c.y + s * 0.09f - boxH / 2f
                drawRoundRect(
                    color = Color(0xEB080A0B),
                    topLeft = Offset(c.x - boxW / 2f, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(s * 0.007f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(c.x - boxW / 2f, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(s * 0.007f),
                    style = Stroke(width = s * 0.002f),
                )
                val text = if (isAvailable) "${groupThousands(feet.roundToInt())} ft" else "---- ft"
                val layout = textMeasurer.measure(AnnotatedString(text), readoutStyle)
                drawText(
                    layout,
                    topLeft = Offset(c.x - layout.size.width / 2f, boxTop + boxH / 2f - layout.size.height / 2f),
                )

                // QNH annunciation: the live altimeter setting, adjustable by
                // tapping the instrument. Not part of the reference artwork.
                val qnhLayout = textMeasurer.measure(
                    AnnotatedString("QNH ${qnhHpa.value.roundToInt()}"),
                    qnhStyle,
                )
                drawText(
                    qnhLayout,
                    topLeft = Offset(c.x - qnhLayout.size.width / 2f, c.y + dialR * 0.66f - qnhLayout.size.height / 2f),
                )

                drawAltimeterNeedles(c, dialR, s, feet)
                drawHub(radiusFraction = 0.033f)
                drawGlass(dialR)
            }
        },
    )
}

private fun groupThousands(value: Int): String {
    val negative = value < 0
    val digits = kotlin.math.abs(value).toString()
    val out = StringBuilder()
    for ((i, ch) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(ch)
    }
    return if (negative) "-$out" else out.toString()
}

private fun DrawScope.drawAltimeterDial(textMeasurer: TextMeasurer, dialR: Float) {
    val s = instrumentSizePx()
    val c = center
    drawDialFace(dialR)

    // 50 ticks: major every 5 (the 0..9 numerals), minor between.
    for (i in 0 until 50) {
        val a = Math.toRadians(i / 50.0 * 360.0 - 90.0)
        val major = i % 5 == 0
        val outer = dialR * 0.90f
        val inner = dialR * if (major) 0.73f else 0.82f
        drawLine(
            color = Palette.inkWhite,
            start = Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
            end = Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
            strokeWidth = s * if (major) 0.0048f else 0.0022f,
        )
    }

    val numeralStyle = TextStyle(
        fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.070f).toSp(),
    )
    for (i in 0..9) {
        val a = Math.toRadians(i / 10.0 * 360.0 - 90.0)
        val rr = dialR * 0.62f
        val layout = textMeasurer.measure(AnnotatedString(i.toString()), numeralStyle)
        drawText(
            layout,
            topLeft = Offset(
                c.x + (cos(a) * rr).toFloat() - layout.size.width / 2f,
                c.y + (sin(a) * rr).toFloat() - layout.size.height / 2f,
            ),
        )
    }

    val feetLayout = textMeasurer.measure(
        AnnotatedString("FEET"),
        TextStyle(fontFamily = BarlowCondensed, color = Palette.inkWhite, fontSize = (s * 0.030f).toSp()),
    )
    drawText(
        feetLayout,
        topLeft = Offset(c.x + s * 0.085f - feetLayout.size.width / 2f, c.y - dialR * 0.80f - feetLayout.size.height / 2f),
    )

    val annotationStyle = TextStyle(
        fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.012f).toSp(),
        fontWeight = FontWeight.Bold,
    )
    val cal1 = textMeasurer.measure(AnnotatedString("CALIBRATED"), annotationStyle)
    drawText(cal1, topLeft = Offset(c.x - dialR * 0.36f, c.y - dialR * 0.20f - cal1.size.height / 2f))
    val cal2 = textMeasurer.measure(AnnotatedString("FT-500 TO 20,000"), annotationStyle)
    drawText(cal2, topLeft = Offset(c.x - dialR * 0.36f, c.y - dialR * 0.15f - cal2.size.height / 2f))
}

private fun DrawScope.drawAltimeterNeedles(c: Offset, dialR: Float, s: Float, feet: Float) {
    fun wrapFraction(value: Float, period: Float): Float {
        var f = (value % period) / period
        if (f < 0f) f += 1f
        return f
    }

    // Reference needle-space (+X forward) mapped to screen (up = -Y).
    fun p(px: Float, py: Float) = Offset(c.x + py, c.y - px)

    val hundredsAngle = wrapFraction(feet, 1_000f) * 360f
    val thousandsAngle = wrapFraction(feet, 10_000f) * 360f
    val tenThousandsAngle = wrapFraction(feet, 100_000f) * 360f

    // TENS OF THOUSANDS: slender shaft with an inward-pointing triangle,
    // drawn first so the other two pointers sit above it.
    rotate(degrees = tenThousandsAngle, pivot = c) {
        val shaftStart = s * 0.004f
        val shaftEnd = dialR * 0.72f
        val shaftMid = shaftStart + (shaftEnd - shaftStart) * 0.60f

        val inner = Path().apply {
            moveTo(p(shaftStart, -s * 0.0080f).x, p(shaftStart, -s * 0.0080f).y)
            lineTo(p(shaftMid, -s * 0.0080f).x, p(shaftMid, -s * 0.0080f).y)
            lineTo(p(shaftMid, s * 0.0080f).x, p(shaftMid, s * 0.0080f).y)
            lineTo(p(shaftStart, s * 0.0080f).x, p(shaftStart, s * 0.0080f).y)
            close()
        }
        drawPath(inner, Color.White)

        val outer = Path().apply {
            moveTo(p(shaftMid, -s * 0.0035f).x, p(shaftMid, -s * 0.0035f).y)
            lineTo(p(shaftEnd, -s * 0.0035f).x, p(shaftEnd, -s * 0.0035f).y)
            lineTo(p(shaftEnd, s * 0.0035f).x, p(shaftEnd, s * 0.0035f).y)
            lineTo(p(shaftMid, s * 0.0035f).x, p(shaftMid, s * 0.0035f).y)
            close()
        }
        drawPath(outer, Color.White)

        val triBase = dialR * 0.86f
        val triLength = s * 0.054f
        val triHalf = s * 0.027f
        val triApex = triBase - triLength
        val tri = Path().apply {
            moveTo(p(triApex, 0f).x, p(triApex, 0f).y)
            lineTo(p(triBase, -triHalf).x, p(triBase, -triHalf).y)
            lineTo(p(triBase, triHalf).x, p(triBase, triHalf).y)
            close()
        }
        drawPath(tri, Color(0xFFF1F1ED))
        drawLine(
            color = Color(0x8C141617),
            start = p(triApex + triLength * 0.12f, 0f),
            end = p(triBase - triLength * 0.10f, 0f),
            strokeWidth = s * 0.0012f,
        )
    }

    // SHORT wide needle: thousands of feet.
    rotate(degrees = thousandsAngle, pivot = c) {
        val tail = Path().apply {
            moveTo(p(-dialR * 0.26f, -s * 0.0044f).x, p(-dialR * 0.26f, -s * 0.0044f).y)
            lineTo(p(-s * 0.090f, -s * 0.0044f).x, p(-s * 0.090f, -s * 0.0044f).y)
            lineTo(p(-s * 0.020f, -s * 0.0105f).x, p(-s * 0.020f, -s * 0.0105f).y)
            lineTo(p(-s * 0.020f, s * 0.0105f).x, p(-s * 0.020f, s * 0.0105f).y)
            lineTo(p(-s * 0.090f, s * 0.0044f).x, p(-s * 0.090f, s * 0.0044f).y)
            lineTo(p(-dialR * 0.26f, s * 0.0044f).x, p(-dialR * 0.26f, s * 0.0044f).y)
            close()
        }
        drawPath(tail, Palette.needleDark)
        drawCircle(Palette.needleDark, radius = s * 0.0052f, center = p(-dialR * 0.26f, 0f))

        val pointer = Path().apply {
            moveTo(p(-s * 0.020f, -s * 0.014f).x, p(-s * 0.020f, -s * 0.014f).y)
            lineTo(p(dialR * 0.44f, -s * 0.010f).x, p(dialR * 0.44f, -s * 0.010f).y)
            lineTo(p(dialR * 0.54f, 0f).x, p(dialR * 0.54f, 0f).y)
            lineTo(p(dialR * 0.44f, s * 0.010f).x, p(dialR * 0.44f, s * 0.010f).y)
            lineTo(p(-s * 0.020f, s * 0.014f).x, p(-s * 0.020f, s * 0.014f).y)
            close()
        }
        drawPath(pointer, Color(0xFFF1F1ED))
    }

    // LONG needle: hundreds of feet.
    rotate(degrees = hundredsAngle, pivot = c) {
        val tail = Path().apply {
            moveTo(p(-dialR * 0.34f, -s * 0.0028f).x, p(-dialR * 0.34f, -s * 0.0028f).y)
            lineTo(p(-s * 0.110f, -s * 0.0028f).x, p(-s * 0.110f, -s * 0.0028f).y)
            lineTo(p(-s * 0.028f, -s * 0.0055f).x, p(-s * 0.028f, -s * 0.0055f).y)
            lineTo(p(-s * 0.028f, s * 0.0055f).x, p(-s * 0.028f, s * 0.0055f).y)
            lineTo(p(-s * 0.110f, s * 0.0028f).x, p(-s * 0.110f, s * 0.0028f).y)
            lineTo(p(-dialR * 0.34f, s * 0.0028f).x, p(-dialR * 0.34f, s * 0.0028f).y)
            close()
        }
        drawPath(tail, Palette.needleDark)
        drawCircle(Palette.needleDark, radius = s * 0.0038f, center = p(-dialR * 0.34f, 0f))

        val pointer = Path().apply {
            moveTo(p(-s * 0.028f, -s * 0.007f).x, p(-s * 0.028f, -s * 0.007f).y)
            lineTo(p(dialR * 0.74f, -s * 0.005f).x, p(dialR * 0.74f, -s * 0.005f).y)
            lineTo(p(dialR * 0.84f, 0f).x, p(dialR * 0.84f, 0f).y)
            lineTo(p(dialR * 0.74f, s * 0.005f).x, p(dialR * 0.74f, s * 0.005f).y)
            lineTo(p(-s * 0.028f, s * 0.007f).x, p(-s * 0.028f, s * 0.007f).y)
            close()
        }
        drawPath(pointer, Color(0xFFF1F1ED))
    }
}
