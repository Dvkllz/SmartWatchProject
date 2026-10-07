package com.watchsos.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.watchsos.model.AudioSegment
import com.watchsos.util.EventLog
import com.watchsos.util.SosConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AudioRecorderManager(
    private val context: Context,
    private val demo: Boolean,
    private val onRecordingError: () -> Unit = {},
) {
    private var recorder: MediaRecorder? = null
    private var part: File? = null
    private var startedAt = 0L
    @Volatile private var failed = false

    @Suppress("DEPRECATION")
    suspend fun start(sessionId: String) = withContext(Dispatchers.IO) {
        check(part == null) { "Ya hay una grabación" }
        val root = File(context.filesDir, "audio")
        check((root.walkTopDown().filter { it.isFile }.sumOf { it.length() }) < SosConfig.MAX_AUDIO_BYTES) {
            "Audio local lleno; sincroniza la alerta pendiente"
        }
        val directory = File(root, sessionId).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.part")
        try {
            failed = false
            if (!demo) {
                val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                recorder = mediaRecorder
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder.setAudioChannels(1)
                mediaRecorder.setAudioSamplingRate(16_000)
                mediaRecorder.setAudioEncodingBitRate(32_000)
                mediaRecorder.setOutputFile(file.absolutePath)
                mediaRecorder.setOnErrorListener { _, _, _ ->
                    failed = true
                    onRecordingError()
                }
                mediaRecorder.prepare()
                mediaRecorder.start()
            }
            part = file
            startedAt = SystemClock.elapsedRealtime()
            EventLog.event(if (demo) "audio_demo_started" else "audio_recording_started")
        } catch (error: Exception) {
            recorder?.release()
            recorder = null
            file.delete()
            throw error
        }
    }

    fun isHealthy() = part != null && !failed

    suspend fun stop(): AudioSegment? = withContext(Dispatchers.IO) {
        val file = part ?: return@withContext null
        part = null
        try {
            if (demo) DemoWav.write(file, SystemClock.elapsedRealtime() - startedAt) else recorder?.stop()
            check(!failed && file.exists() && file.length() > 44) { "No se pudo recuperar el segmento de audio" }
            val completed = File(file.parentFile, file.nameWithoutExtension + if (demo) ".wav" else ".m4a")
            check(file.renameTo(completed)) { "No se pudo guardar el audio" }
            EventLog.event("audio_segment_ready")
            AudioSegment(completed.nameWithoutExtension, completed.absolutePath, if (demo) "audio/wav" else "audio/mp4", demo)
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            recorder?.release()
            recorder = null
        }
    }

    fun release() {
        runCatching { recorder?.release() }
        recorder = null
        part?.delete()
        part = null
    }
}
