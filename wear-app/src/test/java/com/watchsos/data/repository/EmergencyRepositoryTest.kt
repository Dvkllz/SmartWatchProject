package com.watchsos.data.repository

import com.watchsos.data.api.*
import com.watchsos.model.*
import kotlinx.coroutines.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.UUID

class EmergencyRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val storage = MemoryStorage()
    private val api = FakeApi()
    private fun repository() = EmergencyRepository(storage, api, temporary.root)
    private fun location() = LocationSample(1.0, -77.0, simulated = true)
    private fun segment(id: String): AudioSegment {
        val file = File(File(temporary.root, id).apply { mkdirs() }, "${UUID.randomUUID()}.wav")
        file.writeBytes(ByteArray(64))
        return AudioSegment(file.nameWithoutExtension, file.absolutePath, "audio/wav", true)
    }

    @Test fun `cierre envia todo antes de finalizar y elimina audio confirmado`() = runBlocking {
        val repo = repository(); val session = repo.begin(true)
        val segment = segment(session.id)
        repo.addLocation(location()); repo.addAudio(segment); repo.requestFinish()
        assertTrue(repo.sync())
        assertEquals(listOf("create", "location", "audio", "finish"), api.calls)
        assertNull(repo.pending); assertNull(storage.value); assertFalse(File(segment.path).exists())
    }

    @Test fun `respuesta perdida de creacion conserva id y payload al cambiar GPS`() = runBlocking {
        val repo = repository(); repo.begin(true); repo.addLocation(location())
        api.failCreate = true
        expectOffline { repo.sync() }
        repo.addLocation(LocationSample(2.0, -78.0))
        repo.sync()
        assertEquals(2, api.createBodies.size)
        assertEquals(api.createBodies[0], api.createBodies[1])
        assertEquals(1, api.createdIds.size)
        assertTrue(repo.pending!!.serverConfirmed)
    }

    @Test fun `respuesta perdida de audio preserva archivo y reintenta mismo uploadId`() = runBlocking {
        val repo = repository(); val session = repo.begin(true); val segment = segment(session.id)
        repo.addAudio(segment); repo.requestFinish(); api.failAudio = true
        expectOffline { repo.sync() }
        assertTrue(File(segment.path).exists()); assertNotNull(repo.pending)
        assertTrue(repo.sync()); assertEquals(1, api.uploadedIds.size)
        assertFalse(File(segment.path).exists())
    }

    @Test fun `fallo de cierre conserva sesion y no marca finalizada antes de confirmacion`() = runBlocking {
        val repo = repository(); repo.begin(true); repo.requestFinish(); api.failFinish = true
        expectOffline { repo.sync() }
        assertNotNull(repo.pending); assertNotNull(storage.value!!.finishedAt)
        assertTrue(repo.sync()); assertNull(repo.pending)
        assertEquals(1, api.finishedIds.size)
    }

    @Test fun `sesion persistida se recupera tras reinicio con segmentos finalizados`() = runBlocking {
        val first = repository(); val session = first.begin(true)
        segment(session.id)
        val restored = repository(); restored.recoverCompletedAudio()
        assertEquals(1, restored.pending!!.audio.size)
        restored.requestFinish(); assertTrue(restored.sync()); assertEquals(1, api.uploadedIds.size)
    }

    @Test fun `no permite reemplazar una emergencia pendiente`() = runBlocking {
        val repo = repository(); val first = repo.begin(true)
        try { repo.begin(true); fail("No debe reemplazar la sesión") } catch (_: IllegalStateException) { }
        assertEquals(first.id, repo.pending!!.id)
    }

    @Test fun `una red lenta no bloquea la persistencia de nuevas ubicaciones`() = runBlocking {
        val repo = repository(); repo.begin(true)
        val started = CompletableDeferred<Unit>(); val unblock = CompletableDeferred<Unit>()
        api.onCreate = { started.complete(Unit); unblock.await() }
        val sending = async { repo.sync() }
        started.await()
        withTimeout(2_000) { repo.addLocation(location()) }
        assertEquals(1, storage.value!!.locations.size)
        unblock.complete(Unit); sending.await()
        assertEquals(1, api.locationIds.size)
    }

    @Test fun `fallo al guardar no borra la ultima sesion persistida`() = runBlocking {
        val repo = repository(); val before = repo.begin(true)
        storage.failWrite = true
        expectOffline { repo.addLocation(location()) }
        assertEquals(before, repo.pending); assertEquals(before, storage.value)
    }

    private suspend fun expectOffline(action: suspend () -> Unit) {
        try { action(); fail("Se esperaba IOException") } catch (_: IOException) { }
    }
}

private class MemoryStorage : SessionStorage {
    var value: PendingEmergency? = null
    var failWrite = false
    override fun read() = value
    override fun write(session: PendingEmergency) { if (failWrite) throw IOException("disk"); value = session }
    override fun clear() { value = null }
}

private class FakeApi : EmergencyApi {
    val calls = mutableListOf<String>()
    val createdIds = mutableSetOf<String>()
    val uploadedIds = mutableSetOf<String>()
    val finishedIds = mutableSetOf<String>()
    val locationIds = mutableSetOf<String>()
    val createBodies = mutableListOf<CreateEmergencyRequest>()
    var failCreate = false
    var failAudio = false
    var failFinish = false
    var onCreate: suspend () -> Unit = {}
    override suspend fun create(key: String, body: CreateEmergencyRequest): EmergencyResponse {
        onCreate(); calls += "create"; createdIds += key; createBodies += body
        if (failCreate) { failCreate = false; throw IOException("lost response") }
        return EmergencyResponse(key, "ACTIVE")
    }
    override suspend fun location(id: String, body: LocationSample) { calls += "location"; locationIds += body.sampleId }
    override suspend fun audio(id: String, uploadId: String, audio: MultipartBody.Part, simulated: RequestBody) {
        calls += "audio"; uploadedIds += uploadId
        if (failAudio) { failAudio = false; throw IOException("lost response") }
    }
    override suspend fun finish(id: String, body: FinishRequest): EmergencyResponse {
        calls += "finish"; finishedIds += id
        if (failFinish) { failFinish = false; throw IOException("lost response") }
        return EmergencyResponse(id, "FINISHED")
    }
}
