package com.grsks.printerremote.printer.flashforge

import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterException
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterSnapshot
import com.grsks.printerremote.printer.Temperature
import org.json.JSONException
import org.json.JSONObject

/** Разбор ответов HTTP API FlashForge (порт 8898). HTTP-статус всегда 200, успех — поле `code == 0`. */
object FlashForgeHttpParsers {

    /** Проверяет `code` и возвращает корень ответа. */
    fun checked(body: String): JSONObject {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw PrinterException("Ответ не похож на принтер FlashForge (проверьте IP)")
        }
        when (val code = root.optInt("code", Int.MIN_VALUE)) {
            0 -> return root
            1 -> throw PrinterException("Неверный серийный номер или код доступа")
            -2 -> throw PrinterException("На принтере выключен LAN-режим (Настройки → Сеть)")
            2 -> throw PrinterException("Принтер занят")
            3 -> throw PrinterException("Файл не найден на принтере")
            else -> throw PrinterException(
                root.optString("message").ifBlank { "Ошибка принтера (код $code)" },
            )
        }
    }

    fun parseInfo(body: String, fallbackModel: String): PrinterInfo {
        val d = detail(body)
        return PrinterInfo(
            machineType = fallbackModel,
            name = d.optString("name"),
            firmware = d.optString("firmwareVersion"),
            serial = d.optString("serialNumber"),
            buildVolume = null,
            mac = d.optString("macAddr").takeIf { it.isNotBlank() },
        )
    }

    fun parseSnapshot(body: String): PrinterSnapshot {
        val d = detail(body)
        val raw = d.optString("status")
        val state = mapState(raw)
        val active = state == PrintState.PRINTING || state == PrintState.PAUSED || state == PrintState.COMPLETED
        val progress = when {
            state == PrintState.COMPLETED -> 1f
            active -> d.optDouble("printProgress", 0.0).toFloat().coerceIn(0f, 1f)
            else -> null
        }
        val layer = d.optInt("printLayer", 0).takeIf { it > 0 && active }
        val total = d.optInt("targetPrintLayer", 0).takeIf { it > 0 && active }
        val remaining = d.optInt("estimatedTime", 0).takeIf { it > 0 && state == PrintState.PRINTING }
        return PrinterSnapshot(
            state = state,
            rawState = raw,
            moveMode = "",
            nozzle = temp(d, "rightTemp", "rightTargetTemp"),
            bed = temp(d, "platTemp", "platTargetTemp"),
            progress = progress,
            layer = layer,
            totalLayers = total,
            currentFile = d.optString("printFileName").takeIf { it.isNotBlank() && active },
            lightOn = when (d.optString("lightStatus")) {
                "open" -> true
                "close" -> false
                else -> null
            },
            remainingSeconds = remaining,
        )
    }

    fun cameraUrl(body: String): String? = runCatching {
        detail(body).optString("cameraStreamUrl").takeIf { it.startsWith("http") }
    }.getOrNull()

    fun parseFileList(body: String): List<String> {
        val root = checked(body)
        val array = root.optJSONArray("gcodeList") ?: root.optJSONArray("gcodeListDetail") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            when (val item = array.get(i)) {
                is String -> item
                is JSONObject -> item.optString("gcodeFileName").ifBlank { item.optString("fileName") }
                else -> null
            }?.takeIf { it.isNotBlank() }
        }
    }

    private fun detail(body: String): JSONObject =
        checked(body).optJSONObject("detail") ?: throw PrinterException("В ответе принтера нет блока detail")

    private fun temp(d: JSONObject, cur: String, target: String): Temperature? =
        if (d.has(cur)) Temperature(d.optDouble(cur).toFloat(), d.optDouble(target, 0.0).toFloat()) else null

    // Неизвестные статусы считаем «занят», как рекомендует описание протокола.
    fun mapState(raw: String): PrintState = when (raw) {
        "ready" -> PrintState.IDLE
        "printing" -> PrintState.PRINTING
        "pausing", "pause" -> PrintState.PAUSED
        "completed" -> PrintState.COMPLETED
        "cancel", "canceling" -> PrintState.IDLE
        "error" -> PrintState.ERROR
        else -> PrintState.BUSY
    }
}
