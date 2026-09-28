# Spindle — Official Latest Release Package
**Build Date:** September 29, 2026  
**Publisher:** HaNa Innovation  
**Core License:** PolyForm Noncommercial 1.0.0 (Open & free for personal use; commercial sale strictly prohibited)  
**Studio Add-On License:** Spindle Studio Collector EULA ([LICENSE-STUDIO.md](../../LICENSE-STUDIO.md))  
**Signing:** Verified authentic release keystore (`HaNa Innovation`, SHA-256 fingerprint ending in `...7D:C2`)  

---

> **💬 A Personal Note from the Creator:**  
> I am not a professional programmer—just someone who is passionate about music and wanted to repurpose old devices to bring back the nostalgic magic of a vintage cassette player. This is a personal passion project shared freely with you. Please use this software **"as is"** and enjoy your music—don't expect a polished corporate app!

---

## 📦 Package Contents

This release package contains verified, production-ready APK builds for both Spindle editions:

| File | Edition | Version | Size | Target OS | Minimum Android |
| :--- | :--- | :---: | :---: | :--- | :--- |
| **`Spindle-Standard-v1.6.0-STUDIO-release.apk`** | Standard (Production APK) | `v1.6.0-STUDIO` (Code 8) | **4.88 MB** | Modern DAP & Smartphones | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.6.0-STUDIO-release.aab`** | Standard (Play App Bundle) | `v1.6.0-STUDIO` (Code 8) | **7.37 MB** | Google Play Store Upload | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.6.0-STUDIO-debug.apk`** | Standard (Studio Debug) | `v1.6.0-STUDIO` (Code 8) | **11.10 MB** | Developer / Testing | Android 8.0+ (API 26+) |
| **`Spindle-Lite-v1.0.1-LITE.apk`** | Lite | `v1.0.1-LITE` (Code 4) | **1.71 MB** | Vintage DAP & Small Screens | Android 4.4+ (API 19+) |
| **`Spindle-Lite-v1.0.1-LITE-debug.apk`** | Lite (Debug) | `v1.0.1-LITE.debug` | **5.73 MB** | Developer / Testing | Android 4.4+ (API 19+) |

---

## 🔒 Integrity Verification (SHA-256)

Verify downloaded APKs using PowerShell or Linux terminal:

```bash
# Windows PowerShell
Get-FileHash -Algorithm SHA256 *.apk

# Linux / macOS
sha256sum -c SHA256SUMS.txt
```

### Official Checksums:
```
d75cc32b53006ff57ffc2dd2646ae2c9ec3650ca90db1d2b941e2ed8411a6527  Spindle-Standard-v1.6.0-STUDIO-release.apk
c84a3b82d1afa71e737785bb4bb121cfbf68d1f66c8e58a487f5c52db67e6ac7  Spindle-Standard-v1.6.0-STUDIO-release.aab
2c2e71fb626607530acd6ecc6931ad87a9da649492c6aab1eb409fa793fd934d  Spindle-Standard-v1.6.0-STUDIO-debug.apk
b44da01ac6316809cd60de67a8f0b470c73e7f8561f514983773586b131d8382  Spindle-Lite-v1.0.1-LITE.apk
019bf6bfb32de45f73ae45b5065de5813f69d7c8f487275bf72f44c9915efa56  Spindle-Lite-v1.0.1-LITE-debug.apk
```

---

## ✨ What's New in v1.6.0-STUDIO

1. **Synchronized Lyrics & Auto-Download Engine**:
   - Zero-dependency cloud lyrics query engine via LRCLIB (`lrclib.net`), fetching synchronized and plain LRC lyrics.
   - Dual-tier caching: Persistent `.lrc` file cache on disk and instant in-memory LRU cache.
   - Native binary tag extractor for ID3v2 `USLT` / `SYLT` frames (MP3) and Vorbis comments (`LYRICS`, `UNSYNCEDLYRICS` in FLAC / OGG).
   - High-precision timecode parser (`[mm:ss.xx]`, `[mm:ss.xxx]`) with out-of-order line sorting and tag stripping.
   - Dedicated Cassette J-Card / VFD Lyrics Sheet bottom dialog with auto-scrolling, phosphor glow line highlighting, and tap-to-seek.
   - Manual sync offset adjustment (`-0.5s`, `+0.5s`, reset) persisted across track playback sessions.
   - Bulk lyrics downloader dialog in Drawer/Settings scanning the entire library and fetching missing lyrics concurrently.

2. **Custom User EQ Presets & AutoEq Target Workshop**:
   - Persistent Room database storage (`UserEqPresetEntity`, `UserEqPresetDao`) for unlimited custom equalizer presets.
   - AutoEq target profile importer (`.csv`, `.txt`) supporting Harman Target, Crinacle IEF, Moondrop VDSF, and custom headphone compensation curves.
   - Interactive preset management dialog: save, name, duplicate, test, and delete presets.
   - Quick-access horizontal preset pill strip in the Cassette Deck and Equalizer drawers.

3. **Local Album Cover Art Workshop Enhancements**:
   - Export online-fetched HD covers directly into album music folders as `cover.jpg` / `folder.jpg` for system-wide offline artwork display.
   - Manual artwork selector integrating Android system photo gallery / SAF picker to assign custom high-res images to any album or track.
   - 5-tier resolution pipeline (L1 Memory → L2 WebP Disk → Downloaded HD Cache → Local Folder Artwork → Embedded ID3/FLAC Tags).

4. **Retained Audiophile Studio Foundations**:
   - Interactive biquad frequency curve visualizer with touch-vernier probe HUD (`[ f Hz │ ±dB ]`).
   - Harmonic analog tape saturation DSP (`+3.2 dB @ 63 Hz` head bump).
   - 10.5" Studio Reel-to-Reel Master Visual Deck with NAB locking hubs.
   - Procedural 16-bit PCM Cassette Foley engine with continuous pitch-ramped motor spool loop.
   - Audiophile CUE sheet splitter (Red Book 75 fps accurate clipping).

---

## 📲 Installation Instructions

### Standard Installation (Phone Browser / File Manager):
1. Copy or download the `.apk` file to your Android device.
2. Tap the `.apk` file in your Downloads or File Manager.
3. If prompted, allow installation from unknown sources.
4. Launch Spindle and grant storage permissions to scan your music.

### Direct ADB Installation (Over USB / Wi-Fi):
```bash
# Install Spindle Standard on modern device
adb install -r Spindle-Standard-v1.6.0-STUDIO-release.apk

# Install Spindle Lite on vintage device
adb install -r Spindle-Lite-v1.0.1-LITE.apk
```

---

## 🎯 Which Version Should You Install?

- **Choose Spindle Standard (`v1.6.0-STUDIO`)** if your device runs Android 8.0+ (API 26+). Includes synchronized lyrics with auto-download, custom EQ presets with AutoEq import, local artwork export, interactive biquad EQ visualizer, animated kinetic reels, open reel-to-reel deck, audio visualizer, FM radio, 10-band ISO parametric EQ, and analog tape saturation DSP.
- **Choose Spindle Lite (`v1.0.1-LITE`)** if you have an older music player or vintage phone (Android 4.4 down to 4.1, 512MB RAM, HVGA 320x480 screen). Uses less than 18MB RAM and is optimized for physical hardware buttons (camera shutter, volume, d-pad).

---

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
