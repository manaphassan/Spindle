package com.hana.spindle.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ultra-low-RAM image loader tailored for Android DAPs and vintage hardware.
 *
 * Implements:
 * 1. RGB_565 decoding (50% RAM reduction vs ARGB_8888).
 * 2. In-stream byte downsampling (inSampleSize) to prevent OOM on 3000x3000px FLAC covers.
 * 3. 16MB capped LruCache memory pool.
 * 4. Disk cache for downsampled WebP thumbnails.
 */
class ImageLoader(context: Context) {

    private val cacheDir = File(context.cacheDir, "album_thumbs").apply { mkdirs() }

    // Hard limit: 16MB maximum memory cache for album artwork
    private val memoryCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    /**
     * Loads album art from an audio file path, downsampled to reqWidth x reqHeight in RGB_565.
     */
    suspend fun loadCover(
        audioPath: String,
        reqWidth: Int = 300,
        reqHeight: Int = 300
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "$audioPath:${reqWidth}x$reqHeight"
        val diskKey = hashKey(cacheKey)

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
                    return@withContext diskBitmap
                }
            } catch (_: Exception) {
                diskFile.delete()
            }
        }

        // 3. Extract embedded picture byte array
        val pictureBytes = extractPictureBytes(audioPath) ?: return@withContext null

        // 4. Decode dimensions only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, options)

        // 5. Calculate optimal inSampleSize and decode in RGB_565
        options.apply {
            inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.RGB_565 // 2 bytes per pixel
            inDither = true
        }

        val decodedBitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, options)
        if (decodedBitmap != null) {
            memoryCache.put(cacheKey, decodedBitmap)
            // Save to L2 disk cache asynchronously
            try {
                java.io.FileOutputStream(diskFile).use { out ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        decodedBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                    } else {
                        @Suppress("DEPRECATION")
                        decodedBitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext decodedBitmap
    }

    private fun hashKey(key: String): String {
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
