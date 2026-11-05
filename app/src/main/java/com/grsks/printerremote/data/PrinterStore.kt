package com.grsks.printerremote.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedPrinter(
    val id: String,
    val name: String,
    /** IP или имя хоста, при нестандартном порте — «хост:порт». */
    val host: String,
    val modelId: String,
    /** Серийный номер (FlashForge 5M). */
    val serial: String = "",
    /** Код доступа FlashForge или API-ключ OctoPrint. */
    val accessCode: String = "",
    /** Свой адрес видеопотока; пусто — берётся адрес модели. */
    val cameraUrl: String = "",
)

/** Список добавленных принтеров, хранится в SharedPreferences как JSON. */
class PrinterStore(context: Context) {

    private val prefs = context.getSharedPreferences("printers", Context.MODE_PRIVATE)
    private val _printers = MutableStateFlow(load())
    val printers: StateFlow<List<SavedPrinter>> = _printers.asStateFlow()

    fun add(printer: SavedPrinter) {
        save(_printers.value + printer.copy(id = UUID.randomUUID().toString()))
    }

    fun update(printer: SavedPrinter) {
        save(_printers.value.map { if (it.id == printer.id) printer else it })
    }

    fun remove(id: String) {
        save(_printers.value.filterNot { it.id == id })
    }

    private fun load(): List<SavedPrinter> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SavedPrinter(
                id = o.getString("id"),
                name = o.getString("name"),
                host = o.getString("host"),
                modelId = o.getString("modelId"),
                serial = o.optString("serial"),
                accessCode = o.optString("accessCode"),
                cameraUrl = o.optString("cameraUrl"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<SavedPrinter>) {
        _printers.value = list
        val array = JSONArray()
        list.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("host", p.host)
                    .put("modelId", p.modelId)
                    .put("serial", p.serial)
                    .put("accessCode", p.accessCode)
                    .put("cameraUrl", p.cameraUrl),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "saved_printers"
    }
}
