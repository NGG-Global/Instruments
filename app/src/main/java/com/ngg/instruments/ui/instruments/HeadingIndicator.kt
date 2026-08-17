package com.ngg.instruments.ui.instruments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
 * Direction indicator, drawn to the designed compass card: white outer band
 * with dark graduations, numerals every 30 degrees with a red N, a fixed
 * white outline aircraft at the center and a red lubber line at the top.
 *
 * The rotating card follows the selected source (GPS track by default, or
 * compass magnetic/true heading); the readouts are always labeled honestly —
 * TRK for GNSS course over ground, HDG for compass heading.
 */
private val CardBand = Color(0xFFE7E9E6)
private val CardInk = Color(0xFF101315)
private val CardRed = Color(0xFFE04440)

@Composable
fun HeadingIndicator(
    cardDeg: State<Float>,
    cardAvailable: State<Boolean>,
    primaryText: State<String>,
    secondaryText: State<String>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Spacer(
        modifier.drawWithCache {
            val s = min(size.width, size.height)
            val c = Offset(size.width / 2f, size.height / 2f)
            val dialR = s * Chrome.DIAL_RADIUS

            val background = renderStaticLayer {
                drawHousing(cornerFraction = 0.045f)
                drawBezel()
            }
            val card = renderStaticLayer {
                drawCompassCard(textMeasurer, dialR)
            }

            val primaryStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Color.White,
                fontSize = (s * 0.048f).toSp(),
                fontWeight = FontWeight.Bold,
            )
            val secondaryStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Palette.inkCream,
                fontSize = (s * 0.030f).toSp(),
                fontWeight = FontWeight.Bold,
            )

            onDrawBehind {
                drawImage(background)

                // Rotating card: value-up means the card turns opposite. The
                // card is always solid, as on the reference panel; when no
                // value is available it simply holds its last position and the
                // readout below shows dashes.
                rotate(degrees = -cardDeg.value, pivot = c) {
                    drawImage(card)
                }

                drawFixedAircraft(c, dialR, s)

                // Fixed red lubber line across the band, per the design.
                drawLine(
                    color = CardRed,
                    start = Offset(c.x, c.y - dialR * 0.985f),
                    end = Offset(c.x, c.y - dialR * 0.775f),
                    strokeWidth = s * 0.0075f,
                    cap = StrokeCap.Round,
                )

                // Digital readouts: primary in a box, secondary as small text.
                val primaryLayout = textMeasurer.measure(AnnotatedString(primaryText.value), primaryStyle)
                drawReadoutBox(c, s, primaryLayout.size.width.toFloat(), primaryLayout.size.height.toFloat(), yOffset = s * 0.135f)
                drawText(
                    primaryLayout,
                    topLeft = Offset(c.x - primaryLayout.size.width / 2f, c.y + s * 0.135f - primaryLayout.size.height / 2f),
                )
                val secondaryLayout = textMeasurer.measure(AnnotatedString(secondaryText.value), secondaryStyle)
                drawText(
                    secondaryLayout,
                    topLeft = Offset(c.x - secondaryLayout.size.width / 2f, c.y + s * 0.198f - secondaryLayout.size.height / 2f),
                )

                drawGlass(dialR)
            }
        },
    )
}

private fun DrawScope.drawReadoutBox(c: Offset, s: Float, textW: Float, textH: Float, yOffset: Float) {
    val w = textW + s * 0.03f
    val h = textH + s * 0.010f
    drawRoundRect(
        color = Color(0xEB080A0B),
        topLeft = Offset(c.x - w / 2f, c.y + yOffset - h / 2f),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.008f),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.08f),
        topLeft = Offset(c.x - w / 2f, c.y + yOffset - h / 2f),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.008f),
        style = Stroke(width = s * 0.002f),
    )
}

/** The designed rotating card: white graduation band, numerals, red N. */
private fun DrawScope.drawCompassCard(textMeasurer: TextMeasurer, dialR: Float) {
    val s = instrumentSizePx()
    val c = center

    drawDialFace(dialR)

    // White graduation band.
    val bandOuter = dialR * 0.87f
    val bandInner = dialR * 0.71f
    drawCircle(
        color = CardBand,
        radius = (bandOuter + bandInner) / 2f,
        center = c,
        style = Stroke(width = bandOuter - bandInner),
    )
    // Subtle dark rims on both band edges.
    drawCircle(Color(0xFF0B0D0F), radius = bandOuter, center = c, style = Stroke(width = s * 0.0022f))
    drawCircle(Color(0xFF0B0D0F), radius = bandInner, center = c, style = Stroke(width = s * 0.0022f))

    // Dark graduations on the band: every 5 degrees, heavier every 30.
    for (deg in 0 until 360 step 5) {
        val major = deg % 30 == 0
        val a = Math.toRadians(deg.toDouble() - 90.0)
        val inner = dialR * 0.72f
        val outer = dialR * if (major) 0.86f else 0.83f
        drawLine(
            color = CardInk,
            start = Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
            end = Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
            strokeWidth = s * if (major) 0.0056f else 0.0030f,
        )
    }

    // Numerals every 30 degrees inside the band; red N, larger cardinals.
    val cardinalStyle = TextStyle(
        fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.058f).toSp(),
        fontWeight = FontWeight.SemiBold,
    )
    val northStyle = cardinalStyle.copy(color = CardRed)
    val numberStyle = TextStyle(
        fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.047f).toSp(),
        fontWeight = FontWeight.Medium,
    )
    val labels = listOf(
        0 to "N", 30 to "3", 60 to "6", 90 to "E", 120 to "12", 150 to "15",
        180 to "S", 210 to "21", 240 to "24", 270 to "W", 300 to "30", 330 to "33",
    )
    drawCardMarkers(c, dialR, s)

    for ((deg, text) in labels) {
        val style = when {
            text == "N" -> northStyle
            text.length == 1 -> cardinalStyle
            else -> numberStyle
        }
        val layout = textMeasurer.measure(AnnotatedString(text), style)
        rotate(degrees = deg.toFloat(), pivot = c) {
            drawText(
                layout,
                topLeft = Offset(
                    c.x - layout.size.width / 2f,
                    c.y - dialR * 0.605f - layout.size.height / 2f,
                ),
            )
        }
    }
}

/** Eight white index triangles on the rotating card, pointing inward. */
private fun DrawScope.drawCardMarkers(c: Offset, dialR: Float, s: Float) {
    for (i in 0 until 8) {
        val deg = i * 45f
        val a = Math.toRadians(deg.toDouble() - 90.0)
        val rr = dialR * 0.96f
        val tx = c.x + (cos(a) * rr).toFloat()
        val ty = c.y + (sin(a) * rr).toFloat()
        rotate(degrees = deg, pivot = Offset(tx, ty)) {
            val tri = Path().apply {
                moveTo(tx, ty + s * 0.014f)          // apex toward the centre
                lineTo(tx - s * 0.011f, ty - s * 0.006f)
                lineTo(tx + s * 0.011f, ty - s * 0.006f)
                close()
            }
            drawPath(tri, Color(0xFFF2F4F5))
        }
    }
}

/** Fixed white outline aircraft at the card center, nose to the lubber line. */
private fun DrawScope.drawFixedAircraft(c: Offset, dialR: Float, s: Float) {
    fun p(x: Float, y: Float) = Offset(c.x + x * dialR, c.y + y * dialR)
    val plane = Path().apply {
        moveTo(p(0f, -0.30f).x, p(0f, -0.30f).y)          // nose
        lineTo(p(0.035f, -0.21f).x, p(0.035f, -0.21f).y)
        lineTo(p(0.045f, -0.10f).x, p(0.045f, -0.10f).y)
        lineTo(p(0.30f, -0.005f).x, p(0.30f, -0.005f).y)  // right wing tip
        lineTo(p(0.30f, 0.05f).x, p(0.30f, 0.05f).y)
        lineTo(p(0.055f, 0.02f).x, p(0.055f, 0.02f).y)
        lineTo(p(0.045f, 0.14f).x, p(0.045f, 0.14f).y)
        lineTo(p(0.14f, 0.21f).x, p(0.14f, 0.21f).y)      // right tail tip
        lineTo(p(0.14f, 0.25f).x, p(0.14f, 0.25f).y)
        lineTo(p(0f, 0.225f).x, p(0f, 0.225f).y)          // tail center
        lineTo(p(-0.14f, 0.25f).x, p(-0.14f, 0.25f).y)
        lineTo(p(-0.14f, 0.21f).x, p(-0.14f, 0.21f).y)
        lineTo(p(-0.045f, 0.14f).x, p(-0.045f, 0.14f).y)
        lineTo(p(-0.055f, 0.02f).x, p(-0.055f, 0.02f).y)
        lineTo(p(-0.30f, 0.05f).x, p(-0.30f, 0.05f).y)
        lineTo(p(-0.30f, -0.005f).x, p(-0.30f, -0.005f).y)
        lineTo(p(-0.045f, -0.10f).x, p(-0.045f, -0.10f).y)
        lineTo(p(-0.035f, -0.21f).x, p(-0.035f, -0.21f).y)
        close()
    }
    drawPath(
        plane,
        color = Color(0xFFF2F4F5),
        style = Stroke(
            width = s * 0.005f,
            join = StrokeJoin.Round,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.cornerPathEffect(s * 0.008f),
        ),
    )
}
