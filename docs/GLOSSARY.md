# Spindle — Canonical Glossary & Terminology Authority

**Author:** HaNa Innovation Architecture Board  
**Status:** Active Canonical Specification (v1.0)  
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
| **Audio Engine Output** | **Hi-Res Passthrough** | `OutputRoutingMode.HI_RES_PASSTHROUGH` | Bit-Perfect, Direct ALSA (unverified) | Audio playback routed at native resolution through hardware HAL. Reserve "Bit-Perfect" only for verified USB DAC exclusive mode. |
| **Tape Formulation Preset** | **Formulation** | `TapeFormulation` | Tape Type, Cassette Type | Physical magnetic tape tape formulation emulation: Type I Normal (Ferric $\text{Fe}_2\text{O}_3$), Type II High ($\text{CrO}_2$), Type IV Metal. |

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

### 3.4 Chassis vs. Theme
- **Chassis**: Refers specifically to the vintage cassette deck enclosure (e.g., `METAL_81_RED`, `VINTAGE_SILVER`, `STUDIO_BLACK`).
- **Skin**: Refers to the cassette shell visual design (e.g., `TDK_SA_90`, `MAXELL_XLII`, `BASF_CHROME`, `METAL_XR_TYPE4`).
- **Theme**: Refers exclusively to UI surface theming (Dark Mode, Light Mode, OLED Pure Black).

---

## 4. Cross-Reference Index

| Canonical Term | `app-main` Reference | `app-lite` Reference | `core` Reference |
|---|---|---|---|
| **Track** | `TrackEntity`, `TrackDao`, `TrackAdapter` | `Track.kt`, `TABLE_TRACKS` | `SpindleGlossary.TERM_TRACK` |
| **Vault** | `CatalogFragment` (migrating to Vault), `TrackSortGroup` | `page_lite_vault.xml`, `LiteMainActivity` | `SpindleGlossary.TERM_VAULT` |
| **Mixtape** | `MixtapeDialogs.kt`, `PlaylistEntity.kt` | `/Playlists/*.m3u` | `SpindleGlossary.TERM_MIXTAPE` |
| **Favorite** | `TrackEntity.isFavorite`, `TrackDao.updateFavorite` | `Track.isFavorite` | `SpindleGlossary.TERM_FAVORITE` |
| **Rating** | `TrackEntity.rating`, `TrackDao.updateRating` | — | `SpindleGlossary.TERM_RATING` |
| **Chassis** | `CassetteTheme.kt`, `Metal81ChassisView.kt` | `page_lite_player.xml` | `SpindleGlossary.TERM_CHASSIS` |
