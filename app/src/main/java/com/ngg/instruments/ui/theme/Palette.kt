package com.ngg.instruments.ui.theme

import androidx.compose.ui.graphics.Color
import com.ngg.instruments.flight.DataQuality

/**
 * Instrument palette lifted from the reference panel (instruments.html) so the
 * native rendering preserves the original visual identity.
 */
object Palette {
    val panelBackground = Color(0xFF030405)
    val slotBackground = Color(0xFF070809)
    val slotBorder = Color(0xFF1B1F22)

    // Matte black painted housings
    val housingTop = Color(0xFF2D3236)
    val housingUpper = Color(0xFF23272A)
    val housingLower = Color(0xFF181B1E)
    val housingBottom = Color(0xFF101214)
    val housingOutline = Color(0xFF07090A)

    // Bezel ring (black-painted instrument hardware)
    val bezelLight = Color(0xFF43484C)
    val bezelMid = Color(0xFF2B3033)
    val bezelDark = Color(0xFF171A1C)

    // Dial faces
    val faceHighlight = Color(0xFF232628)
    val faceMid = Color(0xFF101213)
    val faceEdge = Color(0xFF070809)

    // Inks
    val inkWhite = Color(0xFFF3F3EF)
    val inkCream = Color(0xFFD7D69A)
    val needleCream = Color(0xFFD9D491)
    val needleDark = Color(0xFF0A0C0D)

    // Attitude ball
    val skyTop = Color(0xFF91C6F2)
    val skyMid = Color(0xFF5896D4)
    val skyHorizon = Color(0xFF3A6EAE)
    val groundHorizon = Color(0xFF714A2A)
    val groundMid = Color(0xFF56361C)
    val groundBottom = Color(0xFF2D1C0E)
    val referenceOrange = Color(0xFFF39A1E)

    val caption = Color(0x7AEEEFEB)

    fun qualityColor(quality: DataQuality): Color = when (quality) {
        DataQuality.GOOD -> Color(0xFF4FBF6B)
        DataQuality.DEGRADED -> Color(0xFFE0A83C)
        DataQuality.POOR -> Color(0xFFD65045)
        DataQuality.UNAVAILABLE -> Color(0xFF585E63)
    }
}
