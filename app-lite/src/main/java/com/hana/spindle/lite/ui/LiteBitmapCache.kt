package com.hana.spindle.lite.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.ImageView
import androidx.collection.LruCache
import com.hana.spindle.lite.R
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Ultra-low memory bitmap cache and asynchronous thumbnail loader for Spindle Lite.
 * Enforces ~48x48 RGB_565 format (~4.6KB per artwork) with a strictly capped 2.0MB heap cap
 * and negative-caching to preserve sub-15MB RAM footprint on 512MB hardware.
 */
object LiteBitmapCache {

    private const val MAX_CACHE_SIZE_BYTES = 2 * 1024 * 1024 // Strict 2 MB cap
    private const val TARGET_DIMENSION = 48 // 48x48 RGB_565

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val missingArtPaths = Collections.synchronizedSet(LinkedHashSet<String>())

    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }.apply { name = "LiteArtLoader" }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun get(filePath: String): Bitmap? {
        return memoryCache.get(filePath)
    }

    fun loadAsync(
        filePath: String,
        targetView: ImageView,
        placeholderResId: Int = R.drawable.ic_album_placeholder
    ) {
        if (filePath.isEmpty()) {
            targetView.setImageResource(placeholderResId)
            return
        }

        targetView.tag = filePath

        val cached = memoryCache.get(filePath)
        if (cached != null) {
            targetView.setImageBitmap(cached)
            return
        }

        if (missingArtPaths.contains(filePath)) {
            targetView.setImageResource(placeholderResId)
            return
        }

        targetView.setImageResource(placeholderResId)

        executor.execute {
            val bmp = loadOrExtract(filePath)
            if (bmp == null) {
                if (missingArtPaths.size > 2000) {
                    missingArtPaths.clear()
                }
                missingArtPaths.add(filePath)
            }
            mainHandler.post {
                if (targetView.tag == filePath) {
                    if (bmp != null) {
                        targetView.setImageBitmap(bmp)
                    } else {
                        targetView.setImageResource(placeholderResId)
                    }
                }
            }
        }
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

            // Calculate power-of-two downsampling for 48x48
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

            val decoded = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, decodeOptions) ?: return null

            // Scale down precisely if needed
            if (decoded.width > TARGET_DIMENSION * 1.5 || decoded.height > TARGET_DIMENSION * 1.5) {
                val scaled = Bitmap.createScaledBitmap(decoded, TARGET_DIMENSION, TARGET_DIMENSION, true)
                if (scaled != decoded) {
                    decoded.recycle()
                }
                scaled
            } else {
                decoded
            }
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
        missingArtPaths.clear()
    }
}
