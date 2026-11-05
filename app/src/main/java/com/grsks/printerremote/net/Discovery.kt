package com.grsks.printerremote.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.grsks.printerremote.printer.PrinterModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

data class DiscoveredPrinter(
    val host: String,
    val name: String,
    val modelId: String,
    val serial: String = "",
    /** Пояснение для списка, например «уточните модель». */
    val note: String = "",
)

/**
 * Поиск принтеров в локальной сети (подсеть /24 телефона):
 * 1) UDP-широковещание FlashForge (порт 48899);
 * 2) проверка портов: Moonraker 7125, FlashForge TCP 8899, OctoPrint 5000/80.
 * Результаты приходят по мере нахождения.
 */
object Discovery {

    fun scan(context: Context): Flow<DiscoveredPrinter> = channelFlow {
        val local = localIpv4(context) ?: return@channelFlow
        val prefix = local.substringBeforeLast('.')
        val seen = HashSet<String>()

        suspend fun emit(p: DiscoveredPrinter) {
            if (seen.add("${p.host}|${p.modelId}")) send(p)
        }

        val lock = multicastLock(context)
        try {
            coroutineScope {
                val udp = async { udpFlashForge(prefix, ::emit) }
                val tcp = async { portScan(prefix, local, ::emit) }
                awaitAll(udp, tcp)
            }
        } finally {
            runCatching { lock?.release() }
        }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------ UDP FlashForge

    private suspend fun udpFlashForge(prefix: String, emit: suspend (DiscoveredPrinter) -> Unit) =
        withContext(Dispatchers.IO) {
            val socket = runCatching { DatagramSocket().apply { broadcast = true; soTimeout = 400 } }.getOrNull()
                ?: return@withContext
            socket.use {
                val probe = ByteArray(8)
                for (target in listOf("$prefix.255", "255.255.255.255")) {
                    runCatching {
                        it.send(DatagramPacket(probe, probe.size, InetAddress.getByName(target), 48899))
                    }
                }
                val deadline = System.currentTimeMillis() + 3_000
                val buf = ByteArray(512)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        it.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    parseFlashForgeReply(packet.address.hostAddress.orEmpty(), buf.copyOf(packet.length))
                        ?.let { found -> emit(found) }
                }
            }
        }

    /** Разбор UDP-ответа: 140 байт — старые модели, от 196 — новые (смещения из описания протокола). */
    fun parseFlashForgeReply(host: String, data: ByteArray): DiscoveredPrinter? {
        if (host.isBlank()) return null
        fun u16(o: Int) = ((data[o].toInt() and 0xFF) shl 8) or (data[o + 1].toInt() and 0xFF)
        fun text(o: Int, len: Int): String {
            val end = (o until minOf(o + len, data.size)).firstOrNull { data[it].toInt() == 0 } ?: minOf(o + len, data.size)
            return String(data, o, end - o, Charsets.UTF_8).trim()
        }
        return when {
            data.size >= 196 -> {
                val model = PrinterModels.byFlashForgePid(u16(0x88))
                DiscoveredPrinter(
                    host = host,
                    name = text(0, 128).ifBlank { model?.displayName ?: "FlashForge" },
                    modelId = (model ?: PrinterModels.byId("flashforge_adventurer_5m")!!).id,
                    serial = text(0x92, 128),
                    note = if (model == null) "Уточните модель" else "",
                )
            }
            data.size == 140 -> DiscoveredPrinter(
                host = host,
                name = text(0, 128).ifBlank { "FlashForge" },
                modelId = PrinterModels.ADVENTURER_4.id,
                note = "Уточните модель",
            )
            else -> null
        }
    }

    // ------------------------------------------------------------ Порты

    private suspend fun portScan(
        prefix: String,
        self: String,
        emit: suspend (DiscoveredPrinter) -> Unit,
    ) = coroutineScope {
        val gate = Semaphore(96)
        (1..254).map { i ->
            val host = "$prefix.$i"
            async {
                if (host != self) {
                    gate.withPermit { probeHost(host, emit) }
                }
            }
        }.awaitAll()
    }

    private suspend fun probeHost(host: String, emit: suspend (DiscoveredPrinter) -> Unit) {
        if (isOpen(host, 7125)) {
            val r = runCatching { Http.call("http://$host:7125/printer/info", timeoutMs = 2_000, connectTimeoutMs = 1_000) }.getOrNull()
            if (r != null && r.ok) {
                val name = runCatching { JSONObject(r.body).getJSONObject("result").optString("hostname") }.getOrDefault("")
                emit(DiscoveredPrinter(host, name.ifBlank { "Klipper" }, "klipper_generic", note = "Moonraker"))
            }
        }
        if (isOpen(host, 8899)) {
            queryFlashForgeTcp(host)?.let { emit(it) }
        }
        for (port in listOf(5000, 80)) {
            if (!isOpen(host, port)) continue
            val r = runCatching {
                Http.call("http://$host:$port/api/version", timeoutMs = 2_000, connectTimeoutMs = 1_000)
            }.getOrNull() ?: continue
            // Moonraker притворяется OctoPrint на /api/version, его не считаем.
            if ((r.ok || r.code == 403) && r.body.contains("OctoPrint", true) && !r.body.contains("Moonraker", true)) {
                emit(
                    DiscoveredPrinter(
                        host = if (port == 80) host else "$host:$port",
                        name = "OctoPrint",
                        modelId = "octoprint_generic",
                        note = "Нужен API-ключ",
                    ),
                )
                break
            }
        }
    }

    private fun isOpen(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 400) }
        true
    } catch (_: Exception) {
        false
    }

    /** Спрашиваем M115 без захвата управления (M601), чтобы не мешать FlashPrint. */
    private fun queryFlashForgeTcp(host: String): DiscoveredPrinter? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, 8899), 800)
            s.soTimeout = 1_200
            s.getOutputStream().apply { write("~M115\r\n".toByteArray()); flush() }
            val buf = ByteArray(2048)
            val sb = StringBuilder()
            try {
                while (!sb.contains("\nok")) {
                    val n = s.getInputStream().read(buf)
                    if (n < 0) break
                    sb.append(String(buf, 0, n, Charsets.UTF_8))
                }
            } catch (_: SocketTimeoutException) {
            }
            val text = sb.toString()
            val type = Regex("""Machine Type:\s*(.+)""").find(text)?.groupValues?.get(1)?.trim().orEmpty()
            if (type.isEmpty()) {
                null
            } else {
                val model = PrinterModels.detect(type)
                DiscoveredPrinter(
                    host = host,
                    name = Regex("""Machine Name:\s*(.+)""").find(text)?.groupValues?.get(1)?.trim()
                        ?.takeIf { it.isNotEmpty() } ?: type,
                    modelId = (model ?: PrinterModels.byId("flashforge_generic")!!).id,
                    serial = Regex("""SN:\s*(\S+)""").find(text)?.groupValues?.get(1).orEmpty(),
                    note = if (model == null) "Модель не распознана: $type" else "",
                )
            }
        }
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------ Окружение

    private fun localIpv4(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(network) ?: return null
        return props.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    private fun multicastLock(context: Context): WifiManager.MulticastLock? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock("printer-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()
}

