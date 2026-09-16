# Spindle (Audiophile DAP Launcher)

<div align="center">
  <h3><i>Analog Soul. Hi-Res Heart.</i></h3>
  <p>An ultra-lightweight, audiophile-grade Android Home Launcher transforming any Android device or dedicated DAP into a physical-feeling, distraction-free Walkman.</p>
</div>

---

## 📻 Hero Design: Sony Walkman II (WM-2) Red (1981)

Spindle draws directly from the industrial design of the iconic **1981 Sony Walkman II (WM-2) in Vibrant Red**:
- **Vibrant Crimson Red Chassis (`#D71920`)**: Anodized casing with clean rectangular form and subtle bevels.
- **Diagonal Black Control Bezel**: Textured matte-black panel cutting across the top-right corner.
- **Knurled Circular Dial**: Tactile aluminum volume wheel reflecting system audio levels.
- **Classic Transport Levers**:
  - `PLAY` lever with the signature **emerald green dot**.
  - `STOP / PAUSE` lever with the signature **crimson red square**.
  - `FF` & `REW` spring-loaded silver push buttons.
- **Live Battery Indicator LED**: Real-time hardware telemetry (Soft Green $\ge 20\%$, Amber $10-20\%$, Pulsing Red $<10\%$, Pulsing Green charging).
- **Panoramic Acrylic Cassette Bay**: Clear window showcasing authentic kinetic cassette reels.

---

## ⚡ Engineered for Slow Hardware & Extreme Low RAM

Dedicated Android DAPs (HiBy R5/R6, Shanling M3X, FiiO M6/M9/M11, Astell&Kern) possess high-end audio DACs paired with weak application processors and as little as **1GB to 2GB of RAM**.

Spindle is specifically engineered to run at silky 60fps under these extreme constraints:

- **Pure Native Views & Canvas (No Jetpack Compose)**: Zero-allocation `onDraw()` rendering eliminates Garbage Collection (GC) stutter and saves over 15MB of APK bloat.
- **`RGB_565` Image Pipeline**: Cuts image memory by **50%** compared to standard ARGB_8888.
- **Aggressive Micro-Downsampling**: Album art is downsampled at decode time to prevent Out-Of-Memory (OOM) crashes on 3000x3000px FLAC covers.
- **Performance Targets**:
  - **Idle RAM**: $\le 35\text{MB}$
  - **Active Playback RAM**: $\le 48\text{MB}$
  - **Release APK**: $\le 6.5\text{MB}$

---

## 🎛️ Audiophile Sound Engine & Features

- **Decoupled Playback Service**: Unkillable Android Foreground Service with standard `MediaSession` and lockscreen controls.
- **High-Resolution Codecs**: Native support for FLAC, ALAC, WAV, AIFF, DSD (DSF/DFF), OGG Vorbis, AAC, and MP3.
- **True Gapless Playback**: Zero-latency buffer pre-loading.
- **ReplayGain (EBU R128)**: Automatic volume leveling without dynamic range compression.
- **Real-Time Audio Metrics (Swipe Left - Tab 2)**:
  - Source bit depth, sample rate, live bitrate, ReplayGain offset.
  - Output hardware routing (3.5mm Jack, Bluetooth LDAC/aptX-HD, USB DAC).
  - Resampling detector (alerts when Android AudioFlinger forces 48kHz).
- **10-Band Equalizer (Swipe Left - Tab 3)**: Audiophile target curves and custom presets.

---

## 🎞️ Kinetic Differential Reel Kinematics

Unlike flat skins that rotate two wheels at a static speed, Spindle implements real mechanical tape kinematics ($v = 4.7625\text{ cm/s}$):
- **Supply Spool (Left)**: Radius physically shrinks and angular velocity accelerates as tape runs out:
  $$R_{\text{left}}(p) = \sqrt{R_{\text{hub}}^2 + (R_{\text{max}}^2 - R_{\text{hub}}^2) \cdot (1 - p)}$$
- **Take-up Spool (Right)**: Radius physically grows and angular velocity decelerates over the track duration:
  $$R_{\text{right}}(p) = \sqrt{R_{\text{hub}}^2 + (R_{\text{max}}^2 - R_{\text{hub}}^2) \cdot p}$$

---

## 📼 Cassette Taxonomy & Parametric Skins

Spindle includes a curated library of period-accurate tape skins rendered with 0MB RAM overhead:
- **Sony Walkman Tape**: Classic red & teal dual-tone branding with red 6-tooth hubs.
- **Sony HF-90**: Textured warm ivory matte paper label with red/black typography (Type I Normal Bias).
- **Sony Metal Master**: Ultra-rigid white ceramic composite chassis with gold lettering (Type IV Metal).
- **Sony CD-IT**: Translucent sapphire blue & purple polycarbonate with exposed gearing.
- **BASF Chromdioxid 90**: Classic orange header on dark graphite body with chrome magnetic tape.
- **TDK SA-90 / DJ2**: Gold foil lettering on midnight-black chassis with red racing hubs.
- **Album Adaptive (Chameleon)**: Dynamically extracts palette colors from currently playing album art.

---

## 📂 Navigation Tree

```
   [ Swipe Left ]               [ Home Screen ]               [ Swipe Right ]
┌────────────────────┐       ┌────────────────────┐       ┌────────────────────┐
│ • App Drawer       │ <───> │ • Sony WM-2 Red    │ <───> │ • Audio Catalog    │
│ • Audio Metrics    │       │   Cassette Player  │       │   (Folders/Albums/ │
│ • 10-Band EQ & DSP │       │ • Side A / Side B  │       │    Artists/Tracks/ │
│ • Theme Selector   │       │   3D Flip Tracklist│       │    Playlists)      │
└────────────────────┘       └────────────────────┘       └────────────────────┘
```

---

## 🛡️ License

Spindle is distributed under the **Apache License 2.0** with Trademark and Visual IP Reservation. See [LICENSE](LICENSE) for details.
