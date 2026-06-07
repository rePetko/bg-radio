package com.example.bgradio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class BrowseActivity : ComponentActivity() {

    private lateinit var repository: StationRepository
    private lateinit var favorites: FavoritesStore
    private lateinit var settings: SettingsStore
    private lateinit var faviconCache: FaviconCache
    private lateinit var adapter: StationAdapter
    private lateinit var search: EditText
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private lateinit var lowDataBanner: TextView

    private var allStations: List<Station> = emptyList()
    private var connectivityManager: ConnectivityManager? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { onNetworkChanged() }
        override fun onLost(network: Network) { onNetworkChanged() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            onNetworkChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        title = getString(R.string.browse_title)

        repository = StationRepository(this)
        favorites = FavoritesStore(this)
        settings = SettingsStore(this)
        faviconCache = FaviconCache(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        loading = findViewById(R.id.loading)
        empty = findViewById(R.id.empty_message)
        search = findViewById(R.id.search)
        lowDataBanner = findViewById(R.id.low_data_banner)

        adapter = StationAdapter(
            isFavorite = { favorites.isFavorite(it.uuid) },
            onPlay = { playAndFinish(it) },
            onToggleFavorite = { station ->
                val nowFavorited = favorites.toggle(station.uuid)
                if (nowFavorited) maybePrefetchFavicon(station)
                adapter.notifyDataSetChanged()
            },
        )

        val list: RecyclerView = findViewById(R.id.stations_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadStations()
    }

    override fun onResume() {
        super.onResume()
        if (settings.autoLowOnMobile) {
            runCatching {
                connectivityManager?.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    networkCallback,
                )
            }
        }
        if (allStations.isNotEmpty()) applyFilter(search.text.toString())
    }

    override fun onPause() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        super.onPause()
    }

    private fun onNetworkChanged() {
        runOnUiThread {
            if (allStations.isNotEmpty()) applyFilter(search.text.toString())
        }
    }

    private fun loadStations() {
        lifecycleScope.launch {
            val cached = repository.loadCached()
            if (!cached.isNullOrEmpty()) {
                allStations = cached
                applyFilter(search.text.toString())
            } else {
                loading.visibility = View.VISIBLE
            }
            runCatching { repository.fetchAndCache() }
                .onSuccess { fresh ->
                    allStations = fresh
                    applyFilter(search.text.toString())
                }
                .onFailure {
                    if (allStations.isEmpty()) {
                        empty.text = getString(R.string.load_failed)
                        empty.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this@BrowseActivity, R.string.refresh_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            loading.visibility = View.GONE
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val lowData = effectiveLowDataMode()
        lowDataBanner.visibility = if (lowData) View.VISIBLE else View.GONE

        var base = allStations
        if (settings.httpsOnly) {
            base = base.filter { it.streamUrl.startsWith("https://", ignoreCase = true) }
        }
        if (lowData) {
            base = base.filter { it.bitrate in 1..SettingsStore.LOW_BITRATE_THRESHOLD }
        }
        val filtered = if (q.isEmpty()) base else base.filter {
            it.name.lowercase().contains(q) || it.tags.lowercase().contains(q)
        }
        adapter.submitList(filtered)
        if (allStations.isNotEmpty() && filtered.isEmpty()) {
            empty.text = getString(R.string.no_matches)
            empty.visibility = View.VISIBLE
        } else if (allStations.isNotEmpty()) {
            empty.visibility = View.GONE
        }
    }

    private fun effectiveLowDataMode(): Boolean {
        if (settings.lowDataMode) return true
        if (settings.autoLowOnMobile && isOnMobileData()) return true
        return false
    }

    private fun isOnMobileData(): Boolean {
        val cm = connectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun playAndFinish(station: Station) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_STATION_UUID, station.uuid))
        finish()
    }

    private fun maybePrefetchFavicon(station: Station) {
        if (!settings.showArtwork) return
        if (station.favicon.isBlank()) return
        if (faviconCache.hasLocal(station.uuid)) return
        if (settings.httpsOnly && !station.favicon.startsWith("https://", ignoreCase = true)) return
        lifecycleScope.launch { faviconCache.fetch(station) }
    }

    companion object {
        const val EXTRA_STATION_UUID = "station_uuid"
    }
}
