package com.hana.spindle.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * Ultra-low-RAM image loader tailored for Android DAPs and vintage hardware.
 *
 * Implements a 5-tier cover art resolution pipeline:
 * 1. L1 Memory Cache: 16MB capped LruCache pool in RGB_565.
 * 2. L2 Persistent Disk Cache: Downsampled WebP thumbnails (album_thumbs/[hash].webp).
 * 3. L3 Embedded Audio Metadata: ID3 / Vorbis embedded APIC art via MediaMetadataRetriever.
 * 4. L4 Local Folder Art: Scans audio file directory for cover.jpg, folder.jpg, front.jpg, etc.
 * 5. L5 Downloaded Art Cache: Online art fetched by CoverArtFetcher (filesDir/covers/{hash}.jpg).
 */
class ImageLoader(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "album_thumbs").apply { mkdirs() }
    val downloadedCoversDir = File(context.filesDir, "covers").apply { mkdirs() }

    // Hard limit: 16MB maximum memory cache for album artwork
    private val memoryCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    /**
     * Resolves the deterministic local cache file for an album/artist combination.
     */
    fun getCoverFileForAlbum(album: String, artist: String): File {
        val normalized = "${artist.trim().lowercase(Locale.ROOT)}_${album.trim().lowercase(Locale.ROOT)}"
        return File(downloadedCoversDir, "${hashKey(normalized)}.jpg")
    }

    /**
     * Inspects the audio file directory for standard local folder artwork.
     */
    fun findLocalFolderArt(audioPath: String): File? {
        try {
            val audioFile = File(audioPath)
            val parentDir = audioFile.parentFile ?: return null
            if (!parentDir.exists() || !parentDir.isDirectory) return null

            val standardNames = arrayOf(
                "cover.jpg", "cover.png", "cover.jpeg",
                "folder.jpg", "folder.png", "folder.jpeg",
                "album.jpg", "album.png", "album.jpeg",
                "front.jpg", "front.png", "front.jpeg",
                "artwork.jpg", "artwork.png"
            )

            for (name in standardNames) {
                val candidate = File(parentDir, name)
                if (candidate.exists() && candidate.isFile && candidate.length() > 512L) {
                    return candidate
                }
            }

            // Case-insensitive fallback scan for any image file in folder
            val imageFiles = parentDir.listFiles { f ->
                if (!f.isFile || f.length() <= 512L) return@listFiles false
                val ext = f.extension.lowercase(Locale.ROOT)
                ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp"
            }

            if (!imageFiles.isNullOrEmpty()) {
                // Prioritize files containing cover/folder/album in name
                return imageFiles.firstOrNull { f ->
                    val n = f.nameWithoutExtension.lowercase(Locale.ROOT)
                    n.contains("cover") || n.contains("folder") || n.contains("album") || n.contains("front")
                } ?: imageFiles[0]
            }
        } catch (_: Exception) {}
        return null
    }

    // Cache boolean presence of cover art per audio path to prevent redundant MediaMetadataRetriever calls
    private val coverPresenceCache = LruCache<String, Boolean>(1000)

    /**
     * Quickly checks if an album or audio track has any cover art available (without full decode).
     */
    fun hasCover(audioPath: String, album: String? = null, artist: String? = null): Boolean {
        // 0. Check in-memory presence cache
        coverPresenceCache.get(audioPath)?.let { return it }

        // 1. Check disk cache
        val diskFile = File(cacheDir, "${hashKey(audioPath)}.webp")
        if (diskFile.exists() && diskFile.length() > 0L) {
            coverPresenceCache.put(audioPath, true)
            return true
        }

        // 2. Check downloaded cache if album and artist are known
        if (!album.isNullOrBlank() && !artist.isNullOrBlank()) {
            val downloadedFile = getCoverFileForAlbum(album, artist)
            if (downloadedFile.exists() && downloadedFile.length() > 0L) {
                coverPresenceCache.put(audioPath, true)
                return true
            }
        }

        // 3. Check local folder
        if (findLocalFolderArt(audioPath) != null) {
            coverPresenceCache.put(audioPath, true)
            return true
        }

        // 4. Check embedded tag picture
        val hasEmbedded = extractPictureBytes(audioPath) != null
        coverPresenceCache.put(audioPath, hasEmbedded)
        return hasEmbedded
    }

    /**
     * Safely saves or copies a cover image file to the parent folder of the audio track as "cover.jpg".
     * Returns true if successfully written.
     */
    fun saveCoverToAlbumFolder(sourceFile: File, audioPath: String): Boolean {
        if (!sourceFile.exists() || audioPath.isBlank()) return false
        return try {
            val parent = File(audioPath).parentFile
            if (parent != null && parent.exists() && parent.canWrite()) {
                val target = File(parent, "cover.jpg")
                sourceFile.copyTo(target, overwrite = true)
                coverPresenceCache.put(audioPath, true)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Saves a user-selected or custom bitmap cover into the internal downloads cache and
     * optionally into the album folder as "cover.jpg".
     * Automatically purges stale in-memory and disk caches to force an immediate refresh.
     */
    fun saveCustomCover(
        bitmap: Bitmap,
        audioPath: String,
        album: String,
        artist: String,
        saveToFolder: Boolean = true
    ): Boolean {
        val targetFile = getCoverFileForAlbum(album, artist)
        var success = false
        try {
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            success = true
        } catch (_: Exception) {
            return false
        }

        if (saveToFolder && audioPath.isNotBlank()) {
            saveCoverToAlbumFolder(targetFile, audioPath)
        }

        invalidateCoverCache(audioPath, album, artist)
        return success
    }

    /**
     * Purges cached artwork entries across L1 Memory, L2 WebP disk, and presence caches
     * so that newly assigned or downloaded artwork renders immediately.
     */
    fun invalidateCoverCache(audioPath: String, album: String? = null, artist: String? = null) {
        if (audioPath.isNotBlank()) {
            coverPresenceCache.remove(audioPath)
            try {
                val diskFile = File(cacheDir, "${hashKey(audioPath)}.webp")
                if (diskFile.exists()) diskFile.delete()
            } catch (_: Exception) {}
        }

        val sizes = listOf("80x80", "120x120", "200x200", "300x300", "500x500", "600x600")
        for (sz in sizes) {
            if (audioPath.isNotBlank()) {
                memoryCache.remove("$audioPath:$sz")
            }
            if (!album.isNullOrBlank() && !artist.isNullOrBlank()) {
                val normArtist = artist.trim().lowercase(Locale.ROOT)
                val normAlbum = album.trim().lowercase(Locale.ROOT)
                memoryCache.remove("album:${normArtist}_${normAlbum}:$sz")
            }
        }
    }

    /**
     * Decodes an input stream into an in-memory sampled Bitmap.
     */
    fun decodeStreamToBitmap(inputStream: InputStream, reqWidth: Int = 600, reqHeight: Int = 600): Bitmap? {
        return try {
            val bytes = inputStream.readBytes()
            if (bytes.size < 100) return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads album art from an audio file path using the multi-tier resolution pipeline.
     */
    suspend fun loadCover(
        audioPath: String,
        reqWidth: Int = 300,
        reqHeight: Int = 300,
        album: String? = null,
        artist: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "$audioPath:${reqWidth}x$reqHeight"
        val diskKey = hashKey(audioPath)

        // 1. Check L1 memory cache
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // 2. Check L2 persistent disk cache
        val diskFile = File(cacheDir, "$diskKey.webp")
        if (diskFile.exists() && diskFile.length() > 0L) {
            try {
                val diskOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath, diskOptions)
                if (diskBitmap != null) {
                    memoryCache.put(cacheKey, diskBitmap)
                    coverPresenceCache.put(audioPath, true)
                    return@withContext diskBitmap
                }
            } catch (_: Exception) {
                diskFile.delete()
            }
        }

        // 3. Check L3 Embedded picture byte array
        val pictureBytes = extractPictureBytes(audioPath)
        if (pictureBytes != null) {
            val bitmap = decodeBytesToBitmap(pictureBytes, reqWidth, reqHeight)
            if (bitmap != null) {
                cacheBitmap(cacheKey, diskFile, bitmap)
                coverPresenceCache.put(audioPath, true)
                return@withContext bitmap
            }
        }

        // 4. Check L4 Local directory folder art
        val folderArtFile = findLocalFolderArt(audioPath)
        if (folderArtFile != null) {
            val bitmap = decodeFileToBitmap(folderArtFile, reqWidth, reqHeight)
            if (bitmap != null) {
                cacheBitmap(cacheKey, diskFile, bitmap)
                coverPresenceCache.put(audioPath, true)
                return@withContext bitmap
            }
        }

        // 5. Check L5 Downloaded online art cache
        if (!album.isNullOrBlank() && !artist.isNullOrBlank()) {
            val downloadedFile = getCoverFileForAlbum(album, artist)
            if (downloadedFile.exists() && downloadedFile.length() > 0L) {
                val bitmap = decodeFileToBitmap(downloadedFile, reqWidth, reqHeight)
                if (bitmap != null) {
                    cacheBitmap(cacheKey, diskFile, bitmap)
                    coverPresenceCache.put(audioPath, true)
                    return@withContext bitmap
                }
            }
        }

        coverPresenceCache.put(audioPath, false)
        return@withContext null
    }

    /**
     * Loads album artwork directly by album and artist names, falling back to representative audio path.
     */
    suspend fun loadAlbumCover(
        album: String,
        artist: String,
        fallbackAudioPath: String? = null,
        reqWidth: Int = 300,
        reqHeight: Int = 300
    ): Bitmap? = withContext(Dispatchers.IO) {
        val albumKey = "album:${artist.trim().lowercase(Locale.ROOT)}_${album.trim().lowercase(Locale.ROOT)}:${reqWidth}x$reqHeight"

        // 1. Check L1 memory
        memoryCache.get(albumKey)?.let { return@withContext it }

        // 2. Check downloaded cover file
        val downloadedFile = getCoverFileForAlbum(album, artist)
        if (downloadedFile.exists() && downloadedFile.length() > 0L) {
            val bitmap = decodeFileToBitmap(downloadedFile, reqWidth, reqHeight)
            if (bitmap != null) {
                memoryCache.put(albumKey, bitmap)
                return@withContext bitmap
            }
        }

        // 3. Fallback to representative audio file if provided
        if (!fallbackAudioPath.isNullOrBlank()) {
            val bitmap = loadCover(fallbackAudioPath, reqWidth, reqHeight, album, artist)
            if (bitmap != null) {
                memoryCache.put(albumKey, bitmap)
                return@withContext bitmap
            }
        }

        return@withContext null
    }

    private fun cacheBitmap(cacheKey: String, diskFile: File, bitmap: Bitmap) {
        memoryCache.put(cacheKey, bitmap)
        try {
            FileOutputStream(diskFile).use { out ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
            }
        } catch (_: Exception) {}
    }

    private fun decodeBytesToBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.apply {
            inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = true
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeFileToBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.apply {
            inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = true
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    internal fun hashKey(key: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(key.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            key.hashCode().toString()
        }
    }

    private fun extractPictureBytes(audioPath: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioPath)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // Cache extracted accent colors per audio path
    private val colorCache = LruCache<String, Int>(100)

    /**
     * Extracts a vibrant or dominant accent color from the embedded cover art.
     * Returns a default audiophile vintage gold if no cover art is found.
     */
    suspend fun extractAccentColor(
        audioPath: String,
        defaultColor: Int = android.graphics.Color.parseColor("#F97316")
    ): Int = withContext(Dispatchers.IO) {
        colorCache.get(audioPath)?.let { return@withContext it }

        val coverBitmap = loadCover(audioPath, 120, 120)
        if (coverBitmap == null) {
            colorCache.put(audioPath, defaultColor)
            return@withContext defaultColor
        }

        val palette = try {
            androidx.palette.graphics.Palette.from(coverBitmap).generate()
        } catch (e: Exception) {
            null
        }

        val extractedColor = palette?.let { p ->
            p.getVibrantColor(
                p.getLightVibrantColor(
                    p.getDominantColor(
                        p.getMutedColor(defaultColor)
                    )
                )
            )
        } ?: defaultColor

        colorCache.put(audioPath, extractedColor)
        return@withContext extractedColor
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
        colorCache.evictAll()
    }
}
