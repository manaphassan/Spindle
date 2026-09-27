# Spindle — Official Latest Release Package
**Build Date:** September 27, 2026  
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
| **`Spindle-Standard-v1.4.0-STUDIO-release.apk`** | Standard (Production APK) | `v1.4.0-STUDIO` (Code 5) | **4.74 MB** | Modern DAP & Smartphones | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.4.0-STUDIO-release.aab`** | Standard (Play App Bundle) | `v1.4.0-STUDIO` (Code 5) | **7.10 MB** | Google Play Store Upload | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.4.0-STUDIO-debug.apk`** | Standard (Studio Debug) | `v1.4.0-STUDIO` (Code 5) | **11.96 MB** | Developer / Testing | Android 8.0+ (API 26+) |
| **`Spindle-Lite-v1.0.0-LITE.apk`** | Lite | `v1.0.0-LITE` (Code 3) | **1.79 MB** | Vintage DAP & Small Screens | Android 4.4+ (API 19+) |
| **`Spindle-Lite-v1.0.0-LITE-debug.apk`** | Lite (Debug) | `v1.0.0-LITE.debug` | **6.00 MB** | Developer / Testing | Android 4.4+ (API 19+) |

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
420a62841a11ed01b8a1d8a80739a8c67ceda7fe99ecb3079011b605657e4431  Spindle-Standard-v1.4.0-STUDIO-release.apk
3fbf6ddbd1d96d9aa94b4cbf1c9a5aaa5033825e6ef0900aa8d653fdefa2a5df  Spindle-Standard-v1.4.0-STUDIO-release.aab
5f3938ddde73cce17543aaf19840ae89c6b8a9b3655cf242e2c284d58b1fc32c  Spindle-Standard-v1.4.0-STUDIO-debug.apk
677cd6e2b355a4e4502aea40cd62368d50a32afd31178ffe3f4ce45457250ad6  Spindle-Lite-v1.0.0-LITE.apk
e4a5795e33a3777fa97f4ba3ac42f4b537dff47c9d0958c12b87d6483c31639d  Spindle-Lite-v1.0.0-LITE-debug.apk
```

---

## ✨ What's New in v1.4.0-STUDIO (Artisan Hardware Suite)

1. **Audiophile CUE Sheet Splitter & Parser Engine**:
   - Standard EAC / CDRWIN / XLD `.cue` syntax parsing with Red Book 75 fps accurate sample-clipping.
   - Gapless sub-track virtual playlist queue integration without splitting or re-encoding physical FLAC/WAV files.
   - Automatic monolithic file duplicate suppression in local storage scanner.
2. **10.5" Studio Reel-to-Reel Master Visual Deck**:
   - Master tape deck visual skin (`theme_reel_to_reel_studio`) with brushed titanium console finish.
   - Precision aluminum reel flanges with 3-window Ampex/Revox cutouts revealing the rotating master oxide pack.
   - Machined 3-winged NAB professional locking hub adapters and concentric lathe turning scorelines.
3. **Harmonic Analog Tape Saturation DSP**:
   - Non-linear 1/3-octave magnetic flux head bump (`+3.2dB @ 63Hz`), smooth mid-range warmth (`+1.5dB @ 250Hz–500Hz`), and hysteresis high-frequency soft-clipping tape compression (`-1.5dB @ 8kHz`, `-2.8dB @ 16kHz`).
   - Integrated dynamic anti-clipping pre-amp headroom attenuation.
4. **Procedural Cassette Foley Engine**:
   - Zero-binary-asset 16-bit 44.1kHz PCM synthesis for solenoid engagement, spring release clicks, carriage eject pops, and toggle snaps.
   - Continuous dual-gear motor spool loop with dynamic real-time pitch ramping ($0.92\times \to 1.65\times$) during touch hold-seeking, auto-stop on leader boundaries.
5. **Fair Ownership & Anti-Subscription Licensing**:
   - One-time permanent lifetime pass ($2.99 – $3.99) with ZERO recurring subscriptions.
   - Dual verification: Google Play In-App Billing + cryptographic offline HMAC-SHA256 sponsor tokens (`SPINDLE-STUDIO-XXXX-YYYY`) for de-googled audiophile DAPs (Fiio, Hiby, Cayin, Astell&Kern).
6. **Custom Laser Nameplate Engraving**:
   - Personalized callsign or DAP serial number engraved directly onto the player faceplate.

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
adb install -r Spindle-Standard-v1.4.0-STUDIO-debug.apk

# Install Spindle Lite on vintage device
adb install -r Spindle-Lite-v1.0.0-LITE.apk
```

---

## 🎯 Which Version Should You Install?

- **Choose Spindle Standard (`v1.4.0-STUDIO`)** if your device runs Android 8.0+ (API 26+). Includes animated kinetic reels, open reel-to-reel deck, live synchronized lyrics, audio visualizer, FM radio, 10-band ISO parametric EQ, and analog tape saturation DSP.
- **Choose Spindle Lite (`v1.0.0-LITE`)** if you have an older music player or vintage phone (Android 4.4 down to 4.1, 512MB RAM, HVGA 320x480 screen). Uses less than 18MB RAM and is optimized for physical hardware buttons (camera shutter, volume, d-pad).

---

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
