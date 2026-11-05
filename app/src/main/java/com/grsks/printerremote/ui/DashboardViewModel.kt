package com.grsks.printerremote.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grsks.printerremote.data.SavedPrinter
import com.grsks.printerremote.printer.Axis
import com.grsks.printerremote.printer.PrinterDrivers
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterModels
import com.grsks.printerremote.printer.PrinterSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class UploadState(val fileName: String, val progress: Float)

data class DashboardState(
    val connection: ConnectionState = ConnectionState.Connecting,
    val info: PrinterInfo? = null,
    val snapshot: PrinterSnapshot? = null,
    val files: List<String> = emptyList(),
    val filesLoaded: Boolean = false,
    val filesLoading: Boolean = false,
    val upload: UploadState? = null,
    val busy: Boolean = false,
    /** Последние замеры температур для графика (раз в опрос). */
    val history: List<TempSample> = emptyList(),
)

data class TempSample(val nozzle: Float?, val bed: Float?)

class DashboardViewModel(val printer: SavedPrinter) : ViewModel() {

    val model = PrinterModels.byId(printer.modelId) ?: PrinterModels.ADVENTURER_4
    private val driver = PrinterDrivers.create(model, printer)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val cameraUrl: String? get() = printer.cameraUrl.trim().ifBlank { null } ?: driver.cameraStreamUrl()

    /**
     * Подключение и опрос статуса. Экран запускает эту функцию, пока он виден,
     * и отменяет, когда пользователь уходит или сворачивает приложение.
     */
    suspend fun runSession() {
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    if (_state.value.connection != ConnectionState.Connected) {
                        _state.update { it.copy(connection = ConnectionState.Connecting) }
                        val info = driver.connect()
                        _state.update { it.copy(info = info, connection = ConnectionState.Connected) }
                    }
                    val snapshot = driver.poll()
                    _state.update {
                        it.copy(
                            snapshot = snapshot,
                            connection = ConnectionState.Connected,
                            history = (it.history + TempSample(snapshot.nozzle?.current, snapshot.bed?.current)).takeLast(HISTORY_SIZE),
                        )
                    }
                    delay(POLL_INTERVAL_MS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    driver.close()
                    _state.update { it.copy(connection = ConnectionState.Error(e.describe())) }
                    delay(RETRY_INTERVAL_MS)
                }
            }
        } finally {
            driver.close()
            _state.update { it.copy(connection = ConnectionState.Connecting) }
        }
    }

    fun pause() = action("Пауза") { driver.pause() }
    fun resume() = action("Продолжение") { driver.resume() }
    fun cancel() = action("Отмена печати") { driver.cancel() }
    fun setLight(on: Boolean) = action(if (on) "Подсветка включена" else "Подсветка выключена") { driver.setLight(on) }
    fun setNozzleTemp(celsius: Int) = action("Сопло → $celsius °C") { driver.setNozzleTemp(celsius) }
    fun setBedTemp(celsius: Int) = action("Стол → $celsius °C") { driver.setBedTemp(celsius) }
    fun home() = action("Парковка осей") { driver.home() }
    fun jog(axis: Axis, distance: Float) = action(null) { driver.jog(axis, distance) }
    fun startPrint(fileName: String) = action("Печать $fileName запущена") { driver.startPrint(fileName) }

    fun refreshFiles() {
        if (_state.value.filesLoading) return
        viewModelScope.launch {
            _state.update { it.copy(filesLoading = true) }
            try {
                val files = driver.listFiles()
                _state.update { it.copy(files = files, filesLoaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Не удалось получить список файлов: ${e.describe()}")
            } finally {
                _state.update { it.copy(filesLoading = false) }
            }
        }
    }

    fun upload(context: Context, uri: Uri, startAfter: Boolean) {
        if (_state.value.upload != null) return
        val resolver = context.applicationContext.contentResolver
        viewModelScope.launch {
            try {
                val (original, bytes) = withContext(Dispatchers.IO) { readDocument(resolver, uri) }
                val name = sanitizeFileName(original)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in model.capabilities.uploadExtensions) {
                    _messages.tryEmit(
                        "Формат .$ext не поддерживается. Нужен: " +
                            model.capabilities.uploadExtensions.joinToString { ".$it" },
                    )
                    return@launch
                }
                _state.update { it.copy(upload = UploadState(name, 0f)) }
                driver.upload(name, bytes) { p ->
                    _state.update { s -> s.copy(upload = UploadState(name, p)) }
                }
                _messages.tryEmit("Файл $name загружен")
                if (startAfter) {
                    driver.startPrint(name)
                    _messages.tryEmit("Печать $name запущена")
                }
                refreshFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Ошибка загрузки: ${e.describe()}")
            } finally {
                _state.update { it.copy(upload = null) }
            }
        }
    }

    private fun action(doneMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
                doneMessage?.let { _messages.tryEmit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Ошибка: ${e.describe()}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        driver.close()
    }

    companion object {
        private const val HISTORY_SIZE = 90
        private const val POLL_INTERVAL_MS = 2_000L
        private const val RETRY_INTERVAL_MS = 5_000L

        fun factory(printer: SavedPrinter) = viewModelFactory {
            initializer { DashboardViewModel(printer) }
        }

        private fun Exception.describe(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

        private fun sanitizeFileName(name: String): String =
            name.trim().replace(Regex("""\s+"""), "_").replace(Regex("""[\\/:*?"<>|~]"""), "_")

        private fun readDocument(resolver: ContentResolver, uri: Uri): Pair<String, ByteArray> {
            var name = "upload.gx"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) c.getString(index)?.let { name = it }
                }
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Не удалось открыть файл")
            return name to bytes
        }
    }
}
