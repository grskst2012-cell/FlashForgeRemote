package com.grsks.printerremote.printer.flashforge

import com.grsks.printerremote.net.Http
import com.grsks.printerremote.printer.Axis
import com.grsks.printerremote.printer.PrinterDriver
import com.grsks.printerremote.printer.PrinterException
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterModel
import com.grsks.printerremote.printer.PrinterSnapshot
import org.json.JSONObject

/** Драйвер FlashForge с HTTP API на порту 8898: Adventurer 5M, 5M Pro, AD5X. */
class FlashForgeHttpDriver(
    override val model: PrinterModel,
    private val host: String,
    private val serial: String,
    private val checkCode: String,
) : PrinterDriver {

    private val base = "http://$host:${model.controlPort}"

    @Volatile
    private var streamUrl: String? = null

    override suspend fun connect(): PrinterInfo {
        if (serial.isBlank() || checkCode.isBlank()) {
            throw PrinterException("Укажите серийный номер и код доступа в настройках принтера")
        }
        val body = post("/detail")
        streamUrl = FlashForgeHttpParsers.cameraUrl(body)
        return FlashForgeHttpParsers.parseInfo(body, model.fullName)
    }

    override suspend fun poll(): PrinterSnapshot = FlashForgeHttpParsers.parseSnapshot(post("/detail"))

    override suspend fun pause() = job("pause")
    override suspend fun resume() = job("continue")
    override suspend fun cancel() = job("cancel")

    override suspend fun setLight(on: Boolean) {
        control("lightControl_cmd", JSONObject().put("status", if (on) "open" else "close"))
    }

    override suspend fun setNozzleTemp(celsius: Int, tool: Int) {
        val t = celsius.coerceIn(0, model.capabilities.maxNozzleTemp)
        control("temperatureCtl_cmd", temps(nozzle = if (t == 0) OFF else t, bed = NO_CHANGE))
    }

    override suspend fun setBedTemp(celsius: Int) {
        val t = celsius.coerceIn(0, model.capabilities.maxBedTemp)
        control("temperatureCtl_cmd", temps(nozzle = NO_CHANGE, bed = if (t == 0) OFF else t))
    }

    override suspend fun home() {
        throw PrinterException("Эта модель не поддерживает ручное управление осями")
    }

    override suspend fun jog(axis: Axis, distanceMm: Float) {
        throw PrinterException("Эта модель не поддерживает ручное управление осями")
    }

    override suspend fun listFiles(): List<String> = FlashForgeHttpParsers.parseFileList(post("/gcodeList"))

    override suspend fun startPrint(fileName: String) {
        FlashForgeHttpParsers.checked(
            post("/printGcode", JSONObject().put("fileName", fileName).put("levelingBeforePrint", false)),
        )
    }

    override suspend fun upload(fileName: String, data: ByteArray, onProgress: (Float) -> Unit) {
        val response = Http.multipart(
            url = "$base/uploadGcode",
            headers = mapOf(
                "serialNumber" to serial,
                "checkCode" to checkCode,
                "fileSize" to data.size.toString(),
                "printNow" to "false",
                "levelingBeforePrint" to "false",
            ),
            fileField = "gcodeFile",
            fileName = fileName,
            data = data,
            onProgress = onProgress,
        )
        FlashForgeHttpParsers.checked(response.body)
        onProgress(1f)
    }

    override fun cameraStreamUrl(): String? {
        streamUrl?.let { return it }
        return if (model.capabilities.camera) model.cameraUrlTemplate?.replace("{host}", host) else null
    }

    override fun close() = Unit

    private suspend fun job(action: String) {
        control("jobCtl_cmd", JSONObject().put("jobID", "").put("action", action))
    }

    private suspend fun control(cmd: String, args: JSONObject) {
        val payload = JSONObject().put("cmd", cmd).put("args", args)
        FlashForgeHttpParsers.checked(post("/control", JSONObject().put("payload", payload)))
    }

    private fun temps(nozzle: Int, bed: Int) = JSONObject()
        .put("platform", bed)
        .put("rightNozzle", nozzle)
        .put("leftNozzle", NO_CHANGE)
        .put("chamber", NO_CHANGE)

    private suspend fun post(path: String, extra: JSONObject = JSONObject()): String {
        val body = extra.put("serialNumber", serial).put("checkCode", checkCode)
        val response = Http.call(
            url = base + path,
            method = "POST",
            body = body.toString().toByteArray(),
            contentType = "application/json",
        )
        if (!response.ok) throw PrinterException("HTTP ${response.code}")
        return response.body
    }

    private companion object {
        const val OFF = -100
        const val NO_CHANGE = -200
    }
}
