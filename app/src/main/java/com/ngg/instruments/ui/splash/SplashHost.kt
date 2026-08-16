package com.ngg.instruments.ui.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Wraps the app content behind the designer's [AttitudeSplash].
 *
 * The splash resolves when [ready] is true AND a minimum display time has
 * elapsed — the intro (bezel + wings, ~1.3 s) plus most of one loading loop.
 * Without the floor, a fast cold start would cut the animation mid-unfold.
 */
private const val MIN_SPLASH_MS = 2600L

@Composable
fun SplashHost(
    ready: Boolean,
    content: @Composable () -> Unit,
) {
    var splashDone by remember { mutableStateOf(false) }
    var minTimeElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(MIN_SPLASH_MS)
        minTimeElapsed = true
    }

    if (splashDone) {
        content()
    } else {
        // Keep the app composing underneath only once it is ready, so the
        // reveal after the exit animation is instant.
        if (ready && minTimeElapsed) content()
        AttitudeSplash(loading = !(ready && minTimeElapsed)) { splashDone = true }
    }
}
