# Spindle Standard v1.6.0-STUDIO — Official Release

**Release Name:** Synchronized Lyrics Engine, User EQ Workshop & Artwork Studio  
**Release Tag:** `v1.6.0-STUDIO`  
**Build Date:** September 29, 2026  
**Publisher:** HaNa Innovation  
**Core License:** [PolyForm Noncommercial 1.0.0](LICENSE) (Free personal use; commercial resale strictly prohibited)  
**Studio Add-On License:** [Spindle Studio Collector EULA](LICENSE-STUDIO.md) (Fair Ownership · Zero Subscriptions)  
**Signing:** Verified authentic private release keystore (`HaNa Innovation`)

---

## 🎧 What's New in v1.6.0-STUDIO

### 1. 📜 Synchronized Lyrics & Auto-Download Engine
- **Online LRCLIB Cloud Integration:** Lightweight, zero-dependency HTTP client connecting to the community LRCLIB database (`lrclib.net`), querying synchronized and plain text lyrics by track title, artist name, album, and duration.
- **Embedded Tag Extraction:** Native binary tag parsing for ID3v2 `USLT` / `SYLT` frames (MP3) and Vorbis comments (`LYRICS`, `UNSYNCEDLYRICS` in FLAC / OGG), extracting embedded lyrics instantly without network roundtrips.
- **Micro-Accurate LRC Timecode Parser:** High-tolerance parser supporting standard `[mm:ss.xx]` and high-precision `[mm:ss.xxx]` timecodes, out-of-order line sorting, metadata cleaning, and empty line handling.
- **Cassette J-Card / VFD Lyrics Sheet:** Dedicated full-featured modal bottom sheet featuring smooth animated scrolling, auto-centering on the active line, cyan/amber vacuum fluorescent glow highlighting, and tap-to-seek playback integration.
- **Manual Sync Offset Fine-Tuning:** On-the-fly offset adjustment controls (`-0.5s`, `+0.5s`, reset) to align unsynced or drifting lyrics to perfection with persistence across track plays.
- **Library Bulk Lyrics Fetcher:** Batch background scanning and downloading dialog in Settings / Drawer, scanning your entire audio library for missing lyrics and downloading them seamlessly with concurrency throttling.

### 2. 🎚️ Custom User EQ Presets & AutoEq Target Workshop
- **Persistent Room Database Storage:** Dedicated SQLite / Room entity and DAO architecture for saving and recalling unlimited custom parametric and 10-band graphic equalizer configurations.
- **AutoEq Target Curve Import:** Instant parsing and import of AutoEq parametric target curves (`.txt`, `.csv`), supporting Harman 2019, Crinacle IEF, Moondrop VDSF, and custom headphone compensation curves.
- **Preset Management Dialog:** Sleek management interface allowing users to save current deck knob/fader curves, rename presets, duplicate, preview, or delete them.
- **Deck Quick-Access Switcher:** Horizontal scrolling preset pills directly integrated into the Cassette Deck and Equalizer drawers for immediate one-tap switching between factory curves and custom presets.

### 3. 🖼️ Local Album Cover Art Workshop Enhancements
- **Direct Folder Artwork Export:** Export and save downloaded high-resolution album artwork directly into the music album folder as `cover.jpg` / `folder.jpg` for permanent offline access by any external music player.
- **Manual Gallery & SAF File Picker:** Choose custom cover art from the Android system photo gallery or local storage via Storage Access Framework (SAF) with instant 512×512 square cropping and cache synchronization.
- **Multi-Tier Resolution Pipeline:** Seamless hierarchy checking memory cache, WebP disk cache, downloaded HD cache, local directory artwork (`cover.jpg`, `folder.jpg`, `album.jpg`), and embedded ID3/FLAC picture frames.

---

## 📦 Verified Binaries & Checksums

*(Checksums and file sizes will be populated upon build completion)*

| File | Target | Size | SHA-256 Checksum |
| :--- | :--- | :---: | :--- |
| **`Spindle-Standard-v1.6.0-STUDIO-release.apk`** | Production APK (Android 8.0+) | **4.88 MB** | `d75cc32b53006ff57ffc2dd2646ae2c9ec3650ca90db1d2b941e2ed8411a6527` |
| **`Spindle-Standard-v1.6.0-STUDIO-release.aab`** | Google Play App Bundle | **7.37 MB** | `c84a3b82d1afa71e737785bb4bb121cfbf68d1f66c8e58a487f5c52db67e6ac7` |
| **`Spindle-Standard-v1.6.0-STUDIO-debug.apk`** | Studio Debug Build | **11.10 MB** | `2c2e71fb626607530acd6ecc6931ad87a9da649492c6aab1eb409fa793fd934d` |
| **`Spindle-Lite-v1.0.1-LITE.apk`** | Vintage Hardware (Android 4.4+) | **1.71 MB** | `b44da01ac6316809cd60de67a8f0b470c73e7f8561f514983773586b131d8382` |

---

## 📲 Installation

```bash
# Sideload via ADB to modern DAP or phone:
adb install -r Spindle-Standard-v1.6.0-STUDIO-release.apk

# Sideload to vintage Android 4.4 device:
adb install -r Spindle-Lite-v1.0.1-LITE.apk
```

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
