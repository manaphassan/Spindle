package com.hana.spindle.data

import android.content.Context
import java.io.File
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsData(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    var offsetMs: Long = 0L
) {
    fun getActiveIndex(positionMs: Long): Int {
        if (!isSynced || lines.isEmpty()) return -1
        // Apply calibration offset (positive offset means lyrics appear earlier / timestamps shift lower)
        val adjustedPosition = positionMs + offsetMs
        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= adjustedPosition) {
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
    private const val OFFSET_PREFS_NAME = "spindle_lyrics_offset_prefs"

    /**
     * Attempts to find and parse lyrics for a given audio file with 4-tier resolution:
     * 1. Adjacent .lrc sidecar file with matching base name.
     * 2. App-internal lyrics cache (.lrc downloaded from LRCLIB).
     * 3. Adjacent .txt sidecar file.
     * 4. Embedded metadata tags (FLAC Vorbis comment, MP3 ID3v2 USLT/SYLT, M4A ©lyr).
     */
    fun loadLyrics(audioPath: String, context: Context? = null): LyricsData? {
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return null

        var parsed: LyricsData? = null

        // 1. Check sidecar .lrc
        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        if (lrcFile.exists() && lrcFile.canRead()) {
            parsed = parseLrcContent(lrcFile.readText())
        }

        // 2. Check internal lyrics cache
        if (parsed == null && context != null) {
            val cachedFile = LyricsFetcher.getCachedLyricsFile(context, audioPath)
            if (cachedFile.exists() && cachedFile.canRead()) {
                parsed = parseLrcContent(cachedFile.readText())
            }
        }

        // 3. Check sidecar .txt
        if (parsed == null) {
            val txtFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.txt")
            if (txtFile.exists() && txtFile.canRead()) {
                parsed = parseLrcContent(txtFile.readText())
            }
        }

        // 4. Check embedded audio tags (FLAC Vorbis, MP3 ID3v2, M4A)
        if (parsed == null) {
            val embeddedText = EmbeddedLyricsExtractor.extractLyrics(audioFile)
            if (!embeddedText.isNullOrBlank()) {
                parsed = parseLrcContent(embeddedText)
            }
        }

        // 5. Apply user-calibrated offset if saved in SharedPreferences
        if (parsed != null && context != null) {
            val savedOffset = getSavedLyricOffset(context, audioPath)
            if (savedOffset != null) {
                parsed.offsetMs = savedOffset
            }
        }

        return parsed
    }

    /**
     * Parses LRC or plain-text lyric format.
     * Supports standard timestamp syntax, multi-timestamp lines, and [offset:±X] headers.
     */
    fun parseLrcContent(content: String): LyricsData? {
        if (content.isBlank()) return null

        val rawLines = content.lines()
        val syncedLines = mutableListOf<LyricLine>()
        var foundAnyTimestamp = false
        var parsedHeaderOffsetMs = 0L

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Parse [offset: +/- ms] header
            if (trimmed.startsWith("[offset:", ignoreCase = true) && trimmed.endsWith("]")) {
                val offsetStr = trimmed.substring(8, trimmed.length - 1).trim()
                offsetStr.toLongOrNull()?.let {
                    parsedHeaderOffsetMs = it
                }
                continue
            }

            // Skip standard ID3/LRC metadata headers
            if (trimmed.startsWith("[ti:", ignoreCase = true) ||
                trimmed.startsWith("[ar:", ignoreCase = true) ||
                trimmed.startsWith("[al:", ignoreCase = true) ||
                trimmed.startsWith("[by:", ignoreCase = true) ||
                trimmed.startsWith("[re:", ignoreCase = true) ||
                trimmed.startsWith("[ve:", ignoreCase = true)) {
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
            return LyricsData(
                isSynced = true,
                lines = syncedLines,
                offsetMs = parsedHeaderOffsetMs
            )
        }

        // Unsynced plain text fallback
        val plainLines = rawLines.mapIndexed { idx, line ->
            LyricLine(timeMs = idx.toLong(), text = line.trim())
        }.filter { it.text.isNotEmpty() }

        return if (plainLines.isNotEmpty()) {
            LyricsData(isSynced = false, lines = plainLines)
        } else {
            null
        }
    }

    fun getSavedLyricOffset(context: Context, audioPath: String): Long? {
        val prefs = context.getSharedPreferences(OFFSET_PREFS_NAME, Context.MODE_PRIVATE)
        val key = LyricsFetcher.getSafeLyricKey(audioPath)
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    fun saveLyricOffset(context: Context, audioPath: String, offsetMs: Long) {
        val prefs = context.getSharedPreferences(OFFSET_PREFS_NAME, Context.MODE_PRIVATE)
        val key = LyricsFetcher.getSafeLyricKey(audioPath)
        prefs.edit().putLong(key, offsetMs).apply()
    }
}
