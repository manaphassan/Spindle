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
import java.lang.ref.SoftReference
import java.util.Collections
import java.util.LinkedList
import java.util.Locale

/**
 * Ultra-low-RAM image loader tailored for Android DAPs and vintage hardware.
 *
 * Implements a 5-tier cover art resolution pipeline with inBitmap memory pooling:
 * 1. L1 Memory Cache: 16MB capped LruCache pool in RGB_565.
 * 2. L2 Persistent Disk Cache: Downsampled WebP thumbnails (album_thumbs/[hash].webp).
 * 3. L3 Embedded Audio Metadata: ID3 / Vorbis embedded APIC art via MediaMetadataRetriever.
 * 4. L4 Local Folder Art: Scans audio file directory for cover.jpg, folder.jpg, front.jpg, etc.
 * 5. L5 Downloaded Art Cache: Online art fetched by CoverArtFetcher (filesDir/covers/{hash}.jpg).
 *
 * Features:
 * - BitmapPool: Reuses mutable RGB_565 bitmaps using SoftReferences to eliminate GC churn.
 * - Non-blocking hasCover: Fast O(1) in-memory and disk presence checks with zero synchronous probe pauses.
 * - Shared Album Disk Thumbnails: Generates and shares album_thumbs/album_[hash].webp across tracks.
 */
class ImageLoader(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "album_thumbs").apply { mkdirs() }
    val downloadedCoversDir = File(context.filesDir, "covers").apply { mkdirs() }

    /**
     * Reusable mutable Bitmap pool to eliminate GC allocations during rapid list/grid scrolling.
     * Uses SoftReferences so memory can be reclaimed by Android OS under system-wide RAM pressure.
     */
    class BitmapPool(private val maxEntries: Int = 32) {
        private val pool = Collections.synchronizedList(LinkedList<SoftReference<Bitmap>>())

        fun acquire(reqWidth: Int, reqHeight: Int, config: Bitmap.Config = Bitmap.Config.RGB_565): Bitmap? {
            val bytesPerPixel = if (config == Bitmap.Config.RGB_565) 2 else 4
            val targetBytes = reqWidth * reqHeight * bytesPerPixel

            synchronized(pool) {
                val iterator = pool.iterator()
                while (iterator.hasNext()) {
                    val ref = iterator.next()
                    val bmp = ref.get()
                    if (bmp == null || bmp.isRecycled) {
                        iterator.remove()
                        continue
                    }
                    if (bmp.isMutable && bmp.allocationByteCount >= targetBytes) {
                        iterator.remove()
                        return bmp
                    }
                }
            }
            return null
        }

        fun release(bitmap: Bitmap) {
            if (!bitmap.isRecycled && bitmap.isMutable) {
                synchronized(pool) {
                    if (pool.size >= maxEntries) {
                        pool.removeAt(0)
                    }
                    pool.add(SoftReference(bitmap))
                }
            }
        }

        fun clear() {
            synchronized(pool) {
                pool.clear()
            }
        }

        val size: Int get() = pool.size
    }

    val bitmapPool = BitmapPool()

    // Hard limit: 16MB maximum memory cache for album artwork in RGB_565
    private val memoryCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue.isMutable && !oldValue.isRecycled) {
                bitmapPool.release(oldValue)
            }
        }
    }

    // Cache local directory folder art file paths to avoid repeated directory scans on slow MicroSD cards
    private val folderArtCache = LruCache<String, String>(500)

    // Cache boolean presence of cover art per audio path or album to eliminate redundant disk queries
    private val coverPresenceCache = LruCache<String, Boolean>(10000)

    /**
     * Resolves the deterministic local cache file for an album/artist combination.
     */
    fun getCoverFileForAlbum(album: String, artist: String): File {
        val normalized = "${artist.trim().lowercase(Locale.ROOT)}_${album.trim().lowercase(Locale.ROOT)}"
        return File(downloadedCoversDir, "${hashKey(normalized)}.jpg")
    }

    /**
     * Resolves the deterministic disk cache file for an audio track's cover.
     */
    fun getDiskCoverFile(audioPath: String): File {
        return File(cacheDir, "${hashKey(audioPath)}.webp")
    }

    /**
     * Resolves the deterministic disk cache file for an album's cover.
     */
    fun getDiskCoverFileForAlbum(album: String, artist: String): File {
        val normalized = "${artist.trim().lowercase(Locale.ROOT)}_${album.trim().lowercase(Locale.ROOT)}"
        return File(cacheDir, "album_${hashKey(normalized)}.webp")
    }

    /**
     * Inspects the audio file directory for standard local folder artwork.
     * Caches resolved paths per directory to avoid repeated MicroSD listFiles() calls.
     */
    fun findLocalFolderArt(audioPath: String): File? {
        try {
            val audioFile = File(audioPath)
            val parentDir = audioFile.parentFile ?: return null
            if (!parentDir.exists() || !parentDir.isDirectory) return null

            val parentPath = parentDir.absolutePath
            folderArtCache.get(parentPath)?.let { cached ->
                return if (cached.isEmpty()) null else File(cached)
            }

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
                    folderArtCache.put(parentPath, candidate.absolutePath)
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
                val candidate = imageFiles.firstOrNull { f ->
                    val n = f.nameWithoutExtension.lowercase(Locale.ROOT)
                    n.contains("cover") || n.contains("folder") || n.contains("album") || n.contains("front")
                } ?: imageFiles[0]
                folderArtCache.put(parentPath, candidate.absolutePath)
                return candidate
            }

            folderArtCache.put(parentPath, "")
        } catch (_: Exception) {}
        return null
    }

    /**
     * Quickly checks if an album or audio track has cover art available.
     * By default, this is completely non-blocking: checks memory cache, disk WebP cache,
     * downloaded covers, and local folder art without synchronous MediaMetadataRetriever probing.
     */
    fun hasCover(
        audioPath: String,
        album: String? = null,
        artist: String? = null,
        allowDiskProbe: Boolean = false
    ): Boolean {
        // 0. Check in-memory presence cache
        coverPresenceCache.get(audioPath)?.let { return it }

        val albumKey = if (!album.isNullOrBlank() && !artist.isNullOrBlank()) {
            "album:${artist.trim().lowercase(Locale.ROOT)}_${album.trim().lowercase(Locale.ROOT)}"
        } else null

        if (albumKey != null) {
            coverPresenceCache.get(albumKey)?.let { return it }
        }

        // 1. Check disk cache
        val diskFile = File(cacheDir, "${hashKey(audioPath)}.webp")
        if (diskFile.exists() && diskFile.length() > 0L) {
            coverPresenceCache.put(audioPath, true)
            albumKey?.let { coverPresenceCache.put(it, true) }
            return true
        }

        // 2. Check album disk cache if album and artist are known
        if (albumKey != null && !album.isNullOrBlank() && !artist.isNullOrBlank()) {
            val albumDiskFile = getDiskCoverFileForAlbum(album, artist)
            if (albumDiskFile.exists() && albumDiskFile.length() > 0L) {
                coverPresenceCache.put(audioPath, true)
                coverPresenceCache.put(albumKey, true)
                return true
            }

            val downloadedFile = getCoverFileForAlbum(album, artist)
            if (downloadedFile.exists() && downloadedFile.length() > 0L) {
                coverPresenceCache.put(audioPath, true)
                coverPresenceCache.put(albumKey, true)
                return true
            }
        }

        // 3. Check local folder
        if (findLocalFolderArt(audioPath) != null) {
            coverPresenceCache.put(audioPath, true)
            albumKey?.let { coverPresenceCache.put(it, true) }
            return true
        }

        // 4. Check embedded tag picture (only if deep disk probe allowed)
        if (allowDiskProbe) {
            val hasEmbedded = extractPictureBytes(audioPath) != null
            coverPresenceCache.put(audioPath, hasEmbedded)
            return hasEmbedded
        }

        return false
    }

    /**
     * Asynchronously checks cover presence on Dispatchers.IO with full embedded probe if needed.
     */
    suspend fun hasCoverAsync(
        audioPath: String,
        album: String? = null,
        artist: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        hasCover(audioPath, album, artist, allowDiskProbe = true)
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
                folderArtCache.remove(parent.absolutePath)
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
            try {
                val parentDir = File(audioPath).parentFile
                if (parentDir != null) folderArtCache.remove(parentDir.absolutePath)
            } catch (_: Exception) {}
        }

        if (!album.isNullOrBlank() && !artist.isNullOrBlank()) {
            val normArtist = artist.trim().lowercase(Locale.ROOT)
            val normAlbum = album.trim().lowercase(Locale.ROOT)
            coverPresenceCache.remove("album:${normArtist}_$normAlbum")
            try {
                val albumDiskFile = getDiskCoverFileForAlbum(album, artist)
                if (albumDiskFile.exists()) albumDiskFile.delete()
            } catch (_: Exception) {}
        }

        val sizes = listOf("80x80", "96x96", "120x120", "160x160", "200x200", "300x300", "400x400", "500x500", "600x600")
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
     * Decodes an input stream into an in-memory sampled Bitmap using inBitmap reuse.
     */
    fun decodeStreamToBitmap(inputStream: InputStream, reqWidth: Int = 600, reqHeight: Int = 600): Bitmap? {
        return try {
            val bytes = inputStream.readBytes()
            if (bytes.size < 100) return null
            decodeBytesToBitmap(bytes, reqWidth, reqHeight)
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
        val albumMemoryKey = if (!album.isNullOrBlank() && !artist.isNullOrBlank()) {
            val normArtist = artist.trim().lowercase(Locale.ROOT)
            val normAlbum = album.trim().lowercase(Locale.ROOT)
            "album:${normArtist}_${normAlbum}:${reqWidth}x$reqHeight"
        } else null

        // 1. Check L1 memory cache (track-specific or shared album key)
        memoryCache.get(cacheKey)?.let { return@withContext it }
        if (albumMemoryKey != null) {
            memoryCache.get(albumMemoryKey)?.let { return@withContext it }
        }

        // 2. Check shared album disk cache if album and artist are known
        val albumDiskFile = if (!album.isNullOrBlank() && !artist.isNullOrBlank()) {
            getDiskCoverFileForAlbum(album, artist)
        } else null

        if (albumDiskFile != null && albumDiskFile.exists() && albumDiskFile.length() > 0L) {
            val diskBitmap = decodeFileToBitmap(albumDiskFile, reqWidth, reqHeight)
            if (diskBitmap != null) {
                memoryCache.put(cacheKey, diskBitmap)
                if (albumMemoryKey != null) memoryCache.put(albumMemoryKey, diskBitmap)
                coverPresenceCache.put(audioPath, true)
                return@withContext diskBitmap
            }
        }

        // 3. Check track-specific persistent disk cache
        val diskKey = hashKey(audioPath)
        val diskFile = File(cacheDir, "$diskKey.webp")
        if (diskFile.exists() && diskFile.length() > 0L) {
            val diskBitmap = decodeFileToBitmap(diskFile, reqWidth, reqHeight)
            if (diskBitmap != null) {
                memoryCache.put(cacheKey, diskBitmap)
                if (albumMemoryKey != null) memoryCache.put(albumMemoryKey, diskBitmap)
                coverPresenceCache.put(audioPath, true)
                return@withContext diskBitmap
            }
        }

        // 4. Check L3 Embedded picture byte array
        val pictureBytes = extractPictureBytes(audioPath)
        if (pictureBytes != null) {
            val bitmap = decodeBytesToBitmap(pictureBytes, reqWidth, reqHeight)
            if (bitmap != null) {
                cacheBitmap(cacheKey, diskFile, bitmap)
                if (albumDiskFile != null && !albumDiskFile.exists()) {
                    cacheBitmap(albumMemoryKey ?: cacheKey, albumDiskFile, bitmap)
                }
                if (albumMemoryKey != null) memoryCache.put(albumMemoryKey, bitmap)
                coverPresenceCache.put(audioPath, true)
                return@withContext bitmap
            }
        }

        // 5. Check L4 Local directory folder art
        val folderArtFile = findLocalFolderArt(audioPath)
        if (folderArtFile != null) {
            val bitmap = decodeFileToBitmap(folderArtFile, reqWidth, reqHeight)
            if (bitmap != null) {
                cacheBitmap(cacheKey, diskFile, bitmap)
                if (albumDiskFile != null && !albumDiskFile.exists()) {
                    cacheBitmap(albumMemoryKey ?: cacheKey, albumDiskFile, bitmap)
                }
                if (albumMemoryKey != null) memoryCache.put(albumMemoryKey, bitmap)
                coverPresenceCache.put(audioPath, true)
                return@withContext bitmap
            }
        }

        // 6. Check L5 Downloaded online art cache
        if (!album.isNullOrBlank() && !artist.isNullOrBlank()) {
            val downloadedFile = getCoverFileForAlbum(album, artist)
            if (downloadedFile.exists() && downloadedFile.length() > 0L) {
                val bitmap = decodeFileToBitmap(downloadedFile, reqWidth, reqHeight)
                if (bitmap != null) {
                    cacheBitmap(cacheKey, diskFile, bitmap)
                    if (albumDiskFile != null && !albumDiskFile.exists()) {
                        cacheBitmap(albumMemoryKey ?: cacheKey, albumDiskFile, bitmap)
                    }
                    if (albumMemoryKey != null) memoryCache.put(albumMemoryKey, bitmap)
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
        val normArtist = artist.trim().lowercase(Locale.ROOT)
        val normAlbum = album.trim().lowercase(Locale.ROOT)
        val albumKey = "album:${normArtist}_${normAlbum}:${reqWidth}x$reqHeight"

        // 1. Check L1 memory
        memoryCache.get(albumKey)?.let { return@withContext it }

        // 2. Check album disk cache
        val albumDiskFile = getDiskCoverFileForAlbum(album, artist)
        if (albumDiskFile.exists() && albumDiskFile.length() > 0L) {
            val bitmap = decodeFileToBitmap(albumDiskFile, reqWidth, reqHeight)
            if (bitmap != null) {
                memoryCache.put(albumKey, bitmap)
                return@withContext bitmap
            }
        }

        // 3. Check downloaded cover file
        val downloadedFile = getCoverFileForAlbum(album, artist)
        if (downloadedFile.exists() && downloadedFile.length() > 0L) {
            val bitmap = decodeFileToBitmap(downloadedFile, reqWidth, reqHeight)
            if (bitmap != null) {
                memoryCache.put(albumKey, bitmap)
                if (!albumDiskFile.exists()) {
                    cacheBitmap(albumKey, albumDiskFile, bitmap)
                }
                return@withContext bitmap
            }
        }

        // 4. Fallback to representative audio file if provided
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
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            val targetWidth = (options.outWidth + sampleSize - 1) / sampleSize
            val targetHeight = (options.outHeight + sampleSize - 1) / sampleSize

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
                inMutable = true
                inDither = true
            }

            val candidate = bitmapPool.acquire(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            if (candidate != null) {
                decodeOptions.inBitmap = candidate
            }

            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            } catch (_: IllegalArgumentException) {
                // inBitmap reuse rejected; fallback to clean decode
                decodeOptions.inBitmap = null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeFileToBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            val targetWidth = (options.outWidth + sampleSize - 1) / sampleSize
            val targetHeight = (options.outHeight + sampleSize - 1) / sampleSize

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
                inMutable = true
                inDither = true
            }

            val candidate = bitmapPool.acquire(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            if (candidate != null) {
                decodeOptions.inBitmap = candidate
            }

            try {
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } catch (_: IllegalArgumentException) {
                decodeOptions.inBitmap = null
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
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

    fun extractPictureBytes(audioPath: String): ByteArray? {
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
        folderArtCache.evictAll()
        coverPresenceCache.evictAll()
        bitmapPool.clear()
    }
}
