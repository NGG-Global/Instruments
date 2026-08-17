package com.ngg.instruments.ui.instruments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import com.ngg.instruments.ui.theme.BarlowCondensed
import com.ngg.instruments.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Heading indicator (directional gyro style). The rotating compass card is
 * rendered once into a bitmap and rotated per frame — an image rotation, not a
 * re-draw. HDG is the aircraft nose direction; GNSS track (TRK) is displayed
 * only as a clearly labeled secondary value and never as heading.
 */
@Composable
fun HeadingIndicator(
    headingDeg: State<Float>,
    headingAvailable: State<Boolean>,
    trackDeg: State<Float?>,
    isTrueHeading: State<Boolean>,
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

            val readoutStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Color.White,
                fontSize = (s * 0.052f).toSp(),
                fontWeight = FontWeight.Bold,
            )
            val subStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Palette.inkCream,
                fontSize = (s * 0.032f).toSp(),
                fontWeight = FontWeight.Bold,
            )

            onDrawBehind {
                drawImage(background)

                val available = headingAvailable.value
                val heading = headingDeg.value

                // Rotating card: heading up means the card turns opposite.
                rotate(degrees = -heading, pivot = c) {
                    drawImage(card)
                }

                drawFixedAircraft(c, dialR)

                // Fixed lubber triangle at the top.
                val lubber = Path().apply {
                    moveTo(c.x, c.y - dialR * 0.98f)
                    lineTo(c.x - s * 0.014f, c.y - dialR * 0.86f)
                    lineTo(c.x + s * 0.014f, c.y - dialR * 0.86f)
                    close()
                }
                drawPath(lubber, Color(0xFFF4F5F3))

                // Digital HDG readout.
                val suffix = if (isTrueHeading.value) "T" else "M"
                val hdgText = if (available) {
                    "HDG ${heading.roundToInt().mod(360).toString().padStart(3, '0')}°$suffix"
                } else {
                    "HDG ---"
                }
                val hdgLayout = textMeasurer.measure(AnnotatedString(hdgText), readoutStyle)
                drawReadoutBox(c, s, hdgLayout.size.width.toFloat(), hdgLayout.size.height.toFloat(), yOffset = s * 0.075f)
                drawText(
                    hdgLayout,
                    topLeft = Offset(c.x - hdgLayout.size.width / 2f, c.y + s * 0.075f - hdgLayout.size.height / 2f),
                )

                // Secondary GNSS track (never called heading).
                val track = trackDeg.value
                val trkText = if (track != null) {
                    "TRK ${track.roundToInt().mod(360).toString().padStart(3, '0')}°"
                } else {
                    "TRK ---"
                }
                val trkLayout = textMeasurer.measure(AnnotatedString(trkText), subStyle)
                drawText(
                    trkLayout,
                    topLeft = Offset(c.x - trkLayout.size.width / 2f, c.y + s * 0.155f - trkLayout.size.height / 2f),
                )

                drawGlass(dialR)
            }
        },
    )
}

private fun DrawScope.drawReadoutBox(c: Offset, s: Float, textW: Float, textH: Float, yOffset: Float) {
    val w = textW + s * 0.03f
    val h = textH + s * 0.012f
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
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.002f),
    )
}

private fun DrawScope.drawCompassCard(textMeasurer: TextMeasurer, dialR: Float) {
    val s = instrumentSizePx()
    val c = center

    drawDialFace(dialR)

    val labelStyle = TextStyle(
                fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.052f).toSp(),
        fontWeight = FontWeight.Bold,
    )
    val cardinalStyle = TextStyle(
                fontFamily = BarlowCondensed,
        color = Palette.inkWhite,
        fontSize = (s * 0.062f).toSp(),
        fontWeight = FontWeight.Bold,
    )

    // Tick marks every 5 degrees; longer every 10.
    for (deg in 0 until 360 step 5) {
        val major = deg % 10 == 0
        val a = Math.toRadians(deg.toDouble() - 90.0)
        val outer = dialR * 0.97f
        val inner = dialR * if (major) 0.86f else 0.905f
        drawLine(
            color = Palette.inkWhite,
            start = Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
            end = Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
            strokeWidth = s * if (major) 0.0042f else 0.0024f,
        )
    }

    // Labels every 30 degrees, rotated so their tops face outward.
    val labels = listOf(
        0 to "N", 30 to "3", 60 to "6", 90 to "E", 120 to "12", 150 to "15",
        180 to "S", 210 to "21", 240 to "24", 270 to "W", 300 to "30", 330 to "33",
    )
    for ((deg, text) in labels) {
        val cardinal = text == "N" || text == "E" || text == "S" || text == "W"
        val style = if (cardinal) cardinalStyle else labelStyle
        val layout = textMeasurer.measure(AnnotatedString(text), style)
        rotate(degrees = deg.toFloat(), pivot = c) {
            drawText(
                layout,
                topLeft = Offset(
                    c.x - layout.size.width / 2f,
                    c.y - dialR * 0.80f - layout.size.height / 2f,
                ),
            )
        }
    }

}

/** Fixed miniature aircraft over the rotating card, nose to the lubber line. */
private fun DrawScope.drawFixedAircraft(c: Offset, dialR: Float) {
    val plane = Path().apply {
        moveTo(c.x, c.y - dialR * 0.16f)
        lineTo(c.x + dialR * 0.035f, c.y - dialR * 0.02f)
        lineTo(c.x + dialR * 0.14f, c.y + dialR * 0.03f)
        lineTo(c.x + dialR * 0.14f, c.y + dialR * 0.06f)
        lineTo(c.x + dialR * 0.03f, c.y + dialR * 0.045f)
        lineTo(c.x + dialR * 0.025f, c.y + dialR * 0.12f)
        lineTo(c.x + dialR * 0.06f, c.y + dialR * 0.15f)
        lineTo(c.x + dialR * 0.06f, c.y + dialR * 0.17f)
        lineTo(c.x, c.y + dialR * 0.155f)
        lineTo(c.x - dialR * 0.06f, c.y + dialR * 0.17f)
        lineTo(c.x - dialR * 0.06f, c.y + dialR * 0.15f)
        lineTo(c.x - dialR * 0.025f, c.y + dialR * 0.12f)
        lineTo(c.x - dialR * 0.03f, c.y + dialR * 0.045f)
        lineTo(c.x - dialR * 0.14f, c.y + dialR * 0.06f)
        lineTo(c.x - dialR * 0.14f, c.y + dialR * 0.03f)
        lineTo(c.x - dialR * 0.035f, c.y - dialR * 0.02f)
        close()
    }
    drawPath(plane, Color.White.copy(alpha = 0.20f))
}
