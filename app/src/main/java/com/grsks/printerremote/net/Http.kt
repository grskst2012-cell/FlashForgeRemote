package com.grsks.printerremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Минимальный HTTP-клиент без зависимостей. Статус не превращает в исключение: это решает вызывающий. */
object Http {

    class Response(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    suspend fun call(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null,
        timeoutMs: Int = 10_000,
        connectTimeoutMs: Int = 5_000,
    ): Response = withContext(Dispatchers.IO) {
        val conn = open(url, method, headers, timeoutMs, connectTimeoutMs)
        try {
            if (body != null) {
                conn.doOutput = true
                contentType?.let { conn.setRequestProperty("Content-Type", it) }
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            read(conn)
        } finally {
            conn.disconnect()
        }
    }

    /** multipart/form-data с одним файлом; прогресс считается по отправленным байтам файла. */
    suspend fun multipart(
        url: String,
        headers: Map<String, String> = emptyMap(),
        fields: Map<String, String> = emptyMap(),
        fileField: String,
        fileName: String,
        data: ByteArray,
        timeoutMs: Int = 60_000,
        onProgress: (Float) -> Unit,
    ): Response = withContext(Dispatchers.IO) {
        val boundary = "----PrinterRemote${UUID.randomUUID()}"
        val head = buildString {
            fields.forEach { (k, v) ->
                append("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
            }
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"$fileField\"; filename=\"${fileName.replace("\"", "_")}\"\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()

        val conn = open(url, "POST", headers, timeoutMs, 5_000)
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
            read(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun open(
        url: String,
        method: String,
        headers: Map<String, String>,
        timeoutMs: Int,
        connectTimeoutMs: Int,
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = connectTimeoutMs
        readTimeout = timeoutMs
        useCaches = false
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }

    private fun read(conn: HttpURLConnection): Response {
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
        return Response(code, text)
    }

    private const val CHUNK = 16 * 1024
}

/** Разбор «хост» или «хост:порт», который пользователь вводит в поле адреса. */
object HostPort {
    fun parse(input: String, defaultPort: Int): Pair<String, Int> {
        val s = input.trim()
        val i = s.lastIndexOf(':')
        if (i > 0) {
            val port = s.substring(i + 1).toIntOrNull()
            if (port != null && port in 1..65535) return s.substring(0, i) to port
        }
        return s to defaultPort
    }
}
