package com.hana.spindle.lite.db

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Process
import android.provider.MediaStore
import com.hana.spindle.lite.util.AudioHeaderParser
import java.io.File
import java.util.concurrent.Executors

/**
 * Background low-overhead media crawler for Spindle Lite.
 * Scans MediaStore and direct FAT32 MicroSD paths without memory spikes.
 */
class LiteMediaScanner(private val context: Context) {

    interface ScanCallback {
        fun onScanProgress(foundCount: Int)
        fun onScanComplete(totalTracks: Int)
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }.apply { name = "SpindleLiteScanner" }
    }

    fun startScan(callback: ScanCallback?) {
        executor.execute {
            val dbHelper = LiteDbHelper.getInstance(context)
            val scannedTracks = ArrayList<Track>()

            // 1. Query Android MediaStore
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA
                )

                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val cursor = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null
                )

                cursor?.use {
                    val titleIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val pathIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (it.moveToNext()) {
                        val path = it.getString(pathIdx) ?: continue
                        val file = File(path)
                        if (!file.exists()) continue

                        val title = it.getString(titleIdx) ?: file.nameWithoutExtension
                        val artist = it.getString(artistIdx) ?: "Unknown Artist"
                        val album = it.getString(albumIdx) ?: "Spindle Vault"
                        val duration = it.getLong(durationIdx)
                        val ext = file.extension.uppercase()

                        scannedTracks.add(
                            Track(
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                filePath = path,
                                format = ext.ifEmpty { "AUDIO" }
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback to direct directory crawler
            }

            // 2. Direct MicroSD file crawl for unindexed FAT32 files
            val storageLocations = listOf(
                "/storage/sdcard1/Music",
                "/storage/extSdCard/Music",
                "/sdcard/Music",
                "/storage/emulated/0/Music"
            )

            val existingPaths = scannedTracks.map { it.filePath }.toHashSet()
            for (path in storageLocations) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    crawlDirectory(dir, scannedTracks, existingPaths)
                }
            }

            // 3. Batch insert into SQLite
            if (scannedTracks.isNotEmpty()) {
                dbHelper.insertTracksBatch(scannedTracks)
            }

            val totalCount = dbHelper.getTrackCount()
            callback?.onScanComplete(totalCount)
        }
    }

    private fun crawlDirectory(
        directory: File,
        targetList: MutableList<Track>,
        seenPaths: MutableSet<String>
    ) {
        val supportedExtensions = setOf("flac", "mp3", "wav", "m4a", "aac", "ogg")
        val files = directory.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                crawlDirectory(file, targetList, seenPaths)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in supportedExtensions && !seenPaths.contains(file.absolutePath)) {
                    seenPaths.add(file.absolutePath)
                    extractTrackMetadata(file)?.let { targetList.add(it) }
                }
            }
        }
    }

    private fun extractTrackMetadata(file: File): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "MicroSD Vault"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val bitrate = (bitrateStr?.toIntOrNull() ?: 0) / 1000

            // Native audio stream header inspection for FLAC and WAV
            val specs = AudioHeaderParser.parse(file)
            val sampleRate = specs?.sampleRate ?: 0
            val bitDepth = specs?.bitDepth ?: 0

            Track(
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                filePath = file.absolutePath,
                format = file.extension.uppercase(),
                bitrate = bitrate,
                sampleRate = sampleRate,
                bitDepth = bitDepth
            )
        } catch (e: Exception) {
            Track(
                title = file.nameWithoutExtension,
                artist = "Unknown Artist",
                album = "MicroSD",
                durationMs = 0L,
                filePath = file.absolutePath,
                format = file.extension.uppercase()
            )
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }
}
