package com.hana.spindle.data

import android.content.Context
import android.os.Environment
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
 * Utilizes direct POSIX directory walking and delta timestamp comparison to index thousands
 * of tracks in seconds without triggering UI stutter or GC spikes.
 */
class MusicScanner(
    private val context: Context,
    private val songDao: SongDao
) {

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val supportedExtensions = setOf(
        "flac", "wav", "aif", "aiff", "alac", "m4a", "mp3", "ogg", "opus", "dsf", "dff"
    )

    /**
     * Initiates a full scan across internal music storage and external MicroSD cards.
     */
    suspend fun scanAll() = withContext(Dispatchers.IO) {
        if (_progress.value.isScanning) return@withContext

        _progress.value = ScanProgress(isScanning = true, songsFound = 0, currentPath = "Starting scan...")

        val roots = findStorageRoots()
        val batch = mutableListOf<SongEntity>()
        var totalFound = 0

        for (root in roots) {
            if (!root.exists() || !root.canRead()) continue
            crawlDirectory(root, batch) { count, path ->
                totalFound += count
                _progress.value = ScanProgress(
                    isScanning = true,
                    songsFound = totalFound,
                    currentPath = path
                )
            }
        }

        // Flush remaining songs in batch
        if (batch.isNotEmpty()) {
            songDao.insertSongs(batch)
            totalFound += batch.size
            batch.clear()
        }

        _progress.value = ScanProgress(isScanning = false, songsFound = totalFound, currentPath = "Scan complete")
    }

    private suspend fun crawlDirectory(
        dir: File,
        batch: MutableList<SongEntity>,
        onProgress: suspend (Int, String) -> Unit
    ) {
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                // Skip hidden folders (e.g. .thumbnails, .android_secure)
                if (!file.name.startsWith(".")) {
                    crawlDirectory(file, batch, onProgress)
                }
            } else {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext in supportedExtensions) {
                    // Delta check: skip parsing if file timestamp is identical in DB
                    val existing = songDao.getSongByPath(file.absolutePath)
                    if (existing == null || existing.dateModified != file.lastModified()) {
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
     */
    private fun findStorageRoots(): List<File> {
        val roots = mutableListOf<File>()

        // 1. Standard Music directories
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let {
            roots.add(it)
        }
        Environment.getExternalStorageDirectory()?.let {
            roots.add(it)
        }

        // 2. MicroSD Card roots in /storage/ (e.g. /storage/0000-0000)
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.isDirectory) {
            storageRoot.listFiles()?.forEach { file ->
                if (file.isDirectory && file.canRead() && !file.name.equals("emulated", ignoreCase = true) && !file.name.equals("self", ignoreCase = true)) {
                    roots.add(file)
                }
            }
        }

        return roots.distinctBy { it.absolutePath }
    }
}
