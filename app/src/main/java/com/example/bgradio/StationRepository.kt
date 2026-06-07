package com.example.bgradio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xbill.DNS.Lookup
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Type
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class StationRepository(context: Context) {

    private val cacheFile = File(context.cacheDir, "stations_bg.json")

    @Volatile private var cachedServer: String? = null

    suspend fun loadCached(): List<Station>? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext null
        runCatching {
            Station.listFromJson(JSONArray(cacheFile.readText()))
        }.getOrNull()
    }

    suspend fun fetchAndCache(): List<Station> = withContext(Dispatchers.IO) {
        val server = resolveServer()
        val url = URL(
            "https://$server/json/stations/bycountrycodeexact/BG" +
                    "?hidebroken=true&order=clickcount&reverse=true"
        )
        val body = httpGet(url)
        val stations = Station.listFromJson(JSONArray(body))
            .distinctBy { it.uuid }
            .sortedBy { it.name.lowercase() }
        cacheFile.writeText(Station.listToJson(stations).toString())
        stations
    }

    /**
     * Resolves an API mirror via the SRV record `_api._tcp.radio-browser.info`,
     * as recommended by radio-browser. Falls back to a known mirror if the
     * lookup fails (e.g. no DNS, captive portal, OS doesn't expose SRV).
     * The result is cached per repository instance.
     */
    private fun resolveServer(): String {
        cachedServer?.let { return it }
        val resolved = runCatching {
            val lookup = Lookup("_api._tcp.radio-browser.info", Type.SRV)
            val records = lookup.run()
            if (lookup.result != Lookup.SUCCESSFUL || records.isNullOrEmpty()) null
            else records.filterIsInstance<SRVRecord>()
                .randomOrNull()
                ?.target
                ?.toString(true) // omit trailing dot
        }.getOrNull()
        val server = resolved ?: FALLBACK_SERVER
        cachedServer = server
        return server
    }

    private fun httpGet(url: URL): String {
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from $url")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val USER_AGENT = "BgRadio/1.0 (github.com/local)"
        private const val FALLBACK_SERVER = "de1.api.radio-browser.info"
    }
}
