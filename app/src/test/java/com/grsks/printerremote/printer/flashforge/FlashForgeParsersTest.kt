package com.grsks.printerremote.printer.flashforge

import com.grsks.printerremote.printer.BuildVolume
import com.grsks.printerremote.printer.PrintState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FlashForgeParsersTest {

    @Test
    fun parsesM115Info() {
        val reply = "CMD M115 Received.\r\n" +
            "Machine Type: FlashForge Adventurer 4\r\n" +
            "Machine Name: Workshop\r\n" +
            "Firmware: v2.2.4\r\n" +
            "SN: SNADVA9501234\r\n" +
            "X: 220 Y: 200 Z: 250\r\n" +
            "Tool Count: 1\r\n" +
            "Mac Address:88:A9:A7:00:11:22\r\n" +
            "ok\r\n"
        val info = FlashForgeParsers.parseInfo(reply)
        assertEquals("FlashForge Adventurer 4", info.machineType)
        assertEquals("Workshop", info.name)
        assertEquals("v2.2.4", info.firmware)
        assertEquals("SNADVA9501234", info.serial)
        assertEquals(BuildVolume(220, 200, 250), info.buildVolume)
        assertEquals("88:A9:A7:00:11:22", info.mac)
    }

    @Test
    fun parsesTemperaturesWithAndWithoutDecimals() {
        val (n1, b1) = FlashForgeParsers.parseTemperatures("CMD M105 Received.\r\nT0:210 /210 B:60 /60\r\nok\r\n")
        assertEquals(210f, n1!!.current)
        assertEquals(60f, b1!!.target)

        val (n2, b2) = FlashForgeParsers.parseTemperatures("CMD M105 Received.\r\nT0:25.4/0.0 T1:0.0/0.0 B:24.1/0.0\r\nok\r\n")
        assertEquals(25.4f, n2!!.current)
        assertEquals(0f, n2.target)
        assertEquals(24.1f, b2!!.current)
    }

    @Test
    fun parsesM119Status() {
        val reply = "CMD M119 Received.\r\n" +
            "Endstop: X-max:0 Y-max:0 Z-max:0\r\n" +
            "MachineStatus: BUILDING_FROM_SD\r\n" +
            "MoveMode: MOVING\r\n" +
            "Status: S:1 L:0 J:0 F:0\r\n" +
            "LED: 1\r\n" +
            "CurrentFile: benchy.gx\r\n" +
            "ok\r\n"
        val status = FlashForgeParsers.parseStatus(reply)
        assertEquals(PrintState.PRINTING, status.state)
        assertEquals(true, status.lightOn)
        assertEquals("benchy.gx", status.currentFile)
    }

    @Test
    fun pausedMoveModeMeansPaused() {
        val reply = "MachineStatus: BUILDING_FROM_SD\r\nMoveMode: PAUSED\r\nLED: 0\r\nCurrentFile: \r\nok\r\n"
        val status = FlashForgeParsers.parseStatus(reply)
        assertEquals(PrintState.PAUSED, status.state)
        assertEquals(false, status.lightOn)
        assertNull(status.currentFile)
    }

    @Test
    fun parsesM27Progress() {
        val p = FlashForgeParsers.parseProgress("CMD M27 Received.\r\nSD printing byte 37/100\r\nLayer: 54/210\r\nok\r\n")
        assertEquals(0.37f, p.fraction!!, 0.0001f)
        assertEquals(54, p.layer)
        assertEquals(210, p.totalLayers)

        assertNull(FlashForgeParsers.parseProgress("SD printing byte 0/0\r\nok\r\n").fraction)
    }

    @Test
    fun parsesBinaryM661FileList() {
        val out = ByteArrayOutputStream()
        out.write("CMD M661 Received.\r\nList files.\r\nok\r\n".toByteArray())
        val names = listOf("/data/cube.gx", "/data/кубик.gcode")
        out.write(byteArrayOf(0x44, 0xAA.toByte(), 0xAA.toByte(), 0x44))
        out.write(ByteBuffer.allocate(4).putInt(names.size).array())
        names.forEach { name ->
            val bytes = name.toByteArray(Charsets.UTF_8)
            out.write(byteArrayOf(0x3A, 0x3A, 0xA3.toByte(), 0xA3.toByte()))
            out.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
            out.write(bytes)
        }
        assertEquals(listOf("cube.gx", "кубик.gcode"), FlashForgeParsers.parseFileList(out.toByteArray()))
    }
}
