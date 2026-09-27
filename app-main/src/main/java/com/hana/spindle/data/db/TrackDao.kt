package com.hana.spindle.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for audio tracks in Spindle.
 * Conforms to canonical glossary (docs/GLOSSARY.md).
 */
@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY discNumber ASC, trackNumber ASC, title ASC")
    fun getTracksByAlbum(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, discNumber ASC, trackNumber ASC")
    fun getTracksByArtist(artist: String): Flow<List<TrackEntity>>

    @Query("""
        SELECT album, COALESCE(albumArtist, artist) as artist, COUNT(*) as trackCount, MIN(path) as representativePath, MAX(year) as year, MIN(fileFormat) as format
        FROM songs 
        GROUP BY album, COALESCE(albumArtist, artist)
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getAlbums(): Flow<List<AlbumItem>>

    @Query("""
        SELECT album, COALESCE(albumArtist, artist) as artist, COUNT(*) as trackCount, MIN(path) as representativePath, MAX(year) as year, MIN(fileFormat) as format
        FROM songs 
        WHERE artist = :artist OR albumArtist = :artist
        GROUP BY album, COALESCE(albumArtist, artist)
        ORDER BY year DESC, album COLLATE NOCASE ASC
    """)
    fun getAlbumsByArtist(artist: String): Flow<List<AlbumItem>>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist COLLATE NOCASE ASC")
    fun getArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre COLLATE NOCASE ASC")
    fun getGenres(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun getTracksByGenre(genre: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE rating > 0 ORDER BY rating DESC, title ASC")
    fun getRatedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE bitDepth >= 24 OR sampleRate > 48000 OR fileFormat IN ('FLAC', 'WAV', 'DSD', 'DSF', 'DFF', 'AIFF') ORDER BY sampleRate DESC, bitrateKbps DESC, title ASC")
    fun getHiResTracks(): Flow<List<TrackEntity>>

    // Smart Mixtapes queries (§6.4)
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAddedTracks(limit: Int = 100): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayedTracks(limit: Int = 100): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun getMostPlayedTracks(limit: Int = 100): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE playCount = 0 ORDER BY dateAdded DESC, title ASC LIMIT :limit")
    fun getNeverPlayedTracks(limit: Int = 100): Flow<List<TrackEntity>>

    // Composer field & browse queries (§6.8)
    @Query("SELECT DISTINCT composer FROM songs WHERE composer IS NOT NULL AND composer != '' ORDER BY composer COLLATE NOCASE ASC")
    fun getComposers(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE composer = :composer ORDER BY album ASC, discNumber ASC, trackNumber ASC, title ASC")
    fun getTracksByComposer(composer: String): Flow<List<TrackEntity>>

    // Duplicate detection (§6.9)
    @Query("""
        SELECT * FROM songs 
        WHERE title IN (
            SELECT title FROM songs 
            GROUP BY title, artist 
            HAVING COUNT(*) > 1
        )
        ORDER BY title COLLATE NOCASE ASC, artist COLLATE NOCASE ASC, durationMs ASC
    """)
    fun getDuplicateTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR composer LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    /**
     * High-speed SQLite FTS4 full-text search across title, artist, album, composer, albumArtist, and genre.
     * Sub-millisecond indexed queries (§6.6).
     */
    @Query("""
        SELECT songs.* FROM songs
        JOIN songs_fts ON songs.id = songs_fts.docid
        WHERE songs_fts MATCH :ftsQuery
        ORDER BY songs.title COLLATE NOCASE ASC
    """)
    fun searchTracksFts(ftsQuery: String): Flow<List<TrackEntity>>

    @Query("""
        SELECT songs.* FROM songs
        JOIN songs_fts ON songs.id = songs_fts.docid
        WHERE songs_fts MATCH :ftsQuery
        LIMIT :limit
    """)
    suspend fun searchTracksFtsList(ftsQuery: String, limit: Int = 20): List<TrackEntity>

    @Query("""
        SELECT DISTINCT songs.album as album, COALESCE(songs.albumArtist, songs.artist) as artist, COUNT(*) as trackCount, MIN(songs.path) as representativePath, MAX(songs.year) as year, MIN(songs.fileFormat) as format
        FROM songs
        JOIN songs_fts ON songs.id = songs_fts.docid
        WHERE songs_fts MATCH :ftsQuery
        GROUP BY songs.album, COALESCE(songs.albumArtist, songs.artist)
        ORDER BY songs.album COLLATE NOCASE ASC
        LIMIT :limit
    """)
    suspend fun searchAlbumsFtsList(ftsQuery: String, limit: Int = 10): List<AlbumItem>

    @Query("""
        SELECT DISTINCT songs.artist FROM songs
        JOIN songs_fts ON songs.id = songs_fts.docid
        WHERE songs_fts MATCH :ftsQuery
        ORDER BY songs.artist COLLATE NOCASE ASC
        LIMIT :limit
    """)
    suspend fun searchArtistsFtsList(ftsQuery: String, limit: Int = 10): List<String>

    @Query("""
        SELECT DISTINCT songs.composer FROM songs
        JOIN songs_fts ON songs.id = songs_fts.docid
        WHERE songs.composer IS NOT NULL AND songs.composer != '' AND songs_fts MATCH :ftsQuery
        ORDER BY songs.composer COLLATE NOCASE ASC
        LIMIT :limit
    """)
    suspend fun searchComposersFtsList(ftsQuery: String, limit: Int = 10): List<String>

    @Query("INSERT INTO songs_fts(songs_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()

    @Query("SELECT * FROM songs WHERE path = :path LIMIT 1")
    suspend fun getTrackByPath(path: String): TrackEntity?

    @Query("SELECT * FROM songs WHERE path LIKE :prefix || '%' ORDER BY path ASC")
    suspend fun getTracksByPathPrefix(prefix: String): List<TrackEntity>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavorite(trackId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET rating = :rating WHERE id = :trackId")
    suspend fun updateRating(trackId: Long, rating: Int)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: Long, playedAt: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album, year = :year, trackNumber = :trackNumber, genre = :genre, composer = :composer WHERE id = :id")
    suspend fun updateTrackMetadata(id: Long, title: String, artist: String, album: String, year: Int, trackNumber: Int, genre: String?, composer: String? = null)

    @Query("DELETE FROM songs WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getTrackCount(): Int

    // --- Backward-Compatibility Aliases ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<TrackEntity>) = insertTracks(songs)

    fun getAllSongs(): Flow<List<TrackEntity>> = getAllTracks()

    fun getSongsByAlbum(album: String): Flow<List<TrackEntity>> = getTracksByAlbum(album)

    fun getSongsByArtist(artist: String): Flow<List<TrackEntity>> = getTracksByArtist(artist)

    fun getSongsByGenre(genre: String): Flow<List<TrackEntity>> = getTracksByGenre(genre)

    fun getHiResSongs(): Flow<List<TrackEntity>> = getHiResTracks()

    fun getRatedSongs(): Flow<List<TrackEntity>> = getRatedTracks()

    fun searchSongs(query: String): Flow<List<TrackEntity>> = searchTracks(query)

    suspend fun getSongByPath(path: String): TrackEntity? = getTrackByPath(path)

    suspend fun updateSongMetadata(id: Long, title: String, artist: String, album: String, year: Int, trackNumber: Int, genre: String?, composer: String? = null) =
        updateTrackMetadata(id, title, artist, album, year, trackNumber, genre, composer)

    suspend fun getSongCount(): Int = getTrackCount()
}
