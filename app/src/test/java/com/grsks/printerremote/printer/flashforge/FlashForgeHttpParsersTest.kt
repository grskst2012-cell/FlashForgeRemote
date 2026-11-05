package com.grsks.printerremote.printer.flashforge

import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashForgeHttpParsersTest {

    private fun detail(status: String, progress: Double = 0.5) = """{"code":0,"message":"Success","detail":{
        "status":"$status","printProgress":$progress,"printLayer":10,"targetPrintLayer":100,"estimatedTime":1800,
        "rightTemp":215.2,"rightTargetTemp":215,"platTemp":59.8,"platTargetTemp":60,
        "lightStatus":"open","printFileName":"cube.gx","name":"Shop","firmwareVersion":"3.1.3",
        "cameraStreamUrl":"http://10.0.0.5:8080/?action=stream"}}"""

    @Test
    fun parsesPrinting() {
        val s = FlashForgeHttpParsers.parseSnapshot(detail("printing"))
        assertEquals(PrintState.PRINTING, s.state)
        assertEquals(0.5f, s.progress!!, 0.001f)
        assertEquals(10, s.layer)
        assertEquals(1800, s.remainingSeconds)
        assertEquals(215f, s.nozzle!!.target)
        assertEquals(true, s.lightOn)
        assertEquals("cube.gx", s.currentFile)
    }

    @Test
    fun readyHasNoProgress() {
        val s = FlashForgeHttpParsers.parseSnapshot(detail("ready"))
        assertEquals(PrintState.IDLE, s.state)
        assertNull(s.progress)
        assertNull(s.remainingSeconds)
    }

    @Test
    fun unknownStatusIsBusyAndErrorIsError() {
        assertEquals(PrintState.BUSY, FlashForgeHttpParsers.mapState("heating"))
        assertEquals(PrintState.BUSY, FlashForgeHttpParsers.mapState("something_new"))
        assertEquals(PrintState.ERROR, FlashForgeHttpParsers.mapState("error"))
    }

    @Test
    fun mapsErrorCodesToMessages() {
        val wrongCode = runCatching { FlashForgeHttpParsers.checked("""{"code":1,"message":"x"}""") }.exceptionOrNull()
        assertTrue(wrongCode is PrinterException)
        assertTrue(wrongCode!!.message!!.contains("код доступа"))
        val lan = runCatching { FlashForgeHttpParsers.checked("""{"code":-2}""") }.exceptionOrNull()
        assertTrue(lan!!.message!!.contains("LAN"))
    }

    @Test
    fun infoAndCamera() {
        val info = FlashForgeHttpParsers.parseInfo(detail("ready"), "FlashForge Adventurer 5M")
        assertEquals("Shop", info.name)
        assertEquals("3.1.3", info.firmware)
        assertEquals("http://10.0.0.5:8080/?action=stream", FlashForgeHttpParsers.cameraUrl(detail("ready")))
    }

    @Test
    fun fileListAcceptsStringsAndObjects() {
        assertEquals(
            listOf("a.gcode", "b.3mf"),
            FlashForgeHttpParsers.parseFileList("""{"code":0,"gcodeList":["a.gcode","b.3mf"]}"""),
        )
        assertEquals(
            listOf("c.gcode"),
            FlashForgeHttpParsers.parseFileList("""{"code":0,"gcodeListDetail":[{"gcodeFileName":"c.gcode"}]}"""),
        )
    }
}
