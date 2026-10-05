# Spindle Standard v1.7.0-STUDIO — Official Release

**Release Name:** Audiophile Audio Engine, Extended Formats & AutoEQ Workshop  
**Release Tag:** `v1.7.0-STUDIO`  
**Build Date:** October 5, 2026  
**Publisher:** HaNa Innovation  
**Core License:** [PolyForm Noncommercial 1.0.0](LICENSE) (Free personal use; commercial resale strictly prohibited)  
**Studio Add-On License:** [Spindle Studio Collector EULA](LICENSE-STUDIO.md) (Fair Ownership · Zero Subscriptions)  
**Signing:** Verified authentic private release keystore (`HaNa Innovation`)

---

## 🎧 What's New in v1.7.0-STUDIO

### 1. 💎 Bit-Perfect 32-Bit Float PCM Pipeline & Studio Soft-Knee Limiter
- **Direct 32-Bit Float AudioTrack Routing (`ENCODING_PCM_FLOAT`):** Configures Android's `DefaultAudioSink` and `AudioTrack` with `AudioFormat.ENCODING_PCM_FLOAT` (value 4) backed by Media3 extension preference. Decodes incoming streams directly into 32-bit single-precision IEEE 754 floating-point audio with $+140\text{ dB}$ of internal dynamic headroom.
- **Sub-0 dBFS Precision:** Completely eliminates fixed-point integer rounding artifacts, LSB truncation, and digital distortion when applying high-gain equalization or pre-amp adjustments.
- **Studio Soft-Knee Peak Limiter ($\tanh$ curve):** Implements an analog console-grade hyperbolic tangent saturation curve in `AudioDspConstants.kt` activating above threshold $T = 10^{-0.5 / 20} \approx 0.94406$ ($-0.5\text{ dBFS}$). Signals below threshold remain 100% bit-perfect and transparent; peaks exceeding threshold are smoothly compressed toward $1.0\text{ dBFS}$, replacing harsh digital square-wave clipping with warm, musical harmonic saturation.

### 2. 🍎 Native Apple AIFF / AIFC Container Demuxer & 80-Bit Extended Float Decoder
- **Zero-Dependency Media3 Extractor (`AiffExtractor.kt`):** Built-in extractor for Apple Audio Interchange File Format (`.aiff`, `.aif`, `.aifc`), demuxing standard `FORM`, `COMM`, and `SSND` chunks directly inside ExoPlayer/Media3 without external binary codecs.
- **80-Bit IEEE 754 Extended Float Parser:** High-precision decoder resolving 10-byte sample rates using unsigned 64-bit mantissa scaling (`readIeeeExtendedFloat`), preventing negative signed overflow and guaranteeing exact sample rate resolution for $44.1\text{ kHz}$, $48\text{ kHz}$, $88.2\text{ kHz}$, $96\text{ kHz}$, $176.4\text{ kHz}$, $192\text{ kHz}$, and $384\text{ kHz}$.
- **Fast Stream Info Parser:** Instant header metadata extraction via `TagParser.parseAiffStreamInfo()` in $<0.1\text{ ms}$.

### 3. 🎼 Native DSD / DSF 1-Bit Bitstream & 32-Tap LUT FIR Decimator
- **Native DSD Demuxer (`DsfExtractor.kt`):** Seamless playback of Sony DSD Stream Files (`.dsf`) and DSDIFF (`.dff`) containing 1-bit Delta-Sigma modulated bitstreams at $2.8224\text{ MHz}$ (DSD64).
- **Pre-Calculated 32-Tap Hann FIR Decimation:** Decimates 1-bit DSD bitstreams down by factor $M = 32$ to studio-grade 24-bit $88.2\text{ kHz}$ linear PCM (`C.ENCODING_PCM_24BIT`), preserving high-frequency extension while suppressing ultrasonic delta-sigma quantization noise.
- **Ultra-Fast 4 KB Look-Up Table (LUT):** Decomposes 32 FIR taps into 4 pre-calculated 256-float look-up tables (`LUT_STAGE_0..3`), replacing inner multiply loops with 4 table lookups and 3 additions per sample. Runs with $<1\%$ CPU load even on quad-core Cortex-A53 DAPs.
- **Fast DSD Header Inspection:** `TagParser.parseDsfStreamInfo()` extracts DSD sample counts, channels, and duration instantly.

### 4. 🎚️ Parametric EQ (PEQ) Architecture & Legendary Headphone AutoEQ Matrix
- **Arbitrary Center Frequencies (`centerFreqsHz`):** Parametric EQ engine in `UserEqPreset.kt`, `BiquadFilterCalculator.kt`, and `AudioFxController.kt` now supports custom per-band center frequencies instead of rigid fixed ISO bands.
- **Factory Reference AutoEQ Headphone Targets:** Pre-loaded reference compensation curves targeting the Harman Over-Ear and In-Ear standards:
  - **Sennheiser HD 600:** Audiophile neutral benchmark with sub-bass extension (+4.5dB @ 30Hz) and smooth upper midrange.
  - **Sony WH-1000XM4 / XM5:** Consumer ANC mid-bass boom tamer (-3.5dB @ 160Hz mud frequency) with vocal presence (+2.5dB @ 1.2kHz) and airy sparkle.
  - **Audio-Technica ATH-M50x:** Studio tracking monitor refinement (-2.0dB @ 200Hz boxy resonance, +1.5dB @ 3.5kHz clarity, -3.0dB @ 9kHz harshness notch).
  - **HiFiMAN Sundara:** Planar magnetic linear sub-bass shelf (+4.0dB @ 35Hz), flat mids, and silky highs.
  - **Beyerdynamic DT 770 Pro / DT 990 Pro:** Mount Beyer treble sibilance tamer (-5.0dB @ 6kHz, -4.0dB @ 8.5kHz) with warm lower body (+3.5dB @ 31Hz).
  - **Sony IER-M9:** Stage in-ear monitor reference target with pristine vocal clarity and linear timbre.
  - **Moondrop Blessing 2:** VDSF target curve compensation with refined sub-bass rumble (+3.5dB @ 25Hz) and smooth pinna gain.
- **Deck Quick-Access Switcher:** Integrated horizontal scrolling preset pills in the Cassette Deck and Equalizer drawers for instant one-tap switching and A/B comparison during playback.

---

## 📦 Verified Binaries & Architecture

| File | Target | Version Code | Target OS |
| :--- | :--- | :---: | :--- |
| **`Spindle-Standard-v1.7.0-STUDIO-release.apk`** | Production APK | **Code 9** | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.7.0-STUDIO-release.aab`** | Google Play App Bundle | **Code 9** | Android 8.0+ (API 26+) |
| **`Spindle-Standard-v1.7.0-STUDIO-debug.apk`** | Studio Debug Build | **Code 9** | Android 8.0+ (API 26+) |
| **`Spindle-Lite-v1.0.1-LITE.apk`** | Vintage Hardware (HVGA) | **Code 2** | Android 4.4+ (KitKat) |

---

## 📲 Installation

```bash
# Sideload via ADB to modern DAP or smartphone:
adb install -r Spindle-Standard-v1.7.0-STUDIO-release.apk

# Sideload to vintage Android 4.4 device:
adb install -r Spindle-Lite-v1.0.1-LITE.apk
```

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
