package com.example.bgradio

import org.json.JSONArray
import org.json.JSONObject

data class Station(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val homepage: String,
    val favicon: String,
    val tags: String,
    val codec: String,
    val bitrate: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("stationuuid", uuid)
        put("name", name)
        put("url_resolved", streamUrl)
        put("homepage", homepage)
        put("favicon", favicon)
        put("tags", tags)
        put("codec", codec)
        put("bitrate", bitrate)
    }

    companion object {
        fun fromJson(obj: JSONObject): Station? {
            val uuid = obj.optString("stationuuid").takeIf { it.isNotBlank() } ?: return null
            val name = obj.optString("name").trim().takeIf { it.isNotBlank() } ?: return null
            val url = obj.optString("url_resolved")
                .ifBlank { obj.optString("url") }
                .takeIf { it.isNotBlank() } ?: return null
            return Station(
                uuid = uuid,
                name = name,
                streamUrl = url,
                homepage = obj.optString("homepage"),
                favicon = obj.optString("favicon"),
                tags = obj.optString("tags"),
                codec = obj.optString("codec"),
                bitrate = obj.optInt("bitrate", 0),
            )
        }

        fun listFromJson(array: JSONArray): List<Station> =
            (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }

        fun listToJson(stations: List<Station>): JSONArray =
            JSONArray().apply { stations.forEach { put(it.toJson()) } }
    }
}
