package com.ngg.instruments.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.ngg.instruments.math.Angles
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Display-animation layer (RAW -> ESTIMATED -> DISPLAY). Estimation filtering
 * lives in the flight layer; this is only the visual easing of needles.
 *
 * Frame-time based exponential approach: alpha = 1 - exp(-dt/tau), so the feel
 * is identical at 60 Hz and 120 Hz (unlike the reference HTML's fixed 0.12 per
 * frame). Values are read inside the draw phase, so animation does not
 * recompose the tree.
 */
@Composable
fun rememberDisplayValue(
    tauMillis: Float,
    angular: Boolean = false,
    target: () -> Float,
): State<Float> {
    // NaN targets mean "hold the last displayed value"; never let NaN in.
    val out = remember { mutableFloatStateOf(target().takeIf { it.isFinite() } ?: 0f) }
    val latestTarget = rememberUpdatedState(target)
    LaunchedEffect(tauMillis, angular) {
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                val t = latestTarget.value.invoke()
                if (lastFrame != 0L && t.isFinite()) {
                    val dt = (now - lastFrame).coerceAtLeast(0L) / 1e9f
                    val alpha = 1f - exp(-dt * 1000f / tauMillis)
                    val c = out.floatValue
                    out.floatValue = if (angular) {
                        val delta = Angles.shortestDeltaDeg(c, t)
                        if (abs(delta) < SNAP_EPSILON) Angles.normalizeDeg(t)
                        else Angles.normalizeDeg(c + delta * alpha)
                    } else {
                        val next = c + (t - c) * alpha
                        if (abs(t - next) < SNAP_EPSILON) t else next
                    }
                } else if (t.isFinite()) {
                    out.floatValue = if (angular) Angles.normalizeDeg(t) else t
                }
                lastFrame = now
            }
        }
    }
    return out
}

private const val SNAP_EPSILON = 0.005f

/**
 * Quantizes an eased value to a whole number with a deadband: the displayed
 * integer only changes once the source moves more than [deadband] away from
 * it, so readouts do not flicker at rounding boundaries. State is mutated in
 * the frame-callback layer, never in the draw phase.
 */
@Composable
fun rememberDeadbandInt(source: State<Float>, deadband: Float): State<Int> {
    val out = remember {
        mutableIntStateOf(source.value.takeIf { it.isFinite() }?.roundToInt() ?: 0)
    }
    LaunchedEffect(deadband) {
        while (true) {
            withFrameNanos {
                val v = source.value
                if (v.isFinite() && abs(v - out.intValue) > deadband) {
                    out.intValue = v.roundToInt()
                }
            }
        }
    }
    return out
}
