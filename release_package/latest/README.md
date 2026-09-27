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
| **`Spindle-Standard-v1.4.1-STUDIO-release.apk`** | Standard (Production APK) | `v1.4.1-STUDIO` (Code 6) | **4.74 MB** | Modern DAP & Smartphones | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.4.1-STUDIO-release.aab`** | Standard (Play App Bundle) | `v1.4.1-STUDIO` (Code 6) | **7.11 MB** | Google Play Store Upload | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.4.1-STUDIO-debug.apk`** | Standard (Studio Debug) | `v1.4.1-STUDIO` (Code 6) | **10.85 MB** | Developer / Testing | Android 8.0+ (API 26+) |
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
33a7b0df1eb985c9b3c2e015d64a1be4233a9856d556cc0032889286dd256c39  Spindle-Standard-v1.4.1-STUDIO-release.apk
cc4ee8018c67ca31cea32c48e826a818dcc0b78e502ffe946d275b6f9cc011d6  Spindle-Standard-v1.4.1-STUDIO-release.aab
6afd6968670bce4545b3398f03e876a33bb08f61a7c3fa70aa88883090a46967  Spindle-Standard-v1.4.1-STUDIO-debug.apk
b44da01ac6316809cd60de67a8f0b470c73e7f8561f514983773586b131d8382  Spindle-Lite-v1.0.1-LITE.apk
019bf6bfb32de45f73ae45b5065de5813f69d7c8f487275bf72f44c9915efa56  Spindle-Lite-v1.0.1-LITE-debug.apk
```

---

## ✨ What's New in v1.4.1-STUDIO & v1.0.1-LITE

1. **Native Japanese Localization (`スピンドル` & `スピンドルライト`)**:
   - Both editions now natively adopt Japanese application names (`スピンドル` for Spindle Standard, `スピンドルライト` for Spindle Lite) on Japanese-locale DAPs and vintage Walkman devices.
2. **App Properties Management Popup on Long-Press**:
   - Long-press any app in the App Drawer to summon a dedicated dark industrial HUD modal.
   - Quick access to **Open App**, **App Info** (`ACTION_APPLICATION_DETAILS_SETTINGS`), **Permissions**, **Storage / Cache** settings, and **Uninstall App**.
   - Live hardware package telemetry displaying package identifier, installed version, and first-install epoch.
3. **Sound Deck UI Ergonomic Hierarchy**:
   - Refactored the Sound Deck drawer bar to a clean 2-tier layout:
     - Top row: Dedicated Section Title, Parametric EQ button, and Analog Tape Saturation toggle.
     - Bottom row: Real-time Audio Hardware Telemetry with dynamic headroom status badge (`NORMAL -0.0dB` vs `HEADROOM -3.0dB`).
4. **Artisan Studio Hardware Features (Retained from v1.4.0)**:
   - Audiophile CUE Sheet Splitter & Parser Engine (Red Book 75 fps accurate sample clipping).
   - 10.5" Studio Reel-to-Reel Master Visual Deck with NAB locking hubs.
   - Harmonic Analog Tape Saturation DSP (`+3.2dB @ 63Hz` head bump) with anti-clipping headroom.
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
adb install -r Spindle-Standard-v1.4.1-STUDIO-release.apk

# Install Spindle Lite on vintage device
adb install -r Spindle-Lite-v1.0.1-LITE.apk
```

---

## 🎯 Which Version Should You Install?

- **Choose Spindle Standard (`v1.4.1-STUDIO`)** if your device runs Android 8.0+ (API 26+). Includes animated kinetic reels, open reel-to-reel deck, live synchronized lyrics, audio visualizer, FM radio, 10-band ISO parametric EQ, and analog tape saturation DSP.
- **Choose Spindle Lite (`v1.0.1-LITE`)** if you have an older music player or vintage phone (Android 4.4 down to 4.1, 512MB RAM, HVGA 320x480 screen). Uses less than 18MB RAM and is optimized for physical hardware buttons (camera shutter, volume, d-pad).

---

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
