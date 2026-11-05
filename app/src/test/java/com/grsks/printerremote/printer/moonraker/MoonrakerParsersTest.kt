package com.grsks.printerremote.printer.moonraker

import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterException
import com.grsks.printerremote.printer.PrinterModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonrakerParsersTest {

    private fun status(state: String, progress: Double, file: String = "cube.gcode", layer: String = "null") =
        """{"result":{"eventtime":1.0,"status":{
            "extruder":{"temperature":214.6,"target":215.0},
            "heater_bed":{"temperature":59.9,"target":60.0},
            "print_stats":{"filename":"$file","state":"$state","info":{"total_layer":120,"current_layer":$layer}},
            "virtual_sdcard":{"progress":$progress}}}}"""

    @Test
    fun parsesPrintingStatus() {
        val s = MoonrakerParsers.parseStatus(status("printing", 0.42, layer = "50"))
        assertEquals(PrintState.PRINTING, s.state)
        assertEquals(0.42f, s.progress!!, 0.001f)
        assertEquals(50, s.layer)
        assertEquals(120, s.totalLayers)
        assertEquals(215f, s.nozzle!!.target)
        assertEquals("cube.gcode", s.currentFile)
    }

    @Test
    fun idleHasNoProgressOrFile() {
        val s = MoonrakerParsers.parseStatus(status("standby", 0.0, file = ""))
        assertEquals(PrintState.IDLE, s.state)
        assertNull(s.progress)
        assertNull(s.currentFile)
        assertNull(s.layer)
    }

    @Test
    fun completeIsFullProgress() {
        val s = MoonrakerParsers.parseStatus(status("complete", 0.99))
        assertEquals(PrintState.COMPLETED, s.state)
        assertEquals(1f, s.progress!!, 0f)
    }

    @Test
    fun cancelledMapsToIdle() {
        assertEquals(PrintState.IDLE, MoonrakerParsers.parseStatus(status("cancelled", 0.3)).state)
    }

    @Test
    fun infoFailsWhenKlipperIsInError() {
        val body = """{"result":{"state":"error","state_message":"MCU shutdown"}}"""
        val e = runCatching { MoonrakerParsers.parseInfo(body, "X") }.exceptionOrNull()
        assertTrue(e is PrinterException)
        assertTrue(e!!.message!!.contains("MCU shutdown"))
    }

    @Test
    fun infoWhenReady() {
        val info = MoonrakerParsers.parseInfo(
            """{"result":{"state":"ready","hostname":"voron","software_version":"v0.12.0"}}""",
            "Klipper X",
        )
        assertEquals("voron", info.name)
        assertEquals("v0.12.0", info.firmware)
    }

    @Test
    fun filesSortedNewestFirst() {
        val body = """{"result":[{"path":"old.gcode","modified":1.0},{"path":"sub/new.gcode","modified":9.0}]}"""
        assertEquals(listOf("sub/new.gcode", "old.gcode"), MoonrakerParsers.parseFileList(body))
    }

    @Test
    fun extractsErrorMessage() {
        assertEquals("Must home axis first", MoonrakerParsers.errorMessage("""{"error":{"code":400,"message":"Must home axis first"}}"""))
        assertNull(MoonrakerParsers.errorMessage("<html>"))
    }

    @Test
    fun detectPrefersLongestFlashForgeAlias() {
        assertEquals("flashforge_adventurer_3_pro", PrinterModels.detect("FlashForge Adventurer 3 Pro")!!.id)
        assertEquals("flashforge_adventurer_4", PrinterModels.detect("FlashForge Adventurer 4")!!.id)
    }
}
