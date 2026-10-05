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
) {
    val isMixtape: Boolean get() = format == FORMAT_MIXTAPE || format == FORMAT_SMART_MIXTAPE
    val isSmartMixtape: Boolean get() = format == FORMAT_SMART_MIXTAPE

    companion object {
        const val FORMAT_MIXTAPE = "MIXTAPE"
        const val FORMAT_SMART_MIXTAPE = "SMART_MIXTAPE"
    }
}
