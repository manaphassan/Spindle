package com.hana.spindle.data.db

/**
 * Projection class for aggregated album list views without loading full track entities.
 */
data class AlbumItem(
    val album: String,
    val artist: String,
    val trackCount: Int,
    val representativePath: String,
    val year: Int = 0,
    val format: String = "FLAC"
)
