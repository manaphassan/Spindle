# Spindle — Master Technical & Architectural Documentation
**Product Name:** Spindle (Audiophile DAP Launcher)  
**Package Name:** `com.hana.spindle`  
**Hero Hardware Inspiration:** Sony Walkman II (WM-2) & Modern Minimalist Audiophile Deck  
**Version:** 1.2.0-RELEASE  
**Author / Art Director & Lead Systems Architect:** Spindle Core Team  
**Platform Target:** Android 8.0 (API 26) through Android 14/15 (API 34/35)  
**Primary Hardware Targets:** Ultra-low-resource Android DAPs (HiBy R5/R6, Shanling M3X/M6, FiiO M6/M9/M11, Sony Walkman NW-A100/ZX500, Sony Xperia X Compact, E-Ink DAPs)  
**License:** Apache License 2.0 (with Trademark & Visual IP Reservation)

---

## 1. Executive Summary

**Spindle** is an ultra-lightweight, audiophile-grade Android Home Launcher designed to transform any Android hardware—especially dedicated Digital Audio Players (DAPs) and compact vintage Android devices like the Sony Xperia X Compact—into a physical-feeling, distraction-free Walkman.

The hero aesthetic unites legendary Japanese industrial audio design with a pure, minimalist audiophile catalog and dedicated single-audio player. Spindle delivers a centered kinetic cassette spindle window, column-aligned hardware telemetry, an interactive circular radial progress scrubber with live audio waveforms, synced lyrics, and an A-Z fast alphabet scroller—all operating with **zero cloud bloat and zero background AI**.

### Core Pillars
1. **Centered Spindle Cassette Deck**: Horizontally centered clear acrylic window with non-linear kinetic reels ($v = 4.7625\text{ cm/s}$), left column-aligned time, Hi-Res format capsule badge, bold Line 0 title, and Line 1 artist/duration.
2. **Dedicated Single Audio Now Playing**: Pure minimalist overlay featuring circular album art framed by a radial progress arc scrubber, dynamic 24-band frequency envelope visualizer, format telemetry chip, and expandable synced lyrics drawer.
3. **Audiophile Music Catalog & Index**: Tactile vertical A-Z alphabet fast-scroller with haptic ticks, 6-attribute sorting dialog (Title, Artist, Album, Year, Duration, Bitrate), format filters (Hi-Res 24-bit, Lossless, MP3), and persistent mini-player.
4. **Curated 3-Theme Hardware Palettes**:
   - **Audiophile Dark (Obsidian)**: Deep charcoal and OLED black with glowing mint accents.
   - **Monochrome E-Ink**: 1-bit high-contrast pure black and white tailored specifically for e-paper / e-ink DAPs (Onyx Boox, Hisense).
   - **Clean Light (Brushed Aluminum)**: Industrial silver and crisp white minimalist aesthetic.
5. **Continuous Auto-Play & State Persistence**: Automatically sequences to the next track upon completion (`REPEAT_MODE_OFF`). Remembers and restores last-played track and seek position across system reboots.
6. **Extreme Low-RAM Footprint**: Tailored specifically for legacy DAPs with as little as **1GB – 2GB of RAM** and weak quad-core Cortex-A53 processors. Idle footprint $< 25\text{MB}$.

---

## 2. Interface Architecture & Component Specifications

### 2.1 Flagship Cassette Deck (`VerticalDeckView.kt`)
The center home screen renders a custom hardware-accelerated Canvas deck:

```
+-------------------------------------------------------------+
|                                                             |
|   +-------------------+  +-------------------------------+  |
|   | WALKMAN           |  |                               |  |
|   | 10:45:22          |  |    CENTERED CLEAR SPINDLE     |  |
|   | [ FLAC 24/96K ]   |  |          WINDOW DECK          |  |
|   |                   |  |                               |  |
|   | Song Title        |  |     (( O ))       (( O ))     |  |
|   | Artist • 01:23    |  |    [Supply]      [Take-up]    |  |
|   +-------------------+  +-------------------------------+  |
|   <--- columnCenterX ---><------ centerWindowRect ------>    |
|                                                             |
|   +-----------------------------------------------------+   |
|   | [<< REW]   [>> FWD]    [> PLAY / || PAUSE]   [EJECT] |  |
|   +-----------------------------------------------------+   |
|                                                             |
+-------------------------------------------------------------+
```

1. **Geometry & Centering (`centerWindowRect`)**:
   - The clear acrylic spindle window is centered horizontally across the display width.
   - Houses the dual mechanical spools, cogs, guide rollers, and simulated magnetic tape ribbon.
2. **Column-Aligned Telemetry (`columnCenterX`)**:
   - Calculated precisely at the horizontal midpoint between the display's left margin and the left edge of the spindle window.
   - **Dynamic Hardware Nameplate Header**: Automatically detects connected hardware model via `Build.MODEL`, `Build.DEVICE`, and `Build.MANUFACTURER` (e.g., `XPERIA ACTIVE`, `XPERIA X COMPACT`, `WALKMAN NW-A105`, `SHANLING M3X`), formatting it into an authentic uppercase engraved nameplate (with user custom override support).
   - **Clock**: Real-time 24-hour clock (`HH:MM:SS`, bold monospace).
   - **Hi-Res Audio Format Capsule Badge**: Drawn directly beneath the clock with rounded pill borders (e.g. `FLAC 16-BIT / 44.1 KHZ`, `MP3 320 KBPS`).
   - **2-Line Active Song Metadata**:
     - **Line 0**: Track Title (bold crisp white, marquee/wrap logic).
     - **Line 1**: Artist Name • mm:ss / mm:ss (slate subtext with bullet separator).
3. **Transport Deck & Kinematics**:
   - `REW`, `FWD`, `PLAY / PAUSE`, and `⏏ EJECT` buttons with physical detent haptics.
   - Differential kinetic reel rotation speeds calculated from physical tape pack radius equations.

### 2.2 Dedicated Single Audio Now Playing (`CatalogFragment.kt`)
Triggered by tapping the floating mini-player in the music catalog:
1. **Circular Cover & Radial Arc (`CircularCoverArcView.kt`)**:
   - Custom view rendering a centered circular album artwork thumbnail.
   - Outer track is framed with a 360° progress arc that supports direct rotational touch scrubbing.
2. **Live Dynamic Waveform (`AudioWaveformView.kt`)**:
   - 24 vertical bars vibrating in real time in response to playback ballistics and frequency envelopes.
3. **Hi-Res Format Telemetry Badge**:
   - Interactive badge displaying container, bit depth, sample frequency, and live bitrate (`FLAC 16-bit / 44.1kHz • 846 kbps`).
4. **Expandable Synced Lyrics Drawer (`LyricsParser.kt` & `LyricsAdapter.kt`)**:
   - Automatically parses `.lrc` timestamp files located alongside the audio file or extracts embedded SYLT/USLT tags.
   - Smoothly auto-scrolls to highlight the currently sung line in sync with playback.
5. **Technical File Specs Dialog (`DialogFileSpecs.kt`)**:
   - Complete technical sheet showing format, codec, sample rate, bit depth, channel configuration, dynamic bitrate, file size, and filesystem URI.

### 2.3 Audiophile Music Catalog & Browsing
1. **A-Z Fast Alphabet Scroller (`AlphabetIndexView.kt`)**:
   - Vertical alphabet rail (A–Z, #) on the right edge of the screen.
   - Dragging across letters triggers haptic tick vibrations and instantly scrolls the list to matching tracks.
2. **Sorting & Grouping (`SortGroupBottomSheet.kt`)**:
   - Sort by **Title**, **Artist**, **Album**, **Year**, **Duration**, or **Bitrate**.
   - Sort direction: Ascending / Descending toggle.
3. **Format Filters**:
   - Instant filter chips for `All`, `Hi-Res (24-bit+)`, `Lossless (FLAC/WAV)`, and `MP3`.

### 2.4 Braun / Dieter Rams Online FM Radio (`RadioFragment.kt`)
1. **Swipe-to-Tune**: Page 2 in the main launcher ViewPager2.
2. **Concentric Speaker Grille (`RadioSpeakerGrilleView.kt`)**: 7-ring concentric perforation pattern with acoustic recess shadows.
3. **3D Ribbed Tuning Dial (`RadioTuningDialView.kt`)**: Tactile cylindrical thumbwheel with moving calibrated frequency scale (`87.5 - 108.0 MHz`).
4. **Vintage LCD Display**: Mint-green backlit panel showing frequency, RDS station info, and connection status.

---

## 3. Playback Architecture & State Persistence

```
                        +----------------------+
                        |   AudioEngine.kt     |
                        |   (Media3/ExoPlayer) |
                        +----------+-----------+
                                   |
                  +----------------+----------------+
                  |                                 |
                  v                                 v
        +-------------------+             +-------------------+
        | onPlaybackEnded() |             | SharedPreferences |
        | Auto Next Song    |             | "playback_state"  |
        | (REPEAT_OFF)      |             | Last Track & Pos  |
        +-------------------+             +-------------------+
```

1. **Auto-Play Progression**:
   - In standard mode (`repeatMode = REPEAT_MODE_OFF`), finishing a track triggers automatic playback of the next sequential song in the catalog/queue.
   - Repeat modes: `REPEAT_MODE_OFF` (sequential progression), `REPEAT_MODE_ALL` (loop entire playlist), `REPEAT_MODE_ONE` (loop current track).
2. **State Persistence**:
   - Track ID, URI, position in milliseconds, and catalog shuffle state are written to `playback_state` SharedPreferences.
   - On launcher launch, the last played track is automatically restored and populated into the cassette deck and mini-player.

---

## 4. Memory Budget & Low-RAM DAP Optimization

```
       +-------------------------------------------------------------+
       |             SPINDLE MEMORY BUDGET (<38 MB)                  |
       +-------------------------------------------------------------+
                                      |
         +----------------------------+----------------------------+
         |                            |                            |
         v                            v                            v
  Bitmap Cache (RGB_565)     Room Database & Cursors       Audio Buffers
     (Max: 12 MB)                 (Max: 4 MB)               (Max: 6 MB)
         |                            |                            |
         v                            v                            v
  Canvas Hardware Render       Lightweight ViewModels       Media3 Audio Engine
     (Max: 5 MB)                  (Max: 3 MB)               (Max: 6 MB)
```

1. **Pure Native Views & Canvas (No Compose Overhead)**:
   - All complex animations (spools, waveforms, radial arcs, grilles) run on custom Android `View` implementations using pre-allocated `Paint`, `Path`, and `RectF` structures.
2. **RGB_565 Image Pipeline**:
   - Album artwork decoded strictly in 16-bit `RGB_565` format, halving memory usage compared to default `ARGB_8888`.
3. **Resource Metrics**:
   - **Idle RAM Overhead**: $< 25\text{MB}$.
   - **Active Playback RAM**: $< 38\text{MB}$.
   - **APK Package Size**: $< 4.5\text{MB}$.

---

## 5. Kinetic Differential Reel Kinematics

Linear tape speed is constant at $v = 4.7625\text{ cm/s}$. Given track progress ratio $p \in [0.0, 1.0]$:
1. **Supply Spool (Left Reel)**:
   $$R_{\text{left}}(p) = \sqrt{R_{\text{hub}}^2 + (R_{\text{max}}^2 - R_{\text{hub}}^2) \cdot (1 - p)}, \quad \omega_{\text{left}}(p) = \frac{v}{R_{\text{left}}(p)}$$
2. **Take-up Spool (Right Reel)**:
   $$R_{\text{right}}(p) = \sqrt{R_{\text{hub}}^2 + (R_{\text{max}}^2 - R_{\text{hub}}^2) \cdot p}, \quad \omega_{\text{right}}(p) = \frac{v}{R_{\text{right}}(p)}$$

---

## 6. Complete Project File Layout

```
dap_launcher/
├── build.gradle.kts                         # Root Gradle build script
├── settings.gradle.kts                      # Module settings
├── MASTER_DOCUMENTATION.md                  # This master specification
├── README.md                                # Repository overview & quick start
├── docs/                                    # GitHub Pages site & assets
│   ├── index.html                           # Live project showcase website
│   ├── BRANDING.md                          # Brand identity & color specifications
│   └── assets/                              # High-resolution screenshots & logos
│       ├── screenshot_walkman_home.png      # Home Cassette Deck (Centered Spindle & Telemetry)
│       ├── screenshot_now_playing.png       # Dedicated Single Audio Now Playing
│       ├── screenshot_catalog.png           # Music Catalog with A-Z Alphabet Scroller
│       ├── screenshot_braun_radio.png       # Braun / Dieter Rams Online FM Radio
│       ├── screenshot_dj_eq.png             # Dark Audiophile DJ Console
│       ├── spindle_app_icon.png             # Official Spindle gear hub icon
│       └── spindle_hero_banner.jpg          # Showcase banner
└── app/
    ├── build.gradle.kts                     # App dependencies (Media3, Room, Palette, R8)
    ├── proguard-rules.pro                   # R8 stripping rules for <4.5MB APK
    └── src/
        └── main/
            ├── AndroidManifest.xml          # Launcher intent, permissions, foreground service
            ├── java/com/hana/spindle/
            │   ├── SpindleApp.kt            # Application entrypoint & dependency container
            │   ├── data/                    # Storage, Database & Parsers
            │   │   ├── MusicScanner.kt      # High-speed SD card scanner
            │   │   ├── TagParser.kt         # Lightweight ID3/FLAC metadata extractor
            │   │   ├── LyricsParser.kt      # .lrc and embedded lyrics parser
            │   │   ├── ImageLoader.kt       # RGB_565 bounded thumbnail cache
            │   │   └── db/
            │   │       ├── SpindleDatabase.kt # Room Database
            │   │       ├── SongDao.kt       # Query operations for songs & albums
            │   │       ├── SongEntity.kt    # Audio entity with format & bitrate fields
            │   │       └── AlbumItem.kt     # Album model
            │   ├── playback/                # Audio Playback Subsystem
            │   │   ├── PlaybackService.kt   # Foreground MediaSession Service
            │   │   ├── AudioEngine.kt       # Media3 / ExoPlayer wrapper with auto-play & state persistence
            │   │   ├── RadioStreamEngine.kt # Low-latency online shoutcast stream player
            │   │   └── AudioMetricsTracker.kt # Real-time sample rate & bit depth analyzer
            │   ├── launcher/                # Launcher Subsystem
            │   │   ├── AppListLoader.kt     # Installed apps fetcher (<2MB overhead)
            │   │   └── AppItem.kt           # App list item model
            │   ├── theme/                   # Parametric Theme Subsystem
            │   │   ├── CassetteTheme.kt     # Palettes for Dark Obsidian, E-Ink, and Light
            │   │   ├── ThemeManager.kt      # Theme selector & preferences store
            │   │   └── PaletteHelper.kt     # Dynamic album art color extractor
            │   └── ui/
            │       ├── MainActivity.kt      # 3-Page ViewPager2 Launcher Controller
            │       ├── PlayerFragment.kt    # Home Screen Walkman Deck Controller
            │       ├── CatalogFragment.kt   # Music Catalog & Single Audio Now Playing Overlay
            │       ├── DrawerFragment.kt    # App Drawer, Volume Slider & Settings
            │       ├── AlphabetIndexView.kt # Tactile vertical A-Z alphabet scroller
            │       ├── LyricsAdapter.kt     # Real-time synced lyrics line adapter
            │       ├── DialogFileSpecs.kt   # Audiophile technical file specifications inspector
            │       ├── cassette/            # Kinetic Cassette Deck Views
            │       │   ├── VerticalDeckView.kt  # Centered spindle deck with column telemetry
            │       │   ├── CassetteKinematics.kt # Reel angular velocity kinematics
            │       │   └── CassetteView.kt  # Custom Canvas tape spool rendering
            │       ├── catalog/             # Catalog Custom Views & Adapters
            │       │   ├── CircularCoverArcView.kt # Circular cover with radial progress arc
            │       │   ├── AudioWaveformView.kt    # Dynamic 24-band frequency visualizer
            │       │   ├── CatalogSortGroup.kt     # Sort & grouping enum definitions
            │       │   ├── SortGroupBottomSheet.kt # Sort & filter modal dialog
            │       │   ├── SongAdapter.kt          # Song list RecyclerView adapter
            │       │   └── AlbumAdapter.kt         # 2-column album grid adapter
            │       └── radio/               # Braun Radio Custom Views
            │           ├── RadioFragment.kt        # Online radio controller
            │           ├── RadioSpeakerGrilleView.kt # Concentric acoustic hole grille
            │           └── RadioTuningDialView.kt  # 3D ribbed tuning cylinder
            └── res/                         # Hardware vector graphics, layouts, and styles
```

---

## 7. Edition Matrix: Spindle (Standard) vs. Spindle Lite

Spindle is maintained across two targeted build modules to optimize for modern audiophile DAPs while preserving 2011–2014 vintage compact Android hardware:

| Architectural Dimension | Spindle Standard (`:app`) | Spindle Lite (`:app-lite` / `satsuma`) |
| :--- | :--- | :--- |
| **Target OS / API** | Android 8.0 – 15 (API 26 – 35) | Android 4.4 KitKat – 7.1 (API 19 – 25) |
| **Target Hardware** | 1 GB – 4 GB RAM, 720p/1080p DAPs & Phones | 512 MB – 1 GB RAM, 320×480 (HVGA) Legacy Devices |
| **Hero Hardware Example**| Sony Xperia X Compact (`SO-02J`), HiBy R5 | Sony Ericsson Xperia active (`satsuma` ST17i) |
| **Audio Engine** | `androidx.media3` (ExoPlayer v1.5.1) | Dual-instance native `android.media.MediaPlayer` |
| **Gapless Playback** | Media3 `AudioSink` & MediaSession | Native `setNextMediaPlayer()` API chaining |
| **Database & Indexing** | Room ORM 2.6.1 + Kotlin Coroutines | Native `SQLiteOpenHelper` + Background POSIX crawler |
| **UI Framework** | ViewPager2 + Material 3 + Pure Canvas | ViewPager (Legacy) + Pure Canvas (Zero Compose/M3) |
| **Display Geometry** | Responsive 720p/1080p (16:9, 18:9, 21:9) | Scaled HVGA 320×480 (3:2 Aspect Ratio) |
| **Hardware Nameplate** | Auto `DeviceNameFormatter` (Customizable) | Auto `DeviceNameFormatter` (Customizable) |
| **Hardware Key Binding**| Standard MediaSession KeyEvents | Dedicated Camera Shutter Key (`KEYCODE_CAMERA`) + Volume Skip |
| **Bitmap Cache** | `RGB_565` (512×512, Max 12 MB) | `RGB_565` (256×256, Max 4 MB) |
| **Idle Memory Footprint**| $< 25\text{ MB}$ | $< 12\text{ MB}$ |
| **Active Playback RAM** | $< 38\text{ MB}$ | $< 18\text{ MB}$ |
| **APK Binary Size** | $< 4.5\text{ MB}$ | $< 1.8\text{ MB}$ |

---

<div align="center">
  <sub>Spindle · Engineered with precision for audiophiles, analog purists, and hardware preservation.</sub>
</div>

