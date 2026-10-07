package com.watchsos.audio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DemoWavTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `genera WAV PCM mono con cabecera y duracion correctas`() {
        val file = temporary.newFile("demo.wav")
        DemoWav.write(file, 1_000)
        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(16_044, bytes.size)
        assertEquals(bytes.size - 8, header.getInt(4))
        assertEquals(8_000, header.getInt(24))
        assertEquals(16_000, header.getInt(40))
    }
    @Test fun `limita el tamano del audio sintetico incluso con duraciones extremas`() {
        val file = temporary.newFile("bounded.wav")
        DemoWav.write(file, Long.MAX_VALUE)
        assertEquals(480_044L, file.length())
        DemoWav.write(file, 0)
        assertEquals(1_644L, file.length())
    }
}
