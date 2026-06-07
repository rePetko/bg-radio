package com.example.bgradio

import android.content.Context
import android.content.SharedPreferences

class FavoritesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ordered list of favorite station UUIDs. Insertion order is preserved. */
    fun ids(): List<String> {
        val raw = prefs.getString(KEY_IDS, null) ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    fun isFavorite(uuid: String): Boolean = ids().contains(uuid)

    fun toggle(uuid: String): Boolean {
        val current = ids().toMutableList()
        val nowFavorite = if (current.contains(uuid)) {
            current.remove(uuid); false
        } else {
            current.add(uuid); true
        }
        prefs.edit().putString(KEY_IDS, current.joinToString("\n")).apply()
        return nowFavorite
    }

    companion object {
        private const val PREFS = "bg_radio_favorites"
        private const val KEY_IDS = "ids"
    }
}
