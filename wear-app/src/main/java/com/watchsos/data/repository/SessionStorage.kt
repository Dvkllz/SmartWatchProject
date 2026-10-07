package com.watchsos.data.repository

import android.util.AtomicFile
import com.google.gson.Gson
import com.watchsos.model.PendingEmergency
import java.io.File

interface SessionStorage {
    fun read(): PendingEmergency?
    fun write(session: PendingEmergency)
    fun clear()
}

class AtomicSessionStorage(file: File) : SessionStorage {
    private val atomicFile = AtomicFile(file)
    private val gson = Gson()
    override fun read(): PendingEmergency? = try {
        atomicFile.openRead().bufferedReader().use {
            checkNotNull(gson.fromJson(it, PendingEmergency::class.java)) { "Sesión vacía o dañada" }
        }
    } catch (error: java.io.FileNotFoundException) {
        if (atomicFile.baseFile.exists()) throw error else null
    }

    override fun write(session: PendingEmergency) {
        val output = atomicFile.startWrite()
        try {
            output.write(gson.toJson(session).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }
    override fun clear() = atomicFile.delete()
}
