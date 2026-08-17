package com.ngg.instruments.recording

import com.ngg.instruments.gnss.GnssFix
import com.ngg.instruments.math.Quaternion
import com.ngg.instruments.sensor.GnssSample
import com.ngg.instruments.sensor.GnssStatusSample
import com.ngg.instruments.sensor.PressureSample
import com.ngg.instruments.sensor.RawSample
import com.ngg.instruments.sensor.RotationKind
import com.ngg.instruments.sensor.RotationSample

/**
 * Line-oriented text codec for raw-sample recordings. One sample per line,
 * comma separated, locale-independent. Pure Kotlin so it is unit-testable and
 * replay is byte-for-byte deterministic.
 *
 * Formats:
 *   grv,<ns>,w,x,y,z,accuracy          game rotation vector
 *   mrv,<ns>,w,x,y,z,accuracy          magnetic rotation vector
 *   prs,<ns>,hPa,accuracy              pressure
 *   gps,<ns>,lat,lon,wgs,msl,spd,brg,hacc,vacc,sacc,bacc,timeMs
 *   sat,<ns>,used,visible              constellation status
 * Missing values are encoded as "-".
 */
object RecordingCodec {
    const val HEADER = "# instruments-recording v1"

    private fun f(v: Float?): String = v?.toString() ?: "-"
    private fun d(v: Double?): String = v?.toString() ?: "-"

    private fun pf(s: String): Float? = if (s == "-") null else s.toFloatOrNull()
    private fun pd(s: String): Double? = if (s == "-") null else s.toDoubleOrNull()

    fun encode(sample: RawSample): String = when (sample) {
        is RotationSample -> {
            val tag = if (sample.kind == RotationKind.GAME) "grv" else "mrv"
            val q = sample.quaternion
            "$tag,${sample.elapsedNanos},${q.w},${q.x},${q.y},${q.z},${sample.accuracy}"
        }

        is PressureSample ->
            "prs,${sample.elapsedNanos},${sample.pressureHpa},${sample.accuracy}"

        is GnssStatusSample ->
            "sat,${sample.elapsedNanos},${sample.satellitesUsed},${sample.satellitesVisible}"

        is GnssSample -> {
            val fx = sample.fix
            "gps,${sample.elapsedNanos},${fx.latitude},${fx.longitude}," +
                "${d(fx.altitudeWgs84M)},${d(fx.altitudeMslM)},${f(fx.speedMps)},${f(fx.bearingDeg)}," +
                "${f(fx.horizontalAccuracyM)},${f(fx.verticalAccuracyM)},${f(fx.speedAccuracyMps)}," +
                "${f(fx.bearingAccuracyDeg)},${fx.timeMs}"
        }
    }

    fun decode(line: String): RawSample? {
        if (line.isBlank() || line.startsWith("#")) return null
        val p = line.trim().split(',')
        return try {
            when (p[0]) {
                "grv", "mrv" -> RotationSample(
                    kind = if (p[0] == "grv") RotationKind.GAME else RotationKind.MAGNETIC,
                    elapsedNanos = p[1].toLong(),
                    quaternion = Quaternion(p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()),
                    accuracy = p[6].toInt(),
                )

                "prs" -> PressureSample(
                    elapsedNanos = p[1].toLong(),
                    pressureHpa = p[2].toFloat(),
                    accuracy = p[3].toInt(),
                )

                "sat" -> GnssStatusSample(
                    elapsedNanos = p[1].toLong(),
                    satellitesUsed = p[2].toInt(),
                    satellitesVisible = p[3].toInt(),
                )

                "gps" -> GnssSample(
                    elapsedNanos = p[1].toLong(),
                    fix = GnssFix(
                        latitude = p[2].toDouble(),
                        longitude = p[3].toDouble(),
                        altitudeWgs84M = pd(p[4]),
                        altitudeMslM = pd(p[5]),
                        speedMps = pf(p[6]),
                        bearingDeg = pf(p[7]),
                        horizontalAccuracyM = pf(p[8]),
                        verticalAccuracyM = pf(p[9]),
                        speedAccuracyMps = pf(p[10]),
                        bearingAccuracyDeg = pf(p[11]),
                        elapsedRealtimeNanos = p[1].toLong(),
                        timeMs = p[12].toLong(),
                    ),
                )

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
