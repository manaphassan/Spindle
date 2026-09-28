package com.hana.spindle.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.hana.spindle.data.db.TrackDao
import com.hana.spindle.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ScanProgress(
    val isScanning: Boolean = false,
    val tracksFound: Int = 0,
    val currentPath: String = ""
) {
    val songsFound: Int get() = tracksFound
}

/**
 * High-performance, low-RAM storage crawler for offline music collections (128GB+ MicroSD cards).
 *
 * Utilizes a two-phase indexing pipeline:
 * Phase 1: Rapid MediaStore indexing (loads thousands of tracks in <1 second).
 * Phase 2: Direct POSIX directory crawler (discovers Hi-Res FLAC/DSD files missed by MediaStore).
 */
class MusicScanner(
    private val context: Context,
    private val trackDao: TrackDao
) {
    val songDao: TrackDao get() = trackDao

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
        "flac", "wav", "aif", "aiff", "alac", "m4a", "mp3", "ogg", "opus", "dsf", "dff", "ape", "wv"
    )

    /**
     * Initiates a full scan across internal music storage and external MicroSD cards,
     * or scans only the custom configured directory if one was selected by the user.
     *
     * Utilizes .spindle_catalog.bin fast index cache when available for <50ms instant library loading.
     */
    suspend fun scanAll(forceFullScan: Boolean = false) = withContext(Dispatchers.IO) {
        if (_progress.value.isScanning) {
            Log.d(TAG, "Scan already in progress, skipping")
            return@withContext
        }

        val customPath = getCustomMusicPath()
        Log.d(TAG, "Starting music library scan (customPath=$customPath, forceFullScan=$forceFullScan)...")
        _progress.value = ScanProgress(isScanning = true, tracksFound = 0, currentPath = "Starting scan...")

        var totalFound = 0

        val customDir = if (!customPath.isNullOrBlank()) File(customPath) else null
        val isCustomScan = customDir != null && customDir.exists() && customDir.isDirectory && customDir.canRead()

        // Phase 1: Fast MediaStore Query (only if scanning all storage and no full force requested)
        if (!isCustomScan && !forceFullScan) {
            try {
                val mediaStoreTracks = scanMediaStore()
                if (mediaStoreTracks.isNotEmpty()) {
                    trackDao.insertTracks(mediaStoreTracks)
                    totalFound += mediaStoreTracks.size
                    Log.d(TAG, "Phase 1: Loaded ${mediaStoreTracks.size} tracks from MediaStore")
                    _progress.value = ScanProgress(isScanning = true, tracksFound = totalFound, currentPath = "MediaStore indexed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Phase 1 MediaStore scan", e)
            }
        }

        // Phase 2: Storage Crawler with Instant Binary Index Caching
        val roots = if (isCustomScan) listOf(customDir!!) else findStorageRoots()
        Log.d(TAG, "Phase 2: Storage roots to crawl: ${roots.map { it.absolutePath }}")

        val batch = mutableListOf<TrackEntity>()

        for (root in roots) {
            if (!root.exists() || !root.canRead()) {
                Log.w(TAG, "Root not accessible: ${root.absolutePath}")
                continue
            }

            var usedCache = false
            if (!forceFullScan) {
                val cached = CatalogCacheManager.readCache(root, context)
                if (cached != null && CatalogCacheManager.isCacheValid(cached, root)) {
                    Log.d(TAG, "Fast Index Cache hit for ${root.name}: loaded ${cached.tracks.size} tracks in <50ms")
                    trackDao.insertTracks(cached.tracks)
                    totalFound += cached.tracks.size
                    _progress.value = ScanProgress(
                        isScanning = true,
                        tracksFound = totalFound,
                        currentPath = "⚡ Fast Cache: ${root.name} (${cached.tracks.size} tracks)"
                    )
                    usedCache = true

                    // Incremental scan for newly added/modified tracks since cache creation
                    val newBatch = mutableListOf<TrackEntity>()
                    crawlDirectoryIncremental(root, cached.timestamp, newBatch) { count, path ->
                        totalFound += count
                        _progress.value = ScanProgress(
                            isScanning = true,
                            tracksFound = totalFound,
                            currentPath = path
                        )
                    }
                    if (newBatch.isNotEmpty()) {
                        trackDao.insertTracks(newBatch)
                        totalFound += newBatch.size
                        newBatch.clear()
                        // Update cache with fresh tracks
                        val allTracksForRoot = trackDao.getTracksByPathPrefix(root.canonicalPath)
                        CatalogCacheManager.writeCache(root, allTracksForRoot, context)
                    }
                }
            }

            if (!usedCache) {
                crawlDirectory(root, batch) { count, path ->
                    totalFound += count
                    _progress.value = ScanProgress(
                        isScanning = true,
                        tracksFound = totalFound,
                        currentPath = path
                    )
                }

                if (batch.isNotEmpty()) {
                    trackDao.insertTracks(batch)
                    totalFound += batch.size
                    batch.clear()
                }

                // Write / update instant binary index cache
                try {
                    val rootTracks = trackDao.getTracksByPathPrefix(root.canonicalPath)
                    if (rootTracks.isNotEmpty()) {
                        CatalogCacheManager.writeCache(root, rootTracks, context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed writing catalog cache for ${root.name}", e)
                }
            }
        }

        Log.d(TAG, "Music library scan completed! Total tracks: $totalFound")
        _progress.value = ScanProgress(isScanning = false, tracksFound = totalFound, currentPath = "Scan complete")
    }

    /**
     * Forces a complete rescan and rebuild of all .spindle_catalog.bin fast index caches.
     */
    suspend fun rebuildFastIndexCache() {
        scanAll(forceFullScan = true)
    }

    /**
     * Checks if any mounted storage root currently possesses a valid fast index cache.
     */
    fun hasFastIndexCache(): Boolean {
        val customPath = getCustomMusicPath()
        val roots = if (!customPath.isNullOrBlank()) listOf(File(customPath)) else findStorageRoots()
        return roots.any { CatalogCacheManager.getCacheFile(it, context).exists() }
    }

    /**
     * Rapidly queries Android system MediaStore for music tracks across all storage volumes.
     */
    private suspend fun scanMediaStore(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackEntity>()
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

                tracks.add(
                    TrackEntity(
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
                        isFavorite = false,
                        dateAdded = System.currentTimeMillis(),
                        dateModified = dateModified,
                        bitrateKbps = bitrateKbps,
                        hasLyrics = lrcFile.exists() || txtFile.exists() || EmbeddedLyricsExtractor.hasLyrics(file)
                    )
                )
            }
        }
        return@withContext tracks
    }

    private suspend fun crawlDirectory(
        dir: File,
        batch: MutableList<TrackEntity>,
        onProgress: suspend (Int, String) -> Unit
    ) {
        val files = dir.listFiles() ?: return

        // 1. Discover and parse external CUE sheets for monolithic audio rips
        val cueFiles = files.filter { it.isFile && it.extension.equals("cue", ignoreCase = true) }
        val referencedAudioPaths = mutableSetOf<String>()

        for (cueFile in cueFiles) {
            try {
                val cueTracks = CueSheetParser.parseCueFile(cueFile)
                for (cueTrack in cueTracks) {
                    val audioFile = File(cueTrack.audioFilePath)
                    if (audioFile.exists()) {
                        referencedAudioPaths.add(audioFile.absolutePath)
                        val audioInfo = TagParser.parseTrack(audioFile)
                        val entity = cueTrack.toTrackEntity(
                            fileFormat = audioInfo?.fileFormat ?: audioFile.extension.uppercase(Locale.ROOT),
                            bitDepth = audioInfo?.bitDepth ?: 16,
                            sampleRate = audioInfo?.sampleRate ?: 44100,
                            channels = audioInfo?.channels ?: 2,
                            bitrateKbps = audioInfo?.bitrateKbps ?: 1411
                        ).copy(
                            dateModified = cueFile.lastModified()
                        )
                        val existing = trackDao.getTrackByPath(entity.path)
                        if (existing == null || Math.abs(existing.dateModified - cueFile.lastModified()) > 2000L) {
                            batch.add(entity)
                            if (batch.size >= 50) {
                                trackDao.insertTracks(batch)
                                onProgress(batch.size, cueFile.name)
                                batch.clear()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse CUE file: ${cueFile.absolutePath}", e)
            }
        }

        // Purge any monolithic single-track database entries superseded by virtual CUE sub-tracks
        for (audioPath in referencedAudioPaths) {
            try {
                trackDao.deleteByPath(audioPath)
            } catch (ignored: Exception) {}
        }

        // 2. Process subdirectories and audio tracks (with embedded CUE support)
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
                if (ext in supportedExtensions && file.absolutePath !in referencedAudioPaths) {
                    // Check for embedded CUE sheet in FLAC/Vorbis comments
                    val embeddedCueTracks = if (ext == "flac" || ext == "ogg") {
                        CueSheetParser.parseEmbeddedCueFromAudioFile(file)
                    } else {
                        emptyList()
                    }

                    if (embeddedCueTracks.isNotEmpty()) {
                        referencedAudioPaths.add(file.absolutePath)
                        try { trackDao.deleteByPath(file.absolutePath) } catch (ignored: Exception) {}

                        val audioInfo = TagParser.parseTrack(file)
                        for (cueTrack in embeddedCueTracks) {
                            val entity = cueTrack.toTrackEntity(
                                fileFormat = audioInfo?.fileFormat ?: file.extension.uppercase(Locale.ROOT),
                                bitDepth = audioInfo?.bitDepth ?: 16,
                                sampleRate = audioInfo?.sampleRate ?: 44100,
                                channels = audioInfo?.channels ?: 2,
                                bitrateKbps = audioInfo?.bitrateKbps ?: 1411
                            ).copy(dateModified = file.lastModified())

                            val existing = trackDao.getTrackByPath(entity.path)
                            if (existing == null || Math.abs(existing.dateModified - file.lastModified()) > 2000L) {
                                batch.add(entity)
                                if (batch.size >= 50) {
                                    trackDao.insertTracks(batch)
                                    onProgress(batch.size, file.name)
                                    batch.clear()
                                }
                            }
                        }
                    } else {
                        val existing = trackDao.getTrackByPath(file.absolutePath)
                        // Skip if already indexed with matching timestamp (+- 2000ms)
                        if (existing == null || Math.abs(existing.dateModified - file.lastModified()) > 2000L) {
                            val parsed = TagParser.parseTrack(file)
                            if (parsed != null) {
                                batch.add(parsed)

                                if (batch.size >= 50) {
                                    trackDao.insertTracks(batch)
                                    onProgress(batch.size, file.parent ?: file.name)
                                    batch.clear()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Rapidly checks for files modified after the catalog cache was generated,
     * ensuring newly copied or edited tracks are added without full re-crawl.
     */
    private suspend fun crawlDirectoryIncremental(
        dir: File,
        sinceTimestamp: Long,
        batch: MutableList<TrackEntity>,
        onProgress: suspend (Int, String) -> Unit
    ) {
        val files = dir.listFiles() ?: return

        // 1. Check for modified/new external CUE sheets
        val cueFiles = files.filter { it.isFile && it.extension.equals("cue", ignoreCase = true) }
        val referencedAudioPaths = mutableSetOf<String>()

        for (cueFile in cueFiles) {
            if (cueFile.lastModified() > sinceTimestamp) {
                try {
                    val cueTracks = CueSheetParser.parseCueFile(cueFile)
                    for (cueTrack in cueTracks) {
                        val audioFile = File(cueTrack.audioFilePath)
                        if (audioFile.exists()) {
                            referencedAudioPaths.add(audioFile.absolutePath)
                            val audioInfo = TagParser.parseTrack(audioFile)
                            val entity = cueTrack.toTrackEntity(
                                fileFormat = audioInfo?.fileFormat ?: audioFile.extension.uppercase(Locale.ROOT),
                                bitDepth = audioInfo?.bitDepth ?: 16,
                                sampleRate = audioInfo?.sampleRate ?: 44100,
                                channels = audioInfo?.channels ?: 2,
                                bitrateKbps = audioInfo?.bitrateKbps ?: 1411
                            ).copy(dateModified = cueFile.lastModified())
                            batch.add(entity)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed incremental CUE parse: ${cueFile.absolutePath}", e)
                }
            }
        }

        for (audioPath in referencedAudioPaths) {
            try { trackDao.deleteByPath(audioPath) } catch (ignored: Exception) {}
        }

        // 2. Check modified subdirectories and audio tracks
        for (file in files) {
            if (file.isDirectory) {
                val name = file.name
                if (!name.startsWith(".") &&
                    !name.equals("Android", ignoreCase = true) &&
                    !name.equals("DCIM", ignoreCase = true) &&
                    !name.equals("Pictures", ignoreCase = true) &&
                    !name.equals("Movies", ignoreCase = true)
                ) {
                    crawlDirectoryIncremental(file, sinceTimestamp, batch, onProgress)
                }
            } else {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext in supportedExtensions && file.lastModified() > sinceTimestamp && file.absolutePath !in referencedAudioPaths) {
                    val embeddedCueTracks = if (ext == "flac" || ext == "ogg") {
                        CueSheetParser.parseEmbeddedCueFromAudioFile(file)
                    } else {
                        emptyList()
                    }

                    if (embeddedCueTracks.isNotEmpty()) {
                        try { trackDao.deleteByPath(file.absolutePath) } catch (ignored: Exception) {}
                        val audioInfo = TagParser.parseTrack(file)
                        for (cueTrack in embeddedCueTracks) {
                            val entity = cueTrack.toTrackEntity(
                                fileFormat = audioInfo?.fileFormat ?: file.extension.uppercase(Locale.ROOT),
                                bitDepth = audioInfo?.bitDepth ?: 16,
                                sampleRate = audioInfo?.sampleRate ?: 44100,
                                channels = audioInfo?.channels ?: 2,
                                bitrateKbps = audioInfo?.bitrateKbps ?: 1411
                            ).copy(dateModified = file.lastModified())
                            batch.add(entity)
                        }
                    } else {
                        val parsed = TagParser.parseTrack(file)
                        if (parsed != null) {
                            batch.add(parsed)
                            if (batch.size >= 50) {
                                trackDao.insertTracks(batch)
                                onProgress(batch.size, file.name)
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
