package com.hana.spindle.data

import com.hana.spindle.data.db.PlaylistEntity
import com.hana.spindle.data.db.PlaylistSongCrossRef
import com.hana.spindle.data.db.SpindleDatabase
import com.hana.spindle.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Standard M3U / M3U8 playlist import and export manager for portable mixtape exchange.
 * Implements Extended M3U directives (#EXTM3U, #EXTINF, #PLAYLIST).
 */
object M3uManager {

    /**
     * Exports a playlist/mixtape to standard Extended M3U8 format.
     */
    suspend fun exportMixtape(
        file: File,
        mixtapeName: String,
        tracks: List<TrackEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            file.parentFile?.mkdirs()
            file.printWriter(Charsets.UTF_8).use { writer ->
                writer.println("#EXTM3U")
                writer.println("#PLAYLIST:$mixtapeName")
                for (track in tracks) {
                    val durationSec = TimeUnit.MILLISECONDS.toSeconds(track.durationMs)
                    writer.println("#EXTINF:$durationSec,${track.artist} - ${track.title}")
                    writer.println(track.path)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Imports an M3U / M3U8 playlist file, matching tracks by path.
     * Inserts a new Mixtape entry in the database.
     */
    suspend fun importMixtape(
        file: File,
        database: SpindleDatabase
    ): Long? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) return@withContext null

        val name = file.nameWithoutExtension.ifBlank { "Imported Mixtape" }
        val lines = file.readLines(Charsets.UTF_8)
        val trackPaths = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val targetFile = if (File(trimmed).isAbsolute) {
                File(trimmed)
            } else {
                File(file.parentFile, trimmed)
            }
            trackPaths.add(targetFile.absolutePath)
        }

        if (trackPaths.isEmpty()) return@withContext null

        val playlistId = database.playlistDao().insertPlaylist(
            PlaylistEntity(
                name = name,
                createdAt = System.currentTimeMillis()
            )
        )

        val allTracks = database.trackDao().getAllTracks().first()
        val trackMapByPath = allTracks.associateBy { it.path }
        val trackMapByFilename = allTracks.associateBy { File(it.path).name.lowercase() }

        var addedCount = 0
        trackPaths.forEachIndexed { index, path ->
            val track = trackMapByPath[path] ?: trackMapByFilename[File(path).name.lowercase()]
            if (track != null) {
                database.playlistDao().addSongToPlaylist(
                    PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = track.id,
                        orderIndex = index.toLong()
                    )
                )
                addedCount++
            }
        }

        if (addedCount == 0) {
            database.playlistDao().deletePlaylist(playlistId)
            null
        } else {
            playlistId
        }
    }

    /**
     * Returns the recommended directory for saving exported playlists.
     */
    fun getDefaultPlaylistDirectory(): File {
        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        val playlistsDir = File(musicDir, "Playlists")
        if (!playlistsDir.exists()) {
            playlistsDir.mkdirs()
        }
        return if (playlistsDir.canWrite()) playlistsDir else musicDir
    }
}
