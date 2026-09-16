# Spindle — Master Technical & Architectural Documentation
**Product Name:** Spindle (Audiophile DAP Launcher)  
**Package Name:** `com.hana.spindle`  
**Hero Hardware Inspiration:** Sony Walkman II (WM-2) Red (1981)  
**Version:** 1.3.0-PROD  
**Author / Art Director & Lead Systems Architect:** Spindle Core Team  
**Platform Target:** Android 8.0 (API 26) through Android 14/15 (API 34/35)  
**Primary Hardware Targets:** Ultra-low-resource Android DAPs (HiBy R5/R6, Shanling M3X/M6, FiiO M6/M9/M11, Sony Walkman NW-A100/ZX500, Sony Xperia X Compact)  
**License:** Apache License 2.0 (with Trademark & Visual IP Reservation)

---

## 1. Executive Summary

**Spindle** is an ultra-lightweight, audiophile-grade Android Home Launcher designed to transform any Android hardware—especially dedicated Digital Audio Players (DAPs) and compact vintage Android devices like the Sony Xperia X Compact—into a physical-feeling, distraction-free Walkman.

The hero aesthetic is directly inspired by the legendary **Sony Walkman II (WM-2) in Vibrant Red**, marrying its iconic asymmetrical diagonal control panel, mechanical pill-shaped play/stop levers, and circular knurled volume dial with authentic kinetic cassette physics.

### Core Pillars
1. **Sony Walkman II (WM-2) Industrial Design**: Asymmetrical diagonal control block, iconic crimson red chassis, knurled metallic dials, battery indicator LED, and classic pill-shaped buttons with green/red indicator dots.
2. **True Android Home Launcher**: Registers as the default home screen with an app drawer, instant search, and complete system lifecycle management.
3. **Skeuomorphic Kinetic Walkman Interface**: An authentic dual-reel cassette player with differential tape spools, dynamic palette accenting, and Side A (Player) <-> Side B (Tracklist) 3D mechanical flip.
4. **Vast Cassette Taxonomy**: Parametric rendering of authentic cassette models from the golden age (Sony Walkman Tape, HF-90, Metal Master, CD-IT, BASF Chromdioxid, TDK SA-90, Fuji).
5. **Extreme Low-RAM & Small APK Footprint**: Tailored specifically for DAPs with as little as **1GB – 2GB of RAM** and weak quad-core Cortex-A53 processors (Snapdragon 425/430, Exynos 7270, Rockchip).
6. **Audiophile Audio Chain**: Native gapless playback, ReplayGain (EBU R128), real-time audio metrics telemetry (bit depth, sample rate, codec, resampling detector), and bit-perfect routing.

---

## 2. Hero Design: Sony Walkman II (WM-2) Red Architectural Blueprint

The 1981 **Sony WM-2** is celebrated as one of the greatest feats of consumer industrial design. Spindle maps every physical detail of the WM-2 directly onto the Android launcher layout:

```
+-------------------------------------------------------------+
|  SPINDLE                                                    |
|                                         +-----------------+ |
|                                         | (( Knurled ))   | |
|                                         | (( Dial/Vol ))  | |
|                                         |                 | |
|                                         |  [FF]   [REW]   | |
|                                         |   O       O     | |
|                                         |                 | |
|   +----------------------------------+  |  ( ) BATTERY    | |
|   | SONY HF 90             SIDE A    |  |                 | |
|   | +------------------------------+ |  |  +------------+ | |
|   | | [Art] Pink Floyd - Time      | |  |  | [> PLAY] ● | | |
|   | +------------------------------+ |  |  +------------+ | |
|   |                                  |  |                 | |
|   |    (( O ))   ======   (( O ))    |  |  +------------+ | |
|   |   [Supply]   [Tape]   [Take-up]  |  |  | [■ STOP] ■ | | |
|   |   (R_left)            (R_right)  |  |  +------------+ | |
|   +----------------------------------+  +-----------------+ |
|   03:45 ═══════════════●═════════════════ 06:53             |
|                                                             |
|   STEREO                                                    |
|   SPINDLE II                                    [ EJECT ▲ ] |
+-------------------------------------------------------------+
```

### 2.1 The WM-2 Physical Elements Translated to UI
1. **The WM-2 Crimson Red Chassis**:
   - Primary shell finished in rich anodized crimson red (`#D71920` / `#C8102E`) with subtle perimeter bevels and corner radiuses that hug the phone display.
2. **The Diagonal Asymmetrical Black Control Bezel**:
   - A textured matte-black (`#1E1E20`) panel cutting diagonally across the top-right of the chassis.
   - **Knurled Circular Dial**: Metallic silver dial that visually indicates current playback volume or allows rotational touch scrub.
   - **Tactile Transport Levers**:
     - **PLAY**: Pill-shaped brushed silver button featuring the iconic **emerald green dot** (`#00C853`). Depressing it triggers a physical latch haptic kick.
     - **STOP / PAUSE**: Matching pill-shaped lever with the classic **crimson red square** (`#D50000`).
     - **FF & REW**: Small, circular spring-loaded silver push buttons for high-speed winding with authentic tape pitch sound effects.
   - **Dynamic Battery LED**: A physical-looking indicator light on the black bezel that reflects actual device battery telemetry:
     - Constant Soft Green: Battery $\ge 20\%$
     - Warm Amber: Battery $10\% - 20\%$
     - Pulsing Red: Battery $< 10\%$
     - Pulsing Green: Connected to charger
3. **The Panoramic Acrylic Cassette Bay**:
   - Centered inside the red chassis, featuring transparent acrylic glass with subtle light reflections.
   - Houses the fully animated cassette tape with differential kinematics and real-time rotating reels.
4. **Retro Stacked Typography**:
   - Bottom-left corner: **`STEREO SPINDLE II`** in the unmistakable rounded 1980s Walkman II display typeface.

---

## 3. Tape Taxonomy & Parametric Skins

Spindle draws from an exhaustive library of classic cassette styles (based on authentic period specimens):

| Tape Family | Signature Aesthetic | Reel Hubs & Shell | Audio Bias Pairing |
| :--- | :--- | :--- | :--- |
| **Sony Walkman Tape** | Red & teal dual-tone branding, wide panoramic window. | Red 6-tooth hubs, smoked charcoal shell. | General / Universal |
| **Sony HF-60 / HF-90** | Warm ivory/cream matte label, red/black typography. | White 6-tooth hubs, dark brown ferric tape pack. | Type I Normal Bias |
| **Sony Metal Master** | Ultra-rigid white ceramic composite chassis, gold lettering. | White precision hubs, deep gunmetal metallic tape. | Type IV Metal (Hi-Res FLAC/DSD) |
| **Sony CD-IT** | 1990s translucent sapphire blue & purple polycarbonate. | White hubs with neon blue accents, visible gears. | CD-Quality 16-bit FLAC |
| **Sony UX-Pro / UCX-S** | Dark smoked guide ribs, green/gold high-bias badges. | Chrome/black hubs, deep black tape ribbon. | Type II Chrome / High Bias |
| **BASF Chromdioxid 90** | Classic two-tone orange/cream header, dark graphite body. | Red/white hubs, chrome magnetic coating. | Type II Chrome |
| **TDK SA-90 / DJ2** | Classic gold foil lettering on midnight black chassis. | Red/black racing hubs, precision guide rollers. | Type II High Bias |
| **Fuji FL / Super Ferro** | Minimalist clean Japanese design, transparent shell. | Vibrant red spindle hubs, transparent tape pack. | Type I Normal Bias |
| **Album Adaptive (Chameleon)**| Automatically adopts primary and accent colors from album art. | Tinted hubs, contrast-clamped label. | Dynamic |

---

## 4. Google Play Store Certification & Compliance Audit

### 4.1 Launcher Intent & Package Visibility (`QUERY_ALL_PACKAGES`)
* **Policy Concern**: Google Play strictly prohibits broad package visibility unless the app's core user-facing functionality cannot function without it.
* **Compliance Verdict**: **FULLY PERMITTED**.
  - Google Play policy explicitly defines **Home Launchers (Launcher Apps)** as an authorized exception for the `android.permission.QUERY_ALL_PACKAGES` permission.
  - Declares `<category android:name="android.intent.category.HOME" />` and `CATEGORY_DEFAULT`.
  - Submit the standard Play Console Home Launcher declaration form.

### 4.2 Storage Permissions & Scoped Storage Strategy
* **Compliance Architecture**:
  1. **Android 13+ (API 33+)**: Uses official `android.permission.READ_MEDIA_AUDIO`.
  2. **Android 8.0 – 12 (API 26 – 32)**: Uses `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`). Enables direct POSIX file path crawling on Android 8.0 (Xperia X Compact / HiBy) without SAF latency.
  3. **Playlists & ID3 Tag Editing**: Uses `MediaStore.createWriteRequest()` on API 30+ or SAF `ACTION_OPEN_DOCUMENT_TREE` on user-specified music folders.

### 4.3 Foreground Service & Background Audio (API 34+ Requirements)
* Declares `android:foregroundServiceType="mediaPlayback"` on `PlaybackService`.
* Declares permissions `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
* Integrates with `MediaSessionCompat` / `androidx.media3.session.MediaSession` for system notifications.

### 4.4 Target SDK Requirements
* `minSdkVersion = 26` (Android 8.0 Oreo).
* `compileSdkVersion = 34` / `targetSdkVersion = 34`.

---

## 5. Extreme Low-RAM & Low-Spec DAP Architecture

```
       +-------------------------------------------------------------+
       |             SPINDLE MEMORY BUDGET (<45 MB)                  |
       +-------------------------------------------------------------+
                                      |
         +----------------------------+----------------------------+
         |                            |                            |
         v                            v                            v
  Bitmap Cache (RGB_565)     Room Database & Cursors       Audio Buffers
     (Max: 16 MB)                 (Max: 6 MB)               (Max: 8 MB)
         |                            |                            |
         v                            v                            v
  Canvas Hardware Render       Lightweight ViewModels       Media3 Audio Engine
     (Max: 5 MB)                  (Max: 4 MB)               (Max: 6 MB)
```

1. **Pure Native Views & Canvas (No Jetpack Compose)**:
   - Single custom `CassetteView` rendering the WM-2 chassis, diagonal control bezel, acrylic window, and kinetic reels directly via hardware-accelerated Android `Canvas`.
   - **Zero Allocations in `onDraw()`**: All `Paint`, `Path`, `Matrix`, and `RectF` objects are pre-allocated during view initialization. Zero GC churn!
2. **RGB_565 Color Format**: Cuts image memory by **50%** compared to standard ARGB_8888.
3. **Micro-Downsampling on Decode**:
   - Player Cassette Label: max 300 x 300 px (~180 KB in RGB_565).
   - Catalog Thumbnails: max 128 x 128 px (~32 KB in RGB_565).
4. **Target Metrics**:
   - **Total Idle RAM**: <= 35 MB.
   - **Peak Playback RAM**: <= 48 MB.
   - **Total APK Size (Release with R8)**: <= 6.5 MB.

---

## 6. Kinetic Differential Reel Kinematics

Linear tape speed is constant at $v = 4.7625\text{ cm/s}$. Given track progress ratio $p \in [0.0, 1.0]$:
1. **Supply Spool (Left Reel)**:
   $$R_{\text{left}}(p) = \sqrt{R_{\text{hub}}^2 + (R_{\text{max}}^2 - R_{\text{hub}}^2) \cdot (1 - p)}, \quad \omega_{\text{left}}(p) = \frac{v}{R_{\text{left}}(p)}$$
2. **Take-up Spool (Right Reel)**:
   $$R_{\text{right}}(p) = \sqrt{R_{\text{hub}}^2 + (R_{\text{max}}^2 - R_{\text{hub}}^2) \cdot p}, \quad \omega_{\text{right}}(p) = \frac{v}{R_{\text{right}}(p)}$$

---

## 7. Audiophile Sound Engine & Real-time Metrics

* **Codecs**: FLAC, ALAC, WAV (16/24/32-bit), AIFF, DSD (DSF/DFF via PCM transcoding), OGG Vorbis, AAC, MP3.
* **Gapless Playback**: Zero-latency buffer pre-loading.
* **ReplayGain (EBU R128)**: Automatic volume leveling without dynamic compression.
* **Audio Metrics (Swipe Left - Tab 2)**:
  - Source: Codec, bit depth, sample rate, live bitrate, ReplayGain offset.
  - Output: Route (3.5mm Jack, Bluetooth LDAC/aptX/AAC, USB DAC), audio sink sample rate, and **Resampling Alert** (Native vs 48kHz Resampled).
* **10-Band Equalizer (Swipe Left - Tab 3)**: Audiophile target presets and custom EQ curves.

---

## 8. Complete Project File Layout

```
dap_launcher/
├── MASTER_DOCUMENTATION.md                  # This master specification
├── build.gradle.kts                         # Root Gradle build script
├── settings.gradle.kts                      # Module settings
└── app/
    ├── build.gradle.kts                     # App dependencies (Media3, Room, Palette, R8)
    ├── proguard-rules.pro                   # R8 stripping rules for <6.5MB APK
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml          # Launcher intent, permissions, foreground service
        │   ├── java/com/hana/spindle/
        │   │   ├── SpindleApp.kt            # Application entrypoint & dependency container
        │   │   ├── theme/                   # Parametric Theme Subsystem
        │   │   │   ├── CassetteTheme.kt     # WM-2 Red & classic cassette skins
        │   │   │   ├── ThemeManager.kt      # Preset themes & reactive state
        │   │   │   └── PaletteHelper.kt     # Dynamic album art color extractor
        │   │   ├── ui/
        │   │   │   ├── MainActivity.kt      # Main container with swipe pager
        │   │   │   ├── cassette/            # Main Cassette Player
        │   │   │   │   ├── Wm2ChassisView.kt # Hardware-accelerated WM-2 Red & diagonal controls
        │   │   │   │   ├── CassetteView.kt  # Custom Canvas: parametric tape, reels, window
        │   │   │   │   ├── SpindleKinematics.kt # Mathematical tape movement model
        │   │   │   │   └── SideBTracklistView.kt # 3D Flip tracklist
        │   │   │   ├── drawer/              # Swipe Left Screen (3 Tabs)
        │   │   │   │   ├── DrawerContainerFragment.kt
        │   │   │   │   ├── AppDrawerFragment.kt      # Installed applications grid
        │   │   │   │   ├── AudioMetricsFragment.kt   # Real-time audiophile metrics
        │   │   │   │   ├── EqualizerFragment.kt      # 10-band DSP & EQ
        │   │   │   │   └── ThemeSelectorDialog.kt    # Visual tape & chassis picker
        │   │   │   └── catalog/             # Swipe Right Screen (Audio Catalog)
        │   │   │       ├── CatalogContainerFragment.kt
        │   │   │       ├── SongsFragment.kt
        │   │   │       ├── AlbumsFragment.kt
        │   │   │       ├── ArtistsFragment.kt
        │   │   │       ├── FolderBrowserFragment.kt  # Direct SD card folder tree
        │   │   │       └── PlaylistFragment.kt
        │   │   ├── playback/                # Audio Playback Subsystem
        │   │   │   ├── PlaybackService.kt   # Foreground MediaSession Service
        │   │   │   ├── AudioEngine.kt       # Media3 / ExoPlayer wrapper
        │   │   │   └── AudioMetricsTracker.kt# Sample rate & bit depth analyzer
        │   │   ├── data/                    # Storage & Database
        │   │   │   ├── MusicScanner.kt      # High-speed SD card scanner
        │   │   │   ├── TagParser.kt         # Lightweight ID3/FLAC metadata extractor
        │   │   │   ├── ImageLoader.kt       # RGB_565 bounded thumbnail cache
        │   │   │   └── db/
        │   │   │       ├── SpindleDatabase.kt # Room Database
        │   │   │       ├── SongDao.kt
        │   │   │       └── SongEntity.kt
        │   │   └── launcher/                # Launcher Subsystem
        │   │       ├── AppListLoader.kt     # Installed apps fetcher
        │   │       └── AppItem.kt
        │   └── res/
        │       ├── drawable/                # WM-2 diagonal paths, knurled dial, buttons
        │       ├── layout/                  # Minimal XML layouts
        │       ├── values/                  # Colors, themes, strings
        │       └── xml/                     # Home launcher configurations
```
