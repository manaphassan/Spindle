package com.hana.spindle.core

/**
 * Spindle Canonical Glossary Authority.
 *
 * Single programmatic source of truth for canonical terms used across the codebase,
 * database schema adapters, UI labels, and telemetry.
 *
 * Reference: docs/GLOSSARY.md
 */
object SpindleGlossary {

    /**
     * Canonical term for a single playable audio item/file.
     * Replaces legacy 'Song' and 'AudioFile'.
     */
    const val TERM_TRACK = "Track"
    const val TERM_TRACKS = "Tracks"

    /**
     * Canonical term for the music library browsing and filtering screen.
     * Replaces legacy 'Catalog', 'Tape Vault', and 'Cassette Vault'.
     */
    const val TERM_VAULT = "Vault"

    /**
     * Canonical term for user-curated ordered lists of tracks.
     * Replaces 'Playlist' in all user-facing copy.
     */
    const val TERM_MIXTAPE = "Mixtape"
    const val TERM_MIXTAPES = "Mixtapes"

    /**
     * Canonical term for a binary bookmark flag ("keep this handy").
     * Decoupled from 5-star quality rating.
     */
    const val TERM_FAVORITE = "Favorite"
    const val TERM_FAVORITES = "Favorites"

    /**
     * Canonical term for 0..5 graded star evaluation.
     */
    const val TERM_RATING = "Rating"

    /**
     * Canonical term for vintage deck hardware rendering.
     */
    const val TERM_CHASSIS = "Chassis"

    /**
     * Canonical term for cassette shell faceplate styling.
     */
    const val TERM_SKIN = "Skin"

    /**
     * Canonical term for high-resolution HAL audio passthrough.
     */
    const val TERM_HI_RES_PASSTHROUGH = "Hi-Res Passthrough"

    /**
     * Canonical transport verbs and actions (§8.2).
     */
    const val ACTION_PLAY = "Play"
    const val ACTION_PAUSE = "Pause"
    const val ACTION_STOP = "Stop"
    const val ACTION_EJECT = "Eject"
    const val ACTION_PREV = "Previous"
    const val ACTION_NEXT = "Next"
    const val ACTION_REW = "Rewind"
    const val ACTION_FF = "Fast Forward"

    /**
     * Canonical playback queue terms.
     */
    const val TERM_QUEUE = "Queue"
    const val ACTION_PLAY_NEXT = "Play Next"
    const val ACTION_ADD_TO_QUEUE = "Add to Queue"

    /**
     * Canonical track transition terms.
     */
    const val TERM_GAPLESS = "Gapless"
    const val TERM_FADE = "Fade"

    /**
     * Standard DAP seek & transport threshold constants.
     */
    const val SEEK_STEP_HOLD_MS = 2500L
    const val SEEK_STEP_MANUAL_MS = 5000L
    const val SEEK_STEP_DEFAULT_MS = 10000L
    const val PREVIOUS_RESTART_THRESHOLD_MS = 3000L

    /**
     * Canonical transport glyphs for vintage DAP chassis buttons (§8.2).
     */
    const val GLYPH_PREV = "|<<"
    const val GLYPH_PLAY = "> PLAY"
    const val GLYPH_PAUSE = "|| PAUSE"
    const val GLYPH_NEXT = ">>|"
}
