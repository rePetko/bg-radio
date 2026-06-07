package com.example.bgradio

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: StationRepository
    private lateinit var faviconCache: FaviconCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)

        settings = SettingsStore(this)
        favorites = FavoritesStore(this)
        repository = StationRepository(this)
        faviconCache = FaviconCache(this)

        val httpsOnly = findViewById<MaterialSwitch>(R.id.switch_https_only)
        val showArtwork = findViewById<MaterialSwitch>(R.id.switch_show_artwork)
        val lowData = findViewById<MaterialSwitch>(R.id.switch_low_data)
        val autoLow = findViewById<MaterialSwitch>(R.id.switch_auto_low)
        val rowHttpsOnly = findViewById<View>(R.id.row_https_only)
        val rowShowArtwork = findViewById<View>(R.id.row_show_artwork)
        val rowLowData = findViewById<View>(R.id.row_low_data)
        val rowAutoLow = findViewById<View>(R.id.row_auto_low)

        httpsOnly.isChecked = settings.httpsOnly
        showArtwork.isChecked = settings.showArtwork
        lowData.isChecked = settings.lowDataMode
        autoLow.isChecked = settings.autoLowOnMobile

        rowHttpsOnly.setOnClickListener { httpsOnly.isChecked = !httpsOnly.isChecked }
        rowShowArtwork.setOnClickListener { showArtwork.isChecked = !showArtwork.isChecked }
        rowLowData.setOnClickListener { lowData.isChecked = !lowData.isChecked }
        rowAutoLow.setOnClickListener { autoLow.isChecked = !autoLow.isChecked }

        httpsOnly.setOnCheckedChangeListener { _, isChecked ->
            settings.httpsOnly = isChecked
        }
        showArtwork.setOnCheckedChangeListener { _, isChecked ->
            settings.showArtwork = isChecked
            if (isChecked) prefetchMissingFavoriteFavicons()
        }
        lowData.setOnCheckedChangeListener { _, isChecked ->
            settings.lowDataMode = isChecked
        }
        autoLow.setOnCheckedChangeListener { _, isChecked ->
            settings.autoLowOnMobile = isChecked
        }
    }

    /**
     * After the user enables artwork, sweep all favorited stations and download
     * any favicon that isn't already cached. Fire-and-forget; if the user closes
     * Settings mid-sweep the lazy fetch in MainActivity covers the rest.
     */
    private fun prefetchMissingFavoriteFavicons() {
        lifecycleScope.launch {
            val stations = repository.loadCached() ?: return@launch
            val favIds = favorites.ids().toSet()
            val httpsOnly = settings.httpsOnly
            stations
                .asSequence()
                .filter { it.uuid in favIds }
                .filter { it.favicon.isNotBlank() }
                .filter { !faviconCache.hasLocal(it.uuid) }
                .filter { !httpsOnly || it.favicon.startsWith("https://", ignoreCase = true) }
                .toList()
                .forEach { faviconCache.fetch(it) }
        }
    }
}
