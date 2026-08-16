package com.ngg.instruments.recording

import android.content.Context
import com.ngg.instruments.sensor.FlightDataSources
import com.ngg.instruments.sensor.RawSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic recorder: tees the raw-sample stream into a local text file
 * (app-private storage, never uploaded). Wrap the live sources with
 * [recordingSources] and the engine records transparently while it runs.
 */
class SensorRecorder(private val context: Context) {

    val recordingsDir: File
        get() = File(context.filesDir, "recordings").apply { mkdirs() }

    fun listRecordings(): List<File> =
        recordingsDir.listFiles { f -> f.isFile && f.name.endsWith(".rec") }
            ?.sortedByDescending { it.name } ?: emptyList()

    fun newRecordingFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(recordingsDir, "rec-$stamp.rec")
    }

    /**
     * Wraps [base] so every sample is also appended to [file]. The writer is
     * opened lazily on first sample and closed when collection ends, tying the
     * file lifetime to the engine run.
     */
    fun recordingSources(base: FlightDataSources, file: File): FlightDataSources =
        object : FlightDataSources {
            override val samples: Flow<RawSample> = run {
                var writer: BufferedWriter? = null
                base.samples
                    .onEach { sample ->
                        val w = writer ?: file.bufferedWriter().also {
                            it.write(RecordingCodec.HEADER)
                            it.newLine()
                            writer = it
                        }
                        w.write(RecordingCodec.encode(sample))
                        w.newLine()
                    }
                    .onCompletion {
                        writer?.flush()
                        writer?.close()
                        writer = null
                    }
            }
        }
}
