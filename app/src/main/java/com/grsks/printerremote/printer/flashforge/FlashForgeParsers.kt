package com.grsks.printerremote.printer.flashforge

import com.grsks.printerremote.printer.BuildVolume
import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.Temperature

/** Разбор ответов FlashForge. Чистые функции, покрыты юнит-тестами. */
object FlashForgeParsers {

    data class Status(
        val state: PrintState,
        val machineStatus: String,
        val moveMode: String,
        val lightOn: Boolean?,
        val currentFile: String?,
    )

    data class Progress(val fraction: Float?, val layer: Int?, val totalLayers: Int?)

    private val nozzleRegex = Regex("""T0:\s*(-?[\d.]+)\s*/\s*(-?[\d.]+)""")
    private val bedRegex = Regex("""B:\s*(-?[\d.]+)\s*/\s*(-?[\d.]+)""")
    private val volumeRegex = Regex("""X:\s*(\d+)\s+Y:\s*(\d+)\s+Z:\s*(\d+)""")
    private val bytesRegex = Regex("""byte\s+(\d+)\s*/\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val layerRegex = Regex("""Layer:\s*(\d+)\s*/\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val fileFallbackRegex =
        Regex("""[^\u0000-\u001f:/]+\.(?:gx|gcode|g)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)

    fun keyValues(response: String): Map<String, String> = response.lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
        .toMap()

    fun isOk(response: String): Boolean = response.trimEnd().endsWith("ok")

    fun parseInfo(response: String): PrinterInfo {
        val kv = keyValues(response)
        val volume = volumeRegex.find(response)?.destructured?.let { (x, y, z) ->
            BuildVolume(x.toInt(), y.toInt(), z.toInt())
        }
        return PrinterInfo(
            machineType = kv["Machine Type"].orEmpty(),
            name = kv["Machine Name"].orEmpty(),
            firmware = kv["Firmware"].orEmpty(),
            serial = kv["SN"].orEmpty(),
            buildVolume = volume,
            mac = kv["Mac Address"],
        )
    }

    /** Возвращает пару (сопло, стол). */
    fun parseTemperatures(response: String): Pair<Temperature?, Temperature?> {
        fun Regex.temp(): Temperature? = find(response)?.destructured?.let { (cur, target) ->
            val c = cur.toFloatOrNull()
            val t = target.toFloatOrNull()
            if (c == null || t == null) null else Temperature(c, t)
        }
        return nozzleRegex.temp() to bedRegex.temp()
    }

    fun parseStatus(response: String): Status {
        val kv = keyValues(response)
        val machine = kv["MachineStatus"].orEmpty()
        val move = kv["MoveMode"].orEmpty()
        val state = when {
            machine.equals("PAUSED", true) || move.equals("PAUSED", true) -> PrintState.PAUSED
            machine.startsWith("BUILDING_FROM_SD", true) -> PrintState.PRINTING
            machine.equals("BUILDING_COMPLETED", true) -> PrintState.COMPLETED
            machine.equals("READY", true) -> PrintState.IDLE
            machine.equals("BUSY", true) -> PrintState.BUSY
            else -> PrintState.UNKNOWN
        }
        val led = kv["LED"]?.let { it == "1" }
        val file = kv["CurrentFile"]?.takeIf { it.isNotBlank() }
        return Status(state, machine, move, led, file)
    }

    fun parseProgress(response: String): Progress {
        val fraction = bytesRegex.find(response)?.destructured?.let { (done, total) ->
            val t = total.toLongOrNull() ?: 0L
            if (t <= 0L) null else ((done.toLongOrNull() ?: 0L).toFloat() / t).coerceIn(0f, 1f)
        }
        val layers = layerRegex.find(response)?.groupValues
        return Progress(
            fraction = fraction,
            layer = layers?.get(1)?.toIntOrNull(),
            totalLayers = layers?.get(2)?.toIntOrNull(),
        )
    }

    /**
     * Ответ на M661 бинарный: перед каждым файлом маркер `::\xA3\xA3`,
     * затем длина имени (4 байта, big-endian) и путь вида `/data/имя.gx`.
     */
    fun parseFileList(data: ByteArray): List<String> {
        val names = mutableListOf<String>()
        var i = 0
        while (i <= data.size - 8) {
            if (data[i] == COLON && data[i + 1] == COLON && data[i + 2] == MARK && data[i + 3] == MARK) {
                val len = ((data[i + 4].toInt() and 0xFF) shl 24) or
                    ((data[i + 5].toInt() and 0xFF) shl 16) or
                    ((data[i + 6].toInt() and 0xFF) shl 8) or
                    (data[i + 7].toInt() and 0xFF)
                val start = i + 8
                if (len in 1..1024 && start + len <= data.size) {
                    names += String(data, start, len, Charsets.UTF_8).trimEnd('\u0000')
                    i = start + len
                    continue
                }
            }
            i++
        }
        if (names.isEmpty()) {
            val text = String(data, Charsets.UTF_8)
            names += fileFallbackRegex.findAll(text).map { it.value }
        }
        return names.map { it.substringAfterLast('/').trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private const val COLON: Byte = 0x3A
    private const val MARK: Byte = -0x5D // 0xA3
}
