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
}
