package com.ngg.instruments.ui.instruments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.sin

/**
 * Ground speed in knots from GNSS Doppler speed. 0–900 kt scale: 0 at the
 * lower-left, sweeping 300° clockwise, in the reference's cream ink.
 * With no valid speed the needle rests at zero; the digital chip on the
 * panel slot is what reports whether the value is live.
 */
@Composable
fun GroundSpeedIndicator(
    groundSpeedKt: State<Float>,
    available: State<Boolean>,
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
                drawSpeedDial(textMeasurer, dialR)
            }

            onDrawBehind {
                drawImage(background)

                // Always solid, as on the reference panel; with no valid
                // speed the needle simply rests at zero.
                val knots = if (available.value) groundSpeedKt.value.coerceIn(0f, 900f) else 0f
                drawSpeedNeedle(c, dialR, s, knots)
                drawSpeedHub(c, s)
                drawGlass(dialR)
            }
        },
    )
}

/** 0 kt at 135°, full scale 900 kt after a 300° clockwise sweep. */
private fun speedAngleDeg(knots: Float): Float = 135f + (knots.coerceIn(0f, 900f) / 900f) * 300f

private fun DrawScope.drawSpeedDial(textMeasurer: TextMeasurer, dialR: Float) {
    val s = instrumentSizePx()
    val c = center
    drawDialFace(dialR)

    val ink = Palette.inkCream

    for (v in 0..900 step 10) {
        val major = v % 100 == 0
        val mid = !major && v % 50 == 0
        val a = Math.toRadians(speedAngleDeg(v.toFloat()).toDouble())
        val outer = dialR * 0.91f
        val inner = dialR * if (major) 0.76f else if (mid) 0.79f else 0.83f
        drawLine(
            color = ink,
            start = Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
            end = Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
            strokeWidth = s * if (major) 0.0041f else if (mid) 0.0031f else 0.0022f,
        )
    }

    val numberStyle = TextStyle(fontFamily = BarlowCondensed, color = ink, fontSize = (s * 0.038f).toSp(), fontWeight = FontWeight.Bold)
    for (v in 0..900 step 100) {
        val a = Math.toRadians(speedAngleDeg(v.toFloat()).toDouble())
        val rr = dialR * 0.64f
        val layout = textMeasurer.measure(AnnotatedString(v.toString()), numberStyle)
        drawText(
            layout,
            topLeft = Offset(
                c.x + (cos(a) * rr).toFloat() - layout.size.width / 2f,
                c.y + (sin(a) * rr).toFloat() - layout.size.height / 2f,
            ),
        )
    }

    val wordStyle = TextStyle(fontFamily = BarlowCondensed, color = ink, fontSize = (s * 0.033f).toSp(), fontWeight = FontWeight.Bold)
    listOf("GROUND" to 0.075f, "SPEED" to 0.111f, "KNOTS" to 0.147f).forEach { (word, dy) ->
        val layout = textMeasurer.measure(AnnotatedString(word), wordStyle)
        drawText(
            layout,
            topLeft = Offset(c.x - layout.size.width / 2f, c.y + s * dy - layout.size.height / 2f),
        )
    }

    // Small face rivets from the reference.
    fun faceDot(dx: Float, dy: Float, r: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to Color(0xFF454848), 1f to Color(0xFF111313)),
                center = Offset(c.x + dx - r * 0.3f, c.y + dy - r * 0.3f),
                radius = r * 1.3f,
            ),
            radius = r,
            center = Offset(c.x + dx, c.y + dy),
        )
    }
    faceDot(0f, -dialR * 0.55f, s * 0.010f)
    faceDot(-dialR * 0.55f, dialR * 0.45f, s * 0.008f)
    faceDot(dialR * 0.52f, dialR * 0.46f, s * 0.008f)
}

private fun DrawScope.drawSpeedNeedle(c: Offset, dialR: Float, s: Float, knots: Float) {
    rotate(degrees = speedAngleDeg(knots), pivot = c) {
        // Black rear counterbalance.
        val tail = Path().apply {
            moveTo(c.x - dialR * 0.35f, c.y - s * 0.008f)
            lineTo(c.x - s * 0.060f, c.y - s * 0.013f)
            lineTo(c.x - s * 0.060f, c.y + s * 0.013f)
            lineTo(c.x - dialR * 0.35f, c.y + s * 0.008f)
            close()
        }
        drawPath(tail, Color(0xFF090B0C))

        // Cream front needle.
        val needle = Path().apply {
            moveTo(c.x - s * 0.018f, c.y - s * 0.010f)
            lineTo(c.x + dialR * 0.72f, c.y - s * 0.008f)
            lineTo(c.x + dialR * 0.88f, c.y)
            lineTo(c.x + dialR * 0.72f, c.y + s * 0.008f)
            lineTo(c.x - s * 0.018f, c.y + s * 0.010f)
            close()
        }
        drawPath(needle, Palette.needleCream)
    }
}

private fun DrawScope.drawSpeedHub(c: Offset, s: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF555958),
                0.48f to Color(0xFF26292A),
                1f to Color(0xFF0B0D0E),
            ),
            center = Offset(c.x - s * 0.008f, c.y - s * 0.008f),
            radius = s * 0.045f,
        ),
        radius = s * 0.035f,
        center = c,
    )
    drawCircle(color = Color(0xFF060707), radius = s * 0.035f, center = c, style = Stroke(width = s * 0.003f))
    drawCircle(color = Color(0xFF5B4630), radius = s * 0.008f, center = c)
}
