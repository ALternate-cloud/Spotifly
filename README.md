# 🎵 Spotifly - Spotify-Inspired Android Music Player

Spotifly 1.1 is a native Android music player crafted with **Jetpack Compose**, **AndroidX Media3 (ExoPlayer)**, and **Material3**. It brings a sleek Spotify dark aesthetic to your personal music library with support for **both online streaming and offline local device playback**.

---

## ✨ Key Features

* **Online Song Search & Streaming**:
  * Search JioSaavn, iTunes, and Deezer catalogs with results appearing as providers respond.
  * Real-time metadata extraction (song titles, artist names, albums, and high-resolution 600x600 album artwork).
  * Playback availability depends on third-party services, region, and network conditions.
  * Curated starter trending hits ready to play immediately on launch.

* **Hybrid Local Device Playback**:
  * Scans device storage for `.mp3`, `.m4a`, `.flac`, and `.wav` files via Android's `MediaStore`.
  * Seamless permission handling for Android 13+ (`READ_MEDIA_AUDIO`) and older Android versions.
  * Unified player engine: mix and play online streams and local songs in the exact same playlist or queue.

* **Spotify-Inspired UI & Aesthetics**:
  * Authentic Spotify dark palette (`#121212` charcoal background, `#1DB954` vibrant green accents, `#242424` elevated cards).
  * **Bottom Navigation Bar**: One-tap switching between *Home*, *Search*, and *Your Library*.
  * **Persistent Mini Player**: Sits above the navigation bar with artwork, scrolling title, favorite toggle, and animated play/pause.
  * **Full-Screen Player Modal**: High-res album artwork, interactive seekbar, timestamp indicators, shuffle, repeat (Off, All, One), and queue button.

* **Karaoke-Style Synchronized Lyrics**:
  * Integrated with **LRCLIB** to fetch time-synced lyrics automatically.
  * Dynamic auto-scrolling display: the currently playing line highlights in bold glowing white while past/upcoming lines remain subtle.
  * Interactive lyrics: tap any line to jump playback directly to that timestamp.

* **Native Background Audio & Media Notification**:
  * Built on **AndroidX Media3** with a foreground `MediaSessionService`.
  * Lock screen playback controls with album artwork.
  * Notification player supporting play, pause, previous, next, and seekbar scrubbing.
  * Automatic audio focus management (auto-pauses on phone calls or headphone disconnection).

---

## 📱 Installing the APK on Your Phone

Build the debug APK using the instructions below. The output is `app/build/outputs/apk/debug/app-debug.apk`. Share built APKs as GitHub Release assets.

### Method 1: Via USB Cable / ADB
If your phone is plugged in with Developer Options & USB Debugging enabled:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Method 2: Transfer Directly to Phone
1. Transfer `app-debug.apk` to your phone via Google Drive, Bluetooth, Telegram, or USB file transfer.
2. On your phone, tap the `.apk` file to install it.
3. If prompted, toggle **"Allow installation from this source"**.
4. Launch **Spotifly** and enjoy!

---

## 🛠️ Project Structure & Architecture

```
com.example.spotifly
├── MainActivity.kt                      // App entrypoint with edge-to-edge Compose
├── Navigation.kt                        // Navigation router
├── model/
│   ├── Song.kt                          // Song and PlaybackState models
│   └── Lyrics.kt                        // LRCLIB synced lyrics models
├── data/
│   ├── online/OnlineMusicService.kt     // Online search, streaming URLs & LRCLIB lyrics
│   └── local/LocalMusicRepository.kt    // MediaStore device audio scanner
├── player/
│   └── MusicController.kt              // MediaController connector, state flow & controls
├── service/
│   └── MusicPlaybackService.kt          // Media3 background service & MediaSession
├── theme/
│   ├── Color.kt                         // Spotify green, charcoal & dark theme tokens
│   ├── Theme.kt                         // Material3 dark color scheme
│   └── Type.kt                          // Typography definitions
└── ui/
    ├── components/
    │   ├── TrackItem.kt                 // Standard song row item
    │   ├── MiniPlayer.kt                // Sticky bottom mini player
    │   ├── FullPlayerModal.kt           // Full-screen player modal
    │   ├── LyricsView.kt                // Synchronized scrolling lyrics
    │   └── QueueBottomSheet.kt         // Playback queue bottom sheet
    ├── home/HomeScreen.kt               // Personalized home with greeting & carousels
    ├── search/SearchScreen.kt           // Live search & genre browse categories
    ├── library/LibraryScreen.kt         // Device audio scanner & liked songs
    └── main/
        ├── MainScreen.kt                // Scaffold, bottom navigation & sheets
        └── MainScreenViewModel.kt       // ViewModel binding UI to MusicController
```

---

## 💻 Rebuilding the Project

Install Android SDK Platform 36 and accept its licenses through Android Studio or the Android command-line tools. Use JDK 17 or newer to run Gradle. Kotlin compilation uses a JDK 17 toolchain, which the configured resolver can provision. Set `JAVA_HOME` if Java is unavailable on `PATH`.

Configure the SDK through Android Studio, `ANDROID_HOME`, or an untracked `local.properties` file containing `sdk.dir=/absolute/path/to/Android/Sdk`.

```bash
./gradlew assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. Android 7.0 (API 24) or newer is supported. Version 1.1 uses Android version code 2.

## Radio and saved mixes

Choose **Start Radio** from a song's menu to queue similar songs. Autoplay uses measured audio features where available, with metadata as a fallback. Open the queue and choose **Save as Playlist** to save the current mix in Your Library.

## Verification

```bash
./gradlew lintDebug
./gradlew testDebugUnitTest --tests 'com.example.spotifly.data.*' --tests 'com.example.spotifly.player.*' --tests 'com.example.spotifly.model.*' --tests 'com.example.spotifly.ui.*'
```

`AcceptanceTests` calls live services and generates a report. The live test in `SearchCoverageTest` is opt-in.

```bash
SPOTIFLY_LIVE_TESTS=1 ./gradlew testDebugUnitTest --tests 'com.example.spotifly.data.online.SearchCoverageTest'
```

Instrumented tests require a device or emulator.

```bash
./gradlew connectedDebugAndroidTest
```

Keep SDK paths, credentials, signing keys, and build outputs out of source control. Release signing requires your own private keystore. Retain the signing key for future app updates.
