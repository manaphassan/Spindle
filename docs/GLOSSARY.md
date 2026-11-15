# Spindle — Canonical Glossary & Terminology Authority

**Author:** HaNa Innovation Architecture Board  
**Status:** Active Canonical Specification (v1.1, updated for v1.7.0-STUDIO)  
**Scope:** `core`, `app-main` (Spindle Standard), `app-lite` (Spindle Lite), documentation, UI strings, and marketing.

---

## 1. Purpose & Authority

This document is the **single source of truth** for all naming across Spindle.
Historical drift across data layers, UI copy, DSP code, and documentation is permanently retired. Every UI string resource, Kotlin class name, database table alias, and user-facing document must conform to the canonical terms defined below.

---

## 2. Canonical Terms Table

| Concept | Canonical Term | Code Symbol | Retired Terms (Do Not Use) | Definition & Scope |
|---|---|---|---|---|
| **Audio File** | **Track** | `TrackEntity`, `Track` | Song, AudioFile, MusicItem | A single audio file on physical storage or memory indexed by Spindle. |
| **Library / Browse Screen** | **Vault** | `VaultFragment`, `VaultController` | Catalog, Cassette Vault, Tape Vault, Media Browser | The primary music library interface for sorting, grouping, filtering, and searching the collection. |
| **Ordered Playlist** | **Mixtape** | `Mixtape`, `PlaylistEntity` *(internal DB table only)* | Playlist, SongList, QueueList | A user-curated, ordered sequence of tracks. "Playlist" is strictly restricted to internal legacy DB table names (`playlists`, `playlist_songs`). All user-facing UI and documentation must say **Mixtape**. |
| **Saved Bookmark Flag** | **Favorite** | `isFavorite: Boolean` | Star, Heart, Rating (overloaded) | A binary boolean flag ("keep this handy"). Completely decoupled from audio quality rating. |
| **Quality Evaluation** | **Rating** | `rating: Int` (0–5) | Favorite, Score, Star (as boolean) | An audiophile quality score from 0 to 5 stars (0 = unrated, 1–5 = graded stars). Independent from Favorite. |
| **Deck Visual Theme** | **Chassis** or **Skin** | `ChassisTheme`, `ChassisStyle`, `Metal81ChassisView` | Cassette Theme, Deck Theme, App Theme | The skeuomorphic hardware rendering and mechanical cassette styling. "App Theme" is reserved exclusively for the system Light/Dark mode. |
| **Audio Engine Output** | **Hi-Res Passthrough** | `OutputRoutingMode.HI_RES_PASSTHROUGH` | Bit-Perfect, Direct ALSA (unverified) | Audio playback routed at native resolution through hardware HAL. Reserve "Bit-Perfect" only for a platform-verified bit-perfect mixer path (`BitPerfectMixerController`, Android 14+) with no DSP active; otherwise say "Direct · No DSP". |
| **Tape Formulation Preset** | **Formulation** | `TapeFormulation` | Tape Type, Cassette Type | Magnetic tape formulation emulation: FLAT reference (default, no tape curve), Type I Normal (Ferric $\text{Fe}_2\text{O}_3$), Type II High ($\text{CrO}_2$), Type III Ferrichrome, Type IV Metal. |
| **Home Deck Look** | **Deck Style** | `pref_deck_style` (`walkman_silver`, `walkman_black`, `classic`) | Skin, Theme, Mode | Which deck the player, radio and drawer render: the **faceplate deck** (Silver '81 / Pro Black colorways) or the **classic** vertical deck. Independent of Chassis, Skin and Theme. |
| **Drawer (deck style)** | **System Deck** | `SystemDeckHeaderView` | System Hub header, faceplate drawer | The drawer chrome in faceplate deck style: SPINDLE band, RUN lamp and FUNCTION switch (Sound / Settings / Apps). |
| **Signal untouched lamp** | **DIRECT** | `AudioFxController.isTonallyFlat` | Bypass, Pure | Lamp on the faceplate deck, lit only when no EQ curve, saturation, bass boost, crossfeed or software PEQ alters the sound. |

---

## 3. Strict Rules & Conventions

### 3.1 Binary "Favorite" vs. 5-Star "Rating"
- **`isFavorite` (`Boolean`)**: Stored in `TrackEntity.isFavorite`. Displayed with heart icon ($\heartsuit$). Toggling favorite does not alter `rating`.
- **`rating` (`Int`)**: Stored in `TrackEntity.rating` (range `0..5`). Displayed with star indicators ($\bigstar$). Setting rating does not alter `isFavorite`.
- **Filtering**:
  - `FAVORITES` filter: `WHERE isFavorite = 1`
  - `RATED` filter: `WHERE rating > 0`

### 3.2 Vault Screen Nomenclature
- In-app tab headers, bottom bars, drawer menu entries, and notifications must refer to **Vault** (never "Catalog" or "Tape Vault").
- Internal view bindings and XML layouts may progressively adopt `vault` prefixes while maintaining compatibility.

### 3.3 Mixtapes (Playlists)
- Public user interface: **"Mixtapes"**, **"Add to Mixtape"**, **"New Mixtape"**, **"Mixtape Editor"**.
- Database schema: May retain SQLite table names `playlists` and `playlist_songs` for Room migration safety, while Kotlin domain classes and DAO queries present them as Mixtapes and Tracks.

### 3.4 Chassis vs. Theme vs. Deck Style
- **Chassis**: Refers specifically to the vintage cassette deck enclosure (e.g., `METAL_81_RED`, `VINTAGE_SILVER`, `STUDIO_BLACK`).
- **Skin**: Refers to the cassette shell visual design (e.g., `TDK_SA_90`, `MAXELL_XLII`, `BASF_CHROME`, `METAL_XR_TYPE4`). Skins restyle the classic deck only.
- **Theme**: Refers exclusively to UI surface theming (Dark Mode, Light Mode, OLED Pure Black).
- **Deck Style**: Chooses the faceplate deck or the classic deck (see §2). User-facing text says "faceplate", never a third-party product name (see CONTRIBUTING §4).

---

## 4. Cross-Reference Index

| Canonical Term | `app-main` Reference | `app-lite` Reference | `core` Reference |
|---|---|---|---|
| **Track** | `TrackEntity`, `TrackDao`, `TrackAdapter` | `Track.kt`, `TABLE_TRACKS` | `SpindleGlossary.TERM_TRACK` |
| **Vault** | `VaultFragment`, `TrackSortGroup` | `page_lite_vault.xml`, `LiteMainActivity` | `SpindleGlossary.TERM_VAULT` |
| **Mixtape** | `MixtapeDialogs.kt`, `PlaylistEntity.kt` | `/Playlists/*.m3u` | `SpindleGlossary.TERM_MIXTAPE` |
| **Favorite** | `TrackEntity.isFavorite`, `TrackDao.updateFavorite` | `Track.isFavorite` | `SpindleGlossary.TERM_FAVORITE` |
| **Rating** | `TrackEntity.rating`, `TrackDao.updateRating` | — | `SpindleGlossary.TERM_RATING` |
| **Chassis** | `CassetteTheme.kt`, `Metal81ChassisView.kt` | `page_lite_player.xml` | `SpindleGlossary.TERM_CHASSIS` |
| **Fade** | `CrossfadeMode.kt` | — | `SpindleGlossary.TERM_FADE` |
| **Deck Style** | `PlayerFragment.applyDeckStyle`, `RadioFragment`, `DrawerFragment` | — | — |
| **System Deck** | `SystemDeckHeaderView.kt` | — | — |

---

## 5. Transport & Queue Specification (§8.2)

### 5.1 Stop vs. Eject
- **Stop = Pause**: Mechanically disengages the tape head and pinch roller. Playback halts immediately, preserving current track position and active queue.
- **Eject**: Physically unloads the cassette. Playback stops, active queue clears, and last-played persistent pointers are purged.

### 5.2 Previous: Universal DAP Rule
- **`currentPosition > 3000ms`**: Restarts the active track from `0:00`.
- **`currentPosition <= 3000ms`**: Jumps to the preceding track in the queue.
- This rule applies universally across on-screen transport buttons, chassis controls, lockscreens, and hardware keys.

### 5.3 Tap vs. Hold Semantics
- **Tap (Single Press)**:
  - Next / Fast Forward: Skip to Next Track (`playNext()`).
  - Previous / Rewind: Skip to Previous Track via Universal Rule (`playPrevious(forcePreviousSong = false)`).
- **Hold (Long Press)**:
  - Fast Forward: Continuous forward seeking (`fastForward(5000L)`).
  - Rewind: Continuous backward seeking (`rewind(5000L)`).

### 5.4 Track Transition
- **Gapless**: 0-latency sample-accurate audio buffer transition.
- **Fade**: Equal-power volume modulation (FADE 2s, FADE 4s). "Crossfade" is retired in single-player configurations.

