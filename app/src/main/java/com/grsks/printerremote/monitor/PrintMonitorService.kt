package com.grsks.printerremote.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.grsks.printerremote.PrinterRemoteApp
import com.grsks.printerremote.data.SavedPrinter
import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterDriver
import com.grsks.printerremote.printer.PrinterDrivers
import com.grsks.printerremote.printer.PrinterModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Видим ли интерфейс. Пока приложение на экране, служба не опрашивает принтеры, чтобы не мешать ему. */
object AppVisibility {
    @Volatile
    var foreground: Boolean = false
}

/**
 * Служба слежения: раз в [INTERVAL_MS] опрашивает все сохранённые принтеры и присылает
 * уведомление, когда печать завершилась, оборвалась или принтер сообщил об ошибке.
 */
class PrintMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    private val drivers = HashMap<String, Pair<SavedPrinter, PrinterDriver>>()
    private val connected = HashSet<String>()
    private val previous = HashMap<String, PrintState>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifier.monitor(this, "Запускаю…", null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, Notifier.MONITOR_ID, notification, type)
        } else {
            startForeground(Notifier.MONITOR_ID, notification)
        }
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val app = application as PrinterRemoteApp
        while (scope.isActive) {
            if (!app.settings.notifications.value) {
                stopSelf()
                return
            }
            val printers = app.store.printers.value
            syncDrivers(printers)

            var progress: Int? = null
            var printing = 0
            if (!AppVisibility.foreground) {
                for ((id, pair) in drivers.entries.toList()) {
                    val snap = pollOne(id, pair.first, pair.second) ?: continue
                    if (snap.state == PrintState.PRINTING) {
                        printing++
                        progress = progress ?: snap.progress?.let { (it * 100).toInt() }
                    }
                }
            }
            val text = when {
                printers.isEmpty() -> "Добавьте принтер, чтобы получать уведомления"
                printing > 0 -> "Печатает: $printing из ${printers.size}"
                else -> "Принтеров: ${printers.size}. Сообщу, когда печать закончится"
            }
            getSystemService(android.app.NotificationManager::class.java)
                .notify(Notifier.MONITOR_ID, Notifier.monitor(this, text, progress))
            delay(INTERVAL_MS)
        }
    }

    /** Приводит набор драйверов к текущему списку принтеров. */
    private fun syncDrivers(printers: List<SavedPrinter>) {
        val ids = printers.map { it.id }.toSet()
        drivers.keys.filter { it !in ids }.forEach { id ->
            drivers.remove(id)?.second?.close()
            connected.remove(id)
            previous.remove(id)
        }
        printers.forEach { p ->
            val existing = drivers[p.id]
            if (existing == null || existing.first != p) {
                existing?.second?.close()
                connected.remove(p.id)
                val model = PrinterModels.byId(p.modelId) ?: return@forEach
                drivers[p.id] = p to PrinterDrivers.create(model, p)
            }
        }
    }

    private suspend fun pollOne(id: String, printer: SavedPrinter, driver: PrinterDriver) = try {
        if (id !in connected) {
            driver.connect()
            connected += id
        }
        val snap = driver.poll()
        detectTransition(id, printer, snap.state, snap.currentFile)
        snap
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Нет связи — состояние не меняем, на следующем круге подключимся заново.
        driver.close()
        connected.remove(id)
        null
    } finally {
        // FlashForge по TCP пускает одного клиента: отпускаем соединение между опросами.
        driver.close()
        connected.remove(id)
    }

    private fun detectTransition(id: String, printer: SavedPrinter, now: PrintState, file: String?) {
        val before = previous.put(id, now)
        val wasActive = before == PrintState.PRINTING || before == PrintState.PAUSED
        val name = printer.name
        val alertId = 100 + (id.hashCode() and 0xFFFF)
        when {
            wasActive && now == PrintState.COMPLETED ->
                Notifier.alert(this, alertId, "Печать завершена", "$name: ${file ?: "готово"}")
            now == PrintState.ERROR && before != PrintState.ERROR && before != null ->
                Notifier.alert(this, alertId, "Ошибка принтера", "$name сообщил об ошибке")
            wasActive && now == PrintState.IDLE ->
                Notifier.alert(this, alertId, "Печать остановлена", "$name: печать прервана или завершена")
        }
    }

    override fun onDestroy() {
        drivers.values.forEach { it.second.close() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val INTERVAL_MS = 15_000L

        fun start(context: Context) {
            val intent = Intent(context, PrintMonitorService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrintMonitorService::class.java))
        }
    }
}
