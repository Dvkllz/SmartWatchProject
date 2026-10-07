package com.watchsos.data.repository

import com.watchsos.data.api.*
import com.watchsos.model.*
import com.watchsos.util.SosConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import java.util.UUID

class EmergencyRepository(private val storage: SessionStorage, private val api: EmergencyApi, private val audioRoot: File) {
    private val diskMutex = Mutex()
    private val syncMutex = Mutex()
    @Volatile var pending: PendingEmergency? = storage.read()
        private set

    init {
        pending?.let { session ->
            UUID.fromString(session.id)
            Instant.parse(session.timestamp)
            require(session.userId.isNotBlank())
            require(session.locations.size <= SosConfig.MAX_PENDING_LOCATIONS)
            val directory = File(audioRoot, session.id).canonicalFile
            session.audio.forEach { segment ->
                UUID.fromString(segment.uploadId)
                require(File(segment.path).canonicalFile.parentFile == directory)
            }
        }
    }

    suspend fun begin(demo: Boolean): PendingEmergency = withContext(Dispatchers.IO) {
        diskMutex.withLock {
            check(pending == null) { "Hay una alerta pendiente de finalizar" }
            PendingEmergency(UUID.randomUUID().toString(), "demo-user", Instant.now().toString(), demo).also { save(it) }
        }
    }

    suspend fun addLocation(sample: LocationSample) = edit { session ->
        check(session.finishedAt == null)
        session.copy(locations = (session.locations + sample).takeLast(SosConfig.MAX_PENDING_LOCATIONS))
    }

    suspend fun addAudio(segment: AudioSegment) = edit { session ->
        session.copy(audio = session.audio.filterNot { it.uploadId == segment.uploadId } + segment)
    }

    suspend fun requestFinish() = edit { it.copy(finishedAt = it.finishedAt ?: Instant.now().toString()) }

    suspend fun recoverCompletedAudio() {
        val session = pending ?: return
        val directory = File(audioRoot, session.id)
        directory.listFiles()?.filter { it.extension in setOf("wav", "m4a") }?.forEach { file ->
            UUID.fromString(file.nameWithoutExtension)
            addAudio(AudioSegment(file.nameWithoutExtension, file.absolutePath,
                if (file.extension == "wav") "audio/wav" else "audio/mp4", file.extension == "wav"))
        }
    }

    // La red nunca mantiene el bloqueo de disco: grabación y GPS pueden guardar mientras se envía.
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            var session = pending ?: return@withLock true
            if (!session.serverConfirmed) {
                if (!session.creationFrozen) edit { it.copy(initialLocation = it.locations.firstOrNull(), creationFrozen = true) }
                session = requireNotNull(pending)
                val response = api.create(session.id, CreateEmergencyRequest(session.userId,
                    session.initialLocation?.latitude, session.initialLocation?.longitude, session.timestamp,
                    simulated = session.simulated || session.initialLocation?.simulated == true))
                check(response.emergencyId == session.id) { "ID de servidor inesperado" }
                edit { it.copy(serverConfirmed = true) }
            }
            session = requireNotNull(pending)
            for (location in session.locations) {
                api.location(session.id, location)
                edit { it.copy(locations = it.locations.filterNot { sample -> sample.sampleId == location.sampleId }) }
            }
            for (segment in session.audio) {
                val file = File(segment.path)
                check(file.isFile) { "Falta un archivo de audio pendiente" }
                api.audio(session.id, segment.uploadId,
                    MultipartBody.Part.createFormData("audio", file.name, file.asRequestBody(segment.mimeType.toMediaType())),
                    segment.simulated.toString().toRequestBody("text/plain".toMediaType()))
                // Si se interrumpe aquí, el mismo ID de subida evita duplicados en el servidor.
                edit { it.copy(audio = it.audio.filterNot { part -> part.uploadId == segment.uploadId }) }
                file.delete()
            }
            session = requireNotNull(pending)
            val finishAt = session.finishedAt
            if (finishAt != null && session.locations.isEmpty() && session.audio.isEmpty()) {
                val response = api.finish(session.id, FinishRequest(finishAt))
                check(response.status == "FINISHED") { "Servidor no confirmó el cierre" }
                diskMutex.withLock { storage.clear(); pending = null }
                return@withLock true
            }
            false
        }
    }

    private suspend fun edit(transform: (PendingEmergency) -> PendingEmergency) = withContext(Dispatchers.IO) {
        diskMutex.withLock { save(transform(requireNotNull(pending))) }
    }

    private fun save(session: PendingEmergency) { storage.write(session); pending = session }
}
