package com.watchsos.model

data class RemoteAudio(
    val uploadId: String,
    val bytes: Long,
    val mimeType: String,
    val receivedAt: String,
    val simulated: Boolean,
)

data class RemoteEmergency(
    val emergencyId: String,
    val status: String,
    val receivedAt: String,
    val finishedAt: String?,
    val simulated: Boolean,
    val audio: List<RemoteAudio>,
)

data class RecordingItem(
    val emergencyId: String,
    val uploadId: String,
    val receivedAt: String,
    val bytes: Long,
    val simulated: Boolean,
    val segmentNumber: Int,
    val emergencyStatus: String,
    val emergencyReceivedAt: String,
)

data class RecordingsState(
    val loading: Boolean = false,
    val items: List<RecordingItem> = emptyList(),
    val error: String? = null,
)
