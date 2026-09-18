package com.hana.spindle.lite.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.collection.LruCache

/**
 * Ultra-low memory bitmap cache for Spindle Lite.
 * Enforces 256x256 RGB_565 format (128KB per artwork) with a strict 4.0MB heap cap.
 */
object LiteBitmapCache {

    private const val MAX_CACHE_SIZE_BYTES = 4 * 1024 * 1024 // 4 MB cap
    private const val TARGET_DIMENSION = 256

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun get(filePath: String): Bitmap? {
        return memoryCache.get(filePath)
    }

    fun loadOrExtract(filePath: String): Bitmap? {
        val cached = memoryCache.get(filePath)
        if (cached != null) return cached

        val extracted = extractArtwork(filePath)
        if (extracted != null) {
            memoryCache.put(filePath, extracted)
        }
        return extracted
    }

    private fun extractArtwork(filePath: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val pictureBytes = retriever.embeddedPicture ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, options)

            // Calculate downsampling for 256x256
            var sampleSize = 1
            val maxDimension = maxOf(options.outWidth, options.outHeight)
            while ((maxDimension / sampleSize) > TARGET_DIMENSION * 1.5) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // 2 bytes per pixel (halves memory)
                inDither = true
            }

            BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, decodeOptions)
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    fun clear() {
        memoryCache.evictAll()
    }
}
