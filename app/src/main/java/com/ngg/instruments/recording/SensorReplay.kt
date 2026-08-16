package com.ngg.instruments.recording

import com.ngg.instruments.sensor.FlightDataSources
import com.ngg.instruments.sensor.RawSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Replays a recording through the exact estimation pipeline used live, so
 * filter changes can be evaluated deterministically without re-flying the
 * same movement.
 *
 * Sample timestamps are preserved verbatim; the engine's clock is
 * sample-driven, so estimator output depends only on the recording, not on
 * playback pacing. [speedFactor] > 1 plays back faster in wall-clock terms.
 */
class SensorReplay(
    private val file: File,
    private val speedFactor: Float = 1f,
) : FlightDataSources {

    override val samples: Flow<RawSample> = flow {
        val parsed = file.useLines { lines ->
            lines.mapNotNull(RecordingCodec::decode).toList()
        }.sortedBy { it.elapsedNanos }

        var previousNanos = Long.MIN_VALUE
        for (sample in parsed) {
            if (previousNanos != Long.MIN_VALUE) {
                val gapMs = (sample.elapsedNanos - previousNanos) / 1_000_000
                if (gapMs > 0) delay((gapMs / speedFactor).toLong().coerceAtMost(MAX_GAP_MS))
            }
            previousNanos = sample.elapsedNanos
            emit(sample)
        }
    }

    private companion object {
        const val MAX_GAP_MS = 2_000L
    }
}
