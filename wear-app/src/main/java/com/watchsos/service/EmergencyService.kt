package com.watchsos.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.watchsos.*
import com.watchsos.audio.AudioRecorderManager
import com.watchsos.location.LocationTracker
import com.watchsos.model.*
import com.watchsos.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update

class EmergencyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as SosApplication
    private val repository get() = app.repository.getOrThrow()
    private lateinit var audio: AudioRecorderManager
    private var startJob: Job? = null
    private var locationJob: Job? = null
    private var audioJob: Job? = null
    private var syncJob: Job? = null
    private var finishing = false
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        audio = AudioRecorderManager(this, BuildConfig.DEMO) {
            app.state.update { it.copy(recording = false, audioMessage = "Micrófono interrumpido; la alerta continúa") }
            EventLog.event("audio_recording_error")
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Emergencia activa", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (startJob == null && !finishing) startEmergency()
            ACTION_FINISH -> if (startJob == null) stopSelf() else if (!finishing) finishEmergency()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startEmergency() {
        try {
            check(SosPermissions.hasRequired(this)) { "Concede micrófono y ubicación antes de activar" }
            check(repository.pending == null) { "Primero finaliza la alerta pendiente" }
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            val realLocationAllowed = SosPermissions.locationEnabled(this)
            if (realLocationAllowed) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), types)
            foreground = true
            app.state.value = EmergencyState(status = EmergencyStatus.STARTING, syncMessage = "Preparando alerta")
            startJob = scope.launch {
                try {
                    val session = repository.begin(BuildConfig.DEMO)
                    EventLog.event("emergency_started")
                    app.state.update { it.copy(emergencyId = session.id, pending = true) }
                    startAudio(session.id)
                    val tracker = LocationTracker(this@EmergencyService, BuildConfig.DEMO, realLocationAllowed)
                    val firstReading = CompletableDeferred<Unit>()
                    locationJob = launch {
                        tracker.updates().collect { reading ->
                            try {
                                reading.sample?.let { repository.addLocation(it) }
                                app.state.update { it.copy(locationMessage = reading.message ?: "Ubicación recibida") }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { app.state.update { it.copy(locationMessage = "No se pudo guardar ubicación") } }
                            firstReading.complete(Unit)
                        }
                    }
                    app.state.update { it.copy(status = EmergencyStatus.ACTIVE, syncMessage = "Enviando alerta") }
                    syncJob = launch {
                        // Da tiempo a una primera posición; nunca bloquea indefinidamente el SOS.
                        withTimeoutOrNull(SosConfig.LOCATION_TIMEOUT_MS + 500) { firstReading.await() }
                        while (isActive) { synchronize(); delay(SosConfig.SYNC_INTERVAL_MS) }
                    }
                    audioJob = launch {
                        while (isActive) {
                            delay(SosConfig.AUDIO_SEGMENT_MS)
                            saveAudio()
                            startAudio(session.id)
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    app.state.update { it.copy(status = EmergencyStatus.ERROR, recording = false,
                        syncMessage = "No se pudo iniciar. Revisa permisos, espacio y configuración.", pending = repository.pending != null) }
                    stopSelf()
                }
            }
        } catch (error: Exception) {
            app.state.update { it.copy(status = EmergencyStatus.ERROR,
                syncMessage = error.message ?: "No se pudo iniciar el servicio") }
            stopSelf()
        }
    }

    private suspend fun startAudio(id: String) {
        try {
            audio.start(id)
            app.state.update { it.copy(recording = true, audioMessage = if (BuildConfig.DEMO) "AUDIO DEMO" else null) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { app.state.update { it.copy(recording = false, audioMessage = "Micrófono/audio no disponible") } }
    }

    private suspend fun saveAudio() {
        try {
            audio.stop()?.let { repository.addAudio(it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { app.state.update { it.copy(audioMessage = "Segmento no guardado; revisa micrófono/espacio") } }
        app.state.update { it.copy(recording = false) }
    }

    private suspend fun synchronize() {
        try {
            repository.sync()
            app.state.update { it.copy(serverConfirmed = repository.pending?.serverConfirmed == true,
                syncMessage = "Servidor actualizado") }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            app.state.update { it.copy(serverConfirmed = repository.pending?.serverConfirmed == true,
                syncMessage = "Sin confirmación reciente; datos pendientes") }
            EventLog.event("sync_pending")
        }
        if (!audio.isHealthy()) app.state.update { it.copy(recording = false, audioMessage = "Audio interrumpido") }
    }

    private fun finishEmergency() {
        finishing = true
        app.state.update { it.copy(status = EmergencyStatus.STOPPING, syncMessage = "Deteniendo sensores y enviando") }
        scope.launch {
            try {
                startJob?.cancelAndJoin()
                locationJob?.cancelAndJoin()
                audioJob?.cancelAndJoin()
                syncJob?.cancelAndJoin()
                saveAudio()
                repository.recoverCompletedAudio()
                repository.requestFinish()
                check(repository.sync())
                app.state.update { it.copy(status = EmergencyStatus.FINISHED, recording = false,
                    pending = false, syncMessage = "Alerta finalizada en el servidor") }
                EventLog.event("emergency_finished")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                app.state.update { it.copy(status = EmergencyStatus.ERROR, recording = false,
                    pending = repository.pending != null, syncMessage = "Captura detenida. Cierre pendiente: reintenta el envío.") }
            } finally {
                audio.release()
                ServiceCompat.stopForeground(this@EmergencyService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                foreground = false
                stopSelf()
            }
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val finish = PendingIntent.getService(this, 1, Intent(this, EmergencyService::class.java).setAction(ACTION_FINISH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_sos)
            .setContentTitle("SOS activo").setContentText("Abre la app para ver audio, ubicación y entrega.")
            .setContentIntent(open).setOngoing(true).addAction(R.drawable.ic_sos, "Finalizar", finish).build()
    }

    override fun onDestroy() {
        scope.cancel()
        audio.release()
        if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (app.state.value.status in setOf(EmergencyStatus.ACTIVE, EmergencyStatus.STARTING, EmergencyStatus.STOPPING)) {
            app.state.update { it.copy(status = EmergencyStatus.ERROR, recording = false,
                syncMessage = "Servicio interrumpido; finaliza los datos pendientes") }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.watchsos.START"
        const val ACTION_FINISH = "com.watchsos.FINISH"
        private const val CHANNEL = "emergency"
        private const val NOTIFICATION_ID = 1001
    }
}
