package com.hana.spindle.lite

import com.hana.spindle.lite.db.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Spindle Lite Track audiophile formatting and tape bias determination.
 */
class TrackTest {

    @Test
    fun testFormattedDuration() {
        val track = Track(
            title = "Test Song",
            artist = "Audiophile Master",
            album = "Hi-Fi Sessions",
            durationMs = 215000L, // 3 min 35 sec
            filePath = "/Music/test.flac",
            format = "FLAC"
        )
        assertEquals("03:35", track.formattedDuration)
    }

    @Test
    fun testFlacLosslessFormatBadgeAndTypeIVMetalBias() {
        val flacHiRes = Track(
            title = "Overture",
            artist = "Tokyo Philharmonic",
            album = "DSD Transfers",
            durationMs = 300000L,
            filePath = "/Music/overture.flac",
            format = "FLAC",
            sampleRate = 96000
        )
        assertEquals("FLAC 96k", flacHiRes.formatBadge)
        assertEquals("SPINDLE • TYPE IV METAL BIAS", flacHiRes.tapeBiasType)

        val flacStandard = Track(
            title = "Sonata",
            artist = "Soloist",
            album = "Red Book",
            durationMs = 200000L,
            filePath = "/Music/sonata.flac",
            format = "FLAC",
            sampleRate = 44100
        )
        assertEquals("FLAC 44.1k", flacStandard.formatBadge)
        assertEquals("SPINDLE • TYPE IV METAL BIAS", flacStandard.tapeBiasType)
    }

    @Test
    fun testMp3HighBiasTypeII() {
        val mp3Track = Track(
            title = "City Pop",
            artist = "Tatsuro",
            album = "For You",
            durationMs = 260000L,
            filePath = "/Music/citypop.mp3",
            format = "MP3",
            bitrate = 320
        )
        assertEquals("MP3 320K", mp3Track.formatBadge)
        assertEquals("SPINDLE • TYPE II HIGH BIAS (CrO2)", mp3Track.tapeBiasType)
    }

    @Test
    fun testLowBitrateNormalBiasTypeI() {
        val voiceMemo = Track(
            title = "Field Recording",
            artist = "Ambient",
            album = "Field Notes",
            durationMs = 120000L,
            filePath = "/Music/memo.mp3",
            format = "MP3",
            bitrate = 128
        )
        assertEquals("MP3 128K", voiceMemo.formatBadge)
        assertEquals("SPINDLE • TYPE I NORMAL BIAS (Fe2O3)", voiceMemo.tapeBiasType)
    }
}
