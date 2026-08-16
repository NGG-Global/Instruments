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
 * Three-pointer altimeter in feet:
 * long needle = hundreds (1 turn / 1,000 ft), short wide needle = thousands
 * (1 turn / 10,000 ft), thin needle with outer triangle = ten-thousands
 * (1 turn / 100,000 ft). Dial numerals 0..9. Digital inset like the reference.
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
                fontSize = (s * 0.042f).toSp(),
                fontWeight = FontWeight.Bold,
            )
            val qnhStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Palette.inkCream,
                fontSize = (s * 0.028f).toSp(),
                fontWeight = FontWeight.Bold,
            )

            onDrawBehind {
                drawImage(background)

                val isAvailable = available.value
                val feet = altitudeFt.value

                // Digital readout drawn first so needles pass over it.
                val text = if (isAvailable) "${feet.roundToInt()} ft" else "---- ft"
                val layout = textMeasurer.measure(AnnotatedString(text), readoutStyle)
                val boxW = s * 0.20f
                val boxH = layout.size.height + s * 0.012f
                drawRoundRect(
                    color = Color(0xEB080A0B),
                    topLeft = Offset(c.x - boxW / 2f, c.y + s * 0.09f - boxH / 2f),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(s * 0.007f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(c.x - boxW / 2f, c.y + s * 0.09f - boxH / 2f),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(s * 0.007f),
                    style = Stroke(width = s * 0.002f),
                )
                drawText(
                    layout,
                    topLeft = Offset(c.x - layout.size.width / 2f, c.y + s * 0.09f - layout.size.height / 2f),
                )

                // QNH annunciation (kept small; adjusted via settings/tap).
                val qnhLayout = textMeasurer.measure(
                    AnnotatedString("QNH ${qnhHpa.value.roundToInt()}"),
                    qnhStyle,
                )
                drawText(
                    qnhLayout,
                    topLeft = Offset(c.x - qnhLayout.size.width / 2f, c.y + dialR * 0.62f - qnhLayout.size.height / 2f),
                )

                if (isAvailable) {
                    drawAltimeterNeedles(c, dialR, s, feet)
                }
                drawHub(radiusFraction = 0.033f)
                drawGlass(dialR)
            }
        },
    )
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
        fontSize = (s * 0.062f).toSp(),
    )
    for (i in 0..9) {
        val a = Math.toRadians(i / 10.0 * 360.0 - 90.0)
        val rr = dialR * 0.60f
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
        TextStyle(fontFamily = BarlowCondensed, color = Palette.inkWhite, fontSize = (s * 0.028f).toSp()),
    )
    drawText(
        feetLayout,
        topLeft = Offset(c.x + s * 0.085f - feetLayout.size.width / 2f, c.y - dialR * 0.80f - feetLayout.size.height / 2f),
    )

    val annotationStyle = TextStyle(
                fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.014f).toSp(),
        fontWeight = FontWeight.Bold,
    )
    val cal1 = textMeasurer.measure(AnnotatedString("CALIBRATED"), annotationStyle)
    drawText(cal1, topLeft = Offset(c.x - dialR * 0.36f, c.y - dialR * 0.22f))
    val cal2 = textMeasurer.measure(AnnotatedString("FT -500 TO 20,000"), annotationStyle)
    drawText(cal2, topLeft = Offset(c.x - dialR * 0.36f, c.y - dialR * 0.14f))
}

private fun DrawScope.drawAltimeterNeedles(c: Offset, dialR: Float, s: Float, feet: Float) {
    fun wrapFraction(value: Float, period: Float): Float {
        var f = (value % period) / period
        if (f < 0f) f += 1f
        return f
    }

    val hundredsAngle = wrapFraction(feet, 1_000f) * 360f
    val thousandsAngle = wrapFraction(feet, 10_000f) * 360f
    val tenThousandsAngle = wrapFraction(feet, 100_000f) * 360f

    // SHORT wide needle: thousands of feet.
    rotate(degrees = thousandsAngle, pivot = c) {
        val tail = Path().apply {
            moveTo(c.x - s * 0.0044f, c.y + dialR * 0.26f)
            lineTo(c.x - s * 0.0105f, c.y + s * 0.020f)
            lineTo(c.x + s * 0.0105f, c.y + s * 0.020f)
            lineTo(c.x + s * 0.0044f, c.y + dialR * 0.26f)
            close()
        }
        drawPath(tail, Palette.needleDark)
        val pointer = Path().apply {
            moveTo(c.x - s * 0.014f, c.y + s * 0.020f)
            lineTo(c.x - s * 0.010f, c.y - dialR * 0.44f)
            lineTo(c.x, c.y - dialR * 0.54f)
            lineTo(c.x + s * 0.010f, c.y - dialR * 0.44f)
            lineTo(c.x + s * 0.014f, c.y + s * 0.020f)
            close()
        }
        drawPath(pointer, Color(0xFFF1F1ED))
    }

    // LONG needle: hundreds of feet.
    rotate(degrees = hundredsAngle, pivot = c) {
        val tail = Path().apply {
            moveTo(c.x - s * 0.0028f, c.y + dialR * 0.34f)
            lineTo(c.x - s * 0.0055f, c.y + s * 0.028f)
            lineTo(c.x + s * 0.0055f, c.y + s * 0.028f)
            lineTo(c.x + s * 0.0028f, c.y + dialR * 0.34f)
            close()
        }
        drawPath(tail, Palette.needleDark)
        val pointer = Path().apply {
            moveTo(c.x - s * 0.007f, c.y + s * 0.028f)
            lineTo(c.x - s * 0.005f, c.y - dialR * 0.74f)
            lineTo(c.x, c.y - dialR * 0.84f)
            lineTo(c.x + s * 0.005f, c.y - dialR * 0.74f)
            lineTo(c.x + s * 0.007f, c.y + s * 0.028f)
            close()
        }
        drawPath(pointer, Color(0xFFF1F1ED))
    }

    // THIN needle with inward triangle: tens of thousands of feet.
    rotate(degrees = tenThousandsAngle, pivot = c) {
        drawLine(
            color = Color.White,
            start = Offset(c.x, c.y - s * 0.004f),
            end = Offset(c.x, c.y - dialR * 0.72f),
            strokeWidth = s * 0.006f,
        )
        val tri = Path().apply {
            moveTo(c.x, c.y - dialR * 0.86f + s * 0.054f)
            lineTo(c.x - s * 0.027f, c.y - dialR * 0.86f)
            lineTo(c.x + s * 0.027f, c.y - dialR * 0.86f)
            close()
        }
        drawPath(tri, Color(0xFFF1F1ED))
    }
}
