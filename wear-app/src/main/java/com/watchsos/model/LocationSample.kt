package com.watchsos.model

import java.time.Instant
import java.util.UUID

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String = Instant.now().toString(),
    val simulated: Boolean = false,
    val sampleId: String = UUID.randomUUID().toString(),
)

data class LocationReading(val sample: LocationSample?, val message: String? = null)
