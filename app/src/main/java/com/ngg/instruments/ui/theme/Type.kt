package com.ngg.instruments.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ngg.instruments.R

/**
 * Aviation typography from the design package: Barlow Condensed (OFL 1.1,
 * bundled in res/font — no remote fonts, keeping the app fully offline).
 * Used for numerals, digital readouts and panel chrome.
 */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)
