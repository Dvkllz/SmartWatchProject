package com.watchsos

import android.app.Application
import com.watchsos.data.api.ApiClient
import com.watchsos.data.repository.AtomicSessionStorage
import com.watchsos.data.repository.EmergencyRepository
import com.watchsos.model.EmergencyState
import com.watchsos.model.EmergencyStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

enum class AppThemeMode { DARK, LIGHT }
enum class AppLanguage { ES, EN }
data class AppUiPreferences(val theme: AppThemeMode = AppThemeMode.DARK, val language: AppLanguage = AppLanguage.ES)

class SosApplication : Application() {
    var repository: Result<EmergencyRepository> = Result.failure(IllegalStateException("Inicializando"))
        private set
    val state = MutableStateFlow(EmergencyState())
    val uiPreferences = MutableStateFlow(AppUiPreferences())

    private val preferences by lazy { getSharedPreferences("ui-preferences", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        uiPreferences.value = AppUiPreferences(
            theme = runCatching { AppThemeMode.valueOf(preferences.getString("theme", null) ?: "DARK") }
                .getOrDefault(AppThemeMode.DARK),
            language = runCatching { AppLanguage.valueOf(preferences.getString("language", null) ?: "ES") }
                .getOrDefault(AppLanguage.ES),
        )
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

    fun setTheme(theme: AppThemeMode) {
        preferences.edit().putString("theme", theme.name).apply()
        uiPreferences.update { it.copy(theme = theme) }
    }

    fun setLanguage(language: AppLanguage) {
        preferences.edit().putString("language", language.name).apply()
        uiPreferences.update { it.copy(language = language) }
    }
}
