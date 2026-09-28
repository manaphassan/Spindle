# Spindle Standard v1.5.0-STUDIO — Official Release

**Release Name:** Biquad Acoustic Scope & HD Cover Art Workshop  
**Release Tag:** `v1.5.0-STUDIO`  
**Build Date:** September 28, 2026  
**Publisher:** HaNa Innovation  
**Core License:** [PolyForm Noncommercial 1.0.0](LICENSE) (Free personal use; commercial resale strictly prohibited)  
**Studio Add-On License:** [Spindle Studio Collector EULA](LICENSE-STUDIO.md) (Fair Ownership · Zero Subscriptions)  
**Signing:** Verified authentic private release keystore (`HaNa Innovation`)

---

## 🎧 What's New in v1.5.0-STUDIO

### 1. 🎛️ Interactive In-App Biquad Frequency Curve Visualizer
- **Mathematical RBJ Transfer Function:** Implements Robert Bristow-Johnson (RBJ) second-order IIR peaking EQ formulas across 120 logarithmic frequency points (20 Hz – 20 kHz) with zero-heap allocations in the drawing loop.
- **Analog Tape Saturation & Head Bump Emulation:** Integrates real-time modeling of analog tape head bumps (`+3.2 dB @ 63 Hz`) and tape saturation warmth filters directly onto the response curve.
- **Oscilloscope-Grade Logarithmic Grid:** Renders standard audiophile frequency divisions (31 Hz, 63 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz, 16 kHz), ±12 dB reference scale markers, dashed 0 dB center line, and dynamic audio spectrum envelope animation.
- **Interactive Touch-Vernier Probe HUD:** Dragging a finger across the visualizer reveals a real-time HUD callout card (`[ f Hz │ ±dB ]`) displaying instantaneous frequency and response magnitude at that coordinate.
- **Hardware Deck Synchronization:** Live synchronization with rotary tone knobs (Low, Mid, Hi), 10-band ISO faders, Q-factor adjustments, AutoEq target profiles (Harman 2019, Crinacle IEF, Moondrop VDSF), presets, and bypass toggles.

### 2. 🎨 High-Resolution Missing Album Cover Art Workshop
- **Zero-Dependency Online Downloader:** Lightweight native HTTP client querying the iTunes Search API (requesting HD 600×600 artwork) with automatic fallback to the Deezer API (500×500).
- **Sanitized Search Term Engine:** Automated tag scrubbing stripping format metadata (e.g., `[FLAC]`, `(Remastered)`, year prefixes `2007 -`, track numbers) for maximum catalog hit rates.
- **5-Tier Resolution Pipeline:** Multi-tier resolution architecture (L1 Memory Bitmap Cache → L2 WebP Disk Cache → Downloaded HD Covers Cache → Local Folder Art `cover.jpg` → Embedded ID3/FLAC Tag Picture Extraction).
- **Fast Memoized Presence Cache:** Integrated `coverPresenceCache` (`LruCache<String, Boolean>(1000)`) preventing repetitive `MediaMetadataRetriever` IPC stalls across large MicroSD libraries.
- **Album Art Workshop Bottom Sheet Dialog:** Dedicated management interface in Settings Category 3 (Music Library) with real-time album scanning, missing artwork detection, progress indicator, download statistics, and cancellation controls.
- **On-Demand Single Album Fetching:** Tap missing cover placeholders directly from the Vault Album Details screen to fetch and cache high-resolution artwork instantly.

---

## 📦 Verified Binaries & Checksums

| File | Target | Size | SHA-256 Checksum |
| :--- | :--- | :---: | :--- |
| **`Spindle-Standard-v1.5.0-STUDIO-release.apk`** | Production APK (Android 8.0+) | **4.85 MB** | `2f3b25fc7aaf2efb6f2be16c56ef6fbff80f32e4e4e51fc74e819d5e35871b55` |
| **`Spindle-Standard-v1.5.0-STUDIO-release.aab`** | Google Play App Bundle | **7.32 MB** | `0b194e79d85b0cb09009469cac4460754478cfea86d662e603276946df823f21` |
| **`Spindle-Standard-v1.5.0-STUDIO-debug.apk`** | Studio Debug Build | **11.05 MB** | `8a0b5dcb48bb6bce86614927c2002a212c111db3634be6855487601830532989` |
| **`Spindle-Lite-v1.0.1-LITE.apk`** | Vintage Hardware (Android 4.4+) | **1.71 MB** | `b44da01ac6316809cd60de67a8f0b470c73e7f8561f514983773586b131d8382` |

---

## 📲 Installation

```bash
# Sideload via ADB to modern DAP or phone:
adb install -r Spindle-Standard-v1.5.0-STUDIO-release.apk

# Sideload to vintage Android 4.4 device:
adb install -r Spindle-Lite-v1.0.1-LITE.apk
```

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
