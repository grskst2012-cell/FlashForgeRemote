package com.grsks.printerremote.printer.octoprint

import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterException
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterSnapshot
import com.grsks.printerremote.printer.Temperature
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Разбор ответов REST API OctoPrint. */
object OctoPrintParsers {

    private fun obj(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw PrinterException("Ответ не похож на OctoPrint (проверьте IP и порт)")
    }

    fun parseInfo(versionBody: String, fallbackModel: String): PrinterInfo {
        val o = obj(versionBody)
        return PrinterInfo(
            machineType = fallbackModel,
            name = "OctoPrint",
            firmware = o.optString("text").ifBlank { o.optString("server") },
            serial = "",
            buildVolume = null,
            mac = null,
        )
    }

    /** [printerBody] может быть null, если принтер не подключён к OctoPrint (HTTP 409). */
    fun parseSnapshot(jobBody: String, printerBody: String?): PrinterSnapshot {
        val job = obj(jobBody)
        val raw = job.optString("state")
        val progress = job.optJSONObject("progress")
        val completion = progress?.takeIf { !it.isNull("completion") }?.optDouble("completion", Double.NaN) ?: Double.NaN
        val fraction = if (completion.isNaN()) null else (completion / 100.0).toFloat().coerceIn(0f, 1f)
        val state = mapState(raw, fraction)
        val active = state == PrintState.PRINTING || state == PrintState.PAUSED || state == PrintState.COMPLETED
        val left = progress?.takeIf { !it.isNull("printTimeLeft") }?.optInt("printTimeLeft", -1)?.takeIf { it > 0 }
        val file = job.optJSONObject("job")?.optJSONObject("file")?.optString("name")

        var nozzle: Temperature? = null
        var bed: Temperature? = null
        if (printerBody != null) {
            val temp = obj(printerBody).optJSONObject("temperature")
            nozzle = temperature(temp?.optJSONObject("tool0"))
            bed = temperature(temp?.optJSONObject("bed"))
        }
        return PrinterSnapshot(
            state = state,
            rawState = raw,
            moveMode = "",
            nozzle = nozzle,
            bed = bed,
            progress = if (active) fraction else null,
            layer = null,
            totalLayers = null,
            currentFile = file?.takeIf { it.isNotBlank() && active },
            lightOn = null,
            remainingSeconds = left.takeIf { state == PrintState.PRINTING },
        )
    }

    /** Плоский список файлов (с учётом папок), новые сверху. */
    fun parseFiles(body: String): List<String> {
        val files = obj(body).optJSONArray("files") ?: return emptyList()
        val out = mutableListOf<Pair<String, Long>>()
        fun walk(array: JSONArray) {
            for (i in 0 until array.length()) {
                val f = array.optJSONObject(i) ?: continue
                f.optJSONArray("children")?.let { walk(it) }
                if (f.optString("type") == "machinecode") {
                    out += f.optString("path").ifBlank { f.optString("name") } to f.optLong("date", 0)
                }
            }
        }
        walk(files)
        return out.sortedByDescending { it.second }.map { it.first }
    }

    fun mapState(raw: String, fraction: Float?): PrintState {
        val s = raw.lowercase()
        return when {
            s.startsWith("printing") || s == "finishing" || s.startsWith("starting") -> PrintState.PRINTING
            s.startsWith("paus") -> PrintState.PAUSED
            s.startsWith("cancel") -> PrintState.BUSY
            s.contains("error") -> PrintState.ERROR
            s == "operational" -> if (fraction != null && fraction >= 0.999f) PrintState.COMPLETED else PrintState.IDLE
            s.isEmpty() || s.startsWith("offline") || s.startsWith("closed") -> PrintState.UNKNOWN
            else -> PrintState.BUSY
        }
    }

    private fun temperature(o: JSONObject?): Temperature? =
        if (o == null || o.isNull("actual")) {
            null
        } else {
            Temperature(o.optDouble("actual").toFloat(), o.optDouble("target", 0.0).toFloat())
        }
}
