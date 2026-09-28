package com.hana.spindle.data

import org.junit.Assert.*
import org.junit.Test

class LyricsAutoDownloadAndParserTest {

    @Test
    fun testStandardLrcParsing() {
        val lrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [00:05.50]Line one starts
            [00:12.00]Line two follows
            [00:20.75]Chorus begins here
        """.trimIndent()

        val parsed = LyricsParser.parseLrcContent(lrc)
        assertNotNull(parsed)
        assertTrue(parsed!!.isSynced)
        assertEquals(3, parsed.lines.size)

        assertEquals(5500L, parsed.lines[0].timeMs)
        assertEquals("Line one starts", parsed.lines[0].text)

        assertEquals(12000L, parsed.lines[1].timeMs)
        assertEquals("Line two follows", parsed.lines[1].text)

        assertEquals(20750L, parsed.lines[2].timeMs)
        assertEquals("Chorus begins here", parsed.lines[2].text)
    }

    @Test
    fun testMultiTimestampAndSorting() {
        val lrc = """
            [00:30.00][00:10.00]This chorus repeats twice
            [00:02.00]Intro line
        """.trimIndent()

        val parsed = LyricsParser.parseLrcContent(lrc)
        assertNotNull(parsed)
        assertTrue(parsed!!.isSynced)
        assertEquals(3, parsed.lines.size)

        // Must be sorted chronologically
        assertEquals(2000L, parsed.lines[0].timeMs)
        assertEquals("Intro line", parsed.lines[0].text)

        assertEquals(10000L, parsed.lines[1].timeMs)
        assertEquals("This chorus repeats twice", parsed.lines[1].text)

        assertEquals(30000L, parsed.lines[2].timeMs)
        assertEquals("This chorus repeats twice", parsed.lines[2].text)
    }

    @Test
    fun testOffsetHeaderAndActiveIndexAdjustment() {
        val lrc = """
            [offset:+500]
            [00:10.00]Synchronized milestone
            [00:20.00]Second milestone
        """.trimIndent()

        val parsed = LyricsParser.parseLrcContent(lrc)
        assertNotNull(parsed)
        assertEquals(500L, parsed!!.offsetMs)

        // At 9400ms + 500ms offset = 9900ms (< 10000ms) -> activeIndex is -1
        assertEquals(-1, parsed.getActiveIndex(9400L))

        // At 9500ms + 500ms offset = 10000ms -> activeIndex is 0
        assertEquals(0, parsed.getActiveIndex(9500L))

        // Negative offset test
        parsed.offsetMs = -1000L // lyrics lag by 1s
        // At 10500ms - 1000ms = 9500ms -> activeIndex is -1
        assertEquals(-1, parsed.getActiveIndex(10500L))
        // At 11000ms - 1000ms = 10000ms -> activeIndex is 0
        assertEquals(0, parsed.getActiveIndex(11000L))
    }

    @Test
    fun testUnsyncedPlainTextFallback() {
        val text = """
            First verse without any timestamps
            Second verse is also plain
            Final outro
        """.trimIndent()

        val parsed = LyricsParser.parseLrcContent(text)
        assertNotNull(parsed)
        assertFalse(parsed!!.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals("First verse without any timestamps", parsed.lines[0].text)
        assertEquals("Final outro", parsed.lines[2].text)
    }

    @Test
    fun testLrclibSearchTermSanitization() {
        val (title1, artist1) = LyricsFetcher.cleanSearchTerm(
            "Get Lucky (Radio Edit) [feat. Pharrell Williams]",
            "Daft Punk feat. Pharrell Williams"
        )
        assertEquals("Get Lucky", title1)
        assertEquals("Daft Punk", artist1)

        val (title2, artist2) = LyricsFetcher.cleanSearchTerm(
            "Comfortably Numb - 2011 Remastered Version (FLAC)",
            "Pink Floyd"
        )
        assertEquals("Comfortably Numb", title2)
        assertEquals("Pink Floyd", artist2)

        val (title3, artist3) = LyricsFetcher.cleanSearchTerm(
            "Starboy [Deluxe Edition] (Official Video)",
            "The Weeknd [with Daft Punk]"
        )
        assertEquals("Starboy", title3)
        assertEquals("The Weeknd", artist3)
    }

    @Test
    fun testSafeLyricKeyGeneration() {
        val path1 = "/storage/emulated/0/Music/Artist/Album/01 - Song Name.flac"
        val key1 = LyricsFetcher.getSafeLyricKey(path1)
        assertTrue(key1.startsWith("01___Song_Name_"))
        assertFalse(key1.contains("/"))
        assertFalse(key1.contains(" "))

        val path2 = "/storage/sdcard1/DSD/02 - Track.dsf"
        val key2 = LyricsFetcher.getSafeLyricKey(path2)
        assertTrue(key2.startsWith("02___Track_"))
    }
}
