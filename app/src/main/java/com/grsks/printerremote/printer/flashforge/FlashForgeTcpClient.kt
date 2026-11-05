package com.grsks.printerremote.printer.flashforge

import com.grsks.printerremote.printer.PrinterException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Соединение с принтером FlashForge по TCP (порт 8899).
 * Команда отправляется как "~КОМАНДА\r\n", ответ заканчивается строкой "ok".
 * Обращения идут по очереди через мьютекс. После обрыва следующая команда открывает
 * соединение заново, и [onConnected] снова выполняет рукопожатие.
 */
class FlashForgeTcpClient(
    private val host: String,
    private val port: Int,
    private val onConnected: (send: (String) -> String) -> Unit = {},
) {
    private class Connection(val socket: Socket, val input: InputStream, val output: OutputStream)

    private val mutex = Mutex()

    @Volatile
    private var connection: Connection? = null

    suspend fun command(cmd: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String =
        String(exchange(cmd, timeoutMs, drainMs = 0), Charsets.UTF_8)

    /** Для команд, после ответа на которые принтер досылает бинарные данные (например, M661). */
    suspend fun commandBinary(cmd: String, drainMs: Int = 800): ByteArray =
        exchange(cmd, DEFAULT_TIMEOUT_MS, drainMs)

    private suspend fun exchange(cmd: String, timeoutMs: Int, drainMs: Int): ByteArray =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    sendAndRead(ensureOpen(), cmd, timeoutMs, drainMs)
                } catch (e: PrinterException) {
                    throw e
                } catch (e: IOException) {
                    // Одна повторная попытка на свежем соединении.
                    closeQuietly()
                    sendAndRead(ensureOpen(), cmd, timeoutMs, drainMs)
                }
            }
        }

    /**
     * Загрузка файла: M28 <размер> 0:/user/<имя>, затем пакеты по 4096 байт
     * с 16-байтным заголовком (0x5A5AA5A5, номер, длина, CRC32), затем M29.
     */
    suspend fun upload(remoteName: String, data: ByteArray, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val conn = ensureOpen()
                val start = String(
                    sendAndRead(conn, "M28 ${data.size} 0:/user/$remoteName", DEFAULT_TIMEOUT_MS, 0),
                    Charsets.UTF_8,
                )
                if (start.contains("fail", ignoreCase = true)) {
                    throw PrinterException("Принтер отклонил загрузку: ${start.trim()}")
                }
                val packet = ByteArray(HEADER_SIZE + CHUNK_SIZE)
                val header = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
                var offset = 0
                var index = 0
                while (offset < data.size) {
                    val len = minOf(CHUNK_SIZE, data.size - offset)
                    val crc = CRC32().apply { update(data, offset, len) }.value
                    header.clear()
                    header.putInt(PACKET_MAGIC).putInt(index).putInt(len).putInt(crc.toInt())
                    System.arraycopy(data, offset, packet, HEADER_SIZE, len)
                    if (len < CHUNK_SIZE) packet.fill(0, HEADER_SIZE + len, packet.size)
                    conn.output.write(packet)
                    offset += len
                    index++
                    onProgress(offset.toFloat() / data.size)
                }
                conn.output.flush()
                val end = String(sendAndRead(conn, "M29", UPLOAD_FINISH_TIMEOUT_MS, 0), Charsets.UTF_8)
                if (end.contains("fail", ignoreCase = true)) {
                    throw PrinterException("Ошибка сохранения файла: ${end.trim()}")
                }
            }
        }
    }

    fun close() = closeQuietly()

    private fun ensureOpen(): Connection {
        connection?.let { if (!it.socket.isClosed) return it }
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.tcpNoDelay = true
            val conn = Connection(socket, BufferedInputStream(socket.getInputStream()), socket.getOutputStream())
            onConnected { cmd -> String(sendAndRead(conn, cmd, DEFAULT_TIMEOUT_MS, 0), Charsets.UTF_8) }
            connection = conn
            return conn
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    private fun sendAndRead(conn: Connection, cmd: String, timeoutMs: Int, drainMs: Int): ByteArray {
        val input = conn.input
        // Выбросить остатки прошлых ответов, чтобы не принять их за новый.
        while (input.available() > 0) input.skip(input.available().toLong())

        conn.output.write("~$cmd\r\n".toByteArray(Charsets.UTF_8))
        conn.output.flush()

        conn.socket.soTimeout = timeoutMs
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!hasOkLine(buffer)) {
            if (System.currentTimeMillis() > deadline) throw SocketTimeoutException("Нет ответа на $cmd")
            val n = input.read(chunk)
            if (n < 0) throw EOFException("Принтер закрыл соединение")
            buffer.write(chunk, 0, n)
        }
        if (drainMs > 0) {
            conn.socket.soTimeout = drainMs
            try {
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                }
            } catch (_: SocketTimeoutException) {
                // Больше данных нет.
            }
        }
        return buffer.toByteArray()
    }

    /**
     * Ответ завершён, когда пришла строка "ok". Ищем её по всему буферу, а не только в конце:
     * у M661 бинарный список файлов может прийти в том же пакете сразу после "ok".
     */
    private fun hasOkLine(buffer: ByteArrayOutputStream): Boolean {
        if (buffer.size() < 2) return false
        return OK_LINE.containsMatchIn(String(buffer.toByteArray(), Charsets.ISO_8859_1))
    }

    private fun closeQuietly() {
        val conn = connection
        connection = null
        runCatching { conn?.socket?.close() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000
        const val CONNECT_TIMEOUT_MS = 4_000
        const val UPLOAD_FINISH_TIMEOUT_MS = 60_000
        private const val CHUNK_SIZE = 4096
        private const val HEADER_SIZE = 16
        private const val PACKET_MAGIC = 0x5A5AA5A5
        private val OK_LINE = Regex("""(?:^|\n)ok(?:\r?\n|\s*$)""")
    }
}
