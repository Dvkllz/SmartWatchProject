package com.watchsos.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object DemoWav {
    private const val SAMPLE_RATE = 8_000
    fun write(file: File, durationMs: Long) {
        val samples = (durationMs.coerceIn(100, 30_000) * SAMPLE_RATE / 1_000).toInt()
        val bytes = samples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes).array()
        file.outputStream().buffered().use { output ->
            output.write(header)
            repeat(samples) { index ->
                val value = (sin(2 * PI * 440 * index / SAMPLE_RATE) * 1_000).toInt()
                output.write(value and 0xff)
                output.write((value shr 8) and 0xff)
            }
        }
    }
}
