# BG Radio — minimal Android streaming app

A Bulgarian radio player built on Media3 ExoPlayer. Browse stations from
[radio-browser.info](https://www.radio-browser.info/), star favorites, play
them from the home screen.

## What it does

- Home screen: list of favorited stations, with a "now playing" card at the
  bottom carrying ⏮ / ▶/⏸ / ⏭ controls and a Browse button
- Browse screen: searchable list of Bulgarian stations fetched from
  `radio-browser.info`, with a ▶ and a ★ on every row (tap row or ▶ to play
  without favoriting; tap ★ to add to favorites)
- Skip controls and Bluetooth media keys move between favorites — pressing
  ⏭ on a paired headset/car-stereo advances to the next favorite station
  and ⏮ goes back
- Settings: HTTPS-only streams, station artwork (cached locally), low data
  mode (filter ≤128 kbps), auto-enable low data on mobile
- Background playback (foreground service + media notification)
- Lock-screen / notification controls via `MediaSession`
- Station list is cached to disk; the app works offline once you've browsed once

## What you need to install (Ubuntu/Debian)

1. **JDK 17**
   ```
   sudo apt install openjdk-17-jdk
   ```

2. **Android Studio** — easiest path:
   - Download from https://developer.android.com/studio
   - Unpack and run the native launcher: `./bin/studio` (the legacy
     `./bin/studio.sh` shell script still works but triggers a "switch to
     native launcher" notification)
   - Inside the IDE: **Tools → Create Desktop Entry…** adds Android Studio
     to your GNOME/KDE app menu so you don't need the install path anymore
   - On first launch it installs the Android SDK (API 34) and Gradle automatically

   Or via the JetBrains Toolbox App (https://www.jetbrains.com/toolbox-app/),
   which manages installs/updates and creates the native launcher and
   desktop entry for you.

3. **A device or emulator**
   - For a real phone (incl. GrapheneOS): enable Developer Options → USB debugging, connect via USB
   - Or use Android Studio's built-in emulator

## How to build and run

1. Open Android Studio → **Open** → select `~/Dev/bg-radio`
2. Wait for Gradle sync (downloads dependencies, ~2–5 min first time)
3. Click the green ▶ Run button (or `Shift+F10`)

## Project layout

```
bg-radio/
├── app/
│   ├── build.gradle.kts                ← deps (Media3, RecyclerView, coroutines, dnsjava)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/bgradio/
│       │   ├── MainActivity.kt         ← favorites list + playback controls
│       │   ├── BrowseActivity.kt       ← searchable station picker
│       │   ├── SettingsActivity.kt     ← privacy / data toggles
│       │   ├── StationAdapter.kt       ← shared RecyclerView adapter
│       │   ├── Station.kt              ← data model + JSON parsing
│       │   ├── StationRepository.kt    ← radio-browser API + disk cache
│       │   ├── FavoritesStore.kt       ← SharedPreferences-backed favorites
│       │   ├── SettingsStore.kt        ← SharedPreferences-backed settings
│       │   ├── FaviconCache.kt         ← per-station logo cache (filesDir)
│       │   └── PlaybackService.kt      ← MediaSessionService (holds ExoPlayer)
│       └── res/
│           ├── drawable/               ← media-control + favorite icons,
│           │                            launcher foreground
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── activity_browse.xml
│           │   ├── activity_settings.xml
│           │   └── item_station.xml
│           ├── mipmap-anydpi-v26/      ← adaptive launcher icon (API 26+)
│           ├── mipmap-anydpi/          ← vector launcher fallback (API 24–25)
│           ├── values/                 ← strings, colors, themes (light)
│           └── values-night/           ← colors (dark)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Where the stations come from

Bulgarian stations are fetched from radio-browser.info:

```
https://<server>/json/stations/bycountrycodeexact/BG?hidebroken=true&order=clickcount&reverse=true
```

The `<server>` is resolved at runtime via an SRV-record lookup of
`_api._tcp.radio-browser.info` (using `dnsjava`) so we don't pin a single
mirror; one of the returned hosts is picked at random and cached for the
session. If DNS or SRV resolution fails, we fall back to
`de1.api.radio-browser.info`. Results are cached to `cacheDir/stations_bg.json`,
so the app loads instantly after the first launch.

## Privacy

The app targets GrapheneOS conventions:

- **No Google Play Services**, no analytics, no crash reporting, no
  advertising ID, no tracking SDKs of any kind.
- **Minimal permissions**: `INTERNET`, `ACCESS_NETWORK_STATE` (normal tier,
  only used when "Auto-enable on mobile data" is on), `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
  `allowBackup="false"` is set so nothing leaks to Google Backup.
- **All data is local**: favorites in `SharedPreferences`, station list in
  app `cacheDir`.
- **No phone-home calls.** Network egress is bounded to (a) `radio-browser`
  mirrors and (b) the audio stream you explicitly tapped.

Four Settings toggles control privacy- and data-relevant behavior:

1. **HTTPS-only streams** (default **on**). When enabled, the Browse list
   hides stations that use cleartext HTTP and playback of such stations is
   blocked with a toast. Turn this off if you want to listen to a station
   that only offers an HTTP feed.
2. **Show station artwork in notification** (default **off**). When enabled,
   the station logo appears on the lock screen and notification shade. The
   logo is downloaded **once** on the first play of a station and cached to
   app-private storage (`filesDir/favicons/<uuid>`); subsequent plays read
   the bytes locally and do not contact the station's server. When the
   toggle is off, no logo is fetched at all and a generic media icon is
   used. Favicon downloads also respect the HTTPS-only toggle — cleartext
   favicon URLs are skipped when HTTPS-only is on.
3. **Low data mode** (default **off**). Filters the Browse list to stations
   at or below 128 kbps. Favorites are not affected, so an already-favorited
   320 kbps station still plays normally — the toggle is for discovery,
   not enforcement.
4. **Auto-enable on mobile data** (default **off**). When on, the low-data
   filter is applied automatically whenever the device is on cellular,
   regardless of the manual toggle's state. Requires the `ACCESS_NETWORK_STATE`
   permission (Android "normal" tier — granted at install without prompt;
   reveals only the connection type to the app, nothing identifying).
   If this toggle is off, the permission is declared but never read.

`usesCleartextTraffic="true"` remains declared in the manifest because the
HTTPS-only toggle is a runtime choice; with it on, the app never attempts
HTTP and the platform-level permission is unused.

### Behind a VPN or Orbot

Android's `VpnService` captures all sockets opened by the app — our HTTPS
calls, the audio stream, and dnsjava's SRV queries all ride the tunnel
without any extra code. Caveats:

- Inside the tunnel, an HTTP stream is still HTTP at the exit. The
  HTTPS-only toggle is the right defense.
- Orbot does not carry UDP, so the dnsjava SRV lookup can fail under Orbot.
  The app falls back to `de1.api.radio-browser.info` and continues to work.
- Orbot's app-by-app mode (SOCKS proxy) is not supported — use Orbot's full
  VPN mode if you want to route this app through Tor.

## Notes

- Favorites are a list of station UUIDs in `SharedPreferences`. The full
  station details come from the cached JSON.
- No image-loading library is included, so station favicons aren't displayed
  in the list itself (only optionally in the media notification).

## What's deliberately left out (add later if you want)

- Now-playing metadata from the stream (ICY title)
- Sleep timer, equalizer
- Station favicons in the list itself (would need Coil/Glide; they already
  appear in the media notification when "Show station artwork" is on)
