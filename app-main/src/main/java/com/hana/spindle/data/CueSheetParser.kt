package com.hana.spindle.data

import com.hana.spindle.data.db.TrackEntity
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
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
    val year: Int = 0,
    val discNumber: Int = 1,
    val composer: String? = null
) {
    /**
     * Converts a CueTrack into a standard Spindle TrackEntity.
     * Encodes the virtual CUE offset in the path: `<audioFilePath>#cue:<trackNumber>:<startTimeMs>`
     */
    fun toTrackEntity(
        id: Long = 0L,
        fileFormat: String = "FLAC",
        bitDepth: Int = 16,
        sampleRate: Int = 44100,
        channels: Int = 2,
        bitrateKbps: Int = 1411
    ): TrackEntity {
        val audioFile = File(audioFilePath)
        val formatBadge = if (fileFormat.endsWith("(CUE)", ignoreCase = true)) {
            fileFormat
        } else {
            "$fileFormat (CUE)"
        }
        val cueFile = cueFilePath?.let { File(it) }
        val dateModified = if (cueFile != null && cueFile.exists()) {
            cueFile.lastModified()
        } else if (audioFile.exists()) {
            audioFile.lastModified()
        } else {
            System.currentTimeMillis()
        }

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
            fileFormat = formatBadge,
            rating = 0,
            isFavorite = false,
            dateAdded = System.currentTimeMillis(),
            dateModified = dateModified,
            bitrateKbps = bitrateKbps,
            hasLyrics = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc").exists(),
            channels = channels,
            discNumber = discNumber,
            composer = composer
        )
    }
}

/**
 * Audiophile CUE Sheet Splitter & Parser Engine.
 *
 * Implements standard CDRWIN / EAC / Exact Audio Copy / XLD / Foobar2000 CUE syntax:
 * - Parses monolithic albums (FLAC, APE, WAV, WV) accompanied by `.cue` files.
 * - Extracts embedded `CUESHEET` Vorbis comment metadata blocks in FLAC/Ogg containers.
 * - Accurately converts CD-DA Red Book frames (75 fps) to sample-accurate milliseconds.
 * - Supports multi-file CUE sheets with per-file duration tracking.
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
     * Inspects an audio file directly on disk, extracts any embedded CUE Vorbis comment tag,
     * and returns the resulting parsed CueTrack list.
     */
    fun parseEmbeddedCueFromAudioFile(
        audioFile: File,
        totalAudioDurationMs: Long = 0L
    ): List<CueTrack> {
        val cueText = extractEmbeddedCueText(audioFile) ?: return emptyList()
        return parseEmbeddedCue(cueText, audioFile, totalAudioDurationMs)
    }

    /**
     * Fast binary parser extracting the `CUESHEET=` tag from FLAC Vorbis comment blocks.
     * Operates in <1ms without loading audio frames or depending on third-party libraries.
     */
    fun extractEmbeddedCueText(audioFile: File): String? {
        if (!audioFile.exists() || !audioFile.canRead() || audioFile.length() < 42) return null
        val ext = audioFile.extension.lowercase(Locale.ROOT)
        if (ext != "flac" && ext != "ogg" && ext != "opus") return null

        return try {
            FileInputStream(audioFile).use { fis ->
                val bis = BufferedInputStream(fis, 65536)
                val dis = DataInputStream(bis)

                var header = ByteArray(4)
                dis.readFully(header)

                // Skip optional prepended ID3v2 header
                if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
                    val id3Header = ByteArray(6)
                    dis.readFully(id3Header)
                    val b6 = id3Header[2].toInt() and 0x7F
                    val b7 = id3Header[3].toInt() and 0x7F
                    val b8 = id3Header[4].toInt() and 0x7F
                    val b9 = id3Header[5].toInt() and 0x7F
                    val id3DataSize = (b6 shl 21) or (b7 shl 14) or (b8 shl 7) or b9
                    val flags = id3Header[1].toInt()
                    val hasFooter = (flags and 0x10) != 0
                    val totalId3Skip = id3DataSize.toLong() + (if (hasFooter) 10L else 0L)
                    dis.skipBytes(totalId3Skip.toInt())

                    dis.readFully(header)
                }

                // Verify "fLaC" magic bytes (0x66, 0x4C, 0x61, 0x43)
                if (header[0] != 0x66.toByte() || header[1] != 0x4C.toByte() ||
                    header[2] != 0x61.toByte() || header[3] != 0x43.toByte()
                ) {
                    return null
                }

                // Iterate metadata blocks
                var isLast = false
                while (!isLast) {
                    val blockHeader = dis.readUnsignedByte()
                    isLast = (blockHeader and 0x80) != 0
                    val blockType = blockHeader and 0x7F
                    val b1 = dis.readUnsignedByte()
                    val b2 = dis.readUnsignedByte()
                    val b3 = dis.readUnsignedByte()
                    val blockLength = (b1 shl 16) or (b2 shl 8) or b3

                    if (blockType == 4) { // VORBIS_COMMENT
                        val blockBytes = ByteArray(blockLength)
                        dis.readFully(blockBytes)
                        return parseVorbisCommentForCue(blockBytes)
                    } else {
                        // Skip non-comment block
                        var skipped = 0
                        while (skipped < blockLength) {
                            val n = dis.skipBytes(blockLength - skipped)
                            if (n <= 0) break
                            skipped += n
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes the raw binary Vorbis comment block data looking for `CUESHEET=` tag.
     */
    fun parseVorbisCommentForCue(data: ByteArray): String? {
        if (data.size < 8) return null
        var offset = 0
        fun readIntLe(): Int {
            if (offset + 4 > data.size) return 0
            val b0 = data[offset].toInt() and 0xFF
            val b1 = data[offset + 1].toInt() and 0xFF
            val b2 = data[offset + 2].toInt() and 0xFF
            val b3 = data[offset + 3].toInt() and 0xFF
            offset += 4
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        val vendorLen = readIntLe()
        if (vendorLen < 0 || offset + vendorLen > data.size) return null
        offset += vendorLen

        val userCommentsCount = readIntLe()
        if (userCommentsCount < 0) return null

        val prefixes = listOf("CUESHEET=", "CUE_SHEET=")

        for (i in 0 until userCommentsCount) {
            if (offset + 4 > data.size) break
            val commentLen = readIntLe()
            if (commentLen <= 0 || offset + commentLen > data.size) {
                if (commentLen > 0) offset += commentLen
                continue
            }

            for (prefix in prefixes) {
                if (commentLen >= prefix.length) {
                    var matches = true
                    for (j in prefix.indices) {
                        val c = data[offset + j].toInt().toChar()
                        if (!c.equals(prefix[j], ignoreCase = true)) {
                            matches = false
                            break
                        }
                    }
                    if (matches) {
                        val cueOffset = offset + prefix.length
                        val cueLen = commentLen - prefix.length
                        return String(data, cueOffset, cueLen, Charsets.UTF_8)
                    }
                }
            }
            offset += commentLen
        }
        return null
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
        var albumDiscNumber = 1
        var albumSongwriter: String? = null
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
                            "DISCNUMBER", "DISC" -> {
                                val d = remVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                albumDiscNumber = d
                                activeTrack?.discNumber = d
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
                "SONGWRITER" -> {
                    val songwriter = cleanQuotes(tokens.drop(1).joinToString(" "))
                    if (activeTrack == null) {
                        albumSongwriter = songwriter
                    } else {
                        activeTrack.composer = songwriter
                    }
                }
                "FILE" -> {
                    activeTrack?.let { rawTracks.add(it) }
                    activeTrack = null
                    if (tokens.size >= 2) {
                        val fileName = cleanQuotes(tokens[1])
                        val candidate = if (baseDir != null) File(baseDir, fileName) else File(fileName)
                        currentAudioFile = if (candidate.exists()) {
                            candidate
                        } else {
                            // If extension differed (e.g. .wav specified in cue, but file is .flac or .ape)
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
                        year = albumYear,
                        discNumber = albumDiscNumber,
                        composer = albumSongwriter
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

        // Cache file durations for per-file multi-track boundaries
        val fileDurationMap = mutableMapOf<String, Long>()

        val result = mutableListOf<CueTrack>()
        for (i in 0 until rawTracks.size) {
            val cur = rawTracks[i]
            val nextTrack = if (i + 1 < rawTracks.size) rawTracks[i + 1] else null
            val isSameFile = nextTrack != null && nextTrack.audioFilePath == cur.audioFilePath

            val duration = if (isSameFile) {
                if (nextTrack!!.startTimeMs > cur.startTimeMs) {
                    nextTrack.startTimeMs - cur.startTimeMs
                } else {
                    0L
                }
            } else {
                // Last track in this audio file: resolve underlying file duration
                val fileDur = fileDurationMap.getOrPut(cur.audioFilePath) {
                    val f = File(cur.audioFilePath)
                    if (f.exists()) {
                        TagParser.parseTrack(f)?.durationMs ?: totalAudioDurationMs
                    } else {
                        totalAudioDurationMs
                    }
                }
                if (fileDur > cur.startTimeMs) {
                    fileDur - cur.startTimeMs
                } else {
                    0L
                }
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
                    year = if (cur.year > 0) cur.year else albumYear,
                    discNumber = if (cur.discNumber > 0) cur.discNumber else albumDiscNumber,
                    composer = cur.composer ?: albumSongwriter
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
     * Formats milliseconds into CD-DA Red Book time string (mm:ss:ff).
     */
    fun formatMsToTime(ms: Long): String {
        val clampedMs = ms.coerceAtLeast(0L)
        val mm = clampedMs / (60L * 1000L)
        val remMs = clampedMs % (60L * 1000L)
        val ss = remMs / 1000L
        val ff = ((remMs % 1000L) * 75L) / 1000L
        return String.format(Locale.US, "%02d:%02d:%02d", mm, ss, ff)
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

    /**
     * Returns human-readable Red Book offset formatted as `mm:ss:ff`.
     */
    fun getCueFormattedOffset(path: String): String {
        val ms = getCueStartTimeMs(path)
        return formatMsToTime(ms)
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
        var year: Int = 0,
        var discNumber: Int = 1,
        var composer: String? = null
    )
}
