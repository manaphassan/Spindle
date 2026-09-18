# Spindle Lite — Master Technical & Architectural Documentation
**Product Name:** Spindle Lite (Legacy & Vintage DAP Edition)  
**Package Name:** `com.hana.spindle.lite`  
**Target Codename:** `satsuma` (Sony Ericsson Xperia active ST17i/a, Xperia mini, Xperia ray, early FiiO/Shanling DAPs)  
**Version:** 1.0.0-LITE-RELEASE  
**Platform Target:** Android 4.4 KitKat (API 19) down to Android 4.1 Jelly Bean (API 16)  
**Hardware Baseline:** Single-Core Qualcomm Snapdragon S2 (MSM8255 / 1.0 GHz), 512 MB RAM, 3.0" 320×480 HVGA Display  
**License:** Apache License 2.0  

---

## 1. Executive Summary & Hardware Target

**Spindle Lite** is an ultra-lightweight, zero-bloat edition of Spindle engineered to turn vintage, ultra-compact Android devices from the 2011–2014 era into dedicated, physical-feeling analog Walkman players.

While the flagship Spindle (v1.2+) targets modern DAPs and compact phones (Android 8.0+ / 1GB+ RAM), **Spindle Lite** strips away modern Jetpack dependencies and refactors the custom Canvas rendering engine to run at a continuous **60 FPS on 512 MB RAM** with a total active memory footprint under **18 MB**.

### Primary Target Device: Sony Ericsson Xperia active (`satsuma`)
- **Display**: 3.0-inch 320 × 480 pixels (HVGA, 192 ppi), 3:2 aspect ratio.
- **SoC**: Qualcomm MSM8255 Snapdragon S2 (1.0 GHz Scorpion ARMv7, Adreno 205 GPU).
- **RAM / Storage**: 512 MB RAM / MicroSD slot up to 64GB FAT32.
- **Physical Controls**: Dedicated 2-stage camera shutter button, volume rocker, power key.
- **Form Factor**: Rugged IP67 water/dust resistant chassis with iconic corner lanyard loop.

---

## 2. Architecture Comparison: Spindle vs. Spindle Lite

```
+-------------------------------------------------------------------------------+
|                            SUBSYSTEM COMPARISON                               |
+--------------------------+----------------------------+-----------------------+
| Component                | Standard Spindle (v1.2)    | Spindle Lite (v1.0)   |
+--------------------------+----------------------------+-----------------------+
| Target Platform          | Android 8.0+ (API 26-35)   | Android 4.4 (API 19)  |
| Audio Engine             | AndroidX Media3 (ExoPlayer)| Native MediaPlayer    |
| Gapless Chaining         | Media3 AudioSink           | setNextMediaPlayer()  |
| Database / Storage       | Room ORM + Coroutines      | Native SQLiteOpenHelper|
| UI Framework             | ViewPager2 + Material 3    | Pure Canvas + Views   |
| Target Display           | 720p / 1080p (16:9 - 21:9) | 320x480 (HVGA 3:2)    |
| Image Format             | RGB_565 (512x512)          | RGB_565 (256x256)     |
| Idle RAM Usage           | < 25 MB                    | < 12 MB               |
| Active Playback RAM      | < 38 MB                    | < 18 MB               |
| APK Binary Size          | < 4.5 MB                   | < 1.8 MB              |
| Hardware Key Integration | Android MediaSession       | KeyEvents + Broadcast |
+--------------------------+----------------------------+-----------------------+
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

To ensure the flagship Sony Walkman kinetic deck renders flawlessly on the 3.0" display of the Xperia active (`satsuma`), all coordinates are mapped dynamically to bounded view ratios:

```
+---------------------------------------------------------+ (0,0)
|  [ WALKMAN ]               +-------------------------+  |
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
   - `WALKMAN` Header: `12sp` bold.
   - Clock / Time: `11sp` monospace.
   - Format Pill Badge: `9sp` uppercase.
   - Song Title: `12sp` bold (marquee scroll on overflow).

---

## 6. Physical Hardware Control Integration (`satsuma`)

The Xperia active features dedicated physical buttons that Spindle Lite binds directly:

1. **Hardware Camera Shutter Button (`KeyEvent.KEYCODE_CAMERA`)**:
   - **Full Press**: Toggle **Play / Pause**.
   - **Long Press**: Toggle screen sleep / wake.
2. **Volume Keys**:
   - **Standard Click**: Incremental volume adjustment.
   - **Screen-Off Long Press**: Trigger Next Track (`KEYCODE_MEDIA_NEXT`) or Previous Track (`KEYCODE_MEDIA_PREVIOUS`).
3. **Headset Remote (3.5mm TRRS)**:
   - Single click: Play/Pause.
   - Double click: Skip track.
   - Triple click: Previous track.

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
       sample_rate INTEGER
   );
   CREATE INDEX idx_artist_album ON tracks(artist, album);
   ```

---

<div align="center">
  <sub>Spindle Lite · Preserving classic industrial Android hardware through extreme optimization.</sub>
</div>
