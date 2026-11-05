package com.grsks.printerremote.printer.moonraker

import com.grsks.printerremote.printer.Axis
import com.grsks.printerremote.printer.PrinterDriver
import com.grsks.printerremote.printer.PrinterException
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterModel
import com.grsks.printerremote.printer.PrinterSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

/** Драйвер Klipper-принтеров через HTTP API Moonraker. Авторизация не поддерживается (доверенные клиенты LAN). */
class MoonrakerDriver(
    override val model: PrinterModel,
    private val host: String,
) : PrinterDriver {

    private val base = "http://$host:${model.controlPort}"

    override suspend fun connect(): PrinterInfo =
        MoonrakerParsers.parseInfo(get("/printer/info"), model.fullName)

    override suspend fun poll(): PrinterSnapshot =
        MoonrakerParsers.parseStatus(get("/printer/objects/query?${MoonrakerParsers.STATUS_QUERY}"))

    override suspend fun pause() { post("/printer/print/pause") }
    override suspend fun resume() { post("/printer/print/resume") }
    override suspend fun cancel() { post("/printer/print/cancel") }

    override suspend fun setLight(on: Boolean) =
        throw PrinterException("Подсветка для Klipper не поддерживается")

    override suspend fun setNozzleTemp(celsius: Int, tool: Int) {
        gcode("M104 S${celsius.coerceIn(0, model.capabilities.maxNozzleTemp)}")
    }

    override suspend fun setBedTemp(celsius: Int) {
        gcode("M140 S${celsius.coerceIn(0, model.capabilities.maxBedTemp)}")
    }

    override suspend fun home() { gcode("G28", timeoutMs = 120_000) }

    override suspend fun jog(axis: Axis, distanceMm: Float) {
        val feed = if (axis == Axis.Z) 600 else 3000
        val distance = String.format(Locale.US, "%.2f", distanceMm)
        gcode("G91\nG1 ${axis.name}$distance F$feed\nG90", timeoutMs = 30_000)
    }

    override suspend fun listFiles(): List<String> =
        MoonrakerParsers.parseFileList(get("/server/files/list?root=gcodes"))

    override suspend fun startPrint(fileName: String) {
        post("/printer/print/start?filename=${enc(fileName)}")
    }

    override suspend fun upload(fileName: String, data: ByteArray, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val boundary = "----PrinterRemote${UUID.randomUUID()}"
            val head = ("--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"root\"\r\n\r\ngcodes\r\n" +
                "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "_")}\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n").toByteArray()
            val tail = "\r\n--$boundary--\r\n".toByteArray()

            val conn = open("/server/files/upload", "POST", timeoutMs = 60_000)
            try {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.setFixedLengthStreamingMode((head.size + data.size + tail.size).toLong())
                conn.outputStream.use { out ->
                    out.write(head)
                    var sent = 0
                    while (sent < data.size) {
                        val n = minOf(CHUNK, data.size - sent)
                        out.write(data, sent, n)
                        sent += n
                        onProgress(sent.toFloat() / data.size)
                    }
                    out.write(tail)
                }
                readResponse(conn)
            } finally {
                conn.disconnect()
            }
        }
        onProgress(1f)
    }

    override fun cameraStreamUrl(): String? =
        if (model.capabilities.camera) model.cameraUrlTemplate?.replace("{host}", host) else null

    override fun close() = Unit

    // ------------------------------------------------------------ HTTP

    private suspend fun gcode(script: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS) =
        post("/printer/gcode/script?script=${enc(script)}", timeoutMs)

    private suspend fun get(path: String): String = request(path, "GET", DEFAULT_TIMEOUT_MS)

    private suspend fun post(path: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String =
        request(path, "POST", timeoutMs)

    private suspend fun request(path: String, method: String, timeoutMs: Int): String =
        withContext(Dispatchers.IO) {
            val conn = open(path, method, timeoutMs)
            try {
                readResponse(conn)
            } finally {
                conn.disconnect()
            }
        }

    private fun open(path: String, method: String, timeoutMs: Int): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = timeoutMs
        }

    private fun readResponse(conn: HttpURLConnection): String {
        val code = conn.responseCode
        if (code in 200..299) return conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        val body = conn.errorStream?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
        val message = MoonrakerParsers.errorMessage(body)
            ?: if (code == 401 || code == 403) "Moonraker требует авторизацию (HTTP $code)" else "HTTP $code"
        throw PrinterException(message)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000
        const val CHUNK = 16 * 1024
    }
}
