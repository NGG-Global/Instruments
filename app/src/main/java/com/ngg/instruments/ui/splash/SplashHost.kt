package com.ngg.instruments.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.ngg.instruments.R
import kotlinx.coroutines.delay

/**
 * Static splash: the winged-emblem logo on the navy brand background, no
 * animation, per the designer's direction. Held briefly so the logo registers
 * instead of flashing, then cut straight to the panel once settings load.
 */
private const val MIN_SPLASH_MS = 800L

@Composable
fun SplashHost(
    ready: Boolean,
    content: @Composable () -> Unit,
) {
    var minTimeElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MIN_SPLASH_MS)
        minTimeElapsed = true
    }

    if (ready && minTimeElapsed) {
        content()
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF080D16)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = "EVionics",
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(1f),
            )
        }
    }
}
