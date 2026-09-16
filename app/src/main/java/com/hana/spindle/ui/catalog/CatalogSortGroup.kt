package com.hana.spindle.ui.catalog

enum class TrackSortOrder(val displayName: String) {
    TITLE_ASC("Title (A–Z)"),
    TITLE_DESC("Title (Z–A)"),
    ARTIST_ASC("Artist (A–Z)"),
    ALBUM_ASC("Album (A–Z)"),
    TRACK_NUMBER("Track Number"),
    YEAR_DESC("Year (Newest)"),
    YEAR_ASC("Year (Oldest)"),
    BITRATE_DESC("Bitrate / Hi-Res Quality"),
    DURATION_DESC("Duration (Longest)"),
    DATE_MODIFIED_DESC("Date Added (Recent)")
}

enum class AlbumSortOrder(val displayName: String) {
    TITLE_ASC("Album (A–Z)"),
    TITLE_DESC("Album (Z–A)"),
    ARTIST_ASC("Artist (A–Z)"),
    YEAR_DESC("Year (Newest)"),
    YEAR_ASC("Year (Oldest)"),
    TRACK_COUNT_DESC("Track Count (Most)"),
    TRACK_COUNT_ASC("Track Count (Fewest)")
}

enum class GroupByMode(val displayName: String) {
    NONE("None (Flat List)"),
    ARTIST("Group by Artist"),
    DECADE("Group by Decade / Year"),
    INITIAL_LETTER("Group by Initial (A–Z)"),
    FORMAT("Group by Audio Format (FLAC, WAV, MP3)")
}

enum class CatalogFilterChip(val displayName: String) {
    ALL("ALL"),
    HI_RES("HI-RES"),
    LOSSLESS("LOSSLESS"),
    FLAC("FLAC"),
    WAV("WAV"),
    MP3("MP3"),
    RATED("★ RATED")
}
