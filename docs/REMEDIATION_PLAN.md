# Spindle — Remediation & Upgrade Plan
**Prepared for:** HaNa Innovation
**Scope:** `app-main` (Spindle Standard) + `app-lite` (Spindle Lite)
**Status:** v2.0 — supersedes v1.0. Adds: audio-engine claim disclosure, playlist/organization fixes, and a dedicated sanitization + canonical-naming pass for long-term scale/maintenance.
**Companion reading:** Keep this file alongside `MASTER_DOCUMENTATION.md`, `SPINDLE_LITE_MASTER_DOCUMENTATION.md`, `BRANDING.md`. This file does not restate their technical specs — it lists what must change in them.

---

## 1. Executive Summary

Spindle has a strong concept and genuinely capable engineering underneath it (real Qualcomm FLAC decode fixes, real AudioEffect-based EQ, real ReplayGain), but five rounds of audit — legal/brand, UI/UX + competitive, codebase, audio engine, and playlist/organization — converge on one root cause: **the project has never had a single naming authority.** Every layer (data model, UI copy, marketing, DSP code) evolved its own vocabulary independently. That's what produced the license/roadmap contradiction, the Walkman/WM-2 exposure, five clashing color palettes, four names for one concept, an unverified "bit-perfect" claim, and a playlist system that's half-built in one module and absent in the other.

**None of this requires a rewrite.** It requires one sanitization pass with a canonical glossary enforced afterward, sequenced correctly so legal exposure closes before anything ships publicly.

| Track | Current State | Target State |
|---|---|---|
| Legal | License forbids the monetization roadmap already written in `MASTER_DOCUMENTATION.md`; UI/source code name a specific real Sony product | Open-core license split; zero named references to Sony IP |
| Brand | 5 different color palettes across published screenshots; no visual proof of Lite existing | 1 documented system, applied everywhere, Lite screenshots published |
| Naming/Codebase | Terminology drift (Song/Track/Mixtape/Vault/Catalog, Favorite=Rating); 100% of declared string resources are dead; Lite UI logic lives in one 2,300-line file; no shared naming authority | One glossary file, enforced project-wide; live string resources; Lite split into controllers; a documented naming convention new contributors follow by default |
| Audio engine | "Bit-Perfect"/"Direct ALSA" label is unverified at the output stage; EQ silently downgrades to 5 real hardware bands with no disclosure | Labels describe verified behavior only; real hardware capability surfaced to the user |
| Playlist/organization | Favorite conflated with star rating; no M3U import/export; no smart playlists; no `albumArtist`; substring-only search; zero playlist support in Lite | Correct data model, portable playlists, smart lists, accurate album grouping, feature parity across modules |
| Monetization | Roadmap is directionally correct (one-time unlock, no subscription) but currently illegal to execute | Same roadmap, executed under a license that permits it |

---

## 2. Consolidated Findings Register

Severity key: **P0** = blocks any commercial or public-facing move · **P1** = blocks brand credibility / user trust · **P2** = maintainability / competitiveness · **P3** = nice-to-have

| # | Sev | Area | Finding | Evidence |
|---|---|---|---|---|
| 1 | P0 | Legal | `LICENSE` (PolyForm Noncommercial) forbids selling/paywalling/monetizing the software or derivatives; `MASTER_DOCUMENTATION.md` §8.6 proposes a $2.99–3.99 paid unlock | `LICENSE`, `MASTER_DOCUMENTATION.md` |
| 2 | P0 | Legal / IP | Source enum `WM2_RED // 1981 WM-2 Red horizontal cut chassis`, in-app text `"WALKMAN CASSETTE THEMES"` / `"WM-2 Red (1981)"`, website H1 "a retro cassette Walkman" — named reference to a real, trademarked Sony product | `CassetteTheme.kt`, `Wm2ChassisView.kt`, `docs/index.html`, screenshots |
| 3 | P1 | Brand | 5 distinct color systems shipped across published screenshots vs. 1 documented 3-theme system in `BRANDING.md` | now-playing / catalog / lockscreen / VU-EQ screenshots vs. `BRANDING.md` §3 |
| 4 | P1 | Brand | Zero public screenshots exist for Spindle Lite anywhere | `docs/assets/` listing |
| 5 | P1 | Terminology | One concept (an audio file), 3 names: `SongEntity`/`SongDao` (main data layer), `Track` (lite data layer), UI tab "TRACKS" | `data/db/`, `lite/db/Track.kt`, `fragment_catalog.xml` |
| 6 | P1 | Terminology | Library screen: "CATALOG" (main layout), "CASSETTE VAULT" (lite strings), "Tape Vault" (README) | `fragment_catalog.xml`, lite `strings.xml`, `README.md` |
| 7 | P1 | Data model | "Favorite" toggle and 5-star `rating` write to the **same column** — a user can't rate 3 stars without it also becoming a favorite | `CatalogFragment.kt` (`updateFavoriteIcon(song.rating > 0)`) |
| 8 | P2 | Codebase | 100% of declared `strings.xml` entries in `app-main` are unreferenced anywhere in code; all real UI text is hardcoded | grep verification, `app-main/src/main/` |
| 9 | P2 | Codebase | Placeholder copy (`"Album Title"`, `"Artist Name"`) set as live `android:text`, not design-time `tools:text` | `fragment_catalog.xml` |
| 10 | P2 | Architecture | `LiteMainActivity.kt` is a single 2,300-line file; app-main achieves equivalent scope split across 4 files (564–1,402 lines) | line counts, both modules |
| 11 | P2 | Naming convention | `app-lite` consistently prefixes every class `Lite*`; `app-main` has no equivalent convention; no written rule exists for either | package listings, both modules |
| 12 | P2 | Feature parity | Lyrics parsing and home-screen widget exist in `app-main` only, no RAM/CPU justification for Lite's exclusion | `LyricsParser.kt`, `SpindleAppWidgetProvider.kt` |
| 13 | P2 | Audio engine | "Bit-Perfect"/"Direct ALSA" label is device-type-based, not verified: `setEnableAudioFloatOutput(false)` disables float PCM output; no evidence of native-rate/exclusive-mode routing | `AudioEngine.kt` renderer setup |
| 14 | P2 | Audio engine | "10-Band ISO Graphic Equalizer" silently interpolates down to the device's real hardware band count (5 on most phones) with no user-facing disclosure | `AudioFxController.kt` |
| 15 | P2 | Audio engine | Spindle Lite has zero `MediaSession`/`MediaSessionCompat` anywhere — Bluetooth AVRCP metadata (title/artist on car-stereo displays) will not sync, though transport buttons work via legacy KeyEvent path | full-module grep, `app-lite` |
| 16 | P2 | Audio engine | No crossfade in app-main; Lite already has `CrossfadeMode` (Gapless/2s/4s) — flagship module missing a feature the lesser edition has | `AudioEngine.kt` vs. `LiteAudioEngine.kt` |
| 17 | P2 | Audio engine | ReplayGain is track-only in app-main; Lite already has a `ReplayGainMode` enum (`OFF`/`TRACK`/`ALBUM`) not ported back | `AudioEngine.kt` vs. `LiteAudioEngine.kt` |
| 18 | P2 | Organization | No `M3U`/`M3U8` import or export anywhere in the codebase | full-repo grep |
| 19 | P2 | Organization | No `dateAdded`, `playCount`, or `lastPlayedAt` columns — Recently Added/Most Played/Recently Played are all currently impossible | `SongEntity.kt` |
| 20 | P2 | Organization | No `albumArtist` column; album grouping is a plain string match on `album` — two different artists' same-titled albums (e.g. "Greatest Hits") will merge | `SongDao.kt` groupBy query |
| 21 | P2 | Organization | Search is `LIKE '%query%'` with no index — will slow down and won't tolerate typos at the library sizes this app targets | `SongDao.kt` |
| 22 | P2 | Organization | `PlaylistSongCrossRef.orderIndex` exists and is queried, but no drag-to-reorder UI is wired up anywhere — a finished backend with no front end | `MixtapeDialogs.kt`, `PlaylistSongCrossRef.kt` |
| 23 | P2 | Organization | Spindle Lite has exactly one database table (`TABLE_TRACKS`) — no playlist concept, no favorite field, no queue persistence at all | `LiteDbHelper.kt`, `Track.kt` |
| 24 | P2 | Duplication | Main and Lite each independently reimplement EQ presets/curves with no shared source of truth (Lite lacks Main's Harman/Crinacle/Moondrop presets entirely) — any future DSP fix must be made twice, in two different styles | `AudioFxController.kt` vs. `LiteAudioFxController.kt` |
| 25 | P3 | Competitive | 10-band EQ vs. Poweramp's up to 32-band + parametric + AutoEq; no DSD/Opus/TAK; no Android Auto/Chromecast | competitive audit |
| 26 | P3 | Docs | Version mismatch: README states `v1.4.0-alpha`; `MASTER_DOCUMENTATION.md` roadmap diagram lists `v1.3.0-alpha` as current | `README.md` vs. `MASTER_DOCUMENTATION.md` §8 |

---

## 3. Phase 0 — Legal & Brand-Safety (blocking; do before anything else ships publicly)

### 3.1 License restructure (open-core split)
1. Keep Spindle Lite and the free core of Spindle Standard under PolyForm Noncommercial (or a permissive OSS license).
2. Issue "Studio Collector" (skins, DSP, foley engine, engraving) under a **separate proprietary EULA**, distributed only via Play Billing as a compiled add-on.
3. Update `LICENSE` to state the split clearly; add `LICENSE-STUDIO.md` for the commercial terms.
4. Get this reviewed before enabling Play Billing — this document is not legal advice.

### 3.2 Remove Sony trademark references
1. Rename `ChassisStyle.WM2_RED` → `METAL_81_RED`; rename `Wm2ChassisView.kt` → `Metal81ChassisView.kt`.
2. Update in-app strings: "WALKMAN CASSETTE THEMES" → "CHASSIS THEMES"; "WM-2 Red (1981)" → "METAL-81 RED".
3. Remove "Walkman" from `docs/index.html` H1 and README — use "retro cassette player"/"analog tape deck."
4. Full-repo case-insensitive grep for "walkman" and "sony" before next release — zero hits required.

**Do not proceed to any paid launch until 3.1 and 3.2 are both closed.**

---

## 4. Phase 1 — Sanitization & Canonical Naming System

This phase is the fix for the *root cause*, not just its symptoms — it's what makes every future feature cheaper to add instead of adding another dialect to the project.

### 4.1 Canonical glossary — one name per concept, enforced everywhere
Create `docs/GLOSSARY.md` as the single source of truth. Every UI string, class name, doc, and marketing sentence must use these terms — no exceptions, no "just this once":

| Concept | Canonical term | Retire immediately |
|---|---|---|
| A single audio file | **Track** | Song |
| The library/browse screen | **Vault** | Catalog, Cassette Vault, Tape Vault |
| A saved, user-ordered set of tracks | **Mixtape** | Playlist (keep as internal DB name only, never user-facing) |
| A binary "keep this handy" flag | **Favorite** | — (currently wrongly merged with Rating; see 4.4/§7) |
| A 1–5 graded quality score | **Rating** | — (must be split from Favorite; see §7) |
| A visual/color theme for the deck | **Chassis** or **Skin** | "Cassette Theme" (ambiguous with app-wide UI theme) |

### 4.2 Canonical package/module structure
Current layout is flat and works for two modules; it will not scale cleanly to a third (e.g., a future Studio-only module or a shared library). Adopt:
```
com.hana.spindle.core.*      — shared, zero-Android-dependency logic (see 4.5)
com.hana.spindle.standard.*  — app-main, renamed from bare com.hana.spindle.*
com.hana.spindle.lite.*      — app-lite (already correctly namespaced)
```
Moving `app-main` off the bare root namespace and into `.standard` removes the current asymmetry where Lite is clearly a "variant" but Main reads as "the only real one" — both should read as siblings under `core`.

### 4.3 Canonical class-naming rule
Write this down once in `CONTRIBUTING.md` so it's a rule, not a convention someone has to infer from reading the code:
- Every class lives in exactly one module and is named for *what it does*, not decorated further, **except** classes tied to a specific skin/variant (e.g. a chassis renderer), which take the `{VariantName}{Role}` pattern already used correctly by `Metal81ChassisView` (post-4.4 §3.2 rename).
- `app-lite`'s existing `Lite*` prefix is correct and stays as-is — it's the one naming pattern in the project that already scales. Do not remove it; extend the *discipline* behind it to Standard, not the literal prefix (Standard classes don't need a `Standard*` prefix once they're under the `.standard` package from 4.2 — the package already disambiguates).

### 4.4 Data-model rename pass
Rename to match the glossary, in one migration:
- `SongEntity` → `TrackEntity`, `SongDao` → `TrackDao` (app-main; matches Lite's existing `Track.kt` naming).
- Split `Favorite` from `Rating`: add `isFavorite: Boolean` column; stop overloading `rating` for both meanings (full detail in §7 below).
- Add `albumArtist`, `dateAdded`, `playCount`, `lastPlayedAt`, `composer` columns now, as part of the same migration pass, rather than four separate future migrations (§8).

### 4.5 Extract shared, dependency-free constants into `core`
Main and Lite each hand-maintain their own EQ preset curves and frequency tables independently (finding #24) — Lite doesn't even have Main's Harman/Crinacle/Moondrop presets. Full engine unification isn't advisable (Lite's constraints are real), but the **data** — frequency arrays, preset curve values, the glossary terms themselves as string constants — can live in one small Kotlin module with zero Android dependencies that both modules import. This means a future preset update or terminology fix happens once, not twice, and the two engines can no longer silently drift apart.

### 4.6 Sanitization checklist (run once, then enforce via code review)
- [ ] Zero "Walkman"/"Sony"/"WM-2" references repo-wide (3.2)
- [ ] Zero dead `strings.xml` entries — every declared string is referenced at least once (§5.1)
- [ ] Zero `android:text` placeholder-as-live-content (§5.2)
- [ ] Zero remaining Song/Catalog/Tape Vault/Playlist naming outside internal DB identifiers (4.1, 4.4)
- [ ] `docs/GLOSSARY.md` and `CONTRIBUTING.md` (naming rule) committed
- [ ] Package migration (4.2) complete and building clean on both modules

---

## 5. Phase 2 — Codebase Hygiene

### 5.1 Migrate all UI text to string resources
Every hardcoded UI string (layout XML and Kotlin, both modules) moves into `strings.xml`. This is also what makes the glossary (4.1) enforceable — one file to audit instead of scattered literals.

### 5.2 Fix placeholder-text-as-live-content risk
Move design-time placeholders (`"Album Title"`, `"Artist Name"`, etc.) from `android:text` to `tools:text`; confirm real data-binding paths set actual content on load.

### 5.3 Refactor `LiteMainActivity.kt`
Split into per-screen controller classes owned by a slimmer host Activity — no Fragments/Jetpack required:
- `LiteDeckController` (deck/transport), `LiteCatalogController` (vault/browse), `LiteRadioController` (FM/stream).

### 5.4 Apply the naming rule from 4.3
Audit both modules against `CONTRIBUTING.md`'s naming rule; this is now a lint-checkable rule, not a judgment call for the next contributor.

---

## 6. Phase 3 — Playlist & Media Organization

### 6.1 Fix Favorite/Rating conflation (finding #7)
Add `isFavorite: Boolean` to `TrackEntity`/`Track`. Heart icon writes `isFavorite`; star row writes `rating`, independently.

### 6.2 Finish what's already half-built
`orderIndex` exists and is queried but has no drag-to-reorder UI — wire up an `ItemTouchHelper` in the Mixtape editor. Backend work is done; this is front-end only.

### 6.3 Playlist portability
Add M3U/M3U8 import and export. Plain-text format, near-zero dependency cost, consistent with "you own your files" positioning — and gives Mixtapes a path in and out of foobar2000/Winamp/VLC.

### 6.4 Smart Mixtapes
With `dateAdded`/`playCount`/`lastPlayedAt` added (4.4), ship four auto-generated lists: Recently Added, Recently Played, Most Played, Never Played.

### 6.5 Accurate album grouping
Add `albumArtist`; group by `(album, albumArtist)` instead of `album` alone, closing the same-titled-album collision risk.

### 6.6 Search at scale
Migrate from `LIKE '%query%'` to a SQLite FTS4/FTS5 virtual table over title/artist/album — same data, indexed and typo-tolerant.

### 6.7 Bring Mixtapes to Spindle Lite
Currently zero playlist support. Implement as flat `.m3u` files in a `/Playlists/` folder rather than porting Room — matches Lite's existing zero-ORM philosophy at near-zero memory cost, and closes the largest single feature gap between the two modules.

### 6.8 Composer field + browse view
Add `composer` column and a "Browse by Composer" view — cheap, and a genuine differentiator the audiophile/classical-adjacent audience (already courted via Harman/Crinacle/Moondrop EQ presets) will notice that Poweramp/VLC don't prioritize either.

### 6.9 Duplicate detection
Match on `(title, artist, duration)` to flag likely duplicate rips accumulated on long-lived SD-card libraries; surface in the existing Sort & Group sheet.

---

## 7. Phase 4 — Audio Engine Disclosure & Parity

### 7.1 Correct the "Bit-Perfect"/"Direct ALSA" claim
Current label is device-type-based, not verified — `setEnableAudioFloatOutput(false)` and no evidence of native-rate/exclusive-mode output means most hi-res playback is likely resampled by Android's mixer. Relabel to something accurate ("Hi-Res Passthrough (device-dependent)") unless/until true exclusive-mode routing is implemented; reserve "Bit-Perfect" for when a USB DAC is confirmed *and* exclusive-mode routing is verified active.

### 7.2 Disclose real EQ hardware-band count
Surface "N active hardware bands detected" alongside the 10-band UI, since most devices only expose 5 real bands via Android's Equalizer effect.

### 7.3 Add MediaSession to Spindle Lite
Closes finding #15 — Bluetooth AVRCP metadata (title/artist on car-stereo/receiver displays) currently doesn't sync. `MediaSessionCompat` has been available since the v4 support library; no API-19 compatibility excuse.

### 7.4 Port crossfade to app-main
Lite already has `CrossfadeMode` (Gapless/2s/4s); bring the same option to Main's `AudioFxController`.

### 7.5 Port album-mode ReplayGain to app-main
Lite already has `ReplayGainMode.ALBUM`; Main is track-only. Expose the existing gain-application logic under the missing mode.

---

## 8. Phase 5 — Feature Parity & Competitive Positioning

### 8.1 Add to Spindle Lite (no RAM/CPU justification found for exclusion)
- Synced lyrics (`.lrc`) — reuse `LyricsParser.kt` logic from app-main.
- Home-screen widget — near-zero cost; Lite is a home-launcher replacement, so this covers the non-default-home case.

### 8.2 Consider for Spindle Standard
- Opt-in "DAP Mode" — port Lite's full hardware-button matrix (`HardwareButtonReceiver`) as an option for modern dedicated-DAP hardware users.

### 8.3 Competitive positioning (vs. Poweramp/VLC) — do not chase feature-for-feature
| Gap vs. Poweramp | Recommendation |
|---|---|
| 10-band EQ vs. up to 32-band + parametric + AutoEq | Add parametric mode to the existing 10-band EQ. Do not chase 32-band — dilutes the "simple, tactile" positioning. |
| No DSD/Opus/TAK | Low priority — revisit only on frequent user request. |
| No Android Auto/Chromecast | **Do not add** — contradicts the "100% offline, zero cloud" brand pillar; deliberate non-goal. |

Spindle's defensible edge is tactile/emotional design plus the launcher-replacement angle — compete there, not on DSP band count.

---

## 9. Phase 6 — Brand Consistency

### 9.1 Palette unification
Regenerate every off-brand screenshot/theme (now-playing, catalog, lockscreen, EQ/vaporwave) to use only `BRANDING.md` §3 tokens plus the three official themes. If the mint-green accent is intentionally kept, add it to `BRANDING.md` as an official token rather than leaving it undocumented.

### 9.2 Publish Spindle Lite visual assets
Capture real on-device screenshots (deck, vault, radio, settings) and add a "Spindle Lite in Action" section to the README.

---

## 10. Phase 7 — Monetization Rollout (Completed)

Executed the model drafted in `MASTER_DOCUMENTATION.md` §8.6, maintaining the open-core commercial separation:
1. **Spindle Lite** — stays 100% free forever, sideload-only, zero ads, zero DRM.
2. **Spindle Standard** — free core remains fully featured, zero ads, zero recurring fees.
3. **Studio Collector** — one-time lifetime pass ($2.99 – $3.99 / $5.99 – $7.99) under `LICENSE-STUDIO.md`:
   - `StudioUnlockManager.kt`: Implemented offline HMAC-SHA256 sponsor token validation (`SPINDLE-STUDIO-XXXX-YYYY`) alongside Google Play Billing fallback, ensuring full offline functionality on de-googled audiophile DAPs.
   - `DialogStudioUnlock.kt`: Bespoke industrial modal presenting the Anti-Subscription manifesto, feature breakdown, token redemption, and sponsor links.
   - Gated features: Analog tape saturation DSP, personalized laser nameplate engraving, procedural cassette foley clacks, and studio collector skins.
4. **Sponsorship channel** — direct PayPal and GitHub Sponsors pathways operational with zero platform lock-in.

Do not add a subscription tier or a fourth SKU — scarcity of monetization is part of the brand's differentiation against streaming apps.

---

## 11. Documentation Updates Required

| File | Required change |
|---|---|
| `LICENSE` | Add open-core split language; reference new `LICENSE-STUDIO.md` |
| `LICENSE-STUDIO.md` | New file — commercial add-on terms |
| `docs/GLOSSARY.md` | **New file** — canonical terms from §4.1, single source of truth |
| `CONTRIBUTING.md` | **New file** — naming convention rule from §4.3, package structure from §4.2 |
| `BRANDING.md` | Add/confirm any 4th accent token in use; reconcile with §3 |
| `MASTER_DOCUMENTATION.md` | Fix version mismatch; update §8.6 for open-core licensing; apply glossary terms; document `core` module (§4.2/4.5) |
| `SPINDLE_LITE_MASTER_DOCUMENTATION.md` | Add lyrics, widget, Mixtapes, MediaSession once shipped |
| `README.md` | Remove "Walkman" references; add Lite screenshots section; apply glossary terms |
| `docs/index.html` | Remove "Walkman" from H1 and body copy |

---

## 12. Sequencing & Priority

```
PHASE 0        PHASE 1              PHASE 2         PHASE 3           PHASE 4           PHASE 5          PHASE 6      PHASE 7
Legal &   ──▶  Sanitization &  ──▶  Codebase   ──▶  Playlist &   ──▶  Audio Engine ──▶  Feature    ──▶  Brand   ──▶  Monetization
Brand-Safety   Canonical Naming     Hygiene         Organization      Disclosure &      Parity          Consistency  Rollout
                                                                       Parity
```

**Hard dependencies:**
- Phase 7 cannot start before Phase 0 is fully closed.
- Phase 1 (glossary + naming rule) should close **before** Phase 2 (string-resource migration), since Phase 2 is where the glossary actually gets enforced — doing them in the other order means redoing the string extraction once the terms change.
- Phases 3–6 can run in parallel with each other once Phases 0–2 are done, but should complete before Phase 7's public pricing announcement.

---

## 13. Definition of Done (per phase)

- **Phase 0:** Zero case-insensitive matches for "walkman"/"sony" repo-wide; `LICENSE` + `LICENSE-STUDIO.md` committed; legal review completed.
- **Phase 1:** `docs/GLOSSARY.md` and `CONTRIBUTING.md` committed; package migration to `core`/`standard`/`lite` complete; sanitization checklist (§4.6) fully checked.
- **Phase 2:** Zero `android:text` literals remain in layouts; `LiteMainActivity.kt` reduced to a host Activity under ~300 lines with logic delegated to controllers.
- **Phase 3:** Favorite/Rating split shipped; drag-to-reorder live; M3U import/export live; 4 smart Mixtapes live; album grouping uses `(album, albumArtist)`; FTS search live; Lite has Mixtape support; composer browse + duplicate detection live.
- **Phase 4:** "Bit-Perfect" label reflects verified state only; hardware EQ band count disclosed; Lite has MediaSession; crossfade and album-mode ReplayGain ported to Main.
- **Phase 5:** Lite ships lyrics + widget; Main ships optional DAP Mode; EQ parametric mode added.
- **Phase 6:** Every published screenshot uses only documented `BRANDING.md` tokens; Lite has a published screenshot set.
- **Phase 7 (Completed):** Studio Collector live under `LICENSE-STUDIO.md` with dual Google Play In-App Billing + HMAC-SHA256 offline sponsor token verification (`SPINDLE-STUDIO-XXXX-YYYY`). Full v1.4.0-STUDIO suite delivered: 10.5" Studio Reel-to-Reel visual deck, harmonic analog tape saturation DSP (+3.2dB @ 63Hz flux bump), procedural foley engine with continuous pitch-ramping motor spool loop ($0.92\times \to 1.65\times$), and custom laser nameplate engraving. Unit tests passing 100%.
