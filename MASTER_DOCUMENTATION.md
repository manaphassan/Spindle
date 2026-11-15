# Spindle — Master Technical & Architectural Documentation
**Product Name:** Spindle (Audiophile DAP Launcher)  
**Package Name:** `com.hana.spindle`  
**Hero Hardware Inspiration:** Classic 1980s Japanese Portable Cassette Decks & Modern Minimalist Audiophile Gear  
**Version:** 1.7.0-STUDIO (current stable release, published 2026-11-16, `versionCode` 9)  
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
7. **Selectable Home Deck Styles**: Settings › Display › Home Deck Style switches the player, radio and drawer between the faceplate deck (Silver '81 or Pro Black colorway, default Silver '81) and the Classic vertical deck (§2.7).

---

## 2. Interface Architecture & Component Specifications

### 2.1 Flagship Cassette Deck (`VerticalDeckView.kt`)
This is the **Classic vertical deck** style, selectable under Settings › Display › Home Deck Style. The default home deck is the faceplate deck (§2.7); `VerticalDeckView` remains available alongside it.

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
   - Left-aligned tactile badges: `[FLAT / TYPE I–IV]` (tape formulation, cycled by tap; FLAT is the default), `[DOLBY B / C / OFF]`, and `[J-CARD]`.
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

### 2.3 Dedicated Single Audio Now Playing (`VaultFragment.kt`)
Triggered by tapping the floating mini-player in the Vault (music catalog):
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
4. **Missing-File Handling (`MainActivity.kt`, `MusicScanner.kt`, `data/StorageVolumes.kt`)**:
   - When playback fails with `ERROR_CODE_IO_FILE_NOT_FOUND`, `AudioEngine` emits a `MissingTrackEvent` and a single prompt offers to rescan the library.
   - Confirming runs `MusicScanner.pruneMissingTracks()` (removes entries whose files are gone; playlist links go with them via foreign-key cascade), then `scanAll()`, and unloads the deck if its track was removed.
   - If the file lives on a removable volume (`/storage/<id>`) that is not mounted, the prompt instead says the SD card is not inserted and keeps the tracks by default; they are removed only if the user picks **REMOVE FROM LIBRARY** (`pruneMissingTracks(includeUnmountedVolumes = true)`). A plain rescan never prunes tracks on an unmounted card.

### 2.5 Bauhaus Minimalist Radio with Hardware Antenna Detection (`RadioFragment.kt`)
This describes the radio page in the Classic deck style. With a faceplate style selected, the same page shows the faceplate radio deck (§2.8).

1. **Swipe-to-Tune**: Page 2 in the main launcher ViewPager.
2. **Dedicated FM vs DIGI Modes**:
   - **FM Mode**: Local over-the-air analog FM receiver requiring 3.5mm wired headphones connected as an antenna, with safety guidance if unplugged.
   - **DIGI Mode**: Dedicated DAB+ / online stream mode for internet radio stations with zero static.
3. **Streamlined Ballistic RF Signal Meter**: High-density compact galvanometer meter displaying signal strength (0–5 S-units) and center-channel tuning needle.
4. **Concentric Speaker Grille (`RadioSpeakerGrilleView.kt`)**: 7-ring concentric perforation pattern with acoustic recess shadows.
5. **3D Ribbed Tuning Dial (`RadioTuningDialView.kt`)**: Tactile cylindrical thumbwheel with moving calibrated frequency scale (`87.5 - 108.0 MHz`).
6. **Vintage LCD Display**: Mint-green backlit panel showing frequency, RDS station info, and connection status.

### 2.6 Studio Sound Deck & DSP Touch Lock (`DrawerFragment.kt`)
In faceplate styles these pages sit inside the System Deck drawer (§2.9).

1. **Lockable Studio Parametric EQ & DSP**: Dedicated tactile `[LOCK]` button securing the 10-band ISO graphic equalizer and parametric DSP knobs against accidental palm and pocket touches during playback.
2. **Dual Analog Ballistic VU Meters**: Dedicated left and right channel galvanometer needles with warm vintage dial face, +3dB redline scale, and glowing peak indicator LEDs. Needles are driven by measured output RMS levels (§3.8) and rest at zero when no local PCM is playing.
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

### 2.7 Home Deck Styles & Faceplate Deck (`ui/cassette/`)
1. **Deck Style Selector**:
   - Settings › Display › **Home Deck Style** cycles faceplate **Silver '81** → faceplate **Pro Black** → **Classic vertical deck** (pref key `pref_deck_style` in `spindle_prefs`; default Silver '81).
   - `PlayerFragment`, `RadioFragment` and `DrawerFragment` listen for the key and restyle live, so the player, radio and drawer always follow the same style.
   - Cassette shell models are offered only while the Classic deck is selected; the faceplate deck uses its two colorways.
2. **Faceplate Deck (Player)** — the ui/cassette/ faceplate deck painters and view, drawn on a fixed 360 × 592 dp design canvas:
   - Painted faceplate with a horizontal cassette behind a smoked door. The cassette label shows title, subtitle and the tape position line (`C-90 · 45 MIN PER SIDE · <POSITION>`).
   - Mechanical counter with reset, a REVERSE control reflecting repeat mode (`OFF` / `ALL` / `ONE`), and five keys: `REW`, `PLAY`, `FF`, `STOP`, `EJECT`.
   - **LED level meter**: 26-segment L/R peak meter on a dBFS scale (−48 to 0) with peak hold and clip indication, fed by `OutputLevelMeter.read()` (§3.8). It stays dark when no audible PCM is being metered.
   - **TAPE EQ** slide switch: `FLAT`, `I`, `II`, `III`, `IV` (`AudioFxController.TapeFormulation`). **DOLBY NR** slide switch: `OFF`, `B`, `C`.
   - **DIRECT lamp**: lit only when `AudioFxController.isTonallyFlat` is true — no EQ curve (user, tape or Dolby), no tape saturation, bass boost or crossfeed, and the software parametric EQ (§3.9) not active. DSP bypass also counts as flat, except for the software PEQ, which runs in the audio sink and ignores bypass.
   - **LCD** with a format line and a rate line.
   - **Accessibility**: an `ExploreByTouchHelper` exposes each key, switch, the counter, cassette and meter as virtual views with spoken descriptions and click / long-click actions.

### 2.8 Faceplate Radio Deck (`ui/radio/RadioDeckView.kt`)
1. **Components**:
   - `RadioDeckView`: drawing and touch host; holds no radio logic. `RadioFragment` pushes a `RadioDeckModel` and reacts to callbacks.
   - `RadioDeckPainter`: chassis, dial scale and recesses cached in a bitmap; needle, LCD, meters, switch knobs, lamps and keys painted per frame.
   - `RadioDeckModel`: plain data class (equality skips redundant redraws) plus `RadioDeckMath` mapping helpers, covered by the unit test `RadioDeckMathTest`.
   - `RadioDeckAccessibility`: exposes the Canvas-drawn controls to TalkBack and switch access.
2. **Controls**: slide-rule dial and LCD, SEEK buttons around a tuning wheel, **BAND** (`FM` / `NET`) and **MODE** (`AUTO` / `MONO`) slide switches with a STEREO lamp, four preset keys plus SCAN. Shares the faceplate deck colorways.
3. **FM Band**: SIG bar and centre-zero TUNE meter. Without a wired headset, FM stays selected and the LCD shows **NO ANT** (tuning controls stay inactive until headphones are plugged in) instead of silently falling back to NET as the Classic radio does.
4. **NET Band**:
   - **BUF** meter: stream audio buffered ahead divided by `RadioStreamEngine.MAX_BUFFER_MS` (8000 ms, also the stream player's maximum buffer), scaled 0–8 s.
   - **LINK** meter: network link quality — Wi-Fi signal strength mapped from −100 dBm (empty) to −50 dBm (full) on Android 10+, otherwise full or empty depending on internet capability.
   - The LCD shows `NO NET` when the device is offline.

### 2.9 System Deck Drawer & Metro Pivot Navigation (`DrawerFragment.kt`)
1. **System Deck (faceplate styles, `ui/SystemDeckHeaderView.kt`)**:
   - SPINDLE band with a **RUN** lamp that follows the playback state.
   - Draggable **FUNCTION** slide switch with `SOUND` / `SETTINGS` / `APPS` stops. The knob tracks the panorama scroll position and selecting a stop scrolls to that page; stops are exposed to accessibility services.
   - Settings render as an LCD menu (numbered rows with title and summary; a row inverts while pressed or focused).
   - A brushed chassis frame (drawn by the faceplate deck chassis drawable) surrounds the panorama.
2. **Classic Style**:
   - Metro pivot headers (`ui/ZunePivotHeader.kt`): the active pivot sits at the left edge and the others follow in order, wrapping around.
   - `SnappingPanoramaView`: a `HorizontalScrollView` with full-width panels that always comes to rest on a panel edge (a flick moves exactly one panel, a slow drag settles on the nearest). Shared by the Vault hub and collection panoramas and the drawer.
3. **Drawer Page Order**: sound, settings, apps (both styles).
4. **Settings Menu**: settings open as a menu of pages — display, playback, sleep timer, output, library, radio, gestures, about (§9.4).

### 2.10 Apps List Index & A–Z Jump Grid
1. **Letter Headers (`launcher/AppLetterHeaderDecoration.kt`, deck style)**: an LCD-style divider (`C ───`) above the first app of each initial. It is an item decoration, so adapter positions stay 1:1 with the app list and A–Z jumps stay exact.
2. **Alphabet Rail Dial Needle (`AlphabetIndexView.kt`, deck style)**: at rest a dial needle marks the letter of the first visible app and follows list scrolling.
3. **A–Z Jump Grid (`ZuneJumpListDialog.kt`)**: 4-column grid of `#`, `A`–`Z` and `↑` tiles; letters absent from the list are dimmed and not clickable. In deck style the tiles are drawn as LCD print on the recess colour.
   - The grid is opaque and does not dim the window behind it, so nothing can show through.
   - Only one instance is shown at a time; a grid restored after activity recreation closes itself.

### 2.11 Launcher Task & Page Behaviour (`MainActivity.kt`)
1. **Single Home Instance**: when Spindle is the default home app, a launch without the HOME category (from another app's list, a widget, `am start`) is forwarded to the home task with its extras, and the extra copy finishes itself, so only one `MainActivity` exists.
2. **Instant Page Jumps**: `jumpToPage()` calls `setCurrentItem(index, false)` and then posts `ViewPager2.requestTransform()`, re-running the depth page transformer so the target page is never left at its off-screen 0.96× scale and 75% alpha.

---

## 3. Audiophile Playback Architecture & Format Subsystem

### 3.1 Audio Subsystem Topology
```
               +-------------------------------------------------------+
               |             Spindle MediaItem / DataSource            |
               +---------------------------+---------------------------+
                                           |
             +-----------------------------+-----------------------------+
             |                                                           |
             v                                                           v
  +----------------------+                                    +----------------------+
  | Media3 Core Decoder  |                                    | Custom Extractor Hub |
  | (FLAC, MP3, WAV, AAC)|                                    | (SpindleExtractors)  |
  +----------+-----------+                                    +----------+-----------+
             |                                                           |
             |                    +---------------------+----------------+----+
             |                    |                     |                     |
             |                    v                     v                     v
             |          +-------------------+ +-------------------+ +-------------------+
             |          | AiffExtractor.kt  | | DsfExtractor.kt   | |WavPackExtractor.kt|
             |          | (IFF COMM/SSND)   | | (LUT FIR or DoP)  | | (pure-Kotlin .wv) |
             |          +---------+---------+ +---------+---------+ +---------+---------+
             |                    |                     |                     |
             +--------------------+---------------------+---------------------+
                                  |
                                  v
                   +------------------------------+
                   |  32-Bit Float PCM Processing |
                   |   (ENCODING_PCM_FLOAT = 4)   |
                   +--------------+---------------+
                                  |
                   +--------------+---------------+
                   | LevelTapAudioSink (metering) |
                   | -> ParametricEqProcessor     |
                   +--------------+---------------+
                                  |
                   +--------------+---------------+
                   | Studio Soft-Knee Peak Limiter|
                   |   (tanh above -0.5 dBFS)     |
                   +--------------+---------------+
                                  |
                   +--------------+---------------+
                   | Hardware ALSA / Direct Output|
                   | (Audiophile DAC / 3.5mm/USB) |
                   +------------------------------+
```

### 3.2 Bit-Perfect 32-Bit Float PCM Pipeline (`ENCODING_PCM_FLOAT`)
1. **Dynamic Headroom & Floating-Point Pipeline**:
   - Modern audiophile DSP processing (equalization, loudness normalization, and crossfading) in conventional 16-bit or 24-bit fixed-point pipelines introduces integer rounding errors and harsh clipping distortion whenever cumulative band boosts exceed $0\text{ dBFS}$.
   - Spindle's `AudioEngine.kt` configures the underlying Android `DefaultAudioSink` and `AudioTrack` with `AudioFormat.ENCODING_PCM_FLOAT` (integer value `4`), backed by `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER`.
   - Incoming audio streams are decoded directly into 32-bit single-precision IEEE 754 floating-point PCM ($[-1.0, 1.0]$ nominal range with $+140\text{ dB}$ of headroom before numeric overflow), preserving sub-LSB micro-detail and eliminating inter-stage quantization noise.

### 3.3 Studio Soft-Knee Peak Limiter (Hyperbolic Tangent DSP)
1. **Saturation Geometry**:
   - When extreme user EQ boosts, pre-amp calibration, or analog tape saturation push peak sample amplitudes above nominal full scale ($|x| > 1.0$), Spindle applies a studio-grade hyperbolic tangent ($\tanh$) soft-knee compression curve defined in `AudioDspConstants.kt`:
     $$\text{Threshold } T = 10^{-0.5 / 20} \approx 0.94406087\text{ (-0.5 dBFS)}$$
   - Samples below $T$ pass through unmodified with absolute bit-perfect transparency:
     $$y(x) = x \quad \text{for } |x| \le T$$
   - Samples exceeding $T$ are smoothly mapped toward the asymptote of $1.0\text{ dBFS}$ without sharp harmonic clipping edges:
     $$y(x) = \text{sgn}(x) \cdot \left[ T + (1.0 - T) \cdot \tanh\left(\frac{|x| - T}{1.0 - T}\right) \right] \quad \text{for } |x| > T$$
   - This models analog studio mastering console behavior, transforming potential digital clipping into smooth, pleasant acoustic saturation.

### 3.4 Native AIFF / AIFC Container Demuxer (`AiffExtractor.kt`)
1. **Big-Endian Interchange File Format (IFF)**:
   - Dedicated zero-dependency Media3 `Extractor` implementation for Apple Audio Interchange File Format (`.aiff`, `.aif`, `.aifc`).
   - Demuxes standard chunk hierarchies: `FORM` container, `COMM` (Common Chunk), and `SSND` (Sound Data Chunk).
2. **80-Bit Extended Precision Float Decoding (`readIeeeExtendedFloat`)**:
   - AIFF standard encodes sample rates as 10-byte (80-bit) IEEE 754 extended precision floating point numbers.
   - Decodes 15-bit exponent ($E - 16383$) and 64-bit explicit significand mantissa.
   - Implements unsigned 64-bit integer conversion via `toULong().toDouble()` to prevent negative signed long overflow when bit 63 (normalized binary point) is set, guaranteeing exact sample rate resolution for $44.1\text{ kHz}$, $48\text{ kHz}$, $88.2\text{ kHz}$, $96\text{ kHz}$, $176.4\text{ kHz}$, $192\text{ kHz}$, and $384\text{ kHz}$.
3. **Metadata Extraction (`TagParser.parseAiffStreamInfo`)**:
   - Extracts sample rate, bit depth (8, 16, 24, 32-bit), channel count, and exact duration in milliseconds in $<0.1\text{ ms}$ without allocating audio buffers.

### 3.5 Native DSD / DSF 1-Bit Bitstream & 32-Tap LUT FIR Decimator (`DsfExtractor.kt`)
1. **Direct Stream Digital (DSD64) Architecture**:
   - Sony DSD Stream File (`.dsf`) and DSD Interchange File Format (`.dff`) audio stores 1-bit Delta-Sigma modulated bitstreams at $2.8224\text{ MHz}$ ($64 \times 44.1\text{ kHz}$).
   - Android standard `AudioTrack` lacks direct 1-bit native DSD HAL support on non-proprietary USB DAC paths.
2. **Precalculated 32-Tap Hann-Windowed LUT FIR Decimation Engine**:
   - Decimates high-frequency 1-bit bitstream down by factor $M = 32$ to studio-quality 24-bit $88.2\text{ kHz}$ linear PCM (`C.ENCODING_PCM_24BIT`), preserving high-frequency air up to $44.1\text{ kHz}$ while heavily suppressing delta-sigma ultrasonic quantization noise above $50\text{ kHz}$.
   - To eliminate heavy floating-point convolution loops on low-power DAP processors, the 32 FIR impulse response coefficients are grouped into 4 bytes ($b_0, b_1, b_2, b_3$) and pre-calculated into four 256-entry float look-up tables (`LUT_STAGE_0..3`):
     $$\text{Total Cache Footprint} = 4 \times 256 \times 4\text{ bytes} = 4\text{ KB}$$
   - Computing a 24-bit PCM output sample requires only 4 array lookups and 3 additions:
     $$S_{\text{norm}} = \text{LUT}_0[b_0] + \text{LUT}_1[b_1] + \text{LUT}_2[b_2] + \text{LUT}_3[b_3]$$
     $$S_{24} = \text{round}(S_{\text{norm}} \cdot 8388607.0)$$
   - Verified CPU overhead is $<1\%$ on quad-core ARM Cortex-A53 processors.
3. **Fast Stream Header Parsing (`TagParser.parseDsfStreamInfo`)**:
   - Parses Sony DSF `'DSD '`, `'fmt '`, and `'data'` chunks, resolving sample rates ($2.8224\text{ MHz}$ / $5.6448\text{ MHz}$), channels (1 to 8), and bitstream duration.
4. **Optional DoP Output**: when DSD over PCM is enabled and a verified bit-perfect mixer path is negotiated, `DsfExtractor` emits DoP frames instead of decimated PCM (§3.10).

### 3.6 Parametric Equalizer (PEQ) Architecture & Reference AutoEQ Workshop
1. **Dynamic PEQ Center Frequencies (`centerFreqsHz`)**:
   - Upgraded `UserEqPreset.kt`, `BiquadFilterCalculator.kt`, and `AudioFxController.kt` from rigid 10-band ISO frequencies (31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz) to dynamic arbitrary center frequencies per preset:
     $$\mathbf{f_c} = [f_0, f_1, f_2, \dots, f_9] \quad \text{where } f_i \in [20, 20000]\text{ Hz}$$
2. **Reference AutoEQ Headphone Profile Matrix**:
   Pre-loaded factory reference compensation curves targeting the Harman Over-Ear / In-Ear targets and diffuse field benchmarks:
   - **Sennheiser HD 600**: Audiophile neutral reference benchmark with subtle sub-bass extension (+4.5dB @ 30Hz) and smooth upper midrange.
   - **Sony WH-1000XM4 / XM5**: Consumer ANC bass boom tamer (-3.5dB @ 160Hz mud frequency, +2.5dB @ 1.2kHz vocal presence, +3.0dB @ 6.5kHz sparkle).
   - **Audio-Technica ATH-M50x**: Studio tracking monitor refinement (-2.0dB @ 200Hz boxy resonance, +1.5dB @ 3.5kHz clarity, -3.0dB @ 9kHz harshness notch).
   - **HiFiMAN Sundara**: Planar magnetic bass extension (+4.0dB sub-bass shelf @ 35Hz, linear mids, silky smooth planar highs).
   - **Beyerdynamic DT 770 Pro / DT 990 Pro**: Mount Beyer treble sibilance tamer (-5.0dB sharp notch @ 6kHz, -4.0dB @ 8.5kHz, +3.5dB sub-bass body).
   - **Sony IER-M9**: Stage in-ear monitor reference target with pristine vocal clarity and linear timbre.
   - **Moondrop Blessing 2**: VDSF target curve matching with refined sub-bass rumble (+3.5dB @ 25Hz) and smooth pinna gain.
3. **Deck Quick-Access Switcher**:
   - Horizontal scrolling preset pills in `DrawerFragment.kt` and `PlayerFragment.kt` allowing instant, single-tap switching across reference targets during live listening.

### 3.7 Auto-Play Progression & State Persistence
1. **Auto-Play Progression**:
   - In standard mode (`repeatMode = REPEAT_MODE_OFF`), finishing a track triggers automatic playback of the next sequential song in the catalog/queue.
   - Repeat modes: `REPEAT_MODE_OFF` (sequential progression), `REPEAT_MODE_ALL` (loop entire playlist), `REPEAT_MODE_ONE` (loop current track).
2. **State Persistence**:
   - Track ID, URI, position in milliseconds, and catalog shuffle state are written to `playback_state` SharedPreferences.
   - On launcher launch, the last played track is automatically restored and populated into the cassette deck and mini-player.

### 3.8 Real Output Metering (`playback/PcmLevelTap.kt`)
1. **Level Tap**: `LevelTapAudioSink` wraps the Media3 `DefaultAudioSink` (a `ForwardingAudioSink`) and measures decoded PCM where it enters the sink, before Android session effects. Supported encodings: 8/16/24/32-bit integer and float PCM.
2. **Timestamped Blocks**: PCM is split into 512-frame blocks, each storing L/R peak and RMS and stamped with its presentation time, in a 512-entry ring buffer.
3. **Audible-Only Reads**: `OutputLevelMeter.read(windowMs, out)` returns `[peakL, peakR, rmsL, rmsR]` from blocks whose timestamp has already become audible, so meters follow what is heard rather than the decoder, which runs ahead by the AudioTrack buffer length. It returns `false` (idle meter) when no PCM stream is being metered, e.g. paused, stopped, passthrough or offload.
4. **DoP Rest**: while DoP is active (§3.10) the samples are not audio, so the tap stops measuring and the meters rest.
5. **Replaces the Simulated VU**: the previous sine-wave meter simulation was removed; the faceplate LED meter, the player's VU meter and the drawer's dual analog VU all read this meter.

### 3.9 Software Parametric EQ (`playback/ParametricEq.kt`)
1. **Processor**: `ParametricEqProcessor` is a Media3 `AudioProcessor` installed in the audio sink (one instance per sink). Cascaded RBJ-cookbook biquads with double-precision state, up to 16 bands of type `PK`, `LSC`, `HSC`, `HPQ`, `LPQ`, plus a preamp.
2. **Zero Cost When Flat**: the processor only joins the chain when the settings are effective at configure time (16-bit or float PCM). Band edits apply live; switching it on mid-track applies from the next track. Filter state is cleared on flush (seek / track change).
3. **AutoEq / Equalizer APO Import**: `AutoEqParametricParser` reads `ParametricEQ.txt` lines (`Preamp:` and `Filter n: ON PK Fc … Gain … Q …`); `toAutoEqText()` writes the same format for copy / edit / re-import.
4. **Editor & Persistence**: `ui/DialogParametricEq.kt` is the text-based editor, opened from the drawer's playback settings. Settings persist as JSON under `pref_peq_settings_json`.
5. **Independent of DSP Bypass**: the software PEQ ignores the effect bypass, so it counts against the DIRECT lamp (§2.7).

### 3.10 Bit-Perfect Mixer Mode (Android 14+) & DSD over PCM (DoP)
1. **`BitPerfectMixerController.kt`**: opt-in (`pref_bitperfect_mixer`). Requests Android 14+ bit-perfect mixer behaviour (`AudioMixerAttributes`) for a wired / USB output.
   - `BitPerfectMixerPolicy.select()` requires an exact sample-rate match; a 16-bit source needs a 16-bit format, deeper sources prefer 24-bit, then 32-bit, never fewer bits.
   - Status is one of `OFF`, `UNSUPPORTED_OS`, `NO_DEVICE`, `NO_MATCH`, `REJECTED`, `ACTIVE`. It is `ACTIVE` only when the platform accepts the request and reports the preferred attributes back; it is never assumed.
2. **DoP for DSF (`extractor/DsdOutput.kt`)**: opt-in (`pref_dsd_dop`) and only honoured together with the bit-perfect mixer mode.
   - `DopPacker` implements DoP 1.1: each 24-bit word carries 16 DSD bits of one channel plus an alternating `0x05` / `0xFA` marker byte; DSD64 maps to 176.4 kHz.
   - `DsdOutput.tryEnableDop()` succeeds only when the mixer confirms the exact DoP rate at 24-bit (up to 705.6 kHz); otherwise `DsfExtractor` falls back to the 32-tap PCM decimator, so a failed negotiation never emits DoP words.
   - While DoP is active the player volume is held at unity (no ReplayGain or limiter gain) and the level meter rests.

### 3.11 WavPack (`.wv`) Lossless Decoder (`extractor/WavPackDecoder.kt`, `WavPackExtractor.kt`)
1. **Pure-Kotlin Decoder**: lossless integer WavPack 4.x, mono and stereo, 16 and 24-bit. Hybrid/lossy, float, 32-bit integer, DSD and multichannel streams are rejected with a clear unsupported-feature error instead of playing noise.
2. **Media3 Extractor**: registered in `SpindleExtractorsFactory`; emits raw little-endian PCM through the normal audio sink (and the bit-perfect mixer path when enabled). On open it walks block headers once to build a block-accurate seek table.
3. **Library Metadata**: `data/WavPackFileInfo.kt` reads stream facts and APEv2 tags, since `MediaMetadataRetriever` cannot parse WavPack.

### 3.12 Signal Path View & FLAT Reference Tape
1. **Signal Path (`playback/SignalPath.kt`)**: `SignalPathBuilder` describes the live chain as source → decode → DSP → output and is shown in the file specs dialog (`DialogFileSpecs.kt`). It lists only stages actually in the path (system equalizer, parametric EQ band count, tape saturation, crossfeed, ReplayGain) and reports bit-perfect output only when `BitPerfectMixerController` has confirmed it.
2. **FLAT Reference Tape**: `TapeFormulation.FLAT` applies no tape curve and is the default formulation for `pref_tape_formulation`.

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
├── .github/workflows/                       # ci.yml (build-test, startup-smoke) & tag-driven release.yml
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
│       ├── CrossfadeMode.kt                 # GAPLESS (0s), CROSSFADE_2S, CROSSFADE_4S transitions
│       ├── AudioDspConstants.kt             # EQ / tape / Dolby curves, tanh soft-knee limiter & saturation math
│       └── SpindleGlossary.kt               # Canonical shared constants
├── app-main/                                # Flagship modern Spindle DAP Launcher (:app-main)
│   ├── build.gradle.kts                     # App dependencies (Media3, Room, Palette, R8)
│   ├── proguard-rules.pro                   # R8 stripping rules for <4.5MB APK
│   ├── lint-baseline.xml                    # Frozen existing lint findings (only new errors fail CI)
│   └── src/
│       ├── androidTest/java/com/hana/spindle/
│       │   └── StartupSmokeTest.kt          # Emulator boot test: SpindleApp.audioEngine init & MainActivity launch
│       ├── test/java/com/hana/spindle/      # JVM unit tests (e.g. RadioDeckMathTest, ParametricEqTest, WavPackDecoderTest, StorageVolumesTest)
│       └── main/
│           ├── AndroidManifest.xml          # Launcher intent, permissions, foreground service
│           ├── java/com/hana/spindle/
│           │   ├── SpindleApp.kt            # Application entrypoint & dependency container
│           │   ├── data/                    # Storage, Database, Parsers & M3U
│           │   │   ├── MusicScanner.kt      # High-speed SD card scanner & pruneMissingTracks()
│           │   │   ├── StorageVolumes.kt    # Tells a missing file apart from an SD card that is not inserted
│           │   │   ├── TagParser.kt         # Lightweight ID3/FLAC metadata & ReplayGain extractor
│           │   │   ├── WavPackFileInfo.kt   # WavPack stream facts & APEv2 tags
│           │   │   ├── M3uManager.kt        # M3U/M3U8 playlist import, export & track matching
│           │   │   ├── LyricsParser.kt      # .lrc and embedded lyrics parser
│           │   │   ├── ImageLoader.kt       # RGB_565 bounded thumbnail cache & album-art accent colour
│           │   │   └── db/
│           │   │       ├── SpindleDatabase.kt # Room Database with Playlist & Track entities
│           │   │       ├── SongDao.kt       # Query operations for songs & albums
│           │   │       ├── PlaylistDao.kt   # Playlist CRUD and drag-to-reorder persistence
│           │   │       ├── SongEntity.kt    # Audio entity with format & bitrate fields
│           │   │       └── AlbumItem.kt     # Album model
│           │   ├── playback/                # Audio Playback Subsystem
│           │   │   ├── PlaybackService.kt   # Foreground MediaSession Service
│           │   │   ├── AudioEngine.kt       # Media3 wrapper with 32-bit Float PCM, soft-knee limiter & custom extractors
│           │   │   ├── AudioFxController.kt # Equalizer, Bass Boost, Virtualizer, PEQ frequencies & AutoEQ
│           │   │   ├── BiquadFilterCalculator.kt # Arbitrary-frequency parametric biquad filter calculator
│           │   │   ├── UserEqPreset.kt      # Parametric & graphic EQ preset model with centerFreqsHz
│           │   │   ├── UserEqPresetManager.kt # Reference AutoEQ headphone profiles & Room DB preset store
│           │   │   ├── CassetteFoleyEngine.kt # Low-latency mechanical clicks, spool whirrs & eject thuds
│           │   │   ├── RadioStreamEngine.kt # Low-latency online shoutcast stream player
│           │   │   ├── AudioMetricsTracker.kt # Real-time sample rate & bit depth analyzer
│           │   │   ├── PcmLevelTap.kt       # LevelTapAudioSink & OutputLevelMeter (real output metering)
│           │   │   ├── ParametricEq.kt      # Software PEQ, ParametricEqProcessor & AutoEq / Equalizer APO parser
│           │   │   ├── BitPerfectMixerController.kt # Android 14+ bit-perfect mixer negotiation
│           │   │   ├── SignalPath.kt        # Source -> decode -> DSP -> output description
│           │   │   └── extractor/           # Native Audio Extractor Subsystem
│           │   │       ├── SpindleExtractorsFactory.kt # Unified Media3 extractors factory
│           │   │       ├── AiffExtractor.kt # Native AIFF/AIFC demuxer with 80-bit float parsing
│           │   │       ├── DsfExtractor.kt  # Native DSD/DSF 1-bit bitstream & 32-tap LUT FIR decimator
│           │   │       ├── DsdOutput.kt     # DoP policy & DopPacker
│           │   │       ├── WavPackDecoder.kt # Pure-Kotlin lossless WavPack decoder
│           │   │       └── WavPackExtractor.kt # Media3 extractor for .wv
│           │   ├── launcher/                # Launcher Subsystem
│           │   │   ├── AppListLoader.kt     # Installed apps fetcher (<2MB overhead)
│           │   │   ├── AppLetterHeaderDecoration.kt # LCD-style letter headers in the apps list
│           │   │   └── AppInfo.kt           # App list item model
│           │   ├── theme/                   # Parametric Theme Subsystem
│           │   │   ├── CassetteTheme.kt     # Palettes for Dark Obsidian, E-Ink, and Light
│           │   │   └── ThemeManager.kt      # Theme selector & preferences store
│           │   └── ui/
│           │       ├── MainActivity.kt      # 3-Page ViewPager2 Launcher Controller, single home instance
│           │       ├── PlayerFragment.kt    # Home Screen Cassette Deck Controller (faceplate or Classic deck)
│           │       ├── VaultFragment.kt     # Vault: Music Catalog, Mixtape Reorder & Single Audio Now Playing Overlay
│           │       ├── DrawerFragment.kt    # App Drawer, 10-Band EQ, Hardware Telemetry & Audio Settings
│           │       ├── SystemDeckHeaderView.kt # System Deck SPINDLE band, RUN lamp & FUNCTION switch
│           │       ├── SnappingPanoramaView.kt # Full-width snapping panels (Vault & drawer)
│           │       ├── ZunePivotHeader.kt   # Metro pivot header arrangement
│           │       ├── ZuneJumpListDialog.kt # A-Z jump grid
│           │       ├── AlphabetIndexView.kt # Tactile vertical A-Z alphabet scroller with deck-style dial needle
│           │       ├── LyricsAdapter.kt     # Real-time synced lyrics line adapter
│           │       ├── DialogFileSpecs.kt   # Audiophile technical file specifications inspector & signal path
│           │       ├── DialogParametricEq.kt # Text-based software PEQ editor
│           │       ├── cassette/            # Kinetic Cassette Deck Views
│           │       │   ├── VerticalDeckView.kt  # Classic deck: centered spindle deck with column telemetry
│           │       │   ├── SpindleKinematics.kt # Reel angular velocity kinematics
│           │       │   ├── CassetteView.kt  # Custom Canvas tape spool rendering
│           │       │   ├── JCardLinerView.kt # 3D J-Card liner notes
│           │       │   ├── Metal81ChassisView.kt # Metal-81 chassis view
│           │       │   └── (6 files)        # Faceplate deck painters, chassis drawable, colorways, accessibility helper and view
│           │       ├── catalog/             # Catalog Custom Views & Adapters
│           │       │   ├── CircularCoverArcView.kt # Circular cover with radial progress arc
│           │       │   ├── AudioWaveformView.kt    # Dynamic 24-band frequency visualizer
│           │       │   ├── MixtapeDialogs.kt       # Cut New Tape, Import M3U & Export M3U8 dialogs
│           │       │   ├── CatalogSortGroup.kt     # Sort & grouping enum definitions
│           │       │   ├── SortGroupBottomSheet.kt # Sort & filter modal dialog
│           │       │   ├── SongAdapter.kt          # Song list RecyclerView adapter
│           │       │   └── AlbumAdapter.kt         # 2-column album grid adapter
│           │       └── radio/               # Acoustic Radio Custom Views
│           │           ├── RadioFragment.kt        # Radio controller (Classic radio or faceplate radio deck)
│           │           ├── RadioDeckView.kt        # Faceplate radio deck view (no radio logic)
│           │           ├── RadioDeckPainter.kt     # Faceplate radio deck drawing & geometry
│           │           ├── RadioDeckModel.kt       # Deck model & RadioDeckMath helpers
│           │           ├── RadioDeckAccessibility.kt # TalkBack / switch access for the radio deck
│           │           ├── RadioSpeakerGrilleView.kt # Concentric acoustic hole grille
│           │           └── RadioTuningDialView.kt  # 3D ribbed tuning cylinder
│           └── res/                         # Hardware vector graphics, layouts, and styles
└── app-lite/                                # Ultra-lightweight vintage/low-RAM variant (:app-lite)
    ├── build.gradle.kts                     # Android 4.4+ target, zero-dependency MediaPlayer engine
    └── src/main/                            # Lightweight HVGA Canvas UI & hardware button matrix
```

### 6.1 Continuous Integration & Tests (`.github/workflows/ci.yml`)
CI runs on pushes to `main` and on pull requests (Markdown and `docs/` changes are ignored).
1. **`build-test` job**:
   - Unit tests: `:app-main:testDebugUnitTest`, `:app-lite:testDebugUnitTest`, `:core:test`.
   - Lint: `:app-main:lintDebug` against `app-main/lint-baseline.xml` (existing findings frozen; only new errors fail).
   - Debug assembles: `:app-main:assembleDebug`, `:app-lite:assembleDebug`.
   - Fails if key material (`.jks`, `.keystore`, `.pem`, `.p12`, signing key files, `keystore.properties`) is tracked.
2. **`startup-smoke` job**: runs `:app-main:connectedDebugAndroidTest` on an API 34 emulator. `app-main/src/androidTest/java/com/hana/spindle/StartupSmokeTest.kt` initialises `SpindleApp.audioEngine` on the main thread and launches `MainActivity` to `RESUMED`, catching startup wiring bugs that JVM unit tests cannot see (such as the v1.7.0 `AudioMetricsTracker` recursion, §8.7).

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
                                  
   [ v1.4.0-STUDIO ] ──> [ v1.5.0-STUDIO ] ──> [ v1.6.0-STUDIO ] ──> [ v1.7.0-STUDIO ] ──> [ v2.0.0-ECOSYSTEM ]
     Studio Pass           Formulation Decks     Synced Lyrics         Bit-Perfect Float      Direct USB ALSA
     10-Band PEQ           Biquad Scope          AutoEq Presets        Studio Peak Limiter    MicroSD Sync
     Reel-to-Reel          SAF SD Importer       Album Art Studio      Native AIFF Demuxer    CUE Sheet Splitter
     Tape Saturation       Tactile Knobs         Offline LRCLIB        32-Tap DSD Decimator   DLNA Renderer
     Procedural Foley      Type IV Foil          Queue Drawer          Headphone AutoEQ       Cross-Platform
```

**Current stable release:** v1.7.0-STUDIO, published 2026-11-16 (§8.7).

### 8.1 v1.2.0-RELEASE (Flagship Base Stable)
* **Flagship Cassette Deck**: Centered acrylic spindle window with kinetic differential spool physics ($v = 4.7625\text{ cm/s}$).
* **Dedicated Single-Audio Now Playing**: 360° touch radial progress arc scrubber, dynamic 24-band frequency envelope visualizer, format telemetry pill, and synced `.lrc` lyrics drawer.
* **Music Catalog**: Fast A-Z right-edge index rail with haptic feedback, 6-attribute sorting, and format filter chips.
* **Bauhaus Minimalist Radio**: Concentric acoustic speaker grille and 3D cylindrical tuning dial.
* **Dynamic Hardware Nameplate Engine**: Auto-detection and engraving of host device model.

### 8.2 v1.3.0-alpha (Flagship Alpha)
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
* **Studio Collector Licensing & Monetization (Completed)**: Dual verification via Google Play In-App Billing and offline sponsor tokens signed with ECDSA P-256 (`SPINDLE-STUDIO-<ID>-<signature>`, issued with `tools/StudioTokenTool.java`; the app embeds only the public key) verified in `StudioUnlockManager.kt`, enabling 100% offline functionality on de-googled audiophile DAPs.
* **Custom Laser Nameplate Engraving (Completed)**: Personalized physical deck callsigns or hardware serial numbers persistently engraved on the player faceplate, gated under `StudioFeature.LASER_NAMEPLATE_ENGRAVING`.

### 8.5 v1.5.0-STUDIO (Cassette Formulation Skins & Biquad Scope)
* **Dynamic Formulation Decks**: Type IV Metal Master (metallic foil stamping), Type II High-Bias Chrome (steely blue shell), and Type I Normal Studio (warm classic brown) skins dynamically assigned or user-customized.
* **Interactive Biquad Frequency Scope (`Iso10BandEqView.kt`)**: Real-time 50-point logarithmic frequency response curve rendering parametric bell filters and total compound gain transfer function.
* **SAF MicroSD Storage Access Framework Importer**: Fast mass-scanning for external removable MicroSD cards on modern Android 11+ scoped storage devices.

### 8.6 v1.6.0-STUDIO (Synchronized Lyrics Engine, User EQ Workshop & Artwork Studio)
* **LRCLIB Online Synced Lyrics Client**: Lightweight zero-dependency HTTP client querying synchronized `.lrc` and plain-text lyrics from `lrclib.net` with offline local file caching.
* **Embedded Binary Tag Extractor**: ID3v2 `USLT` / `SYLT` (MP3) and Vorbis `LYRICS` / `UNSYNCEDLYRICS` (FLAC/OGG) parsing without network roundtrips.
* **Persistent User EQ Preset Room DB**: Save, export, and recall unlimited custom parametric and graphic equalizer curves with instant JSON import/export.
* **Artwork Studio & Folder Export**: Direct 512×512 square artwork export to music album folders as `cover.jpg` / `folder.jpg` for universal offline interoperability.

### 8.7 v1.7.0-STUDIO (Current Stable Release — Audiophile Engine & Extended Format Expansion)
* **Release Status**: Stable, published 2026-11-16 as tag `v1.7.0-STUDIO` (`versionCode` 9, `versionName` `1.7.0-STUDIO`). Signed with the same release key as v1.6.0, so it updates existing installs in place (per `RELEASE_NOTES_v1.7.0-STUDIO.md`).
* **Bit-Perfect 32-Bit Float PCM Pipeline (`ENCODING_PCM_FLOAT`)**:
  - Direct 32-bit single-precision floating-point output routing to Android HAL / `AudioTrack`.
  - $+140\text{ dB}$ internal processing dynamic headroom preventing digital clipping and quantization degradation during aggressive tone sculpting.
* **Studio Soft-Knee Peak Limiter (Hyperbolic Tangent DSP)**:
  - Non-linear $\tanh$ saturation curve acting above $-0.5\text{ dBFS}$ threshold ($T \approx 0.94406$).
  - Smooth analog tape compression eliminating harsh digital hard clipping while preserving natural musical transients.
* **Native Apple AIFF / AIFC Container Extractor (`AiffExtractor.kt`)**:
  - Full IFF `FORM`, `COMM`, and `SSND` demuxing within Media3.
  - 80-bit IEEE 754 extended precision float parser with unsigned 64-bit mantissa arithmetic, resolving sample rates up to $384\text{ kHz}$.
* **Native DSD / DSF 1-Bit Bitstream & 32-Tap LUT FIR Decimator (`DsfExtractor.kt`)**:
  - Direct playback of Sony `.dsf` and `.dff` 1-bit Delta-Sigma audio ($2.8224\text{ MHz}$ DSD64).
  - 32-tap Hann-windowed FIR decimation to 24-bit $88.2\text{ kHz}$ linear PCM.
  - Four 256-float look-up tables (`LUT_STAGE_0..3`, 4 KB cache) delivering $<1\%$ CPU load on quad-core Cortex-A53 processors.
* **Parametric Equalizer (PEQ) & Reference AutoEQ Headphone Profile Matrix**:
  - Arbitrary center frequency arrays (`centerFreqsHz`) for precision biquad filter targeting.
  - Factory reference profiles for Sennheiser HD 600, Sony WH-1000XM4/XM5, Audio-Technica ATH-M50x, HiFiMAN Sundara, Beyerdynamic DT 770/990, Sony IER-M9, and Moondrop Blessing 2.
  - Interactive AutoEQ horizontal pills in Cassette Deck and Equalizer drawers.
* **Home Deck Styles (§2.7)**: faceplate deck (Silver '81, Pro Black) as the default home deck, with the Classic vertical deck selectable under Settings › Display › Home Deck Style; player, radio and drawer follow the same style.
* **Faceplate Radio Deck (§2.8)**: FM and NET bands; `NO ANT` instead of a silent NET fallback without wired headphones; BUF and LINK meters on NET.
* **System Deck Drawer & Metro Pivots (§2.9)**: SPINDLE band, RUN lamp, FUNCTION switch and LCD settings menu in faceplate styles; Metro pivot headers and full-width snapping panels in the Classic style; settings split into a menu of pages.
* **Apps List Index (§2.10)**: letter headers, dial-needle alphabet rail and LCD-tile A–Z jump grid in deck style.
* **Real Output Metering (§3.8)**: meters read measured PCM timed to what is audible, replacing the simulated sine-wave VU.
* **Software Parametric EQ (§3.9)** with AutoEq / Equalizer APO text import.
* **Bit-Perfect Mixer Mode (Android 14+) & DoP for DSF (§3.10)**, reported active only when the platform confirms it.
* **WavPack (`.wv`) Lossless Playback (§3.11)** with a pure-Kotlin decoder.
* **Signal Path View & FLAT Reference Tape (§3.12)**: FLAT is the new default tape formulation.
* **Library Robustness (§2.4)**: rescan prompt on file-not-found, `MusicScanner.pruneMissingTracks()`, and tracks on an SD card that is not inserted kept unless explicitly removed.
* **Launcher Fixes (§2.11)**: one `MainActivity` when Spindle is the default home app; instant page jumps re-run the depth transformer.
* **Startup Crash Fix**: `AudioMetricsTracker`'s initial route-telemetry update read `SpindleApp.audioEngine` while that lazy was still being constructed, recursing until `StackOverflowError` on every launch. The first update is now deferred, and the CI `startup-smoke` job (§6.1) guards against regressions.

### 8.8 v2.0.0-ECOSYSTEM (Future Vision)
* **Direct USB-OTG ALSA Driver**: Custom native user-space USB Audio Class 2.0 driver bypassing Android audio framework for bit-perfect DSD512 / 32-bit 768kHz output.
* **Audiophile CUE Sheet Splitter**: Real-time virtual track indexing for monolithic FLAC/APE album rips.
* **Cross-DAP MicroSD Catalog Sync**: Fast metadata transfer and playlist sharing between devices.
* **Hardware Volumio / DLNA Renderer**: Remote streaming endpoint control for home Hi-Fi stacks.

### 8.9 Commercial Distribution & Monetization Architecture

Spindle adopts a **"Fair Ownership & Anti-Subscription"** monetization architecture tailored specifically for the audiophile and retro-tech communities:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   SPINDLE COMMERCIAL & DISTRIBUTION MATRIX                  │
├──────────────────────────┬─────────────────────────┬────────────────────────┤
│     SPINDLE LITE         │    SPINDLE STANDARD     │    STUDIO COLLECTOR    │
│      (v1.0.0-LITE)       │     (v1.7.0-STUDIO)     │   (One-Time Unlock)    │
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
4. **Community Sponsorship Channels & Offline Sponsor Tokens**: Sideloaders and direct APK users are provided a voluntary sponsorship pathway via direct PayPal contribution ([paypal.me/manaphassan](https://paypal.me/manaphassan)) and GitHub Sponsors to fund physical test device acquisitions. De-googled DAPs (Fiio, Hiby, Cayin, Astell&Kern running AOSP without GMS) can activate the full Studio Collector suite using offline sponsor tokens signed with ECDSA P-256 (`SPINDLE-STUDIO-<ID>-<signature>`) verified in `StudioUnlockManager.kt`.
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
To guarantee effortless navigation on compact 3.5"–5.0" screens, settings in the drawer open as a menu of short pages (`DrawerFragment.settingsPages()`); in faceplate styles the menu is drawn as numbered LCD rows (§2.9):
1. **display**: deck style, theme, status bar.
2. **playback**: buttons, DAC, bit-perfect, PEQ, ReplayGain, crossfade.
3. **sleep timer**: fade out and stop.
4. **output**: source, headphone jack, Bluetooth.
5. **library**: music folders, rescan, art and lyrics.
6. **radio**: internet stations.
7. **gestures**: double tap and ambient mode.
8. **about**: default launcher, studio, version.

### 9.5 App Drawer View Modes & Touch Alphabet Scroller
- **View Modes**: Segmented switcher for **Grid** (3-column responsive icons), **List** (compact vertical layout), and **Recent** (most recently launched applications).
- **Touch-Magnified Alphabet Rail**: The right-edge vertical indexer dynamically enlarges the selected letter upon touch, accompanied by a floating high-contrast center preview bubble and haptic ticks.
- **Deck-Style Index**: in faceplate styles the rail shows a dial needle that tracks scrolling, the list gets LCD letter headers, and the A–Z jump grid uses LCD tiles (§2.10).

---

## 10. 💖 Support Development

Spindle is independently engineered with zero ads, zero trackers, and zero subscriptions. If Spindle enhances your listening experience or revives your classic DAP, consider supporting our hardware lab and ongoing development:

* **PayPal**: [https://paypal.me/manaphassan](https://paypal.me/manaphassan)
* **Author / Maintainer**: Manap Hassan (`manaphassan`)

---

<div align="center">
  <sub>Spindle · Engineered with precision for audiophiles, analog purists, and hardware preservation.</sub>
</div>



