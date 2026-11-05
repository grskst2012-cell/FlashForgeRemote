package com.grsks.printerremote.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Читает поток MJPEG (multipart/x-mixed-replace, как у mjpg-streamer в принтерах FlashForge)
 * и выдаёт кадры. Кадры выделяются по маркерам JPEG SOI (FFD8) и EOI (FFD9),
 * поэтому заголовки частей multipart разбирать не нужно.
 */
object MjpegStream {

    private const val MAX_FRAME_BYTES = 8 * 1024 * 1024

    fun frames(url: String): Flow<Bitmap> = flow {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 10_000
            useCaches = false
        }
        try {
            val input = BufferedInputStream(connection.inputStream, 64 * 1024)
            val frame = ByteArrayOutputStream(256 * 1024)
            var inFrame = false
            var prev = -1
            while (currentCoroutineContext().isActive) {
                val b = input.read()
                if (b < 0) break
                if (!inFrame) {
                    if (prev == 0xFF && b == 0xD8) {
                        inFrame = true
                        frame.reset()
                        frame.write(0xFF)
                        frame.write(0xD8)
                        prev = -1
                        continue
                    }
                } else {
                    frame.write(b)
                    if (prev == 0xFF && b == 0xD9) {
                        inFrame = false
                        val bytes = frame.toByteArray()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { emit(it) }
                        prev = -1
                        continue
                    }
                    if (frame.size() > MAX_FRAME_BYTES) inFrame = false
                }
                prev = b
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}
