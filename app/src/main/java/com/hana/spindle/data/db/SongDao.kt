package com.hana.spindle.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY discNumber ASC, trackNumber ASC, title ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, discNumber ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("""
        SELECT album, artist, COUNT(*) as trackCount, MIN(path) as representativePath, MAX(year) as year, MIN(fileFormat) as format
        FROM songs 
        GROUP BY album 
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getAlbums(): Flow<List<AlbumItem>>

    @Query("""
        SELECT album, artist, COUNT(*) as trackCount, MIN(path) as representativePath, MAX(year) as year, MIN(fileFormat) as format
        FROM songs 
        WHERE artist = :artist
        GROUP BY album 
        ORDER BY year DESC, album COLLATE NOCASE ASC
    """)
    fun getAlbumsByArtist(artist: String): Flow<List<AlbumItem>>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist COLLATE NOCASE ASC")
    fun getArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre COLLATE NOCASE ASC")
    fun getGenres(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE rating > 0 ORDER BY rating DESC, title ASC")
    fun getRatedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE bitDepth >= 24 OR sampleRate > 48000 OR fileFormat IN ('FLAC', 'WAV', 'DSD', 'DSF', 'DFF', 'AIFF') ORDER BY sampleRate DESC, bitrateKbps DESC, title ASC")
    fun getHiResSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE path = :path LIMIT 1")
    suspend fun getSongByPath(path: String): SongEntity?

    @Query("UPDATE songs SET rating = :rating WHERE id = :songId")
    suspend fun updateRating(songId: Long, rating: Int)

    @Query("DELETE FROM songs WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int
}
