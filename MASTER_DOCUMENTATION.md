# Spindle — Master Technical & Architectural Documentation
**Product Name:** Spindle (Audiophile DAP Launcher)  
**Package Name:** `com.hana.spindle`  
**Hero Hardware Inspiration:** Classic 1980s Japanese Portable Cassette Decks & Modern Minimalist Audiophile Gear  
**Version:** 1.4.0-alpha  
**Author / Art Director & Lead Systems Architect:** Spindle Core Team  
**Platform Target:** Android 8.0 (API 26) through Android 14/15 (API 34/35)  
**Primary Hardware Targets:** Ultra-low-resource Android DAPs, Compact Smartphones with Hardware DACs, and E-Ink DAPs  
**License:** PolyForm Noncommercial License 1.0.0 (Open source for personal/non-commercial use; commercial sale strictly prohibited)

---

## 1. Executive Summary

**Spindle** is an ultra-lightweight, audiophile-grade Android Home Launcher designed to transform any Android hardware—especially dedicated Digital Audio Players (DAPs) and compact vintage Android devices—into a physical-feeling, distraction-free analog player.

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
|   | SPINDLE           |  |                               |  |
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

1. **Default Metal-XR Type IV Chassis**:
   - Centered gold/red metallic foil hot-stamping (`EXTRALLOY METAL-XR • TYPE IV METAL`) across the top edge.
   - Halved spindle hub gap for authentic vintage cassette shell geometry.
2. **Geometry & Centering (`centerWindowRect`)**:
   - The clear acrylic spindle window is centered horizontally across the display width.
   - Houses dual mechanical spools, cogs, guide rollers, and traveling magnetic tape oxide.
3. **Transparent Leader Tape & Amber Splice**:
   - When near reel limits (`progress <= 0.025f` or `>= 0.975f`), the magnetic brown oxide transitions to a transparent clear polyfilm leader ribbon.
   - 45° diagonal amber splice line (`#D97706`) realistically connects the clear leader to the magnetic oxide ribbon at the guide rollers.
4. **Continuous High-Speed Kinematics & Acoustic Foley**:
   - Reel rotation accelerates continuously from 1.0x up to 8.0x during sustained FWD/REW button holds.
   - Procedural foley engine loops motor spool whir with dynamic pitch ramping (0.9x to 1.65x).
   - Solenoid release click on key release, and heavy solenoid auto-stop clack when hitting reel limits.
5. **Standardized Status Row & Interactive Badges**:
   - Left-aligned tactile badges: `[TYPE I / II / IV]`, `[DOLBY B / C / OFF]`, and `[J-CARD]`.
   - Right-aligned hardware status: dynamic `Battery` bar and green `Run` transport LED.
6. **Transport Deck & Kinematics**:
   - `REW`, `FWD`, `PLAY / PAUSE`, and `⏏ EJECT` buttons with physical detent haptics.
   - Differential kinetic reel rotation speeds calculated from physical tape pack radius equations.

### 2.2 Dynamic 3D J-Card Liner Notes (`JCardLinerView.kt`)
Triggered by tapping the cassette body or the `[J-CARD]` badge on the deck:
1. **3D Spatial Card Flip**:
   - Perspective Y-axis rotation (`rotationY`, `cameraDistance = 8000dp`) smoothly transitions between the physical cassette deck and the unfolded jewel-case J-Card.
2. **Vintage Jewel-Case Cardstock Architecture**:
   - **Spine Fold**: Bold uppercase retro typography displaying album, artist, tape model formulation (`EXTRALLOY METAL-XR 90 • TYPE IV`), and total Side A / Side B runtimes (`A: mm:ss | B: mm:ss`).
   - **Booklet Section**: High-resolution album artwork thumbnail frame, audiophile format specs (`FLAC 24-bit / 96kHz • Direct PCM`), and mastering notes.
   - **Dual-Column Tracklist**: Automatically splits album tracks into Side A and Side B with vintage numbering (`A01..`, `B01..`), individual track durations, and a glowing theme-accented active playing indicator (`▶`).
   - **Direct Touch-to-Play**: Tapping any track row instantly seeks and plays the selected song while seamlessly flipping back to the running tape deck.

### 2.3 Dedicated Single Audio Now Playing (`CatalogFragment.kt`)
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

### 2.4 Audiophile Music Catalog & Browsing
1. **A-Z Fast Alphabet Scroller (`AlphabetIndexView.kt`)**:
   - Vertical alphabet rail (A–Z, #) on the right edge of the screen.
   - Dragging across letters triggers haptic tick vibrations and instantly scrolls the list to matching tracks.
2. **Sorting & Grouping (`SortGroupBottomSheet.kt`)**:
   - Sort by **Title**, **Artist**, **Album**, **Year**, **Duration**, or **Bitrate**.
   - Sort direction: Ascending / Descending toggle.
3. **Format Filters**:
   - Instant filter chips for `All`, `Hi-Res (24-bit+)`, `Lossless (FLAC/WAV)`, and `MP3`.

### 2.5 Bauhaus Minimalist Radio with Hardware Antenna Detection (`RadioFragment.kt`)
1. **Swipe-to-Tune**: Page 2 in the main launcher ViewPager.
2. **Dedicated FM vs DIGI Modes**:
   - **FM Mode**: Local over-the-air analog FM receiver requiring 3.5mm wired headphones connected as an antenna, with safety guidance if unplugged.
   - **DIGI Mode**: Dedicated DAB+ / online stream mode for internet radio stations with zero static.
3. **Streamlined Ballistic RF Signal Meter**: High-density compact galvanometer meter displaying signal strength (0–5 S-units) and center-channel tuning needle.
4. **Concentric Speaker Grille (`RadioSpeakerGrilleView.kt`)**: 7-ring concentric perforation pattern with acoustic recess shadows.
5. **3D Ribbed Tuning Dial (`RadioTuningDialView.kt`)**: Tactile cylindrical thumbwheel with moving calibrated frequency scale (`87.5 - 108.0 MHz`).
6. **Vintage LCD Display**: Mint-green backlit panel showing frequency, RDS station info, and connection status.

### 2.6 Studio Sound Deck & DSP Touch Lock (`DrawerFragment.kt`)
1. **Lockable Studio Parametric EQ & DSP**: Dedicated tactile `[LOCK]` button securing the 10-band ISO graphic equalizer and parametric DSP knobs against accidental palm and pocket touches during playback.
2. **Dual Analog Ballistic VU Meters**: Dedicated left and right channel galvanometer needles with warm vintage dial face, +3dB redline scale, and glowing peak indicator LEDs.
3. **10-Band ISO Graphic & Parametric Equalizer**:
   - **Dual Operating Modes**: Switch between `GRAPHIC` (standard 1-octave ISO interpolation) and `PARAMETRIC` mode (continuous multi-bell acoustic summation modeling).
   - **Multi-Bell Acoustic Curve**: Uses Gaussian log-frequency formulation:
     $$\text{bell}(f, f_c, Q) = \exp\left(-0.5 \cdot \left(\ln(f / f_c) \cdot Q \cdot 1.5\right)^2\right)$$
     rendered dynamically across 50 log-spaced frequency points (20Hz–20kHz) on `Iso10BandEqView`.
   - **Tactile Q-Factor Cycling**: Double-tapping or long-pressing any band in parametric mode cycles through 4 audiophile bandwidth presets: $0.707$ (Wide tone shaping), $1.414$ (Normal 1-octave ISO), $2.828$ (Narrow resonance suppression), and $5.0$ (Surgical notch/peak), visualized with live badge telemetry (`Q:0.7`, `Q:1.4`, etc.).
4. **Flagship DAP Hardware Button Integration (`MainActivity.kt`)**:
   - **Dedicated Mode Switch**: `switchDapHardwareMode` in hardware settings toggles `"pref_dap_hardware_mode"`.
   - **Exclusive Key Interception**: Consumes matching events in both `onKeyDown` and `onKeyUp` to prevent OEM ROM secondary intent launches (such as camera or voice assistants).
   - **Full Hardware Matrix**:
     - Media Transport: `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_STOP`, `KEYCODE_MEDIA_FAST_FORWARD`, `KEYCODE_MEDIA_REWIND`.
     - Hardware Wheel & D-Pad: `KEYCODE_DPAD_LEFT` (seek -5s), `KEYCODE_DPAD_RIGHT` (seek +5s), `KEYCODE_DPAD_CENTER` (play/pause toggle).
     - Auxiliary DAP Keys: `KEYCODE_FOCUS` (half-shutter advance next track), `KEYCODE_VOLUME_MUTE` (toggle mute), `KEYCODE_SEARCH` (open catalog), `KEYCODE_MENU` / `KEYCODE_APP_SWITCH` (open drawer).
5. **Hi-Res Passthrough (Device-Dependent)**: Direct hardware audio routing bypassing software DSP coloration when supported by device HAL and DAC.
6. **ReplayGain Loudness Calibration**: 3-state normalization mode (`OFF`, `TRACK GAIN`, `ALBUM GAIN`) standardizing untrimmed master tracks to reference 89.0 dB SPL.
7. **Equal-Power Crossfade & Gapless Transition**: 3-way transition selector (`GAPLESS 0s`, `FADE 2s`, `FADE 4s`) utilizing sinusoidal equal-power attenuation ($P_{\text{total}} = \cos^2\theta + \sin^2\theta = 1$) to eliminate transition volume dips.

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
│       ├── screenshot_deck_home.png         # Home Cassette Deck (Centered Spindle & Telemetry)
│       ├── screenshot_now_playing.png       # Dedicated Single Audio Now Playing
│       ├── screenshot_catalog.png           # Music Catalog with A-Z Alphabet Scroller
│       ├── screenshot_braun_radio.png       # Minimalist Bauhaus Online FM Radio
│       ├── screenshot_dj_eq.png             # Dark Audiophile DJ Console
│       ├── spindle_app_icon.png             # Official Spindle gear hub icon
│       └── spindle_hero_banner.jpg          # Showcase banner
├── core/                                    # Common domain contracts and primitives
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/hana/spindle/core/
│       ├── ReplayGainMode.kt                # OFF, TRACK, ALBUM volume normalization modes
│       └── CrossfadeMode.kt                 # GAPLESS (0s), CROSSFADE_2S, CROSSFADE_4S transitions
├── app-main/                                # Flagship modern Spindle DAP Launcher (:app-main)
│   ├── build.gradle.kts                     # App dependencies (Media3, Room, Palette, R8)
│   ├── proguard-rules.pro                   # R8 stripping rules for <4.5MB APK
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml          # Launcher intent, permissions, foreground service
│           ├── java/com/hana/spindle/
│           │   ├── SpindleApp.kt            # Application entrypoint & dependency container
│           │   ├── data/                    # Storage, Database, Parsers & M3U
│           │   │   ├── MusicScanner.kt      # High-speed SD card scanner
│           │   │   ├── TagParser.kt         # Lightweight ID3/FLAC metadata & ReplayGain extractor
│           │   │   ├── M3uManager.kt        # M3U/M3U8 playlist import, export & track matching
│           │   │   ├── LyricsParser.kt      # .lrc and embedded lyrics parser
│           │   │   ├── ImageLoader.kt       # RGB_565 bounded thumbnail cache
│           │   │   └── db/
│           │   │       ├── SpindleDatabase.kt # Room Database with Playlist & Track entities
│           │   │       ├── SongDao.kt       # Query operations for songs & albums
│           │   │       ├── PlaylistDao.kt   # Playlist CRUD and drag-to-reorder persistence
│           │   │       ├── SongEntity.kt    # Audio entity with format & bitrate fields
│           │   │       └── AlbumItem.kt     # Album model
│           │   ├── playback/                # Audio Playback Subsystem
│           │   │   ├── PlaybackService.kt   # Foreground MediaSession Service
│           │   │   ├── AudioEngine.kt       # Media3 / ExoPlayer wrapper with auto-play, sinusoidal crossfade & ReplayGain
│           │   │   ├── AudioFxController.kt # Equalizer, Bass Boost, Virtualizer, and Hardware band disclosure
│           │   │   ├── CassetteFoleyEngine.kt # Low-latency mechanical clicks, spool whirrs & eject thuds
│           │   │   ├── RadioStreamEngine.kt # Low-latency online shoutcast stream player
│           │   │   └── AudioMetricsTracker.kt # Real-time sample rate & bit depth analyzer
│           │   ├── launcher/                # Launcher Subsystem
│           │   │   ├── AppListLoader.kt     # Installed apps fetcher (<2MB overhead)
│           │   │   └── AppItem.kt           # App list item model
│           │   ├── theme/                   # Parametric Theme Subsystem
│           │   │   ├── CassetteTheme.kt     # Palettes for Dark Obsidian, E-Ink, and Light
│           │   │   ├── ThemeManager.kt      # Theme selector & preferences store
│           │   │   └── PaletteHelper.kt     # Dynamic album art color extractor
│           │   └── ui/
│           │       ├── MainActivity.kt      # 3-Page ViewPager2 Launcher Controller
│           │       ├── PlayerFragment.kt    # Home Screen Cassette Deck Controller
│           │       ├── CatalogFragment.kt   # Music Catalog, Mixtape Reorder & Single Audio Now Playing Overlay
│           │       ├── DrawerFragment.kt    # App Drawer, 10-Band EQ, Hardware Telemetry & Audio Settings
│           │       ├── AlphabetIndexView.kt # Tactile vertical A-Z alphabet scroller
│           │       ├── LyricsAdapter.kt     # Real-time synced lyrics line adapter
│           │       ├── DialogFileSpecs.kt   # Audiophile technical file specifications inspector
│           │       ├── cassette/            # Kinetic Cassette Deck Views
│           │       │   ├── VerticalDeckView.kt  # Centered spindle deck with column telemetry
│           │       │   ├── CassetteKinematics.kt # Reel angular velocity kinematics
│           │       │   └── CassetteView.kt  # Custom Canvas tape spool rendering
│           │       ├── catalog/             # Catalog Custom Views & Adapters
│           │       │   ├── CircularCoverArcView.kt # Circular cover with radial progress arc
│           │       │   ├── AudioWaveformView.kt    # Dynamic 24-band frequency visualizer
│           │       │   ├── MixtapeDialogs.kt       # Cut New Tape, Import M3U & Export M3U8 dialogs
│           │       │   ├── CatalogSortGroup.kt     # Sort & grouping enum definitions
│           │       │   ├── SortGroupBottomSheet.kt # Sort & filter modal dialog
│           │       │   ├── SongAdapter.kt          # Song list RecyclerView adapter
│           │       │   └── AlbumAdapter.kt         # 2-column album grid adapter
│           │       └── radio/               # Acoustic Radio Custom Views
│           │           ├── RadioFragment.kt        # Online radio controller
│           │           ├── RadioSpeakerGrilleView.kt # Concentric acoustic hole grille
│           │           └── RadioTuningDialView.kt  # 3D ribbed tuning cylinder
│           └── res/                         # Hardware vector graphics, layouts, and styles
└── app-lite/                                # Ultra-lightweight vintage/low-RAM variant (:app-lite)
    ├── build.gradle.kts                     # Android 4.4+ target, zero-dependency MediaPlayer engine
    └── src/main/                            # Lightweight HVGA Canvas UI & hardware button matrix
```

---

## 7. Edition Matrix: Spindle (Standard) vs. Spindle Lite

Spindle is maintained across two targeted build modules to optimize for modern audiophile DAPs while preserving 2011–2014 vintage compact Android hardware:

| Architectural Dimension | Spindle Standard (`:app-main`) | Spindle Lite (`:app-lite` / `satsuma`) |
| :--- | :--- | :--- |
| **Target OS / API** | Android 8.0 – 15 (API 26 – 35) | Android 4.4 KitKat – 7.1 (API 19 – 25) |
| **Target Hardware** | 1 GB – 4 GB RAM, 720p/1080p DAPs & Phones | 512 MB – 1 GB RAM, 320×480 (HVGA) Legacy Devices |
| **Hero Hardware Example**| Modern Compact Flagships, Android DAPs | Ultra-Compact Legacy Hardware (3.0" HVGA) |
| **Audio Engine** | `androidx.media3` (ExoPlayer v1.5.1) | Dual-instance native `android.media.MediaPlayer` |
| **Gapless Playback** | Media3 `AudioSink` & MediaSession | Native `setNextMediaPlayer()` API chaining |
| **Database & Indexing** | Room ORM 2.6.1 + Kotlin Coroutines | Native `SQLiteOpenHelper` + Background POSIX crawler |
| **UI Framework** | ViewPager2 + Material 3 + Pure Canvas | ViewPager (Legacy) + Pure Canvas (Zero Compose/M3) |
| **Display Geometry** | Responsive 720p/1080p (16:9, 18:9, 21:9) | Scaled HVGA 320×480 (3:2 Aspect Ratio) |
| **Hardware Nameplate** | Auto `DeviceNameFormatter` (Customizable) | Auto `DeviceNameFormatter` (Customizable) |
| **Hardware Key Engine** | Standard MediaSession KeyEvents | Full Physical/Capacitive Button Engine (`BACK`, `MENU`, `HOME`, `APP_SWITCH`, `CAMERA` full/half focus, `HEADSETHOOK`, `VOLUME`, `DPAD`) |
| **Bitmap Cache** | `RGB_565` (512×512, Max 12 MB) | `RGB_565` (256×256, Max 4 MB) |
| **Idle Memory Footprint**| $< 25\text{ MB}$ | $< 12\text{ MB}$ |
| **Active Playback RAM** | $< 38\text{ MB}$ | $< 18\text{ MB}$ |
| **APK Binary Size** | $< 4.5\text{ MB}$ | $< 1.8\text{ MB}$ (1.53 MB Verified) |

---

## 8. Product Roadmap & Development Trajectory

```
                                  SPINDLE ROADMAP & RELEASE MILESTONES
                                  
   [ v1.2.0-RELEASE ] ──> [ v1.3.0-alpha ] ──> [ v1.0.0-LITE ] ──> [ v1.4.0-STUDIO ] ──> [ v2.0.0-ECOSYSTEM ]
     Flagship Deck          Ribbon Reels         :app-lite Released   One-Time Paid / Pass   Direct USB ALSA
     Now Playing Arc        2-Row Routing        Android 4.4 KitKat   $2.99 – $3.99 Unlock   MicroSD Sync
     24-Band Waveform       Lock Screen Player   100% Free Forever    Reel-to-Reel Decks     CUE Sheet Splitter
     Nameplate Engine       Analog RF Tuner      1.79MB / <18MB RAM   Analog DSP & Foley     DLNA Renderer
     A-Z Fast Index         24-bit 96k FLAC      Full Hardware Keys   Tape Saturation        Cross-Platform
```

### 8.1 v1.2.0-RELEASE (Flagship Base Stable)
* **Flagship Cassette Deck**: Centered acrylic spindle window with kinetic differential spool physics ($v = 4.7625\text{ cm/s}$).
* **Dedicated Single-Audio Now Playing**: 360° touch radial progress arc scrubber, dynamic 24-band frequency envelope visualizer, format telemetry pill, and synced `.lrc` lyrics drawer.
* **Music Catalog**: Fast A-Z right-edge index rail with haptic feedback, 6-attribute sorting, and format filter chips.
* **Bauhaus Minimalist Radio**: Concentric acoustic speaker grille and 3D cylindrical tuning dial.
* **Dynamic Hardware Nameplate Engine**: Auto-detection and engraving of host device model.

### 8.2 v1.3.0-alpha (Current Flagship Alpha)
* **Kinetic Magnetic Ribbon & Reel Kinematics**: Real-time traveling magnetic tape ribbon with rotating anisotropic specular sheen glare cones, 3-spoke flange cutouts, and transparent polyfilm leader ribbon with 45° amber splice line.
* **2-Row Real-Time Output Routing Badges**: Dynamic telemetry badge showing container, sample rate, bit depth, and physical output destination (e.g. `FLAC • 24-bit / 96kHz + 3.5mm DIRECT`).
* **Dedicated Vintage Lock Screen Player**: Keyguard-bypassing transport deck with mechanical levers, digital counter, hardware battery diode telemetry, and upward dismiss gesture.
* **Analog FM Radio with Ballistic RF Meter**: Galvanometer RF signal needle, ruby red STEREO pilot LED, and 4-color dial incandescent backlighting.
* **24-bit / 96kHz Hi-Res Native Playback**: Lossless audio routing via AndroidX Media3 (ExoPlayer).

### 8.3 v1.0.0-LITE (Verified Release — Vintage & Legacy Target)
* **Dedicated `:app-lite` Build Module**: Parallel architecture strictly targeting Android 4.4 KitKat (API 19) down to Android 4.1 Jelly Bean (API 16).
* **Extreme Memory Optimization**: Verified release APK binary of **1.79 MB** with total active playback memory footprint under **18 MB** (idle $< 12\text{ MB}$).
* **Responsive 3.0" HVGA Geometry (320×480)**: Dynamic canvas scaling tailored specifically for ultra-compact legacy hardware (`satsuma` vintage compact hardware).
* **Formulation Decks**: Type IV Metal Master, Type II Chrome Hi-Bias, and Type I Normal Studio cassette skins dynamically triggered by file audio fidelity.
* **Mechanical Cassette Hardware Kinematics**: Zero-allocation 3-digit mechanical odometer drum counter (`000`–`999`), optical window glare sheen, and dual brass capstan guide pins.
* **Dual Native `MediaPlayer` Engine**: Zero-overhead gapless audio playback via `setNextMediaPlayer()` chaining.
* **Zero-Dependency Stream Parser (`AudioHeaderParser`)**: Native binary parsing of FLAC STREAMINFO and WAV RIFF chunks in $< 0.1\text{ms}$ for accurate bit-depth extraction.
* **Full Physical & Capacitive Hardware Button Engine**: Complete hardware button matrix intercepting `BACK`, `MENU`, `HOME`, `APP_SWITCH`, `CAMERA` (full click Play/Pause, hold sleep/wake), `FOCUS` (half-press Next Track), `HEADSETHOOK` (1/2/3 clicks), `VOLUME` (screen-off long-press skip), and `DPAD`.
* **Root `BACK` Key Tape Vault Trigger**: Pressing `BACK` at the root deck instantly slides open the Tape Vault catalogue; within overlays, `BACK` gracefully closes the drawer/vault.
* **Tape Vault 3-Tab Music Catalogue**: `TRACKS`, `FOLDERS` (hierarchical physical file-tree browser with `..` parent navigation), and `QUEUE` tabs with instant `HI-RES` and `LOSSLESS` filter chips.
* **Tactile App Drawer with Package Management**: 48dp+ single-thumb hit targets and long-press dialog for App Info (`APPLICATION_DETAILS_SETTINGS`) and App Uninstall (`ACTION_UNINSTALL_PACKAGE`).

### 8.4 v1.4.0-STUDIO (Master Studio Collector Edition & Monetization Milestone)
* **Distribution Milestone**: Launch of the **One-Time Studio Collector Pass ($2.99 – $3.99)** alongside the permanent Free Core player under `LICENSE-STUDIO.md`.
* **10-Band ISO Parametric Studio EQ (Completed)**: Mode switch (`GRAPHIC` vs `PARAMETRIC`), multi-bell Gaussian log-ratio acoustic curve summation, and per-band Q-factor cycle ($0.707$, $1.414$, $2.828$, $5.0$) with live visual badges.
* **Flagship DAP Hardware Button Engine (Completed)**: Full hardware button keycode capture (`onKeyDown`/`onKeyUp`) for transport, D-Pad/wheel seeking, mute, focus shutter, search, and menu keys with OEM intent suppression.
* **10.5" Open Reel-to-Reel Studio Deck (Completed)**: Animated studio master deck skin (`theme_reel_to_reel_studio`), 10.5" brushed aluminum reel flanges with 3-window circular Ampex/Revox cutouts revealing the rotating master oxide pack beneath, precision 3-winged NAB locking hub adapters, and concentric lathe turning scorelines.
* **Harmonic Analog Tape Saturation DSP (Completed)**: Audiophile non-linear 1/3-octave magnetic flux head bump (+3.2dB @ 63Hz), smooth mid-range warmth (+1.5dB @ 250Hz-500Hz), and natural high-frequency hysteresis tape saturation compression roll-off (-1.5dB @ 8kHz, -2.8dB @ 16kHz) with automatic anti-clipping pre-amp headroom attenuation.
* **Procedural Mechanical Foley Engine (Completed)**: Ultra-low-latency 16-bit 44.1kHz PCM sound synthesis requiring zero binary assets. Includes heavy solenoid clack, spring-loaded head release, carriage open pop, metal switch snap, and continuous dual-gear motor spool loop with real-time dynamic pitch ramping ($0.92\times \to 1.65\times$) during touch hold-seeking and leader auto-stop.
* **Studio Collector Licensing & Monetization (Completed)**: Dual verification via Google Play In-App Billing and offline cryptographic sponsor tokens (`SPINDLE-STUDIO-XXXX-YYYY`) verified via HMAC-SHA256 signature verification in `StudioUnlockManager.kt`, enabling 100% offline functionality on de-googled audiophile DAPs.
* **Custom Laser Nameplate Engraving (Completed)**: Personalized physical deck callsigns or hardware serial numbers persistently engraved on the player faceplate, gated under `StudioFeature.LASER_NAMEPLATE_ENGRAVING`.

### 8.5 v2.0.0-ECOSYSTEM (Future Vision)
* **Direct USB-OTG ALSA Driver**: Custom native user-space USB Audio Class 2.0 driver bypassing Android audio framework for bit-perfect DSD512 / 32-bit 768kHz output.
* **Audiophile CUE Sheet Splitter**: Real-time virtual track indexing for monolithic FLAC/APE album rips.
* **Cross-DAP MicroSD Catalog Sync**: Fast metadata transfer and playlist sharing between devices.
* **Hardware Volumio / DLNA Renderer**: Remote streaming endpoint control for home Hi-Fi stacks.

### 8.6 Commercial Distribution & Monetization Architecture

Spindle adopts a **"Fair Ownership & Anti-Subscription"** monetization architecture tailored specifically for the audiophile and retro-tech communities:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   SPINDLE COMMERCIAL & DISTRIBUTION MATRIX                  │
├──────────────────────────┬─────────────────────────┬────────────────────────┤
│     SPINDLE LITE         │    SPINDLE STANDARD     │    STUDIO COLLECTOR    │
│      (v1.0.0-LITE)       │     (v1.4.0-alpha)      │   (One-Time Unlock)    │
├──────────────────────────┼─────────────────────────┼────────────────────────┤
│ • Status: 100% Free      │ • Status: Free Core     │ • Status: $2.99 – $3.99│
│ • Distribution: Sideload │ • Distribution: Direct  │ • Distribution: Play   │
│   APK / GitHub / Web     │   APK & Google Play     │   Store / In-App Key   │
│ • Target: Android 4.4    │ • Target: Android 8.0+  │ • Target: Modern DAPs  │
│ • Full hardware buttons  │ • Kinetic cassette reels│ • Reel-to-Reel decks   │
│ • Tape Vault catalogue   │ • FM & Online radio     │ • Tape saturation DSP  │
│ • Zero tracking / no ads │ • Synced lyrics & EQ    │ • Custom foley engine  │
│ • E-waste revival hero   │ • 100% Offline & ad-free│ • Lifetime license     │
└──────────────────────────┴─────────────────────────┴────────────────────────┘
```

#### Core Commercial Principles:
1. **Zero Recurring Subscriptions**: Subscriptions are strictly prohibited. Local music players manage user-owned audio files stored on physical flash memory; users will never be subjected to monthly or annual charges.
2. **Spindle Lite Permanent Free Exemption**: Spindle Lite is strictly exempt from monetization. Vintage hardware running Android 4.4 (KitKat) cannot reliably execute modern Google Play Services or Play Billing APIs. Lite functions as an open-source gift to the vintage tech community and e-waste revival movement, establishing organic trust and grassroots adoption.
3. **One-Time Lifetime Studio Unlock ($2.99 – $3.99)**: Implemented as a single, permanent in-app purchase or unlock key on Google Play for Spindle Standard. Free users retain all essential playback, radio, and catalog features without ads; paying supporters unlock artisanal cosmetic studio hardware skins and analog audio DSP simulations.
4. **Community Sponsorship Channels & Offline Sponsor Tokens**: Sideloaders and direct APK users are provided a voluntary sponsorship pathway via direct PayPal contribution ([paypal.me/manaphassan](https://paypal.me/manaphassan)) and GitHub Sponsors to fund physical test device acquisitions. De-googled DAPs (Fiio, Hiby, Cayin, Astell&Kern running AOSP without GMS) can activate the full Studio Collector suite using cryptographic offline tokens (`SPINDLE-STUDIO-XXXX-YYYY`) verified via HMAC-SHA256 signature verification in `StudioUnlockManager.kt`.
5. **Technical Entitlement Architecture**:
   - `StudioUnlockManager.kt`: Zero-crash entitlement controller with offline fallback and SHA-256 token verification.
   - `DialogStudioUnlock.kt`: Bespoke industrial modal presenting the Anti-Subscription policy, feature breakdown, Google Play purchase trigger, and offline token redemption.
   - `pref_custom_device_name`: Personalized laser nameplate engraving on the cassette deck chassis gated by `StudioFeature.LASER_NAMEPLATE_ENGRAVING`.

---

## 9. Design System & UI Consistency Standards

Spindle enforces strict visual consistency, tactile physical skeuomorphism, and WCAG 2.2 accessibility across all components, adhering to [.agents/rules/ui-consistency.md](file:///.agents/rules/ui-consistency.md).

### 9.1 Semantic Color Tokens & Theme Matrix

| Design Token | Audiophile Dark (Studio) | Clean Light (Aluminum) | Monochrome E-Ink |
| :--- | :--- | :--- | :--- |
| **`background`** | `#0D0E11` / `#16181F` | `#E8E8E5` / `#FAFAF9` | `#FFFFFF` |
| **`surface`** | `#1C1E2A` | `#DCDCD8` / `#FFFFFF` | `#F0F0F0` |
| **`surface_elevated`** | `#262938` | `#FFFFFF` | `#E0E0E0` |
| **`surface_card`** | `#202334` | `#FFFFFF` | `#FFFFFF` |
| **`surface_container`** | `#15161A` | `#EFEFEA` | `#F8F8F8` |
| **`primary / accent`** | `#F97316` / `#00E676` | `#EA580C` / `#00A854` | `#000000` |
| **`text_primary`** | `#FAFAF9` (White) | `#1E2132` (Navy/Charcoal) | `#000000` (Pitch Black) |
| **`text_secondary`** | `#B0B4CE` (Slate Light) | `#64748B` / `#5A5E78` | `#555555` |
| **`text_muted`** | `#94A3B8` | `#64748B` | `#777777` |
| **`border`** | `#2A2E3D` / `#353A54` | `#C8C8C4` | `#000000` / `#AAAAAA` |

### 9.2 Standardized Spacing, Icon & Typography Scales (`dimens.xml`)
To prevent visual jitter on legacy DAP displays with non-standard screen densities (e.g., 213dpi or 280dpi), all layout dimensions follow strict integer scales defined in `app-main/src/main/res/values/dimens.xml`:
- **8-Point Spacing Scale**: `spacing_xxs` (2dp), `spacing_xs` (4dp), `spacing_s` (8dp), `spacing_m` (12dp), `spacing_l` (16dp), `spacing_xl` (20dp), `spacing_xxl` (24dp), `spacing_xxxl` (32dp).
- **Standardized Icon Bounding Boxes**: `icon_compact` (16dp), `icon_standard` (20dp), `icon_prominent` (24dp), `touch_target_min` (48dp).
- **Integer Typography Scale**: Fractional text sizes (e.g. `10.5sp`, `11.5sp`, `12.5sp`, `13.5sp`) are strictly consolidated into integer points (`10sp`, `11sp`, `12sp`, `14sp`, `15sp`, `16sp`, `18sp`, `24sp`) preventing sub-pixel raster blur.
- **Corner Radii**: `corner_radius_xs` (4dp), `corner_radius_s` (6dp), `corner_radius_m` (8dp), `corner_radius_l` (12dp), `corner_radius_pill` (16dp), `corner_radius_circle` (24dp).

### 9.3 WCAG 2.2 AA Contrast Compliance & String Localization
1. **WCAG 2.2 AA Color Contrast Compliance ($\ge 4.5:1$)**:
   - Fixed contrast failures across dialogs, headers, and metadata chips where low-contrast `#64748B` (3.4:1 ratio) or `#52525B` (2.4:1 ratio) on dark surfaces (`#12131A` / `#15161A`) were upgraded to `@color/text_secondary` (`#B0B4CE`, ratio $> 5.7:1$).
   - Resolved ViewPager2 startup background color flash by aligning root activity canvas from bright red accent to `@color/surface_dark`.
2. **String Resource Externalization**:
   - Extracted 40+ UI labels and messages across transport controls, queue sheet, file telemetry dialog, radio console, sort/group dialog, catalog headers, and drawer DSP controls into `strings.xml`.

### 9.4 Categorized Settings Architecture
To guarantee effortless navigation on compact 3.5"–5.0" screens, the settings screen is organized into 5 clear visual groups with a segmented category switcher:
1. **Audio Engine**: Sample rate, Hi-Res passthrough (device-dependent), ReplayGain loudness calibration (Track/Album modes), equal-power crossfade, and 10-band ISO EQ.
2. **Interface & Themes**: Theme selector (Dark, Light, Mono), tape aesthetics, and haptic feedback.
3. **Hardware Integration**: Custom engraved nameplate, volume button skipping, and headset auto-play.
4. **Battery & Power**: Low-battery diode telemetry, screen timeout, and background throttling.
5. **System & About**: Build info, source code links, and direct PayPal development support.

### 9.5 App Drawer View Modes & Touch Alphabet Scroller
- **View Modes**: Segmented switcher for **Grid** (3-column responsive icons), **List** (compact vertical layout), and **Recent** (most recently launched applications).
- **Touch-Magnified Alphabet Rail**: The right-edge vertical indexer dynamically enlarges the selected letter upon touch, accompanied by a floating high-contrast center preview bubble and haptic ticks.

---

## 10. 💖 Support Development

Spindle is independently engineered with zero ads, zero trackers, and zero subscriptions. If Spindle enhances your listening experience or revives your classic DAP, consider supporting our hardware lab and ongoing development:

* **PayPal**: [https://paypal.me/manaphassan](https://paypal.me/manaphassan)
* **Author / Maintainer**: Manap Hassan (`manaphassan`)

---

<div align="center">
  <sub>Spindle · Engineered with precision for audiophiles, analog purists, and hardware preservation.</sub>
</div>



