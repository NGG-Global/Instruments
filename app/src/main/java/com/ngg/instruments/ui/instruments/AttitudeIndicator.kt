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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.ngg.instruments.ui.rememberDeadbandInt
import com.ngg.instruments.ui.rememberDisplayValue
import com.ngg.instruments.ui.theme.BarlowCondensed
import com.ngg.instruments.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Attitude indicator. Conventional behaviour: the aircraft symbol is fixed;
 * pitch up moves the horizon (and its ladder) down; roll rotates the horizon.
 *
 * The horizon is a geometric transform of gradient shapes — no per-pixel
 * texture is generated, unlike the reference implementation.
 */
@Composable
fun AttitudeIndicator(
    pitchDeg: State<Float>,
    rollDeg: State<Float>,
    available: State<Boolean>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    // Numeric pitch readout pipeline. The incoming pitchDeg is eased at
    // tau = 60 ms for needle motion; a rounded integer needs a slower pass
    // (tau = 300 ms) plus a 0.6° deadband so it does not flicker at rounding
    // boundaries. Quantisation happens in the frame-callback layer, never in
    // the draw phase.
    val readoutPitch = rememberDisplayValue(tauMillis = 300f) { pitchDeg.value }
    val readoutPitchInt = rememberDeadbandInt(readoutPitch, deadband = 0.6f)

    Spacer(
        modifier.drawWithCache {
            val s = min(size.width, size.height)
            val c = Offset(size.width / 2f, size.height / 2f)
            val ballR = s * 0.300f

            val background = renderStaticLayer {
                drawHousing(cornerFraction = 0.075f)
                drawAttitudeBezel(ballR)
            }

            val ladderStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Palette.inkWhite,
                fontSize = (s * 0.032f).toSp(),
                fontWeight = FontWeight.Bold,
            )
            val ladder10 = textMeasurer.measure(AnnotatedString("10"), ladderStyle)
            val ladder20 = textMeasurer.measure(AnnotatedString("20"), ladderStyle)
            val flagLayout = textMeasurer.measure(
                AnnotatedString("ATT"),
                TextStyle(
                fontFamily = BarlowCondensed,
                    color = Color(0xFF14161A),
                    fontSize = (s * 0.045f).toSp(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (s * 0.008f).toSp(),
                ),
            )

            val clipCircle = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(c.x - ballR, c.y - ballR, c.x + ballR, c.y + ballR),
                )
            }

            // Pitch readout box, sized against the widest realistic string so
            // the value never clips.
            val pitchReadoutStyle = TextStyle(
                fontFamily = BarlowCondensed,
                color = Palette.inkWhite,
                fontSize = (s * 0.060f).toSp(),
                fontWeight = FontWeight.Bold,
            )
            val widestPitch = textMeasurer.measure(AnnotatedString("-90°"), pitchReadoutStyle)
            val pitchBoxW = max(s * 0.130f, widestPitch.size.width + s * 0.020f)
            val pitchBoxH = s * 0.050f
            val pitchBoxTop = c.y + s * 0.352f

            onDrawBehind {
                drawImage(background)

                val pitch = pitchDeg.value
                val roll = rollDeg.value
                val isAvailable = available.value

                drawHorizonBall(c, ballR, s, pitch, roll, clipCircle, ladder10, ladder20)
                drawBankScale(c, ballR, s)
                drawBankMarkers(c, ballR, s)
                drawAircraftSymbol(c, s)
                drawBottomMarkers(c, s)

                // Numeric pitch readout: on the housing below the ball
                // (outside ballR, unaffected by the glass), hidden entirely
                // when attitude is unavailable — the ATT flag covers that.
                if (isAvailable) {
                    drawPitchReadout(
                        c, s, pitchBoxW, pitchBoxH, pitchBoxTop,
                        textMeasurer, pitchReadoutStyle, readoutPitchInt.value,
                    )
                }

                drawGlassOverBall(c, ballR)

                if (!isAvailable) {
                    drawFlag(c, s, flagLayout)
                }
            }
        },
    )
}

private fun DrawScope.drawAttitudeBezel(ballR: Float) {
    val s = instrumentSizePx()
    val c = center
    val recessOuter = s * 0.365f * 1.09f
    val recessInner = s * 0.365f * 0.88f

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF0F1113),
                0.72f to Color(0xFF090B0C),
                1f to Color(0xFF010202),
            ),
            center = c,
            radius = recessOuter,
        ),
        radius = recessOuter,
        center = c,
    )
    drawCircle(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(0xFF3D4246),
                0.18f to Color(0xFF2A2E32),
                0.5f to Color(0xFF171A1D),
                0.82f to Color(0xFF24282B),
                1f to Color(0xFF44494E),
            ),
            start = Offset(c.x - recessOuter, c.y - recessOuter),
            end = Offset(c.x + recessOuter, c.y + recessOuter),
        ),
        radius = (recessOuter + recessInner) / 2f,
        center = c,
        style = Stroke(width = recessOuter - recessInner),
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.78f),
        radius = recessInner,
        center = c,
        style = Stroke(width = s * 0.009f),
    )

    // Gimbal ring and trunnions.
    val ringOuter = s * 0.332f
    val ringInner = s * 0.312f
    drawCircle(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(0xFF363A3E),
                0.2f to Color(0xFF23272A),
                0.5f to Color(0xFF15181A),
                0.8f to Color(0xFF25292C),
                1f to Color(0xFF3D4246),
            ),
            start = Offset(c.x - ringOuter, c.y - ringOuter),
            end = Offset(c.x + ringOuter, c.y + ringOuter),
        ),
        radius = (ringOuter + ringInner) / 2f,
        center = c,
        style = Stroke(width = ringOuter - ringInner),
    )
    drawTrunnion(Offset(c.x - ringOuter, c.y), s * 0.025f)
    drawTrunnion(Offset(c.x + ringOuter, c.y), s * 0.025f)

    // Static rim around the ball aperture.
    drawCircle(
        color = Color(0xFF020304),
        radius = ballR,
        center = c,
        style = Stroke(width = s * 0.012f),
    )
}

private fun DrawScope.drawTrunnion(pos: Offset, r: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF6C747A),
                0.42f to Color(0xFF3C4348),
                1f to Color(0xFF161B1F),
            ),
            center = Offset(pos.x - r * 0.25f, pos.y - r * 0.25f),
            radius = r * 1.4f,
        ),
        radius = r,
        center = pos,
    )
    drawCircle(
        color = Color(0xFF0B0E10),
        radius = r,
        center = pos,
        style = Stroke(width = r * 0.18f),
    )
}

private fun DrawScope.drawHorizonBall(
    c: Offset,
    ballR: Float,
    s: Float,
    pitchDeg: Float,
    rollDeg: Float,
    clipCircle: Path,
    ladder10: TextLayoutResult,
    ladder20: TextLayoutResult,
) {
    val pxPerDeg = ballR / 22f

    clipPath(clipCircle) {
        // Right bank rotates the horizon counterclockwise on screen.
        rotate(degrees = -rollDeg, pivot = c) {
            val horizonY = c.y + pitchDeg * pxPerDeg
            val extent = ballR * 3f

            // Sky above the horizon, ground below; both move with pitch.
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Palette.skyTop,
                        0.6f to Palette.skyMid,
                        1f to Palette.skyHorizon,
                    ),
                    startY = horizonY - extent,
                    endY = horizonY,
                ),
                topLeft = Offset(c.x - extent, horizonY - extent),
                size = androidx.compose.ui.geometry.Size(extent * 2f, extent),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Palette.groundHorizon,
                        0.45f to Palette.groundMid,
                        1f to Palette.groundBottom,
                    ),
                    startY = horizonY,
                    endY = horizonY + extent,
                ),
                topLeft = Offset(c.x - extent, horizonY),
                size = androidx.compose.ui.geometry.Size(extent * 2f, extent),
            )
            // Orange horizon line.
            drawLine(
                color = Palette.referenceOrange,
                start = Offset(c.x - extent, horizonY),
                end = Offset(c.x + extent, horizonY),
                strokeWidth = s * 0.006f,
            )

            // Pitch ladder: moves with the horizon.
            for (deg in intArrayOf(-20, -15, -10, -5, 5, 10, 15, 20)) {
                val major = deg % 10 == 0
                val y = horizonY - deg * pxPerDeg
                val half = ballR * if (major) 0.30f else 0.17f
                drawLine(
                    color = Palette.inkWhite,
                    start = Offset(c.x - half, y),
                    end = Offset(c.x + half, y),
                    strokeWidth = s * if (major) 0.004f else 0.0028f,
                )
                if (major) {
                    val layout = if (deg == 10 || deg == -10) ladder10 else ladder20
                    val pad = s * 0.014f
                    drawText(
                        layout,
                        topLeft = Offset(c.x - half - pad - layout.size.width, y - layout.size.height / 2f),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(c.x + half + pad, y - layout.size.height / 2f),
                    )
                }
            }
        }

        // Spherical shading + specular highlight (static gradients over the ball).
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.42f),
                ),
                center = c,
                radius = ballR,
            ),
            radius = ballR,
            center = c,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                ),
                center = Offset(c.x - ballR * 0.35f, c.y - ballR * 0.45f),
                radius = ballR * 0.8f,
            ),
            radius = ballR,
            center = c,
        )
    }
}

private fun DrawScope.drawBankScale(c: Offset, ballR: Float, s: Float) {
    val r = ballR * 1.02f

    // Fixed white bank ticks across the top arc.
    for (deg in intArrayOf(-60, -45, -30, -20, -10, 10, 20, 30, 45, 60)) {
        val major = deg == 30 || deg == -30 || deg == 60 || deg == -60
        val a = Math.toRadians(deg.toDouble() - 90.0)
        val inner = r * if (major) 0.84f else 0.88f
        val outer = r * 0.98f
        drawLine(
            color = Palette.inkWhite,
            start = Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
            end = Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
            strokeWidth = s * 0.0042f,
        )
    }

    // Fixed top reference triangle.
    val topTri = Path().apply {
        moveTo(c.x, c.y - r * 1.03f)
        lineTo(c.x - s * 0.020f, c.y - r * 0.90f)
        lineTo(c.x + s * 0.020f, c.y - r * 0.90f)
        close()
    }
    drawPath(topTri, Color.White)

    // Inner orange cue triangle — fixed on the reference panel, not rolling.
    val cue = Path().apply {
        moveTo(c.x, c.y - r * 0.84f)
        lineTo(c.x - s * 0.020f, c.y - r * 0.78f)
        lineTo(c.x + s * 0.020f, c.y - r * 0.78f)
        close()
    }
    drawPath(cue, Palette.referenceOrange, style = Stroke(width = s * 0.006f))
}

/** Four white triangular bank markers at ±20° and ±45°, as on the reference. */
private fun DrawScope.drawBankMarkers(c: Offset, ballR: Float, s: Float) {
    val r = ballR * 1.02f
    for (deg in intArrayOf(-45, -20, 20, 45)) {
        val a = Math.toRadians(deg.toDouble())
        val rr = r * 0.93f
        val tx = c.x + (sin(a) * rr).toFloat()
        val ty = c.y - (cos(a) * rr).toFloat()
        rotate(degrees = deg.toFloat(), pivot = Offset(tx, ty)) {
            val tri = Path().apply {
                moveTo(tx, ty - s * 0.010f)
                lineTo(tx - s * 0.012f, ty + s * 0.010f)
                lineTo(tx + s * 0.012f, ty + s * 0.010f)
                close()
            }
            drawPath(tri, Color(0xFFF3F5F6))
        }
    }
}

/** White index marks along the bottom of the case, from the reference. */
private fun DrawScope.drawBottomMarkers(c: Offset, s: Float) {
    val yy = c.y + s * 0.3344f       // bodyY + bodyH * 0.88
    val bw = s * 0.035f
    val bh = s * 0.012f
    val left = Path().apply {
        moveTo(c.x - bw * 2.6f, yy)
        lineTo(c.x - bw * 1.7f, yy - bh)
        lineTo(c.x - bw * 0.8f, yy)
        close()
    }
    drawPath(left, Color.White)
    drawRect(
        color = Color.White,
        topLeft = Offset(c.x - bw * 0.35f, yy - bh * 1.8f),
        size = androidx.compose.ui.geometry.Size(bw * 0.7f, bh * 2.2f),
    )
    val right = Path().apply {
        moveTo(c.x + bw * 0.8f, yy)
        lineTo(c.x + bw * 1.7f, yy - bh)
        lineTo(c.x + bw * 2.6f, yy)
        close()
    }
    drawPath(right, Color.White)
}

private fun DrawScope.drawAircraftSymbol(c: Offset, s: Float) {
    // Fixed orange horizon reference bars.
    drawLine(
        color = Palette.referenceOrange,
        start = Offset(c.x - s * 0.17f, c.y),
        end = Offset(c.x - s * 0.042f, c.y),
        strokeWidth = s * 0.010f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Palette.referenceOrange,
        start = Offset(c.x + s * 0.042f, c.y),
        end = Offset(c.x + s * 0.17f, c.y),
        strokeWidth = s * 0.010f,
        cap = StrokeCap.Round,
    )

    // Dark center miniature aircraft.
    val dark = Color(0xFF1A1C1E)
    val w = s * 0.007f
    drawLine(dark, Offset(c.x - s * 0.030f, c.y), Offset(c.x - s * 0.012f, c.y), w, StrokeCap.Round)
    drawLine(dark, Offset(c.x - s * 0.012f, c.y), Offset(c.x, c.y + s * 0.010f), w, StrokeCap.Round)
    drawLine(dark, Offset(c.x, c.y + s * 0.010f), Offset(c.x + s * 0.012f, c.y), w, StrokeCap.Round)
    drawLine(dark, Offset(c.x + s * 0.012f, c.y), Offset(c.x + s * 0.030f, c.y), w, StrokeCap.Round)
    drawLine(dark, Offset(c.x, c.y - s * 0.008f), Offset(c.x, c.y + s * 0.040f), w, StrokeCap.Round)
}

private fun DrawScope.drawGlassOverBall(c: Offset, ballR: Float) {
    drawCircle(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(0xFFA0E1EC).copy(alpha = 0.14f),
                0.16f to Color.White.copy(alpha = 0.06f),
                0.45f to Color.Transparent,
                1f to Color.Transparent,
            ),
            start = Offset(c.x - ballR * 0.85f, c.y - ballR * 0.85f),
            end = Offset(c.x + ballR * 0.55f, c.y + ballR * 0.55f),
        ),
        radius = ballR,
        center = c,
    )
}

private fun DrawScope.drawPitchReadout(
    c: Offset,
    s: Float,
    boxW: Float,
    boxH: Float,
    boxTop: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    pitchInt: Int,
) {
    val topLeft = Offset(c.x - boxW / 2f, boxTop)
    val boxSize = androidx.compose.ui.geometry.Size(boxW, boxH)
    val corner = androidx.compose.ui.geometry.CornerRadius(s * 0.008f)

    drawRoundRect(
        color = Color(0xFF080A0B).copy(alpha = 0.92f),
        topLeft = topLeft,
        size = boxSize,
        cornerRadius = corner,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.10f),
        topLeft = topLeft,
        size = boxSize,
        cornerRadius = corner,
        style = Stroke(width = s * 0.0018f),
    )

    val layout = textMeasurer.measure(AnnotatedString("%+d°".format(pitchInt)), style)
    drawText(
        layout,
        topLeft = Offset(
            c.x - layout.size.width / 2f,
            boxTop + boxH / 2f - layout.size.height / 2f,
        ),
    )
}

private fun DrawScope.drawFlag(c: Offset, s: Float, flagLayout: TextLayoutResult) {
    val w = flagLayout.size.width + s * 0.03f
    val h = flagLayout.size.height + s * 0.012f
    val topLeft = Offset(c.x - w / 2f, c.y - s * 0.20f - h / 2f)
    drawRect(color = Color(0xFFE0A83C), topLeft = topLeft, size = androidx.compose.ui.geometry.Size(w, h))
    drawText(flagLayout, topLeft = Offset(c.x - flagLayout.size.width / 2f, topLeft.y + s * 0.006f))
}
