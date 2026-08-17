package com.ngg.instruments.ui.instruments

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.ngg.instruments.ui.theme.Palette
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared "hardware" rendering: housing, screws, bezel, dial face, glass.
 *
 * Unlike the reference HTML (which repainted thousands of noise primitives and
 * a per-pixel texture every frame), all of this is static and is rendered once
 * per size into a cached bitmap; per-frame work is only needles/cards.
 */

/** Renders [block] once into an offscreen bitmap sized like this draw scope. */
fun CacheDrawScope.renderStaticLayer(block: DrawScope.() -> Unit): ImageBitmap {
    val bitmap = ImageBitmap(
        size.width.roundToInt().coerceAtLeast(1),
        size.height.roundToInt().coerceAtLeast(1),
    )
    val canvas = Canvas(bitmap)
    CanvasDrawScope().draw(this, layoutDirection, canvas, size) { block() }
    return bitmap
}

object Chrome {
    const val BEZEL_OUTER = 0.355f
    const val BEZEL_INNER = 0.305f
    const val DIAL_RADIUS = 0.287f // BEZEL_INNER * 0.94
}

fun DrawScope.instrumentSizePx(): Float = min(size.width, size.height)

fun DrawScope.drawHousing(cornerFraction: Float = 0.05f) {
    val s = instrumentSizePx()
    val c = center
    val topLeft = Offset(c.x - s * 0.43f, c.y - s * 0.43f)
    val rectSize = Size(s * 0.86f, s * 0.86f)
    val corner = CornerRadius(s * cornerFraction)

    drawRoundRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Palette.housingTop,
                0.25f to Palette.housingUpper,
                0.62f to Palette.housingLower,
                1f to Palette.housingBottom,
            ),
            startY = topLeft.y,
            endY = topLeft.y + rectSize.height,
        ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = corner,
    )
    // Satin sheen sweeping across the paint.
    drawRoundRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.055f),
                0.25f to Color.Transparent,
                0.8f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.10f),
            ),
            start = topLeft,
            end = Offset(topLeft.x + rectSize.width, topLeft.y + rectSize.height),
        ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = corner,
    )
    drawRoundRect(
        color = Palette.housingOutline,
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = corner,
        style = Stroke(width = s * 0.009f),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.04f),
        topLeft = Offset(topLeft.x + s * 0.006f, topLeft.y + s * 0.006f),
        size = Size(rectSize.width - s * 0.012f, rectSize.height - s * 0.012f),
        cornerRadius = CornerRadius(s * (cornerFraction - 0.006f).coerceAtLeast(0.01f)),
        style = Stroke(width = s * 0.002f),
    )

    // Corner screws with individually rotated slots.
    val screwR = s * 0.016f
    drawScrew(Offset(topLeft.x + rectSize.width * 0.10f, topLeft.y + rectSize.height * 0.10f), screwR, -32f)
    drawScrew(Offset(topLeft.x + rectSize.width * 0.90f, topLeft.y + rectSize.height * 0.10f), screwR, 26f)
    drawScrew(Offset(topLeft.x + rectSize.width * 0.10f, topLeft.y + rectSize.height * 0.90f), screwR, 20f)
    drawScrew(Offset(topLeft.x + rectSize.width * 0.90f, topLeft.y + rectSize.height * 0.90f), screwR, -23f)
}

fun DrawScope.drawScrew(centerPos: Offset, radius: Float, slotAngleDeg: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF777D81),
                0.42f to Color(0xFF474D51),
                1f to Color(0xFF171A1D),
            ),
            center = Offset(centerPos.x - radius * 0.3f, centerPos.y - radius * 0.3f),
            radius = radius * 1.4f,
        ),
        radius = radius,
        center = centerPos,
    )
    drawCircle(
        color = Color(0xFF07090A),
        radius = radius,
        center = centerPos,
        style = Stroke(width = radius * 0.18f),
    )
    rotate(slotAngleDeg, pivot = centerPos) {
        drawLine(
            color = Color(0xFF2B3033),
            start = Offset(centerPos.x - radius * 0.52f, centerPos.y),
            end = Offset(centerPos.x + radius * 0.52f, centerPos.y),
            strokeWidth = radius * 0.17f,
        )
    }
}

fun DrawScope.drawBezel() {
    val s = instrumentSizePx()
    val c = center
    val outer = s * Chrome.BEZEL_OUTER
    val inner = s * Chrome.BEZEL_INNER

    // Recessed cavity behind the ring.
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF111315),
                0.72f to Color(0xFF080A0B),
                1f to Color(0xFF010202),
            ),
            center = c,
            radius = outer,
        ),
        radius = outer,
        center = c,
    )
    // Painted ring as an annulus stroke with a diagonal metal gradient.
    drawCircle(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Palette.bezelLight,
                0.18f to Palette.bezelMid,
                0.5f to Palette.bezelDark,
                0.82f to Palette.bezelMid,
                1f to Palette.bezelLight,
            ),
            start = Offset(c.x - outer, c.y - outer),
            end = Offset(c.x + outer, c.y + outer),
        ),
        radius = (outer + inner) / 2f,
        center = c,
        style = Stroke(width = outer - inner),
    )
    // Shadow under the inner lip.
    drawCircle(
        color = Color.Black.copy(alpha = 0.78f),
        radius = inner,
        center = c,
        style = Stroke(width = s * 0.010f),
    )
}

/**
 * Deterministic hash used by the reference panel for its dial and paint
 * textures: fract(sin(n * 12.9898 + 78.233) * 43758.5453).
 */
internal fun seededNoise(n: Float): Float {
    val x = kotlin.math.sin(n * 12.9898f + 78.233f) * 43758.5453f
    return x - kotlin.math.floor(x)
}

/**
 * Dial face exactly as the reference draws it: radial gradient offset up and
 * to the left, plus 1400 speckles of fine aged texture. The speckles are part
 * of the cached static layer, so they cost nothing per frame.
 */
fun DrawScope.drawDialFace(radius: Float) {
    val s = instrumentSizePx()
    val c = center
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Palette.faceHighlight,
                0.62f to Palette.faceMid,
                1f to Palette.faceEdge,
            ),
            center = Offset(c.x - s * 0.05f, c.y - s * 0.07f),
            radius = radius,
        ),
        radius = radius,
        center = c,
    )
    // Aged dial texture (reference: 1400 sub-pixel specks inside the face).
    val dot = s * 0.0011f
    for (i in 0 until 1400) {
        val a = seededNoise(i * 7.1f) * (2f * Math.PI.toFloat())
        val rr = kotlin.math.sqrt(seededNoise(i * 3.4f)) * radius
        val alpha = 0.007f + seededNoise(i * 5.8f) * 0.016f
        drawRect(
            color = if (seededNoise(i * 9.2f) > 0.5f) {
                Color.White.copy(alpha = alpha)
            } else {
                Color.Black.copy(alpha = alpha)
            },
            topLeft = Offset(c.x + kotlin.math.cos(a) * rr, c.y + kotlin.math.sin(a) * rr),
            size = androidx.compose.ui.geometry.Size(dot, dot),
        )
    }
}

/** Diagonal glass reflection over a circular dial. */
fun DrawScope.drawGlass(radius: Float) {
    val c = center
    drawCircle(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.10f),
                0.16f to Color.White.copy(alpha = 0.035f),
                0.42f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.06f),
            ),
            start = Offset(c.x - radius, c.y - radius),
            end = Offset(c.x + radius, c.y + radius),
        ),
        radius = radius,
        center = c,
    )
}

/** Center hub over the needles. */
fun DrawScope.drawHub(radiusFraction: Float = 0.034f) {
    val s = instrumentSizePx()
    val c = center
    val r = s * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF4F5151),
                0.5f to Color(0xFF202223),
                1f to Color(0xFF080909),
            ),
            center = Offset(c.x - s * 0.008f, c.y - s * 0.008f),
            radius = r * 1.3f,
        ),
        radius = r,
        center = c,
    )
    drawCircle(
        color = Color(0xFF060707),
        radius = r,
        center = c,
        style = Stroke(width = s * 0.004f),
    )
}
