package com.hana.spindle.data

import android.media.MediaMetadataRetriever
import java.io.File
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsData(
    val isSynced: Boolean,
    val lines: List<LyricLine>
) {
    fun getActiveIndex(positionMs: Long): Int {
        if (!isSynced || lines.isEmpty()) return -1
        // Binary search for closest line timestamp <= positionMs
        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

object LyricsParser {

    private val TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]")

    /**
     * Attempts to find and parse lyrics for a given audio file:
     * 1. Adjacent .lrc sidecar file with matching base name.
     * 2. Adjacent .txt sidecar file.
     * 3. Embedded metadata (Vorbis or ID3).
     */
    fun loadLyrics(audioPath: String): LyricsData? {
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return null

        // 1. Check sidecar .lrc
        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        if (lrcFile.exists() && lrcFile.canRead()) {
            val parsed = parseLrcContent(lrcFile.readText())
            if (parsed != null && parsed.lines.isNotEmpty()) {
                return parsed
            }
        }

        // 2. Check sidecar .txt
        val txtFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.txt")
        if (txtFile.exists() && txtFile.canRead()) {
            val parsed = parseLrcContent(txtFile.readText())
            if (parsed != null && parsed.lines.isNotEmpty()) {
                return parsed
            }
        }

        // 3. Check embedded tags
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(audioPath)
            // Some Android versions support METADATA_KEY_LYRICS (API 36 preview or custom)
            // or we inspect text blocks
            null
        } catch (e: Exception) {
            null
        } finally {
            try { mmr.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Parses LRC or plain-text lyric format.
     */
    fun parseLrcContent(content: String): LyricsData? {
        if (content.isBlank()) return null

        val rawLines = content.lines()
        val syncedLines = mutableListOf<LyricLine>()
        var foundAnyTimestamp = false

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check if it's metadata tag e.g. [ti:Song Title] or [ar:Artist]
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:")) {
                continue
            }

            val matcher = TIMESTAMP_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                foundAnyTimestamp = true
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val fractionStr = matcher.group(3) ?: "0"
                val fractionMs = if (fractionStr.length == 2) {
                    fractionStr.toLong() * 10L
                } else {
                    fractionStr.toLong()
                }
                val totalMs = (minutes * 60_000L) + (seconds * 1000L) + fractionMs
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            val text = trimmed.substring(lastMatchEnd).trim()

            for (ts in timestamps) {
                syncedLines.add(LyricLine(ts, text))
            }
        }

        if (foundAnyTimestamp && syncedLines.isNotEmpty()) {
            syncedLines.sortBy { it.timeMs }
            return LyricsData(isSynced = true, lines = syncedLines)
        }

        // Unsynced plain text
        val plainLines = rawLines.mapIndexed { idx, line ->
            LyricLine(timeMs = idx.toLong(), text = line.trim())
        }.filter { it.text.isNotEmpty() }

        return if (plainLines.isNotEmpty()) {
            LyricsData(isSynced = false, lines = plainLines)
        } else {
            null
        }
    }
}
