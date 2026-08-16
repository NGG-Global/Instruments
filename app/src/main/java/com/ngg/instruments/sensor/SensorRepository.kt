package com.ngg.instruments.sensor

import android.content.Context
import com.ngg.instruments.gnss.GnssRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * Live implementation of [FlightDataSources]: merges the rotation, pressure and
 * GNSS flows into the single raw-sample stream consumed by the engine.
 */
class SensorRepository(
    context: Context,
    val capabilities: SensorCapabilities,
    private val gnssRepository: GnssRepository,
) : FlightDataSources {

    private val attitudeSource = AttitudeSensorSource(context, capabilities)
    private val headingSource = HeadingSensorSource(context, capabilities)
    private val pressureSource = PressureSensorSource(context, capabilities)

    /** True when attitude falls back to the magnetic rotation vector. */
    val attitudeUsesFallback: Boolean get() = attitudeSource.usesFallback

    override val samples: Flow<RawSample> = merge(
        attitudeSource.samples,
        headingSource.samples,
        pressureSource.samples,
        gnssRepository.samples,
    )
}
