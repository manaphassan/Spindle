# Spindle — Official Latest Release Package
**Build Date:** September 28, 2026  
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
| **`Spindle-Standard-v1.5.0-STUDIO-release.apk`** | Standard (Production APK) | `v1.5.0-STUDIO` (Code 7) | **4.85 MB** | Modern DAP & Smartphones | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.5.0-STUDIO-release.aab`** | Standard (Play App Bundle) | `v1.5.0-STUDIO` (Code 7) | **7.32 MB** | Google Play Store Upload | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.5.0-STUDIO-debug.apk`** | Standard (Studio Debug) | `v1.5.0-STUDIO` (Code 7) | **11.05 MB** | Developer / Testing | Android 8.0+ (API 26+) |
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
2f3b25fc7aaf2efb6f2be16c56ef6fbff80f32e4e4e51fc74e819d5e35871b55  Spindle-Standard-v1.5.0-STUDIO-release.apk
0b194e79d85b0cb09009469cac4460754478cfea86d662e603276946df823f21  Spindle-Standard-v1.5.0-STUDIO-release.aab
8a0b5dcb48bb6bce86614927c2002a212c111db3634be6855487601830532989  Spindle-Standard-v1.5.0-STUDIO-debug.apk
b44da01ac6316809cd60de67a8f0b470c73e7f8561f514983773586b131d8382  Spindle-Lite-v1.0.1-LITE.apk
019bf6bfb32de45f73ae45b5065de5813f69d7c8f487275bf72f44c9915efa56  Spindle-Lite-v1.0.1-LITE-debug.apk
```

---

## ✨ What's New in v1.5.0-STUDIO

1. **Interactive In-App Biquad Frequency Curve Visualizer**:
   - Continuous magnitude response transfer function calculation using Robert Bristow-Johnson (RBJ) second-order IIR peaking EQ formulas across 120 logarithmic frequency points (20 Hz – 20 kHz).
   - Zero-heap allocation compute loop pre-evaluating curves without garbage collection pressure.
   - Emulates analog magnetic tape head bump (`+3.2 dB @ 63 Hz`) and analog tape warmth in real-time.
   - Oscilloscope-grade logarithmic frequency grid (31 Hz – 16 kHz), ±12 dB scale markers, dashed 0 dB reference line, and animated audio spectrum envelope.
   - Interactive touch-vernier probe HUD card displaying instantaneous frequency and response magnitude (`[ f Hz │ ±dB ]`) under the user's finger.
   - Fully synchronized with rotary tone knobs (Low, Mid, Hi), 10-band ISO faders, Q-factors, AutoEq profiles (Harman 2019, Crinacle IEF, Moondrop VDSF), presets, and bypass states.

2. **Missing Album Cover Art Auto-Fetcher & Downloader**:
   - Zero-dependency native HTTP client querying iTunes Search API (requesting HD 600×600 artwork) with automatic fallback to Deezer API (500×500).
   - Rate-limited and sanitized search term engine stripping release tags (e.g., `[FLAC]`, `(Remastered)`, `2007 -`, etc.).
   - Multi-tier album art resolution pipeline (L1 Memory Cache → L2 WebP Disk Cache → Downloaded HD Cache → Local Folder Artwork → Embedded Tag Extraction).
   - In-memory presence cache memoization (`coverPresenceCache` LruCache) preventing IPC delays across large MicroSD libraries.
   - Dedicated "Album Art Workshop" Bottom Sheet Dialog in Settings Category 3 (Music Library) with real-time scan telemetry, progress bar, and cancellation controls.
   - Direct on-demand single album artwork fetching from the Vault Album Details screen.

3. **Artisan Studio Hardware Features (Retained from v1.4.x)**:
   - Audiophile CUE Sheet Splitter & Parser Engine (Red Book 75 fps accurate sample clipping).
   - 10.5" Studio Reel-to-Reel Master Visual Deck with NAB locking hubs.
   - Harmonic Analog Tape Saturation DSP (`+3.2 dB @ 63 Hz` head bump) with anti-clipping headroom.
   - Procedural 16-bit PCM Cassette Foley engine with continuous pitch-ramped motor spool loop.
   - 10-Band ISO Parametric Studio EQ with multi-bell Gaussian log-ratio summation.
   - Custom laser nameplate engraving on the physical player faceplate.

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
adb install -r Spindle-Standard-v1.5.0-STUDIO-release.apk

# Install Spindle Lite on vintage device
adb install -r Spindle-Lite-v1.0.1-LITE.apk
```

---

## 🎯 Which Version Should You Install?

- **Choose Spindle Standard (`v1.5.0-STUDIO`)** if your device runs Android 8.0+ (API 26+). Includes interactive biquad EQ visualizer, missing cover art fetcher, animated kinetic reels, open reel-to-reel deck, live synchronized lyrics, audio visualizer, FM radio, 10-band ISO parametric EQ, and analog tape saturation DSP.
- **Choose Spindle Lite (`v1.0.1-LITE`)** if you have an older music player or vintage phone (Android 4.4 down to 4.1, 512MB RAM, HVGA 320x480 screen). Uses less than 18MB RAM and is optimized for physical hardware buttons (camera shutter, volume, d-pad).

---

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
