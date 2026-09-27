package com.hana.spindle.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class PlaylistItem(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val colorAccent: Int
)

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("""
        SELECT p.id, p.name, COUNT(ps.songId) as trackCount, p.colorAccent
        FROM playlists p
        LEFT JOIN playlist_songs ps ON p.id = ps.playlistId
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
    """)
    fun getAllPlaylists(): Flow<List<PlaylistItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.orderIndex ASC, s.title ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    fun getSongsForPlaylist(playlistId: Long): Flow<List<TrackEntity>> = getTracksForPlaylist(playlistId)

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getPlaylistTrackCount(playlistId: Long): Int

    @Query("UPDATE playlist_songs SET orderIndex = :newOrder WHERE playlistId = :playlistId AND songId = :trackId")
    suspend fun updateTrackOrder(playlistId: Long, trackId: Long, newOrder: Long)

    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, trackIds: List<Long>) {
        trackIds.forEachIndexed { index, trackId ->
            updateTrackOrder(playlistId, trackId, index.toLong())
        }
    }

    suspend fun addTrackToPlaylist(crossRef: PlaylistSongCrossRef) = addSongToPlaylist(crossRef)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = removeSongFromPlaylist(playlistId, trackId)
}
