package com.hana.spindle.data

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
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
 * Standard M3U / M3U8 playlist import, export, and cross-DAP synchronization manager.
 * Implements Extended M3U directives (#EXTM3U, #EXTINF, #PLAYLIST) with portable
 * relative path support for swapping MicroSD cards between different audiophile DAPs.
 */
object M3uManager {

    private const val TAG = "SpindleM3uManager"

    data class BulkExportResult(
        val playlistsExported: Int,
        val tracksExported: Int,
        val destinationDir: File
    )

    data class BulkSyncResult(
        val playlistsFound: Int,
        val playlistsImported: Int,
        val totalTracksMapped: Int,
        val playlistNames: List<String>
    )

    data class ImportResult(
        val playlistId: Long,
        val playlistName: String,
        val tracksCount: Int,
        val tapeCapacityMinutes: Int? = null,
        val splitIndex: Int? = null,
        val tapeFormulation: String? = null
    )

    /**
     * Exports a single mixtape to standard Extended M3U8 format with portable relative paths
     * and Spindle extended cassette tape metadata directives (#SPINDLE-TAPE-*).
     */
    suspend fun exportMixtape(
        file: File,
        mixtapeName: String,
        tracks: List<TrackEntity>,
        useRelativePaths: Boolean = true,
        tapeCapacity: MixtapePartition.TapeCapacity = MixtapePartition.TapeCapacity.DEFAULT,
        tapeFormulation: String = "TYPE_I_FERRIC",
        splitIndex: Int = -1
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            file.parentFile?.mkdirs()
            val baseDir = file.parentFile ?: File(".")
            val effectiveSplit = if (splitIndex in 1 until tracks.size) {
                splitIndex
            } else {
                MixtapePartition.partition(tracks, tapeCapacity).splitIndex
            }

            file.printWriter(Charsets.UTF_8).use { writer ->
                writer.println("#EXTM3U")
                writer.println("#PLAYLIST:$mixtapeName")
                writer.println("#SPINDLE-TAPE-NAME:$mixtapeName")
                writer.println("#SPINDLE-TAPE-FORMULATION:$tapeFormulation")
                writer.println("#SPINDLE-TAPE-CAPACITY:${tapeCapacity.totalMinutes}")
                writer.println("#SPINDLE-TAPE-SPLIT-INDEX:$effectiveSplit")
                for (i in tracks.indices) {
                    val track = tracks[i]
                    if (i == 0) {
                        writer.println("#SPINDLE-TAPE-SIDE:A")
                    } else if (i == effectiveSplit) {
                        writer.println("#SPINDLE-TAPE-SIDE:B")
                    }
                    val durationSec = TimeUnit.MILLISECONDS.toSeconds(track.durationMs)
                    val extInfArtist = track.artist.replace(',', ' ')
                    val extInfTitle = track.title.replace(',', ' ')
                    writer.println("#EXTINF:$durationSec,$extInfArtist - $extInfTitle")

                    val pathString = if (useRelativePaths) {
                        calculateRelativePath(baseDir, File(track.path))
                    } else {
                        track.path.replace('\\', '/')
                    }
                    writer.println(pathString)
                }
            }
            Log.d(TAG, "Exported mixtape '$mixtapeName' (${tracks.size} tracks) to: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export mixtape '$mixtapeName' to: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * Exports all user mixtapes in Spindle's database to the specified or default MicroSD playlists directory.
     */
    suspend fun exportAllMixtapes(
        context: Context,
        database: SpindleDatabase,
        targetDir: File? = null
    ): BulkExportResult = withContext(Dispatchers.IO) {
        val destDir = targetDir ?: getDefaultPlaylistDirectory()
        destDir.mkdirs()

        val playlists = database.playlistDao().getAllPlaylists().first()
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        var exportedPlaylists = 0
        var totalTracks = 0

        for (playlist in playlists) {
            val tracks = database.playlistDao().getTracksForPlaylist(playlist.id).first()
            if (tracks.isEmpty()) continue

            val safeName = playlist.name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
            val targetFile = File(destDir, "$safeName.m3u8")

            val capMins = prefs.getInt("mixtape_${playlist.id}_capacity", 90)
            val cap = MixtapePartition.TapeCapacity.fromMinutes(capMins)
            val splitIdx = prefs.getInt("mixtape_${playlist.id}_split_index", -1)
            val formulation = prefs.getString("mixtape_${playlist.id}_formulation", "TYPE_I_FERRIC") ?: "TYPE_I_FERRIC"

            if (exportMixtape(targetFile, playlist.name, tracks, useRelativePaths = true, tapeCapacity = cap, tapeFormulation = formulation, splitIndex = splitIdx)) {
                exportedPlaylists++
                totalTracks += tracks.size
            }
        }

        BulkExportResult(exportedPlaylists, totalTracks, destDir)
    }

    /**
     * Imports an M3U / M3U8 playlist file using multi-tiered track resolution.
     * Matches tracks by:
     * 1. Direct path / Relative path from playlist directory
     * 2. Path suffix (ignoring different volume mount points across DAPs)
     * 3. Case-insensitive filename
     * 4. Metadata matching (Artist + Title from #EXTINF)
     */
    suspend fun importMixtape(
        file: File,
        database: SpindleDatabase,
        context: Context? = null
    ): ImportResult? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) return@withContext null

        val lines = file.readLines(Charsets.UTF_8)

        data class ParsedEntry(
            val rawPath: String,
            val artistHint: String? = null,
            val titleHint: String? = null
        )

        val entries = mutableListOf<ParsedEntry>()
        var lastArtist: String? = null
        var lastTitle: String? = null
        var tapeName: String? = null
        var tapeFormulation: String? = null
        var tapeCapacityMinutes: Int? = null
        var tapeSplitIndex: Int? = null
        var sideBSplitIndex: Int? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#SPINDLE-TAPE-NAME:", ignoreCase = true)) {
                val tagVal = trimmed.substringAfter("#SPINDLE-TAPE-NAME:").trim()
                if (tagVal.isNotBlank()) tapeName = tagVal
            } else if (trimmed.startsWith("#SPINDLE-TAPE-FORMULATION:", ignoreCase = true)) {
                val tagVal = trimmed.substringAfter("#SPINDLE-TAPE-FORMULATION:").trim()
                if (tagVal.isNotBlank()) tapeFormulation = tagVal
            } else if (trimmed.startsWith("#SPINDLE-TAPE-CAPACITY:", ignoreCase = true)) {
                val tagVal = trimmed.substringAfter("#SPINDLE-TAPE-CAPACITY:").trim()
                tapeCapacityMinutes = tagVal.toIntOrNull()
            } else if (trimmed.startsWith("#SPINDLE-TAPE-SPLIT-INDEX:", ignoreCase = true)) {
                val tagVal = trimmed.substringAfter("#SPINDLE-TAPE-SPLIT-INDEX:").trim()
                tapeSplitIndex = tagVal.toIntOrNull()
            } else if (trimmed.equals("#SPINDLE-TAPE-SIDE:B", ignoreCase = true)) {
                if (sideBSplitIndex == null) {
                    sideBSplitIndex = entries.size
                }
            } else if (trimmed.startsWith("#PLAYLIST:", ignoreCase = true)) {
                val tagVal = trimmed.substringAfter("#PLAYLIST:").trim()
                if (tagVal.isNotBlank() && tapeName == null) tapeName = tagVal
            } else if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                // Parse #EXTINF:seconds,Artist - Title
                val metadata = trimmed.substringAfter("#EXTINF:", "").substringAfter(",", "")
                if (metadata.contains(" - ")) {
                    lastArtist = metadata.substringBefore(" - ").trim()
                    lastTitle = metadata.substringAfter(" - ").trim()
                } else if (metadata.isNotBlank()) {
                    lastTitle = metadata.trim()
                }
            } else if (!trimmed.startsWith("#")) {
                entries.add(ParsedEntry(trimmed, lastArtist, lastTitle))
                lastArtist = null
                lastTitle = null
            }
        }

        val name = tapeName ?: file.nameWithoutExtension.ifBlank { "Imported Mixtape" }

        if (entries.isEmpty()) return@withContext null

        val allTracks = database.trackDao().getAllTracks().first()
        if (allTracks.isEmpty()) return@withContext null

        val trackMapByPath = allTracks.associateBy { it.path.replace('\\', '/') }
        val trackMapByFilename = allTracks.groupBy { File(it.path).name.lowercase() }
        val trackMapByMetadata = allTracks.associateBy { "${it.artist.lowercase()} - ${it.title.lowercase()}" }

        val matchedTrackIds = mutableListOf<Long>()
        val playlistBaseDir = file.parentFile ?: File(".")

        for (entry in entries) {
            var matchedTrack: TrackEntity? = null
            val cleanPath = entry.rawPath.replace('\\', '/')

            // 1. Direct or resolved file path
            val resolvedFile = if (File(cleanPath).isAbsolute) {
                File(cleanPath)
            } else {
                File(playlistBaseDir, cleanPath)
            }
            matchedTrack = trackMapByPath[resolvedFile.absolutePath.replace('\\', '/')]

            // 2. Relative suffix match (e.g. "Artist/Album/01.flac")
            if (matchedTrack == null) {
                val parts = cleanPath.split('/')
                val suffix = if (parts.size >= 2) "${parts[parts.size - 2]}/${parts.last()}" else parts.last()
                matchedTrack = allTracks.firstOrNull { it.path.replace('\\', '/').endsWith(suffix, ignoreCase = true) }
            }

            // 3. Filename match
            if (matchedTrack == null) {
                val filename = File(cleanPath).name.lowercase()
                matchedTrack = trackMapByFilename[filename]?.firstOrNull()
            }

            // 4. Metadata match
            if (matchedTrack == null && entry.titleHint != null) {
                val key = if (entry.artistHint != null) {
                    "${entry.artistHint.lowercase()} - ${entry.titleHint.lowercase()}"
                } else {
                    entry.titleHint.lowercase()
                }
                matchedTrack = trackMapByMetadata[key] ?: allTracks.firstOrNull {
                    it.title.equals(entry.titleHint, ignoreCase = true)
                }
            }

            if (matchedTrack != null) {
                matchedTrackIds.add(matchedTrack.id)
            }
        }

        if (matchedTrackIds.isEmpty()) return@withContext null

        // Check if a playlist with this name already exists
        val existing = database.playlistDao().getPlaylistByName(name)
        val playlistId = existing?.id ?: database.playlistDao().insertPlaylist(
            PlaylistEntity(
                name = name,
                createdAt = System.currentTimeMillis()
            )
        )

        // Insert tracks
        matchedTrackIds.distinct().forEachIndexed { index, trackId ->
            database.playlistDao().addSongToPlaylist(
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = trackId,
                    orderIndex = index.toLong()
                )
            )
        }

        val effectiveSplit = tapeSplitIndex ?: sideBSplitIndex
        if (context != null) {
            val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            tapeCapacityMinutes?.let { editor.putInt("mixtape_${playlistId}_capacity", it) }
            effectiveSplit?.let { editor.putInt("mixtape_${playlistId}_split_index", it) }
            tapeFormulation?.let { editor.putString("mixtape_${playlistId}_formulation", it) }
            editor.apply()
        }

        Log.d(TAG, "Imported mixtape '$name' with ${matchedTrackIds.size} tracks from ${file.name} (Split: $effectiveSplit, Cap: $tapeCapacityMinutes m, Form: $tapeFormulation)")
        ImportResult(playlistId, name, matchedTrackIds.size, tapeCapacityMinutes, effectiveSplit, tapeFormulation)
    }

    /**
     * Scans all mounted storage volumes, MicroSD cards, and playlists directories,
     * bulk-importing any valid .m3u and .m3u8 playlists.
     */
    suspend fun syncAllMixtapesFromStorage(
        context: Context,
        database: SpindleDatabase
    ): BulkSyncResult = withContext(Dispatchers.IO) {
        val playlistFiles = findPlaylistFiles(context)
        var importedCount = 0
        var totalTracks = 0
        val importedNames = mutableListOf<String>()

        for (file in playlistFiles) {
            val result = importMixtape(file, database, context)
            if (result != null && result.tracksCount > 0) {
                importedCount++
                totalTracks += result.tracksCount
                importedNames.add(result.playlistName)
            }
        }

        BulkSyncResult(
            playlistsFound = playlistFiles.size,
            playlistsImported = importedCount,
            totalTracksMapped = totalTracks,
            playlistNames = importedNames
        )
    }

    /**
     * Recursively locates all M3U and M3U8 files across all storage volumes.
     */
    fun findPlaylistFiles(context: Context): List<File> {
        val searchDirs = mutableListOf<File>()

        // 1. Default Playlist Directory
        getDefaultPlaylistDirectory().let { searchDirs.add(it) }

        // 2. Public Music & Downloads
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { searchDirs.add(it) }
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { searchDirs.add(it) }

        // 3. External Files / MicroSD roots
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        for (dir in externalDirs) {
            if (dir != null) {
                val path = dir.absolutePath
                if (path.contains("/Android/")) {
                    val sdRoot = File(path.substringBefore("/Android/"))
                    if (sdRoot.exists() && sdRoot.isDirectory && sdRoot.canRead()) {
                        searchDirs.add(File(sdRoot, "Playlists"))
                        searchDirs.add(File(sdRoot, "Music"))
                        searchDirs.add(sdRoot)
                    }
                }
            }
        }

        // 4. Directly inspect /storage subdirectories
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.isDirectory) {
            storageRoot.listFiles()?.forEach { file ->
                if (file.isDirectory && file.canRead() &&
                    !file.name.equals("emulated", ignoreCase = true) &&
                    !file.name.equals("self", ignoreCase = true)
                ) {
                    searchDirs.add(File(file, "Playlists"))
                    searchDirs.add(File(file, "Music"))
                    searchDirs.add(file)
                }
            }
        }

        val found = mutableListOf<File>()
        val visitedDirs = mutableSetOf<String>()

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.canRead() || !visitedDirs.add(dir.canonicalPath)) continue
            dir.listFiles()?.forEach { file ->
                if (file.isFile && (file.extension.equals("m3u8", ignoreCase = true) || file.extension.equals("m3u", ignoreCase = true))) {
                    found.add(file)
                }
            }
        }

        return found.distinctBy { it.canonicalPath }
    }

    /**
     * Computes a relative path string with forward slashes from a base directory to a target file.
     * Falls back to absolute path if on different storage volumes.
     */
    fun calculateRelativePath(baseDir: File, targetFile: File): String {
        return try {
            val base = baseDir.canonicalFile
            val target = targetFile.canonicalFile
            target.relativeTo(base).path.replace('\\', '/')
        } catch (e: Exception) {
            targetFile.absolutePath.replace('\\', '/')
        }
    }

    /**
     * Returns the recommended directory for saving exported playlists.
     */
    fun getDefaultPlaylistDirectory(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val playlistsDir = File(musicDir, "Playlists")
        if (!playlistsDir.exists()) {
            playlistsDir.mkdirs()
        }
        return if (playlistsDir.canWrite()) playlistsDir else musicDir
    }
}
