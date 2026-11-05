package com.grsks.printerremote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoveryTest {

    private fun reply(size: Int, name: String, pid: Int, serial: String = ""): ByteArray {
        val b = ByteArray(size)
        name.toByteArray().copyInto(b, 0)
        b[0x88] = (pid shr 8).toByte(); b[0x89] = pid.toByte()
        if (serial.isNotEmpty()) serial.toByteArray().copyInto(b, 0x92)
        return b
    }

    @Test
    fun modernReplyGivesModelAndSerial() {
        val p = Discovery.parseFlashForgeReply("192.168.1.9", reply(280, "Мастерская", 36, "SNMQRA9500123"))!!
        assertEquals("flashforge_adventurer_5m_pro", p.modelId)
        assertEquals("SNMQRA9500123", p.serial)
        assertEquals("192.168.1.9", p.host)
    }

    @Test
    fun legacyReplyIsAdventurer4WithNote() {
        val p = Discovery.parseFlashForgeReply("192.168.1.4", reply(140, "A4", 29))!!
        assertEquals("flashforge_adventurer_4", p.modelId)
        assertEquals("A4", p.name)
    }

    @Test
    fun otherSizesAreIgnored() {
        assertNull(Discovery.parseFlashForgeReply("1.1.1.1", ByteArray(50)))
    }

    @Test
    fun hostPortParsing() {
        assertEquals("10.0.0.2" to 5000, HostPort.parse("10.0.0.2:5000", 80))
        assertEquals("10.0.0.2" to 80, HostPort.parse("10.0.0.2", 80))
        assertEquals("printer.local" to 7125, HostPort.parse(" printer.local ", 7125))
    }
}
