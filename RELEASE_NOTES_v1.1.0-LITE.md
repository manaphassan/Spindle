# Spindle Lite v1.1.0-LITE — Stable Production Release

**Release Name:** Zune HD Metro Overhaul & 5-Pivot Music Vault  
**Release Tag:** `v1.1.0-LITE`  
**Build Date:** October 5, 2026  
**Publisher:** HaNa Innovation  
**Target Hardware:** Vintage DAP & Low-RAM Android Devices (Android 4.4 KitKat down to 4.1 Jelly Bean, 512MB RAM, HVGA 320×480)  
**Reference Device:** Sony Ericsson Xperia active (`ST17i` / Satsuma)  
**Core License:** [PolyForm Noncommercial 1.0.0](LICENSE) (Free personal use; commercial resale strictly prohibited)  
**Signing:** Verified authentic private release keystore (`HaNa Innovation`)

---

## 📻 What's New in Spindle Lite v1.1.0-LITE

### 1. 🎨 Zune HD Metro Layout & High-Density Typography
- **Kinetic Typographic Overhaul:** Replaced generic Android UI with the iconic Windows Phone / Zune HD Metro typography and aesthetic.
- **Zero-Bloat System Typography:** Clean layout utilizing native Android `sans-serif-light`, `sans-serif-medium`, and `sans-serif` fonts, keeping RAM overhead below 18MB on vintage 512MB Dalvik runtimes.
- **High-Density 54dp Vault Rows:** Optimized row dimensions with orange tape-spine indicators (`#FF6A00`), track numbers, secondary artist/album details, and right-aligned duration timestamps.

### 2. 🗃️ Full 5-Pivot Music Vault
The Music Vault now hosts the complete classic Zune HD music collection:
$$\text{songs} \quad\bullet\quad \text{artists} \quad\bullet\quad \text{albums} \quad\bullet\quad \text{folders} \quad\bullet\quad \text{queue}$$

- **Horizontal Sliding Pivot Bar:** Wrapped in an ultra-smooth, scrollbar-free `HorizontalScrollView`. Selected pivot glows bold in brand orange, with inactive pivots dimmed to muted grey.
- **Dedicated Artists Pivot:** Aggregates library tracks in-memory into `LiteArtist` entities with zero database penalty. Displays album count, track count, and total artist listening duration.
- **Dedicated Albums Pivot:** Aggregates tracks into `LiteAlbum` entities with album name, artist subtitle, and track counts.
- **Discography Drill-Down Navigation:** Tapping any artist or album opens their complete tracklist with a signature `← ARTISTS` / `← ALBUMS` pill back-button, title header, and instant `PLAY ALL` action.
- **Hierarchical Back-Button Handling:** Device **Back** key gracefully steps backward from drilldown into the parent list before closing the drawer.

### 3. ⚡ Authentic Zune 26-Letter Quick-Jump Grid (A–Z)
- Dedicated `A–Z` quick-jump button in the Vault header opening a 4×7 grid of letter tiles (`#`, `A`–`Z`, `↑`).
- Highlights available letters present in the library in bright orange; dims and disables empty letters.
- Instant jump to any alphabetical section with one tap.

### 4. 🔄 MicroSD Scan Button Modernization
- Replaced the wide text button with a sleek 32×32dp circular `ic_refresh` vector button, reclaiming over 60dp of horizontal space for the kinetic `music` title.

### 5. 🕹️ Physical Hardware Key & DPAD Ergonomics
- Modulo 5 tab cycling (`% 5`) mapped to the hardware **Menu** key.
- D-Pad Left/Right navigation across all 5 pivots.
- Hardware camera shutter button play/pause & half-press track advance.
- Headset inline remote click-sequence decoder (single = toggle, double = next, triple = prev).

---

## 📦 Verified Binaries & Checksums

| File | Target | Size | SHA-256 Checksum |
| :--- | :--- | :---: | :--- |
| **`Spindle-Lite-v1.1.0-LITE.apk`** | Production Release APK (minSdk 19) | **1.73 MB** (1,814,039 bytes) | `b39e720345addd8033207a7f5fea8e6c13c06affde0aa6ba04a86f8288641310` |
| **`Spindle-Lite-v1.1.0-LITE-debug.apk`** | Debug Instrumentation APK | **5.76 MB** (6,044,505 bytes) | `09765bb2989d07ab5a1b2599994674df9ed6fa9d36275ff133e1fa7e89917ad7` |

---

## 📲 Sideload Instructions

```bash
# Sideload to vintage Android device via ADB:
adb install -r Spindle-Lite-v1.1.0-LITE.apk
```

---

<sub>Spindle Lite · Dedicated Vintage DAP Launcher · HaNa Innovation</sub>
