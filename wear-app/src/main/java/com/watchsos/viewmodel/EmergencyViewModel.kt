package com.watchsos.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchsos.SosApplication
import com.watchsos.model.EmergencyStatus
import com.watchsos.service.EmergencyService
import com.watchsos.util.SosPermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EmergencyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SosApplication
    val state = app.state.asStateFlow()
    val configurationReady get() = app.repository.isSuccess

    fun start() {
        if (state.value.status in setOf(EmergencyStatus.STARTING, EmergencyStatus.ACTIVE, EmergencyStatus.STOPPING) || state.value.pending) return
        if (!configurationReady) return
        if (!SosPermissions.hasRequired(app)) {
            app.state.update { it.copy(status = EmergencyStatus.ERROR, syncMessage = "Micrófono y ubicación necesitan permiso") }
            return
        }
        app.state.update { it.copy(status = EmergencyStatus.STARTING, syncMessage = "Iniciando servicio") }
        try {
            ContextCompat.startForegroundService(app, Intent(app, EmergencyService::class.java).setAction(EmergencyService.ACTION_START))
        } catch (_: Exception) {
            app.state.update { it.copy(status = EmergencyStatus.ERROR, syncMessage = "No se pudo iniciar. Abre la app y revisa permisos.") }
        }
    }

    fun finish() {
        if (state.value.status !in setOf(EmergencyStatus.STARTING, EmergencyStatus.ACTIVE)) return
        try {
            app.startService(Intent(app, EmergencyService::class.java).setAction(EmergencyService.ACTION_FINISH))
        } catch (_: Exception) {
            app.state.update { it.copy(syncMessage = "No se pudo pedir el cierre. Reintenta desde la app.") }
        }
    }

    fun retryFinish() {
        if (!state.value.pending || state.value.status != EmergencyStatus.ERROR) return
        app.state.update { it.copy(status = EmergencyStatus.STOPPING, syncMessage = "Enviando datos pendientes") }
        viewModelScope.launch {
            try {
                val repository = app.repository.getOrThrow()
                repository.recoverCompletedAudio()
                repository.requestFinish()
                check(repository.sync())
                app.state.update { it.copy(status = EmergencyStatus.FINISHED, pending = false,
                    serverConfirmed = true, syncMessage = "Alerta finalizada en el servidor") }
            } catch (cancelled: CancellationException) {
                app.state.update { it.copy(status = EmergencyStatus.ERROR, syncMessage = "Envío interrumpido. Reintenta el cierre.") }
                throw cancelled
            } catch (_: Exception) {
                app.state.update { it.copy(status = EmergencyStatus.ERROR,
                    syncMessage = "Cierre pendiente. Comprueba conexión y backend; los datos siguen guardados.") }
            }
        }
    }
}
