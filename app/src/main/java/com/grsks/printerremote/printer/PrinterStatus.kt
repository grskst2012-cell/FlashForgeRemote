package com.grsks.printerremote.printer

enum class PrintState { IDLE, PRINTING, PAUSED, COMPLETED, BUSY, ERROR, UNKNOWN }

enum class Axis { X, Y, Z }

data class Temperature(val current: Float, val target: Float)

data class PrinterInfo(
    val machineType: String,
    val name: String,
    val firmware: String,
    val serial: String,
    val buildVolume: BuildVolume?,
    val mac: String?,
)

data class PrinterSnapshot(
    val state: PrintState,
    val rawState: String,
    val moveMode: String,
    val nozzle: Temperature?,
    val bed: Temperature?,
    /** От 0 до 1, когда принтер печатает. */
    val progress: Float?,
    val layer: Int?,
    val totalLayers: Int?,
    val currentFile: String?,
    val lightOn: Boolean?,
    /** Оставшееся время печати в секундах, если принтер его сообщает или оно вычислимо. */
    val remainingSeconds: Int? = null,
)
