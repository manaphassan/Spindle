# Contributing to Spindle

Thank you for contributing to **Spindle**! To maintain long-term code quality, brand consistency, and legal safety across all modules, all contributions must strictly adhere to the following architecture rules and conventions.

---

## 1. Modular Architecture & Package Namespaces

Spindle is partitioned into three discrete layers:

```
:core       -> com.hana.spindle.core.*
               Zero-Android-dependency pure Kotlin library.
               Shared audio DSP constants, AutoEq curves, and canonical glossary constants.

:app-main   -> com.hana.spindle.* (Standard Spindle Flagship)
               Primary modern DAP launcher & player for Android 8.0+ (API 26–35).
               Full Room database, Media3 gapless playback, AudioEffect DSP, skeuomorphic canvas rendering.

:app-lite   -> com.hana.spindle.lite.* (Spindle Lite)
               Ultra-lightweight DAP launcher optimized for vintage/low-RAM hardware (API 19–34).
               Zero-ORM SQLite, raw Stagefright MediaPlayer, zero background allocations.
```

### Module Boundary Rules:
1. **`core` has zero Android SDK dependencies.** Never import `android.*` packages in `:core`.
2. **`app-lite` has strict RAM and API compatibility limits (minSdk 19).** Do not introduce modern AndroidX libraries (Media3, Room, Navigation, Compose) into `app-lite`.
3. **`app-lite` is isolated.** All standard feature requests and improvements default to `app-main` unless `app-lite` is explicitly scoped.

---

## 2. Canonical Class-Naming Rules

1. **Role-First Class Naming:**
   Every class lives in exactly one module and must be named for **what it does**, without gratuitous prefixes or dialect noise.
   - Good: `TrackEntity`, `TrackDao`, `AudioEngine`, `MusicScanner`, `ThemeManager`
   - Bad: `SpindleSongEntity`, `MainAudioEngine`, `SongDbDao`

2. **Skin & Chassis Variant Suffix Pattern:**
   Custom views and renderers tied to a specific hardware chassis or aesthetic skin must follow the `{VariantName}{Role}` convention:
   - Good: `Metal81ChassisView` (`Metal81` variant + `ChassisView` role)
   - Good: `VintageSilverChassisView`, `StudioBlackChassisView`
   - Bad: `Wm2ChassisView` (trademark violation), `RedChassisView` (vague)

3. **`Lite*` Prefix Discipline:**
   Classes within `:app-lite` maintain the explicit `Lite*` prefix (`LiteMainActivity`, `LiteAudioEngine`, `LiteDbHelper`).
   - This convention disambiguates lightweight implementations from full implementations during stack traces and cross-module code reviews.
   - Do **not** mirror this by prefixing flagship classes with `Main*` or `Standard*` — flagship classes reside in the primary package namespace.

---

## 3. Canonical Vocabulary Enforcement

Always consult [docs/GLOSSARY.md](docs/GLOSSARY.md) before naming classes, variables, database columns, or UI string resources.

| Use | Do NOT Use | Context |
|---|---|---|
| `Track` | `Song`, `AudioFile` | Single audio item |
| `Vault` | `Catalog`, `Tape Vault` | Main library browsing screen |
| `Mixtape` | `Playlist` *(in UI)* | User-curated track lists |
| `Favorite` | `Star`, `Rating` (overloaded) | Binary boolean bookmark (`isFavorite`) |
| `Rating` | `Favorite` (overloaded) | 0–5 graded quality score (`rating`) |
| `Chassis` / `Skin` | `Cassette Theme` | Skeuomorphic hardware rendering |

---

## 4. Legal & Trademark Safeguards

Spindle respects third-party intellectual property.
1. **Never use third-party trademarks** (including "Sony", "Walkman", "WM-2", "Walkman Cassette", "SoundAlive") anywhere in the repository, including code comments, XML layout IDs, drawable assets, test fixtures, and documentation.
2. Pre-commit check:
   ```bash
   git grep -in "walkman"
   git grep -in "sony"
   git grep -in "wm-2"
   ```
   All checks must return zero matches (outside historical changelogs).

---

## 5. Coding Standards & Review Checklist

Before opening a pull request or merging changes:
- [ ] `./gradlew :app-main:testDebugUnitTest` passes.
- [ ] `./gradlew :app-main:assembleDebug` builds cleanly.
- [ ] All new UI strings are externalized in `strings.xml` (no hardcoded English strings in layout XML or Kotlin code).
- [ ] All layout preview placeholders use `tools:text` instead of `android:text`.
- [ ] Any shared constant added is defined in `:core` rather than duplicated across modules.
