# Ytify-native 🎵

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://developer.android.com)
[![APK Size](https://img.shields.io/badge/APK%20Size-~200%20KB-brightgreen.svg)](https://github.com)

**Ytify-native** is an ultra-lightweight, high-performance native Android wrapper for [Ytify](https://ytify.pp.ua) with an embedded, on-device YouTube InnerTube stream extraction engine.

---

## ✨ Features

- **⚡ Ultra Lightweight**: Minimal APK footprint (~200 KB) with zero heavy third-party scrapers or bloated native dependencies.
- **🛡️ Pure On-Device Extraction**: Embedded local HTTP server running on `127.0.0.1:9999` resolving direct unciphered YouTube audio & video formats via InnerTube clients (`VISIONOS`, `ANDROID_VR`, `TVHTML5`).
- **🚀 Zero Media Proxying**: Direct `googlevideo.com` signed URLs for maximum throughput, low latency, and instant seeking.
- **🤖 Bot-Bypass Bootstrapping**: Automated `visitorData` session context generation prevents "Sign in to confirm you're not a bot" playback errors.
- **💉 Transparent Stream Interception**: Automatically intercepts web media queries in WebView and prioritizes your local engine before falling back to remote instances.
- **🎧 Full Background & Lockscreen Playback**: Native Android Foreground Service and `MediaSession` bridge with album art, metadata, track seeking, and notification controls.
- **🖤 Sleek Monochrome Onboarding**: Minimalist Apple Music-inspired first-launch onboarding screen with official Ytify branding.
- **🖼️ Picture-in-Picture (PiP)**: Native PiP support for video streaming.
- **🔒 Privacy First**: 100% on-device processing. No external tracking, ads, or middleman proxies.

---

## 🏗️ Architecture & How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                     Android Application                     │
│                                                             │
│  ┌──────────────────────┐         ┌──────────────────────┐  │
│  │   VeltrixWebview     │         │   Local HTTP Server  │  │
│  │  (ytify.pp.ua UI)    │         │     (Port 9999)      │  │
│  └──────────┬───────────┘         └──────────┬───────────┘  │
│             │                                │              │
│       Fetch Interceptor                PlaybackResolver     │
│    (Routes /api/v1/videos)                   │              │
│             │                                │              │
│             ▼                                ▼              │
│  Localhost Request ────────────────► Pure YouTube InnerTube │
│  (http://127.0.0.1:9999)             (VisitorData + Clients)│
│                                              │              │
│  ◄──────────────── Direct googlevideo URL ───┘              │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │     Native MediaSession & Android ForegroundService   │  │
│  │     (Notification Controls, Seek, Lockscreen Art)     │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

1. **Web Interface**: Loads the responsive SolidJS-based Ytify interface from `ytify.pp.ua`.
2. **Stream Interception**: A custom JS script injected on page load hooks `window.fetch` to intercept media stream resolution requests for YouTube video IDs.
3. **Localhost Engine**: Requests are routed to `http://127.0.0.1:9999/api/v1/videos/{videoId}`, where `PlaybackResolver` extracts high-bitrate unciphered audio (`itag 140`, `itag 251`, etc.) directly from YouTube.
4. **Native Media Bridge**: Bridges HTML5 media events into Android's native `MediaSessionCompat` and `NotificationManager` for background playback and lockscreen controls.

---

## 🚀 Building & Installing

### Prerequisites

- **Android SDK** with build-tools (API 33+)
- **JDK 11** or higher
- **Gradle** (or use the included `./gradlew` wrapper)

### Build APK

```bash
# Clone the repository
git clone https://github.com/shashwat/Ytify-native.git
cd Ytify-native

# Build release APK
./gradlew assembleRelease
```

The resulting APK will be located at:
```
app/build/outputs/apk/release/app-release.apk
```

### Install on Device via ADB

```bash
# Check connected device
adb devices

# Install APK
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 🛠️ Configuration & Permissions

- `POST_NOTIFICATIONS` (Android 13+): For media playback notifications.
- `FOREGROUND_SERVICE`: For uninterrupted background audio playback.
- `INTERNET`: For streaming audio and loading the web interface.
- `WAKE_LOCK`: To maintain audio playback when the screen is locked.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
