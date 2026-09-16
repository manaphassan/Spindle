package com.hana.spindle.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.hana.spindle.data.db.SongDao
import com.hana.spindle.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ScanProgress(
    val isScanning: Boolean = false,
    val songsFound: Int = 0,
    val currentPath: String = ""
)

/**
 * High-performance, low-RAM storage crawler for offline music collections (128GB+ MicroSD cards).
 *
 * Utilizes a two-phase indexing pipeline:
 * Phase 1: Rapid MediaStore indexing (loads thousands of tracks in <1 second).
 * Phase 2: Direct POSIX directory crawler (discovers Hi-Res FLAC/DSD files missed by MediaStore).
 */
class MusicScanner(
    private val context: Context,
    private val songDao: SongDao
) {

    companion object {
        private const val TAG = "SpindleScanner"
        const val PREF_CUSTOM_MUSIC_PATH = "pref_custom_music_path"
    }

    private val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)

    fun getCustomMusicPath(): String? {
        return prefs.getString(PREF_CUSTOM_MUSIC_PATH, null)
    }

    fun setCustomMusicPath(path: String?) {
        if (path.isNullOrBlank()) {
            prefs.edit().remove(PREF_CUSTOM_MUSIC_PATH).apply()
        } else {
            prefs.edit().putString(PREF_CUSTOM_MUSIC_PATH, path).apply()
        }
    }

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val supportedExtensions = setOf(
        "flac", "wav", "aif", "aiff", "alac", "m4a", "mp3", "ogg", "opus", "dsf", "dff"
    )

    /**
     * Initiates a full scan across internal music storage and external MicroSD cards,
     * or scans only the custom configured directory if one was selected by the user.
     */
    suspend fun scanAll() = withContext(Dispatchers.IO) {
        if (_progress.value.isScanning) {
            Log.d(TAG, "Scan already in progress, skipping")
            return@withContext
        }

        val customPath = getCustomMusicPath()
        Log.d(TAG, "Starting music library scan (customPath=$customPath)...")
        _progress.value = ScanProgress(isScanning = true, songsFound = 0, currentPath = "Starting scan...")

        var totalFound = 0

        val customDir = if (!customPath.isNullOrBlank()) File(customPath) else null
        val isCustomScan = customDir != null && customDir.exists() && customDir.isDirectory && customDir.canRead()

        // Phase 1: Fast MediaStore Query (only if scanning all storage)
        if (!isCustomScan) {
            try {
                val mediaStoreSongs = scanMediaStore()
                if (mediaStoreSongs.isNotEmpty()) {
                    songDao.insertSongs(mediaStoreSongs)
                    totalFound += mediaStoreSongs.size
                    Log.d(TAG, "Phase 1: Loaded ${mediaStoreSongs.size} tracks from MediaStore")
                    _progress.value = ScanProgress(isScanning = true, songsFound = totalFound, currentPath = "MediaStore indexed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Phase 1 MediaStore scan", e)
            }
        }

        // Phase 2: Direct Storage Crawler (crawl custom directory or storage roots)
        val roots = if (isCustomScan) listOf(customDir!!) else findStorageRoots()
        Log.d(TAG, "Phase 2: Storage roots to crawl: ${roots.map { it.absolutePath }}")

        val batch = mutableListOf<SongEntity>()

        for (root in roots) {
            if (!root.exists() || !root.canRead()) {
                Log.w(TAG, "Root not accessible: ${root.absolutePath}")
                continue
            }
            crawlDirectory(root, batch) { count, path ->
                totalFound += count
                _progress.value = ScanProgress(
                    isScanning = true,
                    songsFound = totalFound,
                    currentPath = path
                )
            }
        }

        if (batch.isNotEmpty()) {
            songDao.insertSongs(batch)
            totalFound += batch.size
            batch.clear()
        }

        Log.d(TAG, "Music library scan completed! Total songs: $totalFound")
        _progress.value = ScanProgress(isScanning = false, songsFound = totalFound, currentPath = "Scan complete")
    }

    /**
     * Rapidly queries Android system MediaStore for music tracks across all storage volumes.
     */
    private suspend fun scanMediaStore(): List<SongEntity> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<SongEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                val file = File(path)
                if (!file.exists()) continue

                val duration = cursor.getLong(durationCol)
                if (duration <= 0) continue

                val title = cursor.getString(titleCol) ?: file.nameWithoutExtension
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val trackNumber = cursor.getInt(trackCol)
                val year = cursor.getInt(yearCol)
                val dateModified = cursor.getLong(dateModCol) * 1000L
                val format = file.extension.uppercase(Locale.ROOT)

                val bitrateKbps = if (duration > 0) ((file.length() * 8L) / duration).toInt() else 320
                val lrcFile = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
                val txtFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")

                songs.add(
                    SongEntity(
                        title = title.trim(),
                        artist = artist.trim(),
                        album = album.trim(),
                        durationMs = duration,
                        path = path,
                        trackNumber = trackNumber,
                        year = year,
                        bitDepth = if (format in setOf("FLAC", "WAV", "AIFF")) 24 else 16,
                        sampleRate = 44100,
                        fileFormat = format,
                        rating = 0,
                        dateModified = dateModified,
                        bitrateKbps = bitrateKbps,
                        hasLyrics = lrcFile.exists() || txtFile.exists()
                    )
                )
            }
        }
        return@withContext songs
    }

    private suspend fun crawlDirectory(
        dir: File,
        batch: MutableList<SongEntity>,
        onProgress: suspend (Int, String) -> Unit
    ) {
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                val name = file.name
                // Skip hidden folders and non-audio system directories
                if (!name.startsWith(".") &&
                    !name.equals("Android", ignoreCase = true) &&
                    !name.equals("DCIM", ignoreCase = true) &&
                    !name.equals("Pictures", ignoreCase = true) &&
                    !name.equals("Movies", ignoreCase = true)
                ) {
                    crawlDirectory(file, batch, onProgress)
                }
            } else {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext in supportedExtensions) {
                    val existing = songDao.getSongByPath(file.absolutePath)
                    // Skip if already indexed with matching timestamp (+- 2000ms)
                    if (existing == null || Math.abs(existing.dateModified - file.lastModified()) > 2000L) {
                        val parsed = TagParser.parseSong(file)
                        if (parsed != null) {
                            batch.add(parsed)

                            if (batch.size >= 50) {
                                songDao.insertSongs(batch)
                                onProgress(batch.size, file.parent ?: file.name)
                                batch.clear()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Identifies all mounted storage paths (Internal storage + MicroSD cards).
     * Deduplicates overlapping directory trees so subfolders aren't crawled twice.
     */
    private fun findStorageRoots(): List<File> {
        val candidates = mutableListOf<File>()

        // 1. Standard internal storage
        Environment.getExternalStorageDirectory()?.let { candidates.add(it) }
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { candidates.add(it) }

        // 2. MicroSD Card roots via getExternalFilesDirs
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        for (dir in externalDirs) {
            if (dir != null) {
                val path = dir.absolutePath
                if (path.contains("/Android/")) {
                    val sdRoot = File(path.substringBefore("/Android/"))
                    if (sdRoot.exists() && sdRoot.isDirectory && sdRoot.canRead()) {
                        candidates.add(sdRoot)
                    }
                }
            }
        }

        // 3. Directly inspect /storage subdirectories (e.g. /storage/000B-B400)
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.isDirectory) {
            storageRoot.listFiles()?.forEach { file ->
                if (file.isDirectory && file.canRead() &&
                    !file.name.equals("emulated", ignoreCase = true) &&
                    !file.name.equals("self", ignoreCase = true)
                ) {
                    candidates.add(file)
                }
            }
        }

        // Filter valid readable directories, sort by path length ascending
        val validCandidates = candidates
            .filter { it.exists() && it.isDirectory && it.canRead() }
            .distinctBy { it.canonicalPath }
            .sortedBy { it.canonicalPath.length }

        // Deduplicate child directories whose parents are already included
        val filteredRoots = mutableListOf<File>()
        for (candidate in validCandidates) {
            val candidatePath = candidate.canonicalPath
            val hasAncestor = filteredRoots.any { root ->
                candidatePath == root.canonicalPath ||
                candidatePath.startsWith(root.canonicalPath + File.separator)
            }
            if (!hasAncestor) {
                filteredRoots.add(candidate)
            }
        }

        return filteredRoots
    }
}
