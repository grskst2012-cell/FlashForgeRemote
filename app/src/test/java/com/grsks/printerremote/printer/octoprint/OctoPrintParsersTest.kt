package com.grsks.printerremote.printer.octoprint

import com.grsks.printerremote.printer.PrintState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OctoPrintParsersTest {

    private fun job(state: String, completion: String = "42.5") =
        """{"state":"$state","job":{"file":{"name":"benchy.gcode"}},
            "progress":{"completion":$completion,"printTimeLeft":600}}"""

    private val printer = """{"temperature":{"tool0":{"actual":210.0,"target":215.0},"bed":{"actual":60.1,"target":60.0}}}"""

    @Test
    fun parsesPrinting() {
        val s = OctoPrintParsers.parseSnapshot(job("Printing"), printer)
        assertEquals(PrintState.PRINTING, s.state)
        assertEquals(0.425f, s.progress!!, 0.001f)
        assertEquals(600, s.remainingSeconds)
        assertEquals(215f, s.nozzle!!.target)
        assertEquals("benchy.gcode", s.currentFile)
    }

    @Test
    fun operationalAtFullIsCompleted() {
        assertEquals(PrintState.COMPLETED, OctoPrintParsers.parseSnapshot(job("Operational", "100.0"), printer).state)
        assertEquals(PrintState.IDLE, OctoPrintParsers.parseSnapshot(job("Operational", "null"), printer).state)
    }

    @Test
    fun worksWithoutPrinterBody() {
        val s = OctoPrintParsers.parseSnapshot(job("Paused"), null)
        assertEquals(PrintState.PAUSED, s.state)
        assertNull(s.nozzle)
    }

    @Test
    fun stateMapping() {
        assertEquals(PrintState.ERROR, OctoPrintParsers.mapState("Error: thermal runaway", null))
        assertEquals(PrintState.UNKNOWN, OctoPrintParsers.mapState("Offline", null))
        assertEquals(PrintState.BUSY, OctoPrintParsers.mapState("Cancelling", null))
    }

    @Test
    fun filesAreFlattenedAndSorted() {
        val body = """{"files":[
            {"name":"old.gcode","path":"old.gcode","type":"machinecode","date":1},
            {"name":"dir","type":"folder","children":[{"name":"new.gcode","path":"dir/new.gcode","type":"machinecode","date":9}]},
            {"name":"model.stl","type":"model","date":99}]}"""
        assertEquals(listOf("dir/new.gcode", "old.gcode"), OctoPrintParsers.parseFiles(body))
    }
}
