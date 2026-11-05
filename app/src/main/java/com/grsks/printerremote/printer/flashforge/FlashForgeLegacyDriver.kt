package com.grsks.printerremote.printer.flashforge

import com.grsks.printerremote.printer.Axis
import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterDriver
import com.grsks.printerremote.printer.PrinterException
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterModel
import com.grsks.printerremote.printer.PrinterSnapshot
import java.util.Locale

/** Драйвер для принтеров FlashForge со старым TCP-протоколом (Adventurer 3/4 и родственные). */
class FlashForgeLegacyDriver(
    override val model: PrinterModel,
    private val host: String,
) : PrinterDriver {

    private val client = FlashForgeTcpClient(host, model.controlPort) { send ->
        // Принтер принимает команды только после захвата управления.
        val reply = send("M601 S1")
        if (reply.contains("fail", ignoreCase = true)) {
            throw PrinterException("Принтер не дал управление (занят другой программой?): ${reply.trim()}")
        }
    }

    override suspend fun connect(): PrinterInfo =
        FlashForgeParsers.parseInfo(client.command("M115"))

    override suspend fun poll(): PrinterSnapshot {
        val status = FlashForgeParsers.parseStatus(client.command("M119"))
        val (nozzle, bed) = FlashForgeParsers.parseTemperatures(client.command("M105"))
        val progress = if (status.state in ACTIVE_STATES) {
            FlashForgeParsers.parseProgress(client.command("M27"))
        } else {
            null
        }
        return PrinterSnapshot(
            state = status.state,
            rawState = status.machineStatus,
            moveMode = status.moveMode,
            nozzle = nozzle,
            bed = bed,
            progress = progress?.fraction,
            layer = progress?.layer,
            totalLayers = progress?.totalLayers,
            currentFile = status.currentFile,
            lightOn = status.lightOn,
        )
    }

    override suspend fun pause() { checked("M25") }
    override suspend fun resume() { checked("M24") }
    override suspend fun cancel() { checked("M26") }

    override suspend fun setLight(on: Boolean) {
        checked(if (on) "M146 r255 g255 b255 F0" else "M146 r0 g0 b0 F0")
    }

    override suspend fun setNozzleTemp(celsius: Int, tool: Int) {
        checked("M104 S${celsius.coerceIn(0, model.capabilities.maxNozzleTemp)} T$tool")
    }

    override suspend fun setBedTemp(celsius: Int) {
        checked("M140 S${celsius.coerceIn(0, model.capabilities.maxBedTemp)}")
    }

    override suspend fun home() { checked("G28", timeoutMs = 30_000) }

    override suspend fun jog(axis: Axis, distanceMm: Float) {
        val feed = if (axis == Axis.Z) 600 else 3000
        val distance = String.format(Locale.US, "%.2f", distanceMm)
        checked("G91")
        try {
            checked("G1 ${axis.name}$distance F$feed", timeoutMs = 15_000)
        } finally {
            checked("G90")
        }
    }

    override suspend fun listFiles(): List<String> =
        FlashForgeParsers.parseFileList(client.commandBinary("M661"))

    override suspend fun startPrint(fileName: String) {
        checked("M23 0:/user/$fileName", timeoutMs = 15_000)
    }

    override suspend fun upload(fileName: String, data: ByteArray, onProgress: (Float) -> Unit) =
        client.upload(fileName, data, onProgress)

    override fun cameraStreamUrl(): String? =
        if (model.capabilities.camera) model.cameraUrlTemplate?.replace("{host}", host) else null

    override fun close() = client.close()

    private suspend fun checked(cmd: String, timeoutMs: Int = FlashForgeTcpClient.DEFAULT_TIMEOUT_MS): String {
        val reply = client.command(cmd, timeoutMs)
        if (reply.contains("fail", ignoreCase = true) || reply.contains("error", ignoreCase = true)) {
            val reason = reply.lines().firstOrNull { it.isNotBlank() && !it.startsWith("CMD") }?.trim()
            throw PrinterException(reason ?: reply.trim())
        }
        return reply
    }

    private companion object {
        val ACTIVE_STATES = setOf(PrintState.PRINTING, PrintState.PAUSED, PrintState.COMPLETED)
    }
}
