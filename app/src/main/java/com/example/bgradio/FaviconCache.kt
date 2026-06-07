package com.example.bgradio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stores station favicons in app-private storage so the media notification
 * can show them without re-contacting the station's server on every play.
 * The favicon is fetched once on demand and reused thereafter.
 */
class FaviconCache(context: Context) {

    private val dir: File = File(context.filesDir, "favicons").apply { mkdirs() }

    fun fileFor(uuid: String): File = File(dir, uuid)

    fun hasLocal(uuid: String): Boolean {
        val f = fileFor(uuid)
        return f.exists() && f.length() > 0
    }

    fun readBytes(uuid: String): ByteArray? =
        if (hasLocal(uuid)) runCatching { fileFor(uuid).readBytes() }.getOrNull() else null

    /**
     * Fetches the station favicon and writes it to disk. No-op if a cached
     * copy already exists or the station has no favicon URL. Best-effort —
     * any failure (network, oversized, non-2xx) is swallowed silently.
     */
    suspend fun fetch(station: Station) = withContext(Dispatchers.IO) {
        if (station.favicon.isBlank() || hasLocal(station.uuid)) return@withContext
        runCatching {
            val conn = (URL(station.favicon).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.size in 1..MAX_BYTES) {
                    fileFor(station.uuid).writeBytes(bytes)
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        private const val USER_AGENT = "BgRadio/1.0"
        private const val MAX_BYTES = 512 * 1024
    }
}
