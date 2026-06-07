package com.example.bgradio

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (::controllerFuture.isInitialized && controllerFuture.isDone)
            controllerFuture.get() else null

    private lateinit var repository: StationRepository
    private lateinit var favorites: FavoritesStore
    private lateinit var settings: SettingsStore
    private lateinit var faviconCache: FaviconCache
    private lateinit var adapter: StationAdapter

    private lateinit var playPauseButton: FloatingActionButton
    private lateinit var skipPrevious: ImageButton
    private lateinit var skipNext: ImageButton
    private lateinit var nowPlaying: TextView
    private lateinit var emptyView: TextView

    private var allStations: List<Station> = emptyList()
    private var currentStation: Station? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* audio plays regardless; notification just won't show if denied */ }

    private val browseLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uuid = result.data?.getStringExtra(BrowseActivity.EXTRA_STATION_UUID)
        lifecycleScope.launch {
            reloadCachedStations()
            refreshFavoritesUi()
            if (uuid != null) {
                findStation(uuid)?.let { station ->
                    if (favorites.isFavorite(station.uuid)) {
                        playFavoritesFrom(station.uuid)
                    } else {
                        playSingleStation(station)
                    }
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uuid = mediaItem?.mediaId ?: return
            currentStation = allStations.firstOrNull { it.uuid == uuid }
            updateNowPlayingLabel()
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateNowPlayingLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = StationRepository(this)
        favorites = FavoritesStore(this)
        settings = SettingsStore(this)
        faviconCache = FaviconCache(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        playPauseButton = findViewById(R.id.play_pause)
        skipPrevious = findViewById(R.id.skip_previous)
        skipNext = findViewById(R.id.skip_next)
        nowPlaying = findViewById(R.id.now_playing)
        emptyView = findViewById(R.id.empty_favorites)

        adapter = StationAdapter(
            isFavorite = { true },
            onPlay = { playFavoritesFrom(it.uuid) },
            onToggleFavorite = { station ->
                favorites.toggle(station.uuid)
                refreshFavoritesUi()
            },
        )

        val list: RecyclerView = findViewById(R.id.favorites_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        playPauseButton.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            when {
                c.isPlaying -> c.pause()
                c.mediaItemCount > 0 -> { c.prepare(); c.play() }
                else -> playFavoritesFrom(null) // first favorite, if any
            }
        }
        skipPrevious.setOnClickListener { controller?.seekToPreviousMediaItem() }
        skipNext.setOnClickListener { controller?.seekToNextMediaItem() }

        findViewById<Button>(R.id.browse).setOnClickListener {
            browseLauncher.launch(Intent(this, BrowseActivity::class.java))
        }
        findViewById<Button>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        loadStationsThenRender()
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller?.addListener(playerListener)
            updatePlayPauseIcon()
            updateNowPlayingLabel()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshFavoritesUi()
    }

    private fun loadStationsThenRender() {
        lifecycleScope.launch {
            reloadCachedStations()
            refreshFavoritesUi()
        }
    }

    private suspend fun reloadCachedStations() {
        val cached = repository.loadCached()
        if (!cached.isNullOrEmpty()) allStations = cached
    }

    private fun refreshFavoritesUi() {
        val favStations = currentFavoriteStations()
        adapter.submitList(favStations)
        emptyView.visibility = if (favStations.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun currentFavoriteStations(): List<Station> {
        val byId = allStations.associateBy { it.uuid }
        return favorites.ids().mapNotNull { byId[it] }
    }

    private fun findStation(uuid: String): Station? =
        allStations.firstOrNull { it.uuid == uuid }

    /**
     * Loads the current favorites as the player's playlist and starts playback
     * at the given station UUID (or the first favorite if null).
     */
    private fun playFavoritesFrom(startUuid: String?) {
        val c = controller ?: return
        val favs = currentFavoriteStations()
        if (favs.isEmpty()) {
            Toast.makeText(this, R.string.pick_a_station, Toast.LENGTH_SHORT).show()
            return
        }
        val startIndex = startUuid?.let { uuid -> favs.indexOfFirst { it.uuid == uuid } }
            ?.takeIf { it >= 0 } ?: 0

        val target = favs[startIndex]
        if (settings.httpsOnly && !target.streamUrl.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(this, R.string.cleartext_blocked, Toast.LENGTH_LONG).show()
            return
        }

        c.setMediaItems(favs.map { buildMediaItem(it) }, startIndex, 0L)
        c.repeatMode = Player.REPEAT_MODE_ALL
        c.prepare()
        c.play()
        currentStation = target
        updateNowPlayingLabel()
        favs.forEach { maybeCacheFavicon(it) }
    }

    /**
     * Plays a single non-favorite station. The playlist is replaced with just
     * that item, so skip controls have nothing to navigate to.
     */
    private fun playSingleStation(station: Station) {
        val c = controller ?: return
        if (settings.httpsOnly && !station.streamUrl.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(this, R.string.cleartext_blocked, Toast.LENGTH_LONG).show()
            return
        }
        c.setMediaItem(buildMediaItem(station))
        // REPEAT_MODE_ALL even for a single item — keeps the skip-prev/next
        // commands "available" so the lock-screen media tile keeps showing
        // those buttons. For live radio, wrap-around on a 1-item playlist
        // just restarts the same stream.
        c.repeatMode = Player.REPEAT_MODE_ALL
        c.prepare()
        c.play()
        currentStation = station
        updateNowPlayingLabel()
        maybeCacheFavicon(station)
    }

    private fun maybeCacheFavicon(station: Station) {
        if (!settings.showArtwork) return
        if (station.favicon.isBlank()) return
        if (faviconCache.hasLocal(station.uuid)) return
        if (settings.httpsOnly && !station.favicon.startsWith("https://", ignoreCase = true)) return
        lifecycleScope.launch { faviconCache.fetch(station) }
    }

    private fun buildMediaItem(station: Station): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(getString(R.string.live_radio))
            .apply {
                if (settings.showArtwork) {
                    val cached = faviconCache.readBytes(station.uuid)
                    if (cached != null) {
                        setArtworkData(cached, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                }
            }
            .build()
        return MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaId(station.uuid)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun updatePlayPauseIcon() {
        val isPlaying = controller?.isPlaying == true
        playPauseButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        playPauseButton.contentDescription =
            getString(if (isPlaying) R.string.pause else R.string.play)
    }

    private fun updateNowPlayingLabel() {
        val title = controller?.mediaMetadata?.title?.toString()
            ?: currentStation?.name
        nowPlaying.text = if (title.isNullOrBlank())
            getString(R.string.nothing_playing)
        else
            getString(R.string.now_playing_fmt, title)
    }
}
