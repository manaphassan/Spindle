# Spindle Standard v1.4.0-STUDIO — Official Release

**Release Name:** Artisan Hardware Suite & Audiophile CUE Splitter Engine  
**Release Tag:** `v1.4.0-STUDIO`  
**Build Date:** September 27, 2026  
**Publisher:** HaNa Innovation  
**Core License:** [PolyForm Noncommercial 1.0.0](LICENSE) (Free personal use; commercial resale strictly prohibited)  
**Studio Add-On License:** [Spindle Studio Collector EULA](LICENSE-STUDIO.md) (Fair Ownership · Zero Subscriptions)  
**Signing:** Verified authentic private release keystore (`HaNa Innovation`)

---

## 🎧 What's New in v1.4.0-STUDIO

### 1. ✂️ Audiophile CUE Sheet Splitter & Parser Engine
- **Accurate CD-DA Red Book Timing:** Converts Red Book sectors (75 frames per second, 13.333ms per frame) to sample-accurate milliseconds.
- **Monolithic File Support:** Parses standard EAC, CDRWIN, and XLD `.cue` sheets accompanying monolithic single-file FLAC, APE, WAV, and WV album rips.
- **Embedded CUESHEET Support:** Reads embedded Vorbis comment cuesheets in FLAC headers.
- **Sample-Accurate Gapless Playback:** Integrated directly with AndroidX Media3 via `MediaItem.ClippingConfiguration`. Sub-tracks play seamlessly and gaplessly without re-encoding or duplicating files on disk.
- **Duplicate Suppression:** Automatically indexes virtual tracks and purges monolithic full-album entries from the library database.

### 2. 📼 10.5" Studio Reel-to-Reel Master Visual Deck
- **Brushed Titanium Console Finish:** Master studio tape deck aesthetic (`theme_reel_to_reel_studio`).
- **Precision 3-Window Flanges:** Ampex/Revox-style circular window cutouts revealing the rotating master oxide tape pack underneath.
- **Machined NAB Hub Adapters:** 3-winged professional locking hub adapters and concentric lathe turning scorelines.
- **Physical Pack Kinematics:** Continuous reel diameter recalculation during playback, fast-forward, and rewind.

### 3. 🎛️ Harmonic Analog Tape Saturation DSP
- **Magnetic Flux Head Bump:** Non-linear `+3.2 dB @ 63 Hz` analog tape head bump modeling the frequency response of professional 1/2" tape run at 15 IPS.
- **Smooth Midrange Warmth:** `+1.5 dB @ 250 Hz–500 Hz` body enhancement.
- **Tape Hysteresis Compression:** Soft-clipping high-frequency roll-off (`-1.5 dB @ 8 kHz`, `-2.8 dB @ 16 kHz`).
- **Dynamic Headroom Protection:** Real-time anti-clipping pre-amp attenuation.

### 4. 🔊 Procedural 16-bit PCM Cassette Foley Engine
- **Zero Binary Audio Overhead:** 100% procedurally synthesized 16-bit 44.1kHz audio samples.
- **Tactile Solenoids & Latches:** Solenoid engagement clacks, spring release clicks, and carriage eject pops.
- **Continuous Pitch Ramping:** Motor fast-forward and rewind spool loops dynamically modulate pitch from $0.92\times$ up to $1.65\times$ during touch hold-seeking.

### 5. 🛡️ Fair Ownership & Anti-Subscription Licensing
- **No Recurring Fees:** Lifetime collector pass ($2.99 – $3.99) with zero subscription lock-in.
- **Dual Verification:** Supports Google Play In-App Billing AND cryptographic offline HMAC-SHA256 sponsor tokens (`SPINDLE-STUDIO-XXXX-YYYY`) for de-googled audiophile DAPs (Fiio, Hiby, Cayin, Astell&Kern).
- **Personal Laser Nameplate Engraving:** Custom callsign or serial number engraved directly onto the player faceplate.

---

## 📦 Verified Binaries & Checksums

| File | Target | Size | SHA-256 Checksum |
| :--- | :--- | :---: | :--- |
| **`Spindle-Standard-v1.4.0-STUDIO-release.apk`** | Production APK (Android 8.0+) | **4.74 MB** | `420a62841a11ed01b8a1d8a80739a8c67ceda7fe99ecb3079011b605657e4431` |
| **`Spindle-Standard-v1.4.0-STUDIO-release.aab`** | Google Play App Bundle | **7.10 MB** | `3fbf6ddbd1d96d9aa94b4cbf1c9a5aaa5033825e6ef0900aa8d653fdefa2a5df` |
| **`Spindle-Standard-v1.4.0-STUDIO-debug.apk`** | Debug Build | **11.96 MB** | `5f3938ddde73cce17543aaf19840ae89c6b8a9b3655cf242e2c284d58b1fc32c` |
| **`Spindle-Lite-v1.0.0-LITE.apk`** | Vintage Hardware (Android 4.4+) | **1.79 MB** | `677cd6e2b355a4e4502aea40cd62368d50a32afd31178ffe3f4ce45457250ad6` |

---

## 📲 Installation

```bash
# Sideload via ADB to modern DAP or phone:
adb install -r Spindle-Standard-v1.4.0-STUDIO-release.apk

# Sideload to vintage Android 4.4 device:
adb install -r Spindle-Lite-v1.0.0-LITE.apk
```

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
