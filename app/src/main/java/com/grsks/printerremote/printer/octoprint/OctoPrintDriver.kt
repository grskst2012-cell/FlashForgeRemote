package com.grsks.printerremote.printer.octoprint

import com.grsks.printerremote.net.Http
import com.grsks.printerremote.printer.Axis
import com.grsks.printerremote.printer.PrinterDriver
import com.grsks.printerremote.printer.PrinterException
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterModel
import com.grsks.printerremote.printer.PrinterSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Драйвер OctoPrint (REST, опрос раз в несколько секунд; без SockJS). */
class OctoPrintDriver(
    override val model: PrinterModel,
    private val host: String,
    private val apiKey: String,
) : PrinterDriver {

    private val base = if (model.controlPort == 80) "http://$host" else "http://$host:${model.controlPort}"
    private val headers get() = mapOf("X-Api-Key" to apiKey)

    override suspend fun connect(): PrinterInfo {
        if (apiKey.isBlank()) throw PrinterException("Укажите API-ключ OctoPrint в настройках принтера")
        return OctoPrintParsers.parseInfo(get("/api/version"), model.fullName)
    }

    override suspend fun poll(): PrinterSnapshot {
        val job = get("/api/job")
        // 409 — принтер не подключён к OctoPrint; статус всё равно покажем.
        val printer = Http.call("$base/api/printer", headers = headers).let { if (it.ok) it.body else null }
        return OctoPrintParsers.parseSnapshot(job, printer)
    }

    override suspend fun pause() = command("/api/job", JSONObject().put("command", "pause").put("action", "pause"))
    override suspend fun resume() = command("/api/job", JSONObject().put("command", "pause").put("action", "resume"))
    override suspend fun cancel() = command("/api/job", JSONObject().put("command", "cancel"))

    override suspend fun setLight(on: Boolean) {
        throw PrinterException("Подсветка не поддерживается")
    }

    override suspend fun setNozzleTemp(celsius: Int, tool: Int) = command(
        "/api/printer/tool",
        JSONObject().put("command", "target")
            .put("targets", JSONObject().put("tool$tool", celsius.coerceIn(0, model.capabilities.maxNozzleTemp))),
    )

    override suspend fun setBedTemp(celsius: Int) = command(
        "/api/printer/bed",
        JSONObject().put("command", "target").put("target", celsius.coerceIn(0, model.capabilities.maxBedTemp)),
    )

    override suspend fun home() = command(
        "/api/printer/printhead",
        JSONObject().put("command", "home").put("axes", JSONArray(listOf("x", "y", "z"))),
    )

    override suspend fun jog(axis: Axis, distanceMm: Float) = command(
        "/api/printer/printhead",
        JSONObject().put("command", "jog").put(axis.name.lowercase(Locale.ROOT), distanceMm.toDouble()),
    )

    override suspend fun listFiles(): List<String> =
        OctoPrintParsers.parseFiles(get("/api/files/local?recursive=true"))

    override suspend fun startPrint(fileName: String) = command(
        "/api/files/local/${fileName.split('/').joinToString("/") { Http.encode(it) }}",
        JSONObject().put("command", "select").put("print", true),
    )

    override suspend fun upload(fileName: String, data: ByteArray, onProgress: (Float) -> Unit) {
        val r = Http.multipart(
            url = "$base/api/files/local",
            headers = headers,
            fields = mapOf("select" to "false", "print" to "false"),
            fileField = "file",
            fileName = fileName,
            data = data,
            onProgress = onProgress,
        )
        if (!r.ok) throw PrinterException(errorText(r))
        onProgress(1f)
    }

    override fun cameraStreamUrl(): String? =
        if (model.capabilities.camera) model.cameraUrlTemplate?.replace("{host}", host) else null

    override fun close() = Unit

    private suspend fun get(path: String): String {
        val r = Http.call(base + path, headers = headers)
        if (!r.ok) throw PrinterException(errorText(r))
        return r.body
    }

    private suspend fun command(path: String, body: JSONObject) {
        val r = Http.call(base + path, "POST", headers, body.toString().toByteArray(), "application/json")
        if (!r.ok) throw PrinterException(errorText(r))
    }

    private fun errorText(r: Http.Response): String = when (r.code) {
        401, 403 -> "Неверный API-ключ OctoPrint"
        409 -> "Принтер не подключён к OctoPrint или находится в другом состоянии"
        else -> "HTTP ${r.code}"
    }
}
