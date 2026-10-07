package com.watchsos.model

data class PendingEmergency(
    val id: String,
    val userId: String,
    val timestamp: String,
    val simulated: Boolean,
    val initialLocation: LocationSample? = null,
    val creationFrozen: Boolean = false,
    val serverConfirmed: Boolean = false,
    val locations: List<LocationSample> = emptyList(),
    val audio: List<AudioSegment> = emptyList(),
    val finishedAt: String? = null,
)
