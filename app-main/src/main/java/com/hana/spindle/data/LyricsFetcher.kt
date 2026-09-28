package com.hana.spindle.data

import android.content.Context
import com.hana.spindle.data.db.TrackDao
import com.hana.spindle.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * High-performance, zero-dependency Online Synchronized Lyrics Fetcher & Downloader.
 * Integrates the free, open-source LRCLIB API (https://lrclib.net/).
 *
 * Implements:
 * - Rate-limited HTTP pipeline with strict timeouts.
 * - Automatic string sanitization (cleans FLAC, Remastered, deluxe edition tags for clean search queries).
 * - Dual-tier storage: First writes adjacent .lrc sidecar file; falls back to app-internal lyrics cache.
 * - Auto-download hook for audio engine on track play.
 * - Bulk scanner for batch library synchronization.
 */
class LyricsFetcher(
    private val context: Context,
    private val trackDao: TrackDao
) {

    sealed class FetchStatus {
        object Idle : FetchStatus()
        data class Scanning(val totalTracks: Int) : FetchStatus()
        data class InProgress(
            val current: Int,
            val total: Int,
            val currentTitle: String,
            val currentArtist: String,
            val downloadedCount: Int
        ) : FetchStatus()
        data class Finished(
            val totalScanned: Int,
            val missingFound: Int,
            val downloadedSuccess: Int,
            val failedCount: Int
        ) : FetchStatus()
    }

    companion object {
        private const val PREFS_NAME = "spindle_lyrics_prefs"
        private const val KEY_AUTO_DOWNLOAD = "auto_download_lyrics"

        /**
         * Cleans track title and artist for optimum lyric catalog matching.
         * E.g. "Get Lucky (Radio Edit) [feat. Pharrell Williams]" -> ("Get Lucky", "Daft Punk")
         */
        fun cleanSearchTerm(title: String, artist: String): Pair<String, String> {
            var cleanTitle = title
                .replace(Regex("(?i)\\[.*?\\]"), "")
                .replace(Regex("(?i)\\((feat\\.?|ft\\.?|with).*?\\)"), "")
                .replace(Regex("(?i)\\((official video|music video|lyric video|audio|official audio).*?\\)"), "")
                .replace(Regex("(?i)\\s*\\b(feat|ft|with)(\\.|\\b).*"), "")
                .replace(Regex("(?i)\\s*[-–—]\\s*(19|20)?\\d{2}?\\s*remaster.*"), "")
                .replace(Regex("(?i)\\b(remaster(ed)?|deluxe|edition|bonus track(s)?|anniversary|flac|lossless|radio edit|original mix|extended mix|version)\\b"), "")
                .replace(Regex("\\(\\s*\\)"), "")
                .replace(Regex("\\[\\s*\\]"), "")
                .replace(Regex("\\((19|20)\\d{2}\\)"), "")
                .replace(Regex("[-–—\\s]+$"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            var cleanArtist = artist
                .replace(Regex("(?i)\\[.*?\\]"), "")
                .replace(Regex("(?i)\\((feat\\.?|ft\\.?|with).*?\\)"), "")
                .replace(Regex("(?i)\\s*\\b(feat|ft|with)(\\.|\\b).*"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (cleanTitle.isBlank()) cleanTitle = title.trim()
            if (cleanArtist.isBlank() || cleanArtist.equals("<unknown>", ignoreCase = true)) {
                cleanArtist = ""
            }

            return Pair(cleanTitle, cleanArtist)
        }

        fun getSafeLyricKey(path: String): String {
            val filename = path.substringAfterLast('/').substringAfterLast('\\')
            val nameWithoutExt = if (filename.contains('.')) filename.substringBeforeLast('.') else filename
            val safeName = nameWithoutExt.replace(Regex("[^a-zA-Z0-9]"), "_")
            val hash = (path.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
            return "${safeName}_$hash"
        }

        fun getCachedLyricsFile(context: Context, audioPath: String): File {
            val dir = File(context.filesDir, "lyrics")
            if (!dir.exists()) dir.mkdirs()
            val safeKey = getSafeLyricKey(audioPath)
            return File(dir, "$safeKey.lrc")
        }

        fun hasCachedLyrics(context: Context, audioPath: String): Boolean {
            val f = getCachedLyricsFile(context, audioPath)
            return f.exists() && f.length() > 10L
        }
    }

    fun isAutoDownloadEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_DOWNLOAD, true)
    }

    fun setAutoDownloadEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD, enabled).apply()
    }

    /**
     * Fetches lyrics for a track, saving to local sidecar or cache, and returning parsed LyricsData.
     */
    suspend fun fetchLyricsForTrack(
        track: TrackEntity,
        forceRefresh: Boolean = false
    ): LyricsData? = withContext(Dispatchers.IO) {
        val audioPath = track.path
        val audioFile = File(audioPath)

        // 1. If not forcing refresh, check if lyrics already exist on disk
        if (!forceRefresh) {
            val existing = LyricsParser.loadLyrics(audioPath, context)
            if (existing != null && existing.lines.isNotEmpty()) {
                return@withContext existing
            }
        }

        val (cleanTitle, cleanArtist) = cleanSearchTerm(track.title, track.artist)
        if (cleanTitle.isBlank()) return@withContext null

        val durationSec = (track.durationMs / 1000L).toInt()

        // 2. Query LRCLIB
        val lrcContent = queryLrclib(cleanTitle, cleanArtist, track.album, durationSec)
        if (lrcContent.isNullOrBlank()) return@withContext null

        // 3. Save lyrics to disk
        val savedFile = saveLyrics(audioPath, lrcContent)
        if (savedFile != null) {
            try {
                trackDao.updateLyricsStatus(track.id, true)
            } catch (_: Exception) {}
        }

        // 4. Return parsed LyricsData
        LyricsParser.parseLrcContent(lrcContent)
    }

    /**
     * Queries LRCLIB API:
     * Step A: /api/get (exact match on track, artist, album, duration)
     * Step B: /api/search (fuzzy search fallback)
     */
    private fun queryLrclib(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int
    ): String? {
        // Step A: Try exact GET
        val exactResult = queryLrclibGet(title, artist, album, durationSec)
        if (!exactResult.isNullOrBlank()) {
            return exactResult
        }

        // Step B: Fallback search
        return queryLrclibSearch(title, artist)
    }

    private fun queryLrclibGet(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            var urlStr = "https://lrclib.net/api/get?track_name=$encTitle&artist_name=$encArtist"
            if (!album.isNullOrBlank()) {
                val encAlbum = URLEncoder.encode(album, "UTF-8")
                urlStr += "&album_name=$encAlbum"
            }
            if (durationSec > 0) {
                urlStr += "&duration=$durationSec"
            }

            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Spindle-DAP/1.0 (Android; +https://github.com/manaphassan/Spindle)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(jsonStr)
                val syncedLyrics = root.optString("syncedLyrics")
                if (syncedLyrics.isNotBlank() && syncedLyrics != "null") {
                    return syncedLyrics
                }
                val plainLyrics = root.optString("plainLyrics")
                if (plainLyrics.isNotBlank() && plainLyrics != "null") {
                    return plainLyrics
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun queryLrclibSearch(title: String, artist: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val urlStr = "https://lrclib.net/api/search?track_name=$encTitle&artist_name=$encArtist"

            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Spindle-DAP/1.0 (Android; +https://github.com/manaphassan/Spindle)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val array = JSONArray(jsonStr)
                var fallbackPlain: String? = null

                for (i in 0 until minOf(array.length(), 5)) {
                    val item = array.getJSONObject(i)
                    val synced = item.optString("syncedLyrics")
                    if (synced.isNotBlank() && synced != "null") {
                        return synced
                    }
                    if (fallbackPlain == null) {
                        val plain = item.optString("plainLyrics")
                        if (plain.isNotBlank() && plain != "null") {
                            fallbackPlain = plain
                        }
                    }
                }
                return fallbackPlain
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Attempts writing .lrc sidecar adjacent to audio track.
     * If write-protected (e.g. Scoped Storage / SD card restrictions),
     * falls back to app internal storage.
     */
    fun saveLyrics(audioPath: String, lrcContent: String, preferSidecar: Boolean = true): File? {
        val audioFile = File(audioPath)

        val parent = audioFile.parentFile
        if (preferSidecar && parent != null && parent.exists()) {
            try {
                val sidecar = File(parent, "${audioFile.nameWithoutExtension}.lrc")
                sidecar.writeText(lrcContent, Charsets.UTF_8)
                if (sidecar.exists() && sidecar.length() > 0) {
                    return sidecar
                }
            } catch (_: Exception) {
                // Ignore and fall back to internal cache
            }
        }

        // Internal cache fallback
        return try {
            val internalFile = getCachedLyricsFile(context, audioPath)
            internalFile.writeText(lrcContent, Charsets.UTF_8)
            internalFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Batch scans the local database for tracks without lyrics and downloads them from LRCLIB.
     */
    suspend fun scanAndFetchMissingLyrics(
        saveToSidecar: Boolean = true,
        onProgress: (FetchStatus) -> Unit
    ) = withContext(Dispatchers.IO) {
        val missingTracks = try {
            trackDao.getTracksWithoutLyrics()
        } catch (_: Exception) {
            emptyList()
        }

        if (missingTracks.isEmpty()) {
            onProgress(FetchStatus.Finished(0, 0, 0, 0))
            return@withContext
        }

        val total = missingTracks.size
        onProgress(FetchStatus.Scanning(total))

        var downloadedCount = 0
        var failedCount = 0

        for ((idx, track) in missingTracks.withIndex()) {
            // Check if lyrics file exists now (could be sidecar or cache)
            val existing = LyricsParser.loadLyrics(track.path, context)
            if (existing != null && existing.lines.isNotEmpty()) {
                try { trackDao.updateLyricsStatus(track.id, true) } catch (_: Exception) {}
                continue
            }

            onProgress(
                FetchStatus.InProgress(
                    current = idx + 1,
                    total = total,
                    currentTitle = track.title,
                    currentArtist = track.artist,
                    downloadedCount = downloadedCount
                )
            )

            val lyrics = fetchLyricsForTrack(track, forceRefresh = false)
            if (lyrics != null && lyrics.lines.isNotEmpty()) {
                downloadedCount++
            } else {
                failedCount++
            }

            // Polite pause between requests to respect community LRCLIB rate limits
            delay(300L)
        }

        onProgress(
            FetchStatus.Finished(
                totalScanned = total,
                missingFound = total,
                downloadedSuccess = downloadedCount,
                failedCount = failedCount
            )
        )
    }
}
