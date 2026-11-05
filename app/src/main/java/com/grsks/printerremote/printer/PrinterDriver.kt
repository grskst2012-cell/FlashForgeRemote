package com.grsks.printerremote.printer

import com.grsks.printerremote.data.SavedPrinter
import com.grsks.printerremote.net.HostPort
import com.grsks.printerremote.printer.flashforge.FlashForgeHttpDriver
import com.grsks.printerremote.printer.flashforge.FlashForgeLegacyDriver
import com.grsks.printerremote.printer.octoprint.OctoPrintDriver
import com.grsks.printerremote.printer.moonraker.MoonrakerDriver
import java.io.IOException

/** Ошибка, о которой сообщил сам принтер (а не сеть). */
class PrinterException(message: String) : IOException(message)

/**
 * Общий интерфейс для всех принтеров. UI работает только с ним,
 * поэтому новая модель или протокол не требуют правок в экранах.
 */
interface PrinterDriver {
    val model: PrinterModel

    suspend fun connect(): PrinterInfo
    suspend fun poll(): PrinterSnapshot

    suspend fun pause()
    suspend fun resume()
    suspend fun cancel()

    suspend fun setLight(on: Boolean)
    suspend fun setNozzleTemp(celsius: Int, tool: Int = 0)
    suspend fun setBedTemp(celsius: Int)
    suspend fun home()
    suspend fun jog(axis: Axis, distanceMm: Float)

    suspend fun listFiles(): List<String>
    suspend fun startPrint(fileName: String)
    suspend fun upload(fileName: String, data: ByteArray, onProgress: (Float) -> Unit)

    fun cameraStreamUrl(): String?

    /** Закрыть соединение. Следующая команда откроет его заново. */
    fun close()
}

object PrinterDrivers {
    /** Хост может содержать порт («192.168.1.5:7125»): тогда он заменяет порт модели. */
    fun create(model: PrinterModel, printer: SavedPrinter): PrinterDriver {
        val (host, port) = HostPort.parse(printer.host, model.controlPort)
        val m = if (port == model.controlPort) model else model.copy(controlPort = port)
        return when (m.protocol) {
            PrinterProtocol.FLASHFORGE_LEGACY_TCP -> FlashForgeLegacyDriver(m, host)
            PrinterProtocol.MOONRAKER -> MoonrakerDriver(m, host)
            PrinterProtocol.FLASHFORGE_HTTP -> FlashForgeHttpDriver(m, host, printer.serial.trim(), printer.accessCode.trim())
            PrinterProtocol.OCTOPRINT -> OctoPrintDriver(m, host, printer.accessCode.trim())
        }
    }
}
