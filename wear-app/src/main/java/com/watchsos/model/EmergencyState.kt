package com.watchsos.model

enum class EmergencyStatus { IDLE, STARTING, ACTIVE, STOPPING, FINISHED, ERROR }

data class EmergencyState(
    val status: EmergencyStatus = EmergencyStatus.IDLE,
    val emergencyId: String? = null,
    val serverConfirmed: Boolean = false,
    val recording: Boolean = false,
    val audioMessage: String? = null,
    val locationMessage: String = "Buscando ubicación",
    val syncMessage: String? = null,
    val pending: Boolean = false,
)
