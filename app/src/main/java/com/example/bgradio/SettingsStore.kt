package com.example.bgradio

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** When true, only HTTPS streams are shown in browse and allowed to play. */
    var httpsOnly: Boolean
        get() = prefs.getBoolean(KEY_HTTPS_ONLY, true)
        set(value) { prefs.edit().putBoolean(KEY_HTTPS_ONLY, value).apply() }

    /** When true, station favicons are passed to the media notification as artwork. */
    var showArtwork: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ARTWORK, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_ARTWORK, value).apply() }

    /** When true, the browse list is filtered to stations at or below LOW_BITRATE_THRESHOLD. */
    var lowDataMode: Boolean
        get() = prefs.getBoolean(KEY_LOW_DATA, false)
        set(value) { prefs.edit().putBoolean(KEY_LOW_DATA, value).apply() }

    /** When true, low-data filtering is automatically applied while the device is on cellular. */
    var autoLowOnMobile: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOW_MOBILE, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_LOW_MOBILE, value).apply() }

    companion object {
        const val LOW_BITRATE_THRESHOLD = 128
        private const val PREFS = "bg_radio_settings"
        private const val KEY_HTTPS_ONLY = "https_only"
        private const val KEY_SHOW_ARTWORK = "show_artwork"
        private const val KEY_LOW_DATA = "low_data_mode"
        private const val KEY_AUTO_LOW_MOBILE = "auto_low_on_mobile"
    }
}
