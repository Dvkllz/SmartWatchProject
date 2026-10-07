package com.watchsos

import android.app.Application
import com.watchsos.data.api.ApiClient
import com.watchsos.data.repository.AtomicSessionStorage
import com.watchsos.data.repository.EmergencyRepository
import com.watchsos.model.EmergencyState
import com.watchsos.model.EmergencyStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class SosApplication : Application() {
    var repository: Result<EmergencyRepository> = Result.failure(IllegalStateException("Inicializando"))
        private set
    val state = MutableStateFlow(EmergencyState())
    override fun onCreate() {
        super.onCreate()
        repository = runCatching {
            EmergencyRepository(AtomicSessionStorage(File(filesDir, "pending-emergency.json")), ApiClient.create(), File(filesDir, "audio"))
        }
        val pending = repository.getOrNull()?.pending
        if (pending != null) state.value = EmergencyState(EmergencyStatus.ERROR, pending.id,
            serverConfirmed = pending.serverConfirmed, pending = true,
            syncMessage = "Sesión interrumpida. Finaliza y envía los datos pendientes.")
        if (repository.isFailure) state.value = EmergencyState(EmergencyStatus.ERROR,
            syncMessage = "No se pudo leer la sesión o configuración. Los archivos se conservaron.")
    }
}
