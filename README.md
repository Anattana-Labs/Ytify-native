# ytify-native 🎵

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://developer.android.com)
[![APK Size](https://img.shields.io/badge/APK%20Size-~131%20KB-brightgreen.svg)](https://github.com/Anattana-Labs/Ytify-native/releases)
[![GitHub Repo](https://img.shields.io/badge/GitHub-Anattana--Labs%2FYtify--native-181717.svg?logo=github)](https://github.com/Anattana-Labs/Ytify-native)

**ytify-native** is an ultra-lightweight, high-performance native Android application for [ytify](https://ytify.pp.ua) with an embedded, on-device YouTube InnerTube extraction engine and low-latency chunk-streaming proxy.

It achieves instant, background audio/video playback and lockscreen media session control with a minimal **~131 KB** APK footprint — containing **zero third-party scrapers, zero remote bucket/worker dependencies, and zero telemetry**.

---

## ⚡ Key Highlights

- **🪶 Tiny Footprint (~131 KB APK)**: Built strictly with native Android components, R8 bytecode optimization, and an embedded micro-HTTP server. No bulky frameworks or heavy scraping libraries.
- **🛡️ 100% Local InnerTube Extraction**: Extracts direct, unciphered YouTube audio (`itag 140`, `itag 251`, `itag 249`) and video streams (`itag 18`, `itag 313`, `itag 137`, etc.) directly on device via InnerTube clients (`ANDROID_VR`, `VISIONOS`, `TVHTML5`).
- **🔄 Smart Chunk-Streaming Proxy (`MultiChunkInputStream`)**: Bridges YouTube Google Video CDN range limitations (handling 256 KB bounded chunks) seamlessly to provide continuous playback without `HTTP 403 Forbidden` interruptions.
- **🌐 Chromium Private Network Access (PNA) Support**: Fully compliant with modern Chromium Private Network Access policies (`Access-Control-Allow-Private-Network: true`), allowing seamless communication between public origins (`ytify.pp.ua`) and loopback (`127.0.0.1:9999`).
- **🤖 Dynamic `visitorData` Bootstrapping**: Automates YouTube visitor session context acquisition to prevent bot challenges and ensure stream playability.
- **🎧 Native Android Background & Lockscreen Playback**: Bridges web audio events directly into Android's native `MediaSessionCompat` and foreground notification service with dynamic artwork, scrubber sync, and hardware/Bluetooth controls.
- **🖤 Minimalist Monochrome Onboarding**: First-run tutorial explaining app architecture and operation in a clean, high-contrast, theme-adaptive interface.
- **🖼️ Picture-in-Picture (PiP)**: Native picture-in-picture window support for videos.
- **🔒 Privacy First**: All extraction and stream proxying happen strictly within the local device loopback. No middleman servers or user data logging.

---

## 🏗️ Architecture & Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Android Application Process                      │
│                                                                         │
│  ┌───────────────────────────┐           ┌───────────────────────────┐  │
│  │      VeltrixWebview       │           │     Local HTTP Server     │  │
│  │   (https://ytify.pp.ua)   │           │     (127.0.0.1:9999)      │  │
│  └─────────────┬─────────────┘           └─────────────┬─────────────┘  │
│                │                                       │                │
│       Fetch Interceptor                          NanoHTTPD &            │
│   (Traps /api/v1/videos/{id})                 MultiChunkProxy           │
│                │                                       │                │
│                ▼                                       ▼                │
│       Routes to 127.0.0.1:9999           ┌───────────────────────────┐  │
│                │                         │     PlaybackResolver      │  │
│                │                         │   (Multi-Client InnerTube)│  │
│                │                         └─────────────┬─────────────┘  │
│                │                                       │                │
│                │                                       ▼                │
│                │                             Pure YouTube InnerTube     │
│                │                                 Endpoint Query         │
│                │                                       │                │
│                ▼                                       ▼                │
│  Stream URLs: 127.0.0.1:9999/proxy  ◄──────── Unciphered Stream Formats │
│                │                                                        │
│                ▼                                                        │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │              Native Android MediaSession & ForegroundService      │  │
│  │       (Lockscreen Controls, Notification Player, Track Seeking)   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### Core Execution Pipeline

1. **WebView Injection**: On page start, `MainActivity` injects `JS_STREAM_INTERCEPTOR` into the WebView context.
2. **Media Interception**: When ytify attempts to resolve tracks via `/api/v1/videos/{id}` or `/s/{id}`, the fetch interceptor redirects the query to `http://127.0.0.1:9999/api/v1/videos/{id}`.
3. **InnerTube Resolution**: `PlaybackResolver` queries YouTube's `youtubei/v1/player` endpoint using unciphered client payloads (`ANDROID_VR` 1.65.10, `VISIONOS`, `TVHTML5`) combined with dynamic `visitorData` acquired from `youtubei/v1/visitor_id`.
4. **Stream Proxying**: Format URLs are returned as `http://127.0.0.1:9999/proxy?itag={itag}&url={encoded_googlevideo_url}`.
5. **Bounded Chunk Delivery**: When the HTML5 audio/video element requests media, `HttpServer` uses `MultiChunkInputStream` to request bounded chunks (<= 256 KB) from Google Video CDN, preventing `HTTP 403 Forbidden` range errors and assembling a smooth, continuous byte stream.
6. **Native Media Session Bridge**: `JS_MEDIA_SCRIPT` listens to `navigator.mediaSession` updates and `<audio>` lifecycle events, bridging track title, artist, duration, position, and artwork into Android's native `ForegroundService` and `MediaSessionCompat`.

---

## 📁 Repository Structure

```
.
├── app/
│   ├── build.gradle                       # Android build configuration & dependencies
│   ├── proguard-rules.pro                 # Proguard optimization rules
│   └── src/main/
│       ├── AndroidManifest.xml            # Permissions, Service & Activity declarations
│       ├── java/com/shashwat/ytify/
│       │   ├── MainActivity.java          # Core Activity, WebView setup & JS interfaces
│       │   ├── HttpServer.java            # NanoHTTPD proxy & MultiChunk streaming engine
│       │   ├── PlaybackResolver.java      # Pure InnerTube client resolution pipeline
│       │   ├── InnerTube.java             # Direct InnerTube connection primitives
│       │   ├── ForegroundService.java     # Android media notification & background service
│       │   ├── VeltrixWebview.java        # Custom WebView preserving background state
│       │   ├── WebAppInterface.java       # Javascript-to-Java interface bridge
│       │   ├── CustomWebClient.java       # Fullscreen and video chrome handler
│       │   └── ...
│       └── res/                           # Layouts, icons, and theme definitions
├── gradle/wrapper/                        # Gradle wrapper binaries & configuration
├── README.md                              # Project documentation
├── build.gradle                           # Top-level Gradle configuration
└── settings.gradle                        # Project settings & module inclusions
```

---

## 🛠️ Building from Source

### Prerequisites

- **Android SDK** (API Level 33+)
- **JDK 11** or **JDK 17**
- **Git**

### 1. Clone the Repository

```bash
git clone https://github.com/Anattana-Labs/Ytify-native.git
cd Ytify-native
```

### 2. Build Release APK

```bash
# Build release APK
./gradlew assembleRelease
```

The compiled APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

### 3. Build Debug APK

```bash
# Build debug APK
./gradlew assembleDebug
```

Output:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📲 Installation via ADB

Connect your Android device with USB Debugging enabled, then run:

```bash
# Verify device connection
adb devices

# Install the APK
adb install -r app/build/outputs/apk/release/app-release.apk

# Launch the app
adb shell am start -n com.shashwat.ytifynative/com.shashwat.ytify.MainActivity
```

---

## 🔍 Development & Debugging

### Port Forwarding

To inspect or interact directly with the embedded HTTP server running on your phone from your development computer:

```bash
# Forward port 9999 from device to local machine
adb forward tcp:9999 tcp:9999

# Test server health
curl -i http://127.0.0.1:9999/

# Test stream extraction for any YouTube video ID
curl -s http://127.0.0.1:9999/api/v1/videos/xvT1jH8B9AM | jq .
```

### Real-Time Log Monitoring

```bash
# Filter app-specific tags
adb logcat -s YtifyHttpServer PlaybackResolver MainActivity
```

---

## 🔒 Permissions & Security

| Permission | Purpose |
| :--- | :--- |
| `INTERNET` | Loading the ytify web interface and fetching YouTube stream chunks |
| `FOREGROUND_SERVICE` | Keeping audio playback active when the screen is turned off or app is minimized |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ media playback foreground service compliance |
| `POST_NOTIFICATIONS` | Android 13+ permission for lockscreen & notification media playback controls |
| `MODIFY_AUDIO_SETTINGS` | Audio focus management and volume adjustments |
| `WAKE_LOCK` | Preventing CPU sleep during active audio playback |

---

## 🤝 Contributing

Contributions, bug reports, and pull requests are welcome!

1. Fork the repository: [https://github.com/Anattana-Labs/Ytify-native](https://github.com/Anattana-Labs/Ytify-native)
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
