package com.hana.spindle.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hana.spindle.data.db.TrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Lightweight, zero-dependency Online Album Cover Art Fetcher & Downloader.
 *
 * Exclusively targets missing artwork.
 * Primary Provider: Apple iTunes Search API (600x600 high-res artwork).
 * Secondary Provider: Deezer Search API (500x500 artwork).
 *
 * Implements:
 * - Rate-limited HTTP pipeline with strict timeouts (prevents 429 throttling).
 * - Automatic string sanitization (cleans FLAC, Remastered, deluxe edition tags for clean search queries).
 * - Direct RGB_565 downsampling to keep RAM footprint minimal.
 * - Reactive progress telemetry for UI binding.
 */
class CoverArtFetcher(
    private val context: Context,
    private val trackDao: TrackDao,
    private val imageLoader: ImageLoader
) {

    sealed class FetchStatus {
        object Idle : FetchStatus()
        data class Scanning(val totalAlbums: Int) : FetchStatus()
        data class InProgress(
            val current: Int,
            val total: Int,
            val currentAlbum: String,
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
        /**
         * Sanitizes album and artist strings for optimum music metadata catalog matching.
         * E.g. "Discovery [2001] (24-bit Remaster)" -> "Discovery"
         */
        fun cleanSearchTerm(album: String, artist: String): String {
            val cleanAlbum = album
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("(?i)\\b(remaster(ed)?|deluxe|edition|bonus track(s)?|anniversary|flac|lossless|cd\\s*\\d*)\\b"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            val cleanArtist = artist
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            return if (cleanArtist.isNotBlank() && cleanArtist != "<unknown>") {
                "$cleanArtist $cleanAlbum".trim()
            } else {
                cleanAlbum
            }
        }
    }

    /**
     * Fetches missing album art online and caches it to the local covers directory.
     * Returns the local File if successfully downloaded, or null if not found.
     */
    suspend fun fetchCoverForAlbum(album: String, artist: String): File? = withContext(Dispatchers.IO) {
        val targetFile = imageLoader.getCoverFileForAlbum(album, artist)
        if (targetFile.exists() && targetFile.length() > 1024L) {
            return@withContext targetFile
        }

        val searchTerm = cleanSearchTerm(album, artist)
        if (searchTerm.isBlank()) return@withContext null

        // 1. Try Apple iTunes Search API (Primary)
        val itunesUrl = queryItunes(searchTerm)
        if (!itunesUrl.isNullOrBlank()) {
            val downloaded = downloadAndCacheImage(itunesUrl, targetFile)
            if (downloaded != null) return@withContext downloaded
        }

        // 2. Try Deezer Search API (Fallback)
        val deezerUrl = queryDeezer(searchTerm)
        if (!deezerUrl.isNullOrBlank()) {
            val downloaded = downloadAndCacheImage(deezerUrl, targetFile)
            if (downloaded != null) return@withContext downloaded
        }

        return@withContext null
    }

    private fun queryItunes(term: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(term, "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$encoded&entity=album&limit=1")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Spindle-DAP/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val item = results.getJSONObject(0)
                    var artUrl = item.optString("artworkUrl100")
                    if (artUrl.isNotBlank()) {
                        // Replace 100x100bb with 600x600bb for high-resolution album art
                        artUrl = artUrl.replace("100x100bb.jpg", "600x600bb.jpg")
                            .replace("100x100bb.png", "600x600bb.jpg")
                        return artUrl
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun queryDeezer(term: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(term, "UTF-8")
            val url = URL("https://api.deezer.com/search/album?q=$encoded&limit=1")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Spindle-DAP/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val data = root.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val item = data.getJSONObject(0)
                    val artUrl = item.optString("cover_big").ifEmpty {
                        item.optString("cover_medium")
                    }
                    if (artUrl.isNotBlank()) {
                        return artUrl
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadAndCacheImage(urlStr: String, targetFile: File): File? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Spindle-DAP/1.0 (Android)")
            }

            if (conn.responseCode == 200) {
                val bytes = readStreamToBytes(conn.inputStream)
                if (bytes.size > 1024) {
                    // Decode bounds only
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                    // Target 600x600 max
                    var sampleSize = 1
                    while ((options.outWidth / sampleSize) > 800 || (options.outHeight / sampleSize) > 800) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inDither = true
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    if (bitmap != null) {
                        FileOutputStream(targetFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        return targetFile
                    }
                }
            }
            null
        } catch (_: Exception) {
            if (targetFile.exists() && targetFile.length() == 0L) {
                targetFile.delete()
            }
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readStreamToBytes(input: InputStream): ByteArray {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Scans Room database for albums that are missing artwork and automatically fetches them.
     */
    suspend fun scanAndFetchMissingCovers(
        onStatusUpdate: ((FetchStatus) -> Unit)? = null
    ): FetchStatus.Finished = withContext(Dispatchers.IO) {
        val albums = trackDao.getAlbums().firstOrNull() ?: emptyList()
        onStatusUpdate?.invoke(FetchStatus.Scanning(albums.size))

        // Filter strictly albums without local cover art
        val missingAlbums = albums.filter { album ->
            !imageLoader.hasCover(album.representativePath, album.album, album.artist)
        }

        var downloadedCount = 0
        var failedCount = 0

        missingAlbums.forEachIndexed { index, album ->
            onStatusUpdate?.invoke(
                FetchStatus.InProgress(
                    current = index + 1,
                    total = missingAlbums.size,
                    currentAlbum = album.album,
                    currentArtist = album.artist,
                    downloadedCount = downloadedCount
                )
            )

            val file = fetchCoverForAlbum(album.album, album.artist)
            if (file != null && file.exists()) {
                downloadedCount++
            } else {
                failedCount++
            }

            // Polite delay between network calls (400ms)
            delay(400)
        }

        val finished = FetchStatus.Finished(
            totalScanned = albums.size,
            missingFound = missingAlbums.size,
            downloadedSuccess = downloadedCount,
            failedCount = failedCount
        )
        onStatusUpdate?.invoke(finished)
        return@withContext finished
    }
}
