# Spindle Lite — Master Technical & Architectural Documentation
**Product Name:** Spindle Lite (Legacy & Vintage DAP Edition)  
**Package Name:** `com.hana.spindle.lite`  
**Target Codename:** `satsuma` (Ultra-Compact 3.0" HVGA Hardware & Legacy DAPs)  
**Version:** 1.0.0-LITE-RELEASE  
**Platform Target:** Android 4.4 KitKat (API 19) down to Android 4.1 Jelly Bean (API 16)  
**Hardware Baseline:** Single-Core ARMv7 SoC (1.0 GHz), 512 MB RAM, 3.0" 320×480 HVGA Display  
**License:** Apache License 2.0  

---

## 1. Executive Summary & Hardware Target

**Spindle Lite** is an ultra-lightweight, zero-bloat edition of Spindle engineered to turn vintage, ultra-compact Android devices from the 2011–2014 era into dedicated, physical-feeling analog cassette players.

While the flagship Spindle (v1.2+) targets modern DAPs and compact phones (Android 8.0+ / 1GB+ RAM), **Spindle Lite** strips away modern Jetpack dependencies and refactors the custom Canvas rendering engine to run at a continuous **60 FPS on 512 MB RAM** with a total active memory footprint under **18 MB**.

### Primary Target Device: Ultra-Compact Legacy Hardware (`satsuma`)
- **Display**: 3.0-inch 320 × 480 pixels (HVGA, 192 ppi), 3:2 aspect ratio.
- **SoC**: Qualcomm MSM8255 (1.0 GHz Scorpion ARMv7, Adreno 205 GPU).
- **RAM / Storage**: 512 MB RAM / MicroSD slot up to 64GB FAT32.
- **Physical Controls**: Dedicated 2-stage camera shutter button, volume rocker, power key.
- **Form Factor**: Rugged IP67 water/dust resistant chassis with corner lanyard loop.

---

## 2. Architecture Comparison: Spindle vs. Spindle Lite

```
+---------------------------------------------------------------------------------------+
|                                SUBSYSTEM COMPARISON                                   |
+--------------------------+--------------------------------+---------------------------+
| Architectural Dimension  | Standard Spindle (v1.2 / :app-main) | Spindle Lite (v1.0 / :app-lite)|
+--------------------------+--------------------------------+---------------------------+
| Target Platform          | Android 8.0+ (API 26 – 35)     | Android 4.4 KitKat (API 19)|
| Target Hardware          | 1GB – 4GB RAM DAPs & Phones    | 512MB RAM Legacy Devices  |
| Audio Engine             | AndroidX Media3 (ExoPlayer)    | Native MediaPlayer        |
| Gapless Chaining         | Media3 AudioSink               | setNextMediaPlayer()      |
| Database / Storage       | Room ORM + Coroutines          | Native SQLiteOpenHelper   |
| UI Framework             | ViewPager2 + Material 3        | Pure Canvas + Legacy Views|
| Target Display           | 720p / 1080p (16:9 – 21:9)     | 320×480 (HVGA 3:2)        |
| Hardware Nameplate       | Auto DeviceNameFormatter       | Auto DeviceNameFormatter  |
| Image Format             | RGB_565 (512×512, Max 12MB)    | RGB_565 (256×256, Max 4MB)|
| Idle RAM Usage           | < 25 MB                        | < 12 MB                   |
| Active Playback RAM      | < 38 MB                        | < 18 MB                   |
| APK Binary Size          | < 4.5 MB                       | < 1.8 MB                  |
| Hardware Key Integration | Android MediaSession           | Full Hardware Suite (BACK, MENU, HOME, RECENT, CAMERA, DPAD, VOL, HEADSET) |
+--------------------------+--------------------------------+---------------------------+
```

---

## 3. Audio Engine & Gapless Sequencing

On Android 4.4 (API 19), modern `androidx.media3` cannot be used. Spindle Lite implements `LiteAudioEngine` utilizing dual chained `android.media.MediaPlayer` instances:

```
[ Track N Playing: MediaPlayer Instance A ]
                   |
     (At 90% Track Progress)
                   |
                   v
[ Prepare Track N+1: MediaPlayer Instance B ]
                   |
                   v
[ setNextMediaPlayer(Instance B) ]  <--- Hardware seamless audio handoff
                   |
                   v
[ onCompletion(Instance A) -> Release A -> Swap Roles ]
```

1. **Native Hardware Codec Support**:
   - **FLAC 16-bit / 44.1 kHz & 48 kHz**: Native hardware decoding via Qualcomm Stagefright audio engine.
   - **MP3 (up to 320 kbps)** and **AAC (LC/HE)**: High efficiency, $< 3\%$ CPU load.
2. **Audio Becoming Noisy**:
   - Registers `AudioManager.ACTION_AUDIO_BECOMING_NOISY` to instantly pause playback if the 3.5mm headphone jack is unplugged.

---

## 4. Memory Budget (< 18 MB Total Footprint)

On a device with 512 MB total system RAM (where Android OS and low-memory killer leave only ~200 MB available for user processes), memory discipline is paramount:

```
       +-------------------------------------------------------------+
       |             SPINDLE LITE MEMORY BUDGET (<18 MB)             |
       +-------------------------------------------------------------+
                                      |
         +----------------------------+----------------------------+
         |                            |                            |
         v                            v                            v
   Bitmap Cache (RGB_565)     SQLite Cursor & Models         Audio Buffers
      (Max: 4.0 MB)                (Max: 1.5 MB)             (Max: 2.5 MB)
         |                            |                            |
         v                            v                            v
   Canvas Hardware Render       App UI & View Stack       MediaPlayer Native Overhead
      (Max: 3.5 MB)                (Max: 2.5 MB)             (Max: 4.0 MB)
```

1. **Strict 256×256 RGB_565 Bitmap Pipeline**:
   $$\text{Bitmap Size} = 256 \times 256 \times 2 \text{ bytes} = 131,072 \text{ bytes} \approx 128 \text{ KB}$$
   A fixed cache of 30 album thumbnails occupies merely **3.84 MB** of heap memory.
2. **Zero Allocation in `onDraw()`**:
   - No `new Paint()`, `new RectF()`, `new Path()`, or string concatenations inside the rendering loop. All objects are pre-allocated during view initialization.

---

## 5. HVGA (320 × 480) Responsive Canvas Geometry

To ensure the flagship kinetic cassette deck renders flawlessly on the 3.0" display of the legacy target (`satsuma`), all coordinates are mapped dynamically to bounded view ratios:

```
+---------------------------------------------------------+ (0,0)
|  [ PORTABLE PLAYER ]       +-------------------------+  |
|  10:45:22                  |   CLEAR SPINDLE WINDOW  |  |
|  [FLAC 16/44]              |  (( O ))       (( O ))  |  |
|  Track Title               |  [Supply]      [Takeup] |  |
|  Artist • 03:45            +-------------------------+  |
|  <--- 35% Width --->       <------- 60% Width ------>   |
|                                                         |
|  [<< REW]      [>> FWD]      [> PLAY]         [EJECT]   |
+---------------------------------------------------------+ (320, 480)
```

1. **Kinetic Reel Calculations**:
   - Hub Radius: $R_{\text{hub}} = \text{WindowHeight} \times 0.22$
   - Max Tape Pack: $R_{\text{max}} = \text{WindowHeight} \times 0.44$
   - Linear velocity constant: $v = 4.7625\text{ cm/s}$
2. **Typography Scaling**:
   - **Hardware Nameplate Header**: `12sp` bold (auto-detected via `DeviceNameFormatter`).
   - Clock / Time: `11sp` monospace.
   - Format Pill Badge: `9sp` uppercase.
   - Song Title: `12sp` bold (marquee scroll on overflow).

---

## 6. Physical & Capacitive Hardware Button Engine

On dedicated vintage DAPs and rugged compact hardware (e.g. `satsuma` Sony Ericsson Xperia active), physical and capacitive buttons provide deterministic tactile control without requiring touch screen interaction:

```
+-----------------------------------------------------------------------------------------+
|                                HARDWARE KEY DISPATCH MATRIX                             |
+----------------------+---------------------------+--------------------------------------+
| Hardware Key         | KeyEvent Constant         | Functional Action                    |
+----------------------+---------------------------+--------------------------------------+
| BACK                 | KEYCODE_BACK              | Vault open -> Close Vault            |
|                      |                           | Drawer open -> Close Drawer          |
|                      |                           | Root deck -> Open Tape Vault Catalog |
| MENU                 | KEYCODE_MENU              | Toggle Tactile App Drawer            |
| HOME                 | KEYCODE_HOME              | Dismiss overlays -> Return to Deck   |
| APP_SWITCH / RECENT  | KEYCODE_APP_SWITCH        | Open system Recent Apps task manager |
|                      | (or KEYCODE_BUTTON_SELECT)|                                      |
| CAMERA (Full Click)  | KEYCODE_CAMERA            | Toggle Play / Pause                  |
| CAMERA (Long Press)  | KEYCODE_CAMERA (Hold)     | Toggle display sleep / low-power lock|
| FOCUS (Half Shutter) | KEYCODE_FOCUS             | Skip to Next Track                   |
| HEADSET HOOK         | KEYCODE_HEADSETHOOK       | 1 click: Play / Pause                |
|                      |                           | 2 clicks: Next Track                 |
|                      |                           | 3 clicks: Previous Track             |
| VOLUME UP / DOWN     | KEYCODE_VOLUME_UP/DOWN    | Screen on: Hardware audio volume     |
|                      |                           | Screen off: Long-press Next/Prev Skip|
| DPAD CENTER          | KEYCODE_DPAD_CENTER       | Toggle Play / Pause                  |
| DPAD RIGHT / LEFT    | KEYCODE_DPAD_RIGHT / LEFT | Skip Next Track / Previous Track     |
| DPAD DOWN / UP       | KEYCODE_DPAD_DOWN / UP    | DOWN: Open Vault / UP: Close Vault   |
+----------------------+---------------------------+--------------------------------------+
```

1. **Root `BACK` Button Tape Vault Trigger**:
   - Intercepted in `LiteMainActivity.onKeyDown()`. If `VaultDialogFragment` or `AppDrawerDialogFragment` is visible, `BACK` gracefully closes the active overlay.
   - When already at the root kinetic tape deck, pressing `BACK` immediately slides open the **Tape Vault** music catalog—providing instantaneous thumb navigation without reaching for on-screen touch tabs.
2. **Dedicated Camera Shutter Two-Stage Engine (`KEYCODE_CAMERA` & `KEYCODE_FOCUS`)**:
   - Utilizing the two-stage tactile microswitch found on dedicated camera/audio hardware:
     - **Half-Press (`KEYCODE_FOCUS`)**: Instantly advances to the next track (`skipNext()`).
     - **Full-Press (`KEYCODE_CAMERA`)**: Toggles audio playback between Play and Pause.
     - **Long-Press**: Triggers screen sleep/wake for one-handed pocket operation.
3. **Screen-Off Volume Rocker Long-Press**:
   - Handled via `HardwareButtonReceiver` to allow eyes-free pocket track changes without waking the CPU or powering the display.
4. **Capacitive Navigation Keys (`MENU`, `HOME`, `APP_SWITCH`)**:
   - `MENU`: Toggles the tactile app drawer.
   - `HOME`: Instantly returns focus to the running cassette deck from any sub-state.
   - `APP_SWITCH` (`KEYCODE_APP_SWITCH`, `KEYCODE_BUTTON_SELECT`): Directly invokes the Android Recent Apps task manager via internal intent.

---

## 7. Storage Indexing & File Organization

1. **Direct FAT32 MicroSD Crawler**:
   - Scans `/storage/sdcard1/Music/` or `/sdcard/Music/`.
   - Reads standard ID3v1, ID3v2.3, and Vorbis/FLAC metadata headers in a low-priority background thread (`Process.THREAD_PRIORITY_BACKGROUND`).
2. **SQLite Schema**:
   ```sql
   CREATE TABLE tracks (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       title TEXT NOT NULL,
       artist TEXT,
       album TEXT,
       duration_ms INTEGER,
       file_path TEXT UNIQUE,
       format TEXT,
       bitrate INTEGER,
       sample_rate INTEGER,
       bit_depth INTEGER
   );
   CREATE INDEX idx_artist_album ON tracks(artist, album);
   ```

---

## 8. Dynamic Hardware Nameplate Engine (`DeviceNameFormatter`)

Rather than hardcoding static branding, Spindle Lite inspects `android.os.Build` at runtime to format a custom industrial hardware nameplate:

* **Generic Auto-Format**: `[MANUFACTURER] [MODEL]` or cleaned uppercase model code (e.g. `SPINDLE • SATSUMA HVGA`).
* **Audiophile Engraving Presets**: Tap-to-engrave directly on the nameplate header to toggle curated presets:
  - `SPINDLE • REFERENCE DAP`
  - `HIGH BIAS • MASTER RECORDER`
  - `DIRECT ALSA • 192K LOSSLESS`
  - `SATSUMA AUDIO EDITION`
* **Persistent State**: Stored in `spindle_lite_prefs` with instant zero-overhead restoration across reboots.

---

## 9. Collector Cassette Formulations & Kinetic Canvas

Spindle Lite dynamically transforms its custom hardware-accelerated Canvas deck based on track audio fidelity:

1. **Type IV Metal Master (Lossless Hi-Res / Studio Masters)**:
   - **Chassis**: Brushed gunmetal faceplate (`#1F232D`) with precision metallic chamfer.
   - **Label**: Metallic gold foil strip (`#FACC15`) with deep graphite screenprinted lettering (`#0F172A`).
   - **Reel Accents**: Ruby crimson hero spoke (`#EF4444`) with dark metal oxide tape pack (`#231F20`).
2. **Type II Chrome Hi-Bias (High-Bitrate MP3 $\ge$ 256k / AAC)**:
   - **Chassis**: Deep anthracite slate (`#2A2E45`) with structured 8dp seam borders.
   - **Label**: Warm butter amber tape label (`#FDE68A`) with dark indigo typography (`#1E2132`).
   - **Reel Accents**: Industrial safety orange hero spoke (`#F97316`) with dark cobalt chrome pack (`#3A2218`).
3. **Type I Normal Studio (Standard Audio)**:
   - **Chassis**: Studio matte grey (`#282C37`) with subtle border framing.
   - **Label**: Crisp studio off-white (`#E2E8F0`) with graphite ink (`#1E293B`).
   - **Reel Accents**: Cobalt blue hero spoke (`#3B82F6`) with classic ferric oxide red-brown pack (`#451A11`).
4. **Mechanical Tactility (Zero-Allocation Loop)**:
   - **3-Digit Mechanical Tape Drum Counter**: Real-time mechanical odometer (`000` to `999`) rendered in a recessed window on the central bridge plate.
   - **Optical Window Glare**: Subtle diagonal acrylic sheen across the upper-right tape window.
   - **Playback Head & Capstans**: Machined playback head housing and dual brass guide pins anchored at the cassette chamber base.

---

## 10. Native Audio Stream Header Parser (`AudioHeaderParser`)

To bypass the limitations of legacy Android MediaStore scrapers on Android 4.4 KitKat, Spindle Lite implements a zero-dependency binary stream parser:

* **FLAC STREAMINFO Extraction**: Inspects the native 34-byte metadata header in $<0.1\text{ms}$, extracting exact sample rate (e.g. `96000 Hz`, `44100 Hz`), true bit depth (`24-bit` vs `16-bit`), and channel layout.
* **WAV RIFF fmt Extraction**: Scans RIFF chunks directly for uncompressed PCM bit depth and sample frequency.
* **Dynamic Audiophile Badging**: Surfaces genuine mastering resolution on the deck (`FLAC 24b/96k`, `WAV 24b/96k`, `FLAC 16b/44.1k`, `MP3 320K`).

---

## 11. Tactile App Drawer & Package Management (`AppDrawerDialogFragment`)

On compact 3.0" screens, standard Android launcher grids cause high mistap rates. Spindle Lite redesigns the application drawer for single-thumb ergonomics:

1. **48dp Minimum Touch Targets**:
   - Every application list item conforms to standard ergonomic touch guidelines with an enlarged 48dp+ hit boundary, prominent high-contrast app icon, and distinct package title.
2. **Interactive App Management Modal**:
   - **Standard Tap**: Instantly launches the target application and dismisses the drawer.
   - **Long-Press**: Opens a dedicated native action dialog:
     - **App Info / Properties**: Dispatches `android.settings.APPLICATION_DETAILS_SETTINGS` to inspect permissions, storage cache, force stop, or manage notifications.
     - **Uninstall App**: Dispatches `Intent.ACTION_UNINSTALL_PACKAGE` (with fallback to `Intent.ACTION_DELETE` for API 14+) with clean package URI targeting.
3. **Tactile Invocation**:
   - Can be opened via the on-screen tactile bottom-sheet handle or instantly toggled via the physical/capacitive hardware **`MENU`** key.

---

## 12. Tape Vault Catalogue Architecture (`VaultDialogFragment`)

The **Tape Vault** is Spindle Lite's lightweight music library browser, built specifically to navigate massive MicroSD music collections on low-memory hardware:

1. **3-Tab Segmented Organization**:
   - **`TRACKS`**: Unified alphabetical listing of all indexed audio files with format badges, track artist, and duration.
   - **`FOLDERS`**: Direct physical file-tree browser (`/storage/sdcard1/Music/`) supporting hierarchical navigation, folder drill-down, and quick parent directory (`..`) navigation.
   - **`QUEUE`**: Active playback sequence with real-time indicator of the currently playing track.
2. **Audiophile Format Quick-Filters**:
   - **`HI-RES`**: Instantly isolates 24-bit / 96kHz+ master files.
   - **`LOSSLESS`**: Filters the catalog down to FLAC and WAV bit-perfect audio.
3. **Instant Search Filter**:
   - Real-time text filtering across title, artist, and album with zero background allocation overhead.
4. **Seamless Navigation & Dismissal**:
   - Opened via on-screen bottom bar or physical hardware **`BACK`** button on the root deck.
   - Pressing **`BACK`** within the vault instantly returns focus to the running cassette deck.

---

<div align="center">
  <sub>Spindle Lite · Preserving classic industrial Android hardware through extreme optimization.</sub>
</div>
