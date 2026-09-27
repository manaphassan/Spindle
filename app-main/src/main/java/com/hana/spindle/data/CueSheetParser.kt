package com.hana.spindle.data

import com.hana.spindle.data.db.TrackEntity
import java.io.File
import java.util.Locale

/**
 * Data model for a virtual sub-track extracted from an audiophile CUE sheet.
 */
data class CueTrack(
    val trackNumber: Int,
    val title: String,
    val performer: String,
    val album: String,
    val albumArtist: String? = null,
    val startTimeMs: Long,
    val durationMs: Long = 0L,
    val audioFilePath: String,
    val cueFilePath: String? = null,
    val genre: String? = null,
    val year: Int = 0
) {
    /**
     * Converts a CueTrack into a standard Spindle TrackEntity.
     * Encodes the virtual CUE offset in the path: `<audioFilePath>#cue:<trackNumber>:<startTimeMs>`
     */
    fun toTrackEntity(
        id: Long = 0L,
        fileFormat: String = "FLAC",
        bitDepth: Int = 16,
        sampleRate: Int = 44100
    ): TrackEntity {
        return TrackEntity(
            id = id,
            title = title,
            artist = performer,
            album = album,
            albumArtist = albumArtist,
            durationMs = durationMs,
            path = CueSheetParser.formatVirtualPath(audioFilePath, trackNumber, startTimeMs),
            trackNumber = trackNumber,
            year = year,
            genre = genre,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            fileFormat = "$fileFormat (CUE)"
        )
    }
}

/**
 * Audiophile CUE Sheet Splitter & Parser Engine.
 *
 * Implements standard CDRWIN / EAC / Exact Audio Copy / XLD CUE sheet syntax:
 * - Parses monolithic albums (FLAC, APE, WAV, WV) accompanied by `.cue` files.
 * - Parses embedded `CUESHEET` metadata blocks in FLAC/Vorbis headers.
 * - Accurately converts CD-DA Red Book frames (75 fps) to sample-accurate milliseconds.
 * - Generates virtual sub-tracks for seamless, gapless playback queue integration.
 */
object CueSheetParser {

    private const val CUE_PREFIX = "#cue:"

    /**
     * Parses an external `.cue` file on disk.
     * Resolves referenced audio files relative to the `.cue` file's parent directory.
     *
     * @param cueFile The .cue file to parse
     * @param totalAudioDurationMs Optional total duration of the underlying audio file in ms
     */
    fun parseCueFile(cueFile: File, totalAudioDurationMs: Long = 0L): List<CueTrack> {
        if (!cueFile.exists() || !cueFile.canRead()) return emptyList()
        val text = try {
            cueFile.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                // Fallback for Shift-JIS or Windows-1252 encoded vintage rips
                cueFile.readText(Charsets.ISO_8859_1)
            } catch (ignored: Exception) {
                return emptyList()
            }
        }
        return parseCueText(text, cueFile.parentFile, cueFile.absolutePath, totalAudioDurationMs)
    }

    /**
     * Parses an embedded CUE sheet string from FLAC/Vorbis metadata.
     *
     * @param cueText Raw text from the CUESHEET tag
     * @param audioFile The host audio file
     * @param totalAudioDurationMs Total duration of the audio file in ms
     */
    fun parseEmbeddedCue(
        cueText: String,
        audioFile: File,
        totalAudioDurationMs: Long = 0L
    ): List<CueTrack> {
        if (cueText.isBlank()) return emptyList()
        return parseCueText(cueText, audioFile.parentFile, null, totalAudioDurationMs, fallbackAudioFile = audioFile)
    }

    /**
     * Core parsing engine converting CUE syntax to a list of CueTrack objects.
     */
    fun parseCueText(
        text: String,
        baseDir: File?,
        cuePath: String?,
        totalAudioDurationMs: Long = 0L,
        fallbackAudioFile: File? = null
    ): List<CueTrack> {
        val lines = text.lines()
        var albumTitle = "Unknown Album"
        var albumPerformer = "Unknown Artist"
        var albumGenre: String? = null
        var albumYear = 0
        var currentAudioFile: File? = fallbackAudioFile

        val rawTracks = mutableListOf<MutableCueTrack>()
        var activeTrack: MutableCueTrack? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val tokens = splitCommand(line)
            if (tokens.isEmpty()) continue
            val command = tokens[0].uppercase(Locale.ROOT)

            when (command) {
                "REM" -> {
                    if (tokens.size >= 3) {
                        val remKey = tokens[1].uppercase(Locale.ROOT)
                        val remVal = cleanQuotes(tokens.drop(2).joinToString(" "))
                        when (remKey) {
                            "GENRE" -> if (activeTrack == null) albumGenre = remVal else activeTrack.genre = remVal
                            "DATE", "YEAR" -> {
                                val y = remVal.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                                if (activeTrack == null) albumYear = y else activeTrack.year = y
                            }
                        }
                    }
                }
                "TITLE" -> {
                    val title = cleanQuotes(tokens.drop(1).joinToString(" "))
                    if (activeTrack == null) {
                        albumTitle = title
                    } else {
                        activeTrack.title = title
                    }
                }
                "PERFORMER" -> {
                    val performer = cleanQuotes(tokens.drop(1).joinToString(" "))
                    if (activeTrack == null) {
                        albumPerformer = performer
                    } else {
                        activeTrack.performer = performer
                    }
                }
                "FILE" -> {
                    if (tokens.size >= 2) {
                        val fileName = cleanQuotes(tokens[1])
                        val candidate = if (baseDir != null) File(baseDir, fileName) else File(fileName)
                        currentAudioFile = if (candidate.exists()) {
                            candidate
                        } else {
                            // If extension differed (e.g. .wav specified in cue, but file is .flac)
                            val nameWithoutExt = candidate.nameWithoutExtension
                            baseDir?.listFiles()?.firstOrNull { it.nameWithoutExtension.equals(nameWithoutExt, ignoreCase = true) }
                                ?: fallbackAudioFile ?: candidate
                        }
                    }
                }
                "TRACK" -> {
                    val trackNum = tokens.getOrNull(1)?.toIntOrNull() ?: (rawTracks.size + 1)
                    activeTrack?.let { rawTracks.add(it) }
                    activeTrack = MutableCueTrack(
                        trackNumber = trackNum,
                        audioFilePath = currentAudioFile?.absolutePath ?: fallbackAudioFile?.absolutePath ?: "",
                        cueFilePath = cuePath,
                        album = albumTitle,
                        albumArtist = albumPerformer,
                        genre = albumGenre,
                        year = albumYear
                    )
                }
                "INDEX" -> {
                    if (tokens.size >= 3) {
                        val indexId = tokens[1]
                        val timeStr = tokens[2]
                        val ms = parseTimeToMs(timeStr)
                        if (indexId == "01" && activeTrack != null) {
                            activeTrack.startTimeMs = ms
                        }
                    }
                }
            }
        }
        activeTrack?.let { rawTracks.add(it) }

        if (rawTracks.isEmpty()) return emptyList()

        // Calculate track durations by inspecting start times of successive tracks
        val result = mutableListOf<CueTrack>()
        for (i in 0 until rawTracks.size) {
            val cur = rawTracks[i]
            val nextStart = if (i + 1 < rawTracks.size) rawTracks[i + 1].startTimeMs else totalAudioDurationMs
            val duration = if (nextStart > cur.startTimeMs) {
                nextStart - cur.startTimeMs
            } else {
                0L
            }

            result.add(
                CueTrack(
                    trackNumber = cur.trackNumber,
                    title = cur.title.ifBlank { "Track ${cur.trackNumber}" },
                    performer = cur.performer.ifBlank { albumPerformer },
                    album = cur.album.ifBlank { albumTitle },
                    albumArtist = albumPerformer,
                    startTimeMs = cur.startTimeMs,
                    durationMs = duration,
                    audioFilePath = cur.audioFilePath,
                    cueFilePath = cur.cueFilePath,
                    genre = cur.genre ?: albumGenre,
                    year = if (cur.year > 0) cur.year else albumYear
                )
            )
        }

        return result
    }

    /**
     * Converts CD-DA Red Book time string (mm:ss:ff) into milliseconds.
     * 1 second = 75 frames (sectors), 1 frame = 13.333ms.
     */
    fun parseTimeToMs(timeStr: String): Long {
        val parts = timeStr.trim().split(":")
        if (parts.size != 3) return 0L
        val mm = parts[0].toLongOrNull() ?: 0L
        val ss = parts[1].toLongOrNull() ?: 0L
        val ff = parts[2].toLongOrNull() ?: 0L
        return (mm * 60L * 1000L) + (ss * 1000L) + ((ff * 1000L) / 75L)
    }

    /**
     * Formats virtual path containing audio file path and cue seek offset:
     * `<audioPath>#cue:<trackNumber>:<startTimeMs>`
     */
    fun formatVirtualPath(audioPath: String, trackNumber: Int, startTimeMs: Long): String {
        return "$audioPath$CUE_PREFIX$trackNumber:$startTimeMs"
    }

    /**
     * Returns true if path is a virtual CUE sub-track.
     */
    fun isCueVirtualPath(path: String): Boolean {
        return path.contains(CUE_PREFIX)
    }

    /**
     * Extracts the real audio file path from a virtual CUE path.
     */
    fun getAudioFilePath(path: String): String {
        return if (isCueVirtualPath(path)) {
            path.substringBefore(CUE_PREFIX)
        } else {
            path
        }
    }

    /**
     * Extracts the seek start offset in milliseconds from a virtual CUE path.
     */
    fun getCueStartTimeMs(path: String): Long {
        if (!isCueVirtualPath(path)) return 0L
        val cueInfo = path.substringAfter(CUE_PREFIX)
        val parts = cueInfo.split(":")
        return if (parts.size >= 2) parts[1].toLongOrNull() ?: 0L else 0L
    }

    /**
     * Extracts the track number from a virtual CUE path.
     */
    fun getCueTrackNumber(path: String): Int {
        if (!isCueVirtualPath(path)) return 0
        val cueInfo = path.substringAfter(CUE_PREFIX)
        val parts = cueInfo.split(":")
        return if (parts.isNotEmpty()) parts[0].toIntOrNull() ?: 0 else 0
    }

    private fun splitCommand(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var sb = StringBuilder()
        var inQuotes = false

        for (c in line) {
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    sb.append(c)
                }
                c.isWhitespace() && !inQuotes -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb = StringBuilder()
                    }
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }

    private fun cleanQuotes(str: String): String {
        return str.trim().trim('"', '\'')
    }

    private data class MutableCueTrack(
        var trackNumber: Int,
        var title: String = "",
        var performer: String = "",
        var album: String = "",
        var albumArtist: String? = null,
        var startTimeMs: Long = 0L,
        var audioFilePath: String = "",
        var cueFilePath: String? = null,
        var genre: String? = null,
        var year: Int = 0
    )
}
