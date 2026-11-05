package com.grsks.printerremote.printer.moonraker

import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterException
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterSnapshot
import com.grsks.printerremote.printer.Temperature
import org.json.JSONException
import org.json.JSONObject

/** Разбор JSON-ответов Moonraker. Отделён от сети, чтобы его можно было проверить юнит-тестами. */
object MoonrakerParsers {

    /** Объекты Klipper, которые запрашиваются при каждом опросе. */
    const val STATUS_QUERY = "extruder&heater_bed&print_stats&virtual_sdcard"

    private fun result(body: String): JSONObject = try {
        val root = JSONObject(body)
        root.optJSONObject("result") ?: throw PrinterException("Неожиданный ответ Moonraker")
    } catch (e: JSONException) {
        throw PrinterException("Ответ не похож на Moonraker (проверьте IP и порт 7125)")
    }

    /** Текст ошибки из тела ответа с кодом 4xx/5xx. */
    fun errorMessage(body: String): String? = try {
        val error = JSONObject(body).opt("error")
        when (error) {
            is JSONObject -> error.optString("message").takeIf { it.isNotBlank() }
            is String -> error.takeIf { it.isNotBlank() }
            else -> null
        }
    } catch (e: JSONException) {
        null
    }

    fun parseInfo(printerInfoBody: String, fallbackModel: String): PrinterInfo {
        val r = result(printerInfoBody)
        val state = r.optString("state")
        if (state == "error" || state == "shutdown") {
            val message = r.optString("state_message").trim()
            throw PrinterException(
                if (message.isNotEmpty()) "Klipper: $message" else "Klipper в состоянии «$state»",
            )
        }
        return PrinterInfo(
            machineType = fallbackModel,
            name = r.optString("hostname"),
            firmware = r.optString("software_version"),
            serial = "",
            buildVolume = null,
            mac = null,
        )
    }

    fun parseStatus(body: String): PrinterSnapshot {
        val status = result(body).optJSONObject("status") ?: throw PrinterException("В ответе нет status")
        val stats = status.optJSONObject("print_stats")
        val raw = stats?.optString("state").orEmpty()
        val state = mapState(raw)
        val active = state == PrintState.PRINTING || state == PrintState.PAUSED || state == PrintState.COMPLETED

        val sdProgress = status.optJSONObject("virtual_sdcard")?.optDouble("progress", Double.NaN) ?: Double.NaN
        val progress = when {
            state == PrintState.COMPLETED -> 1f
            active && !sdProgress.isNaN() -> sdProgress.toFloat().coerceIn(0f, 1f)
            else -> null
        }

        val layerInfo = stats?.optJSONObject("info")
        val layer = layerInfo?.optInt("current_layer", -1)?.takeIf { it > 0 && active }
        val totalLayers = layerInfo?.optInt("total_layer", -1)?.takeIf { it > 0 && active }

        return PrinterSnapshot(
            state = state,
            rawState = raw,
            moveMode = "",
            nozzle = temperature(status.optJSONObject("extruder")),
            bed = temperature(status.optJSONObject("heater_bed")),
            progress = progress,
            layer = layer,
            totalLayers = totalLayers,
            currentFile = stats?.optString("filename")?.takeIf { it.isNotBlank() && active },
            lightOn = null,
            remainingSeconds = remaining(state, stats, progress),
        )
    }

    fun parseFileList(body: String): List<String> {
        val array = try {
            JSONObject(body).optJSONArray("result")
        } catch (e: JSONException) {
            null
        } ?: throw PrinterException("Неожиданный ответ со списком файлов")
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .sortedByDescending { it.optDouble("modified", 0.0) }
            .map { it.optString("path") }
            .filter { it.isNotBlank() }
    }

    /** Оценка по пройденному времени и доле файла; до 3% прогресса не показываем, она слишком неточна. */
    private fun remaining(state: PrintState, stats: JSONObject?, progress: Float?): Int? {
        if (state != PrintState.PRINTING || progress == null || progress < 0.03f) return null
        val elapsed = stats?.optDouble("print_duration", 0.0) ?: 0.0
        if (elapsed <= 0.0) return null
        return (elapsed / progress - elapsed).toInt().takeIf { it > 0 }
    }

    private fun mapState(raw: String): PrintState = when (raw) {
        "standby", "cancelled" -> PrintState.IDLE
        "printing" -> PrintState.PRINTING
        "paused" -> PrintState.PAUSED
        "complete" -> PrintState.COMPLETED
        "error" -> PrintState.ERROR
        else -> PrintState.UNKNOWN
    }

    private fun temperature(o: JSONObject?): Temperature? {
        if (o == null || !o.has("temperature")) return null
        return Temperature(o.optDouble("temperature").toFloat(), o.optDouble("target", 0.0).toFloat())
    }
}
