package com.hana.spindle.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("""
        SELECT album, artist, COUNT(*) as trackCount, MIN(path) as representativePath
        FROM songs 
        GROUP BY album 
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getAlbums(): Flow<List<AlbumItem>>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist COLLATE NOCASE ASC")
    fun getArtists(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE rating > 0 ORDER BY rating DESC, title ASC")
    fun getRatedSongs(): Flow<List<SongEntity>>

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
