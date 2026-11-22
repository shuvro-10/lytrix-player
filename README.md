# 🎵 Lytrix Player

<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="Lytrix Player Logo" />

### *An elegant, feature-packed Material 3 Music & Cinema streaming player for Android.*

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/UI-Material%203%20Expressive-6750A4.svg)](https://m3.material.io/)

[**Download Latest APK**](https://github.com/shuvro-10/lytrix-player/releases) • [**Features**](#-features) • [**Cinema Studio**](#-cinema-studio) • [**Building from Source**](#-building-from-source) • [**Acknowledgments**](#-acknowledgments--credits)

</div>

---

## ✨ Features

### 🎧 Music Streaming & Playback
- **Multi-Source Streaming**: High-quality audio streaming powered by **YouTube Music** and **JioSaavn** (up to 320kbps).
- **Local Audio Library**: Automatically scans and plays device music (MP3, FLAC, AAC, Opus, WAV, OGG).
- **Synchronized Lyrics**: Real-time synchronized lyrics support with translation and romanization.
- **Offline Downloads**: High-bitrate audio caching and downloading with embedded metadata and album art.
- **Advanced Audio Engine**: Built-in 10-band equalizer, bass booster, virtualizer, and seamless gapless playback.
- **Sleep Timer & Music Alarms**: Wake up or drift to sleep to your favorite tracks.
- **Listen Together**: Synchronized real-time music sharing with friends across rooms.

### 🎨 Material 3 Expressive UI
- **Dynamic Color Extraction**: Colors seamlessly adapt to album artwork with Material You palette support.
- **Customizable Player Skins**: Choose between Modern Deck, Vinyl Turntable, Cassette, and Waveform Visualizer.
- **Homescreen Widgets**: Modern interactive widgets (Capsule Deck, Mini Turntable, Playlist Quick-Launch).
- **Always-On Display (AOD)**: Ambient clock and media controls designed for OLED screens.

### 🎬 Cinema Studio (On-Demand Engine)
- **TMDb Catalog Feed**: Discover trending movies, top-rated TV shows, trailers, and cast details.
- **Multi-Server Streaming**: Seamless streaming via trusted providers (VidLink 1080p, VidSrc, 2Embed, Torrentio).
- **PUBG-Style Resource Manager**: Keeps the initial app install lightweight (~35 MB in release). The torrent acceleration engine (`libtorrent4j.so` ~6.2 MB) is downloaded inside the app only when Cinema features are accessed.
- **Background Downloads**: Download movies directly to device storage with multi-peer torrent acceleration.

---

## 📱 Architecture & Tech Stack

- **UI Framework**: Jetpack Compose (100% declarative UI with Material 3 Expressive components).
- **Audio/Media**: AndroidX Media3 (ExoPlayer) + Custom Audio Processors.
- **Database**: Room Database with SQLite for playlists, history, and favorites.
- **Networking**: Retrofit, OkHttp3, Kotlin Coroutines, and Flow.
- **Image Loading**: Coil (Async image loading with hardware bitmap caching).
- **Torrent Engine**: libtorrent4j with on-demand ABI-specific runtime dynamic loading.

---

## 🛠️ Building from Source

### Prerequisites
- [Android Studio Ladybug | 2024.2+](https://developer.android.com/studio)
- JDK 17 or higher
- Android SDK 35 (API 35)

### Build Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/shuvro-10/lytrix-player.git
   cd lytrix-player
   ```
2. Copy sample configuration files:
   ```bash
   cp app/google-services.json.example app/google-services.json
   ```
   *(Add your own Firebase configuration if using Firebase Push Notifications / Analytics).*

3. Build Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   Output APKs will be located in `app/build/outputs/apk/debug/`.

---

## 🙏 Acknowledgments & Credits

Lytrix Player stands on the shoulders of giants. We express our heartfelt gratitude and acknowledge the remarkable open-source projects, authors, and communities whose work inspired, contributed to, or is utilized within this project:

- **[Metrolist](https://github.com/vfsfitvnm/ViMusic)** (by [@vfsfitvnm](https://github.com/vfsfitvnm) and contributors)  
  *Inspiration for YouTube Music client integration, playback controller architecture, database schema concepts, and Material vector icon sets under GPL-3.0.*

- **[NewPipe / NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)** (by Team NewPipe)  
  *Pioneering YouTube media stream extraction algorithms, audio stream deciphering, and video format parsing under GPL-3.0.*

- **[Meld](https://github.com/)**  
  *Inspiration for fluid player animations, tactile physics, gesture-driven bottom sheets, and now-playing responsive layout paradigms.*

- **[InnerTube & SimpMusic](https://github.com/maxrave-dev/SimpMusic)** (by [@maxrave-dev](https://github.com/maxrave-dev) and contributors)  
  *InnerTube API reverse-engineering models, request parsing, and multi-source synchronized lyrics providers (LRCLIB, KuGou, Paxsenix, BetterLyrics) under GPL-3.0.*

- **[libtorrent4j](https://github.com/aldenml/libtorrent4j)** (by [@aldenml](https://github.com/aldenml))  
  *High-performance, lightweight Java/Android native JNI wrapper around Rasterbar libtorrent.*

- **[AndroidX Media3 / ExoPlayer](https://github.com/androidx/media)** (by Google)  
  *The industry-standard media playback and audio pipeline foundation.*

- **[Material Design 3 Expressive](https://m3.material.io/)** (by Google)  
  *Expressive component design system, dynamic color theming, and motion guidelines.*

---

## 📄 License

Lytrix Player is free and open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**.  
See the [LICENSE](LICENSE) file for the full license text.

```text
Lytrix Player
Copyright (C) 2026 Shuvro (shuvro-10) & Lytrix Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```
