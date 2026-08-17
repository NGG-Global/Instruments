package com.ngg.instruments.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * App-wide Material 3 dark theme derived from the instrument [Palette], so
 * Material components (Switch, RadioButton, Button, AlertDialog, Scaffold)
 * stop falling back to the default light scheme on the near-black panel.
 */
private val InstrumentsColorScheme = darkColorScheme(
    primary = Palette.referenceOrange,
    onPrimary = Color(0xFF120C02),
    primaryContainer = Color(0xFF3A2A0D),
    onPrimaryContainer = Color(0xFFF5CE93),
    secondary = Palette.inkCream,
    onSecondary = Color(0xFF14140A),
    background = Palette.panelBackground,
    onBackground = Color(0xFFE9EEF0),
    surface = Color(0xFF0D1013),
    onSurface = Color(0xFFE9EEF0),
    surfaceVariant = Color(0xFF171B1F),
    onSurfaceVariant = Color(0xFF9AA6AC),
    surfaceContainer = Color(0xFF101417),
    surfaceContainerHigh = Color(0xFF14181C),
    surfaceContainerHighest = Color(0xFF181D21),
    outline = Color(0xFF2B3135),
    outlineVariant = Color(0xFF1B1F22),
    error = Color(0xFFD65045), // POOR quality red
    onError = Color(0xFF160503),
)

private val InstrumentsTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 1.5.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 1.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
)

@Composable
fun InstrumentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InstrumentsColorScheme,
        typography = InstrumentsTypography,
        content = content,
    )
}
