package com.hana.spindle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class CueSheetParserTest {

    @Test
    fun testParseTimeToMs_accurateRedBookConversion() {
        // 00:00:00 = 0 ms
        assertEquals(0L, CueSheetParser.parseTimeToMs("00:00:00"))

        // 01:23:45:
        // 1 min = 60,000 ms
        // 23 sec = 23,000 ms
        // 45 frames = (45 * 1000) / 75 = 600 ms
        // Total = 83,600 ms
        assertEquals(83600L, CueSheetParser.parseTimeToMs("01:23:45"))

        // 04:12:00 = 4 * 60,000 + 12,000 = 252,000 ms
        assertEquals(252000L, CueSheetParser.parseTimeToMs("04:12:00"))

        // 74 frames = (74 * 1000) / 75 = 986 ms
        assertEquals(986L, CueSheetParser.parseTimeToMs("00:00:74"))
    }

    @Test
    fun testFormatMsToTime_accurateRedBookFormatting() {
        assertEquals("00:00:00", CueSheetParser.formatMsToTime(0L))
        assertEquals("01:23:45", CueSheetParser.formatMsToTime(83600L))
        assertEquals("04:12:00", CueSheetParser.formatMsToTime(252000L))

        val virtualPath = "/music/album.flac#cue:3:83600"
        assertEquals("01:23:45", CueSheetParser.getCueFormattedOffset(virtualPath))
    }

    @Test
    fun testParseCueText_standardEacCueSyntax() {
        val cueData = """
            REM GENRE "Progressive Rock"
            REM DATE 1973
            PERFORMER "Pink Floyd"
            TITLE "The Dark Side of the Moon"
            FILE "DarkSide.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Speak to Me"
                PERFORMER "Pink Floyd"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Breathe (In the Air)"
                PERFORMER "Pink Floyd"
                INDEX 01 01:30:00
              TRACK 03 AUDIO
                TITLE "On the Run"
                PERFORMER "Pink Floyd"
                INDEX 01 04:13:25
        """.trimIndent()

        val baseDir = File("/storage/music")
        val tracks = CueSheetParser.parseCueText(
            text = cueData,
            baseDir = baseDir,
            cuePath = "/storage/music/DarkSide.cue",
            totalAudioDurationMs = 500000L
        )

        assertEquals(3, tracks.size)

        // Track 1
        val t1 = tracks[0]
        assertEquals(1, t1.trackNumber)
        assertEquals("Speak to Me", t1.title)
        assertEquals("Pink Floyd", t1.performer)
        assertEquals("The Dark Side of the Moon", t1.album)
        assertEquals("Progressive Rock", t1.genre)
        assertEquals(1973, t1.year)
        assertEquals(0L, t1.startTimeMs)
        assertEquals(90000L, t1.durationMs) // 01:30:00 - 00:00:00

        // Track 2
        val t2 = tracks[1]
        assertEquals(2, t2.trackNumber)
        assertEquals("Breathe (In the Air)", t2.title)
        assertEquals(90000L, t2.startTimeMs)
        // Track 3 starts at 04:13:25 = 4*60000 + 13000 + (25*1000/75 = 333) = 253333L
        val expectedT3Start = 253333L
        assertEquals(expectedT3Start - 90000L, t2.durationMs)

        // Track 3
        val t3 = tracks[2]
        assertEquals(3, t3.trackNumber)
        assertEquals("On the Run", t3.title)
        assertEquals(expectedT3Start, t3.startTimeMs)
        assertEquals(500000L - expectedT3Start, t3.durationMs)
    }

    @Test
    fun testParseCueText_multiFileCueSheet() {
        val cueData = """
            PERFORMER "Led Zeppelin"
            TITLE "Physical Graffiti"
            REM DISCNUMBER 1
            FILE "Disc1.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Custard Pie"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "The Rover"
                INDEX 01 04:13:00
            REM DISCNUMBER 2
            FILE "Disc2.flac" WAVE
              TRACK 03 AUDIO
                TITLE "In the Light"
                INDEX 01 00:00:00
        """.trimIndent()

        val baseDir = File("/storage/music")
        val tracks = CueSheetParser.parseCueText(
            text = cueData,
            baseDir = baseDir,
            cuePath = "/storage/music/PhysicalGraffiti.cue",
            totalAudioDurationMs = 600000L
        )

        assertEquals(3, tracks.size)
        assertEquals(File(baseDir, "Disc1.flac").absolutePath, tracks[0].audioFilePath)
        assertEquals(1, tracks[0].discNumber)
        assertEquals("Custard Pie", tracks[0].title)
        assertEquals(253000L, tracks[0].durationMs) // 04:13:00 = 253,000ms

        assertEquals(File(baseDir, "Disc1.flac").absolutePath, tracks[1].audioFilePath)
        assertEquals("The Rover", tracks[1].title)

        assertEquals(File(baseDir, "Disc2.flac").absolutePath, tracks[2].audioFilePath)
        assertEquals(2, tracks[2].discNumber)
        assertEquals("In the Light", tracks[2].title)
        assertEquals(0L, tracks[2].startTimeMs)
    }

    @Test
    fun testParseCueText_remMetadataAndSongwriter() {
        val cueData = """
            REM GENRE "Psychedelic Rock"
            REM DATE 1971
            REM DISCNUMBER 2
            PERFORMER "Pink Floyd"
            TITLE "Meddle"
            SONGWRITER "Roger Waters"
            FILE "Meddle.flac" WAVE
              TRACK 01 AUDIO
                TITLE "One of These Days"
                SONGWRITER "Waters, Gilmour, Wright, Mason"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "A Pillow of Winds"
                INDEX 01 05:57:00
        """.trimIndent()

        val tracks = CueSheetParser.parseCueText(
            text = cueData,
            baseDir = File("/music"),
            cuePath = null,
            totalAudioDurationMs = 900000L
        )

        assertEquals(2, tracks.size)
        assertEquals("Psychedelic Rock", tracks[0].genre)
        assertEquals(1971, tracks[0].year)
        assertEquals(2, tracks[0].discNumber)
        assertEquals("Waters, Gilmour, Wright, Mason", tracks[0].composer)

        assertEquals("Roger Waters", tracks[1].composer)
        assertEquals(2, tracks[1].discNumber)
    }

    @Test
    fun testParseVorbisCommentForCue_binaryExtraction() {
        val baos = ByteArrayOutputStream()

        fun writeIntLe(value: Int) {
            baos.write(value and 0xFF)
            baos.write((value shr 8) and 0xFF)
            baos.write((value shr 16) and 0xFF)
            baos.write((value shr 24) and 0xFF)
        }

        // Vendor string: "reference libFLAC 1.4.2"
        val vendor = "reference libFLAC 1.4.2".toByteArray(Charsets.UTF_8)
        writeIntLe(vendor.size)
        baos.write(vendor)

        // 2 comments:
        // 1. "ARTIST=Radiohead"
        // 2. "CUESHEET=TITLE \"OK Computer\"\nTRACK 01 AUDIO\nINDEX 01 00:00:00"
        writeIntLe(2)

        val c1 = "ARTIST=Radiohead".toByteArray(Charsets.UTF_8)
        writeIntLe(c1.size)
        baos.write(c1)

        val cueSheetText = "TITLE \"OK Computer\"\nTRACK 01 AUDIO\nINDEX 01 00:00:00"
        val c2 = "CUESHEET=$cueSheetText".toByteArray(Charsets.UTF_8)
        writeIntLe(c2.size)
        baos.write(c2)

        val vorbisBlockBytes = baos.toByteArray()
        val extractedCue = CueSheetParser.parseVorbisCommentForCue(vorbisBlockBytes)

        assertNotNull(extractedCue)
        assertEquals(cueSheetText, extractedCue)
    }

    @Test
    fun testVirtualPathHelpers_formattingAndExtraction() {
        val audioPath = "/storage/emulated/0/Music/Album.flac"
        val trackNum = 4
        val startMs = 125600L

        val virtualPath = CueSheetParser.formatVirtualPath(audioPath, trackNum, startMs)
        assertEquals("/storage/emulated/0/Music/Album.flac#cue:4:125600", virtualPath)

        assertTrue(CueSheetParser.isCueVirtualPath(virtualPath))
        assertFalse(CueSheetParser.isCueVirtualPath(audioPath))

        assertEquals(audioPath, CueSheetParser.getAudioFilePath(virtualPath))
        assertEquals(audioPath, CueSheetParser.getAudioFilePath(audioPath))

        assertEquals(startMs, CueSheetParser.getCueStartTimeMs(virtualPath))
        assertEquals(0L, CueSheetParser.getCueStartTimeMs(audioPath))

        assertEquals(trackNum, CueSheetParser.getCueTrackNumber(virtualPath))
        assertEquals(0, CueSheetParser.getCueTrackNumber(audioPath))
    }

    @Test
    fun testCueTrack_toTrackEntityConversion() {
        val cueTrack = CueTrack(
            trackNumber = 3,
            title = "Subterranean Homesick Alien",
            performer = "Radiohead",
            album = "OK Computer",
            startTimeMs = 540000L,
            durationMs = 267000L,
            audioFilePath = "/sdcard/Music/Radiohead - OK Computer.flac",
            genre = "Art Rock",
            year = 1997,
            discNumber = 1,
            composer = "Thom Yorke"
        )

        val entity = cueTrack.toTrackEntity(bitDepth = 24, sampleRate = 96000, channels = 2, bitrateKbps = 2400)
        assertEquals("Subterranean Homesick Alien", entity.title)
        assertEquals("Radiohead", entity.artist)
        assertEquals("OK Computer", entity.album)
        assertEquals(267000L, entity.durationMs)
        assertEquals(3, entity.trackNumber)
        assertEquals(1997, entity.year)
        assertEquals("Art Rock", entity.genre)
        assertEquals(24, entity.bitDepth)
        assertEquals(96000, entity.sampleRate)
        assertEquals(2400, entity.bitrateKbps)
        assertEquals(2, entity.channels)
        assertEquals(1, entity.discNumber)
        assertEquals("Thom Yorke", entity.composer)
        assertEquals("FLAC (CUE)", entity.fileFormat)
        assertEquals("/sdcard/Music/Radiohead - OK Computer.flac#cue:3:540000", entity.path)
    }

    @Test
    fun testParseEmbeddedCue_fallbackAudioFile() {
        val embeddedCue = """
            PERFORMER "Miles Davis"
            TITLE "Kind of Blue"
            TRACK 01 AUDIO
              TITLE "So What"
              INDEX 01 00:00:00
            TRACK 02 AUDIO
              TITLE "Freddie Freeloader"
              INDEX 01 09:22:00
        """.trimIndent()

        val mockFile = File("/music/Kind_of_Blue.flac")
        val tracks = CueSheetParser.parseEmbeddedCue(
            cueText = embeddedCue,
            audioFile = mockFile,
            totalAudioDurationMs = 1100000L
        )

        assertEquals(2, tracks.size)
        assertEquals(mockFile.absolutePath, tracks[0].audioFilePath)
        assertEquals("So What", tracks[0].title)
        assertEquals(0L, tracks[0].startTimeMs)
        assertEquals(562000L, tracks[0].durationMs)

        assertEquals(mockFile.absolutePath, tracks[1].audioFilePath)
        assertEquals("Freddie Freeloader", tracks[1].title)
        assertEquals(562000L, tracks[1].startTimeMs)
    }
}
