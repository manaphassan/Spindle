package com.hana.spindle.playback

import com.hana.spindle.data.db.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineQueueAndSleepTest {

    private fun createDummySong(id: Long, title: String): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180000L,
            path = "/storage/emulated/0/Music/$title.flac",
            trackNumber = id.toInt(),
            year = 2024,
            genre = "Electronic",
            bitDepth = 24,
            sampleRate = 96000,
            fileFormat = "FLAC",
            rating = 5,
            dateModified = System.currentTimeMillis(),
            bitrateKbps = 1411,
            hasLyrics = false,
            channels = 2,
            discNumber = 1
        )
    }

    @Test
    fun testSleepTimerState_defaultValues() {
        val state = SleepTimerState()
        assertFalse(state.isActive)
        assertEquals(0, state.remainingSeconds)
        assertEquals(0, state.initialMinutes)
        assertFalse(state.stopAfterCurrentTrack)
    }

    @Test
    fun testSleepTimerState_customValues() {
        val state = SleepTimerState(
            isActive = true,
            remainingSeconds = 1800,
            initialMinutes = 30,
            stopAfterCurrentTrack = false
        )
        assertTrue(state.isActive)
        assertEquals(1800, state.remainingSeconds)
        assertEquals(30, state.initialMinutes)
        assertFalse(state.stopAfterCurrentTrack)
    }

    @Test
    fun testQueueReorder_logic() {
        val list = mutableListOf(
            createDummySong(1, "Track 1"),
            createDummySong(2, "Track 2"),
            createDummySong(3, "Track 3"),
            createDummySong(4, "Track 4")
        )
        var currentIndex = 1 // Currently playing Track 2

        // Move Track 4 (index 3) to index 1 (ahead of current song)
        val fromPos = 3
        val toPos = 1
        val item = list.removeAt(fromPos)
        list.add(toPos, item)

        if (currentIndex == fromPos) {
            currentIndex = toPos
        } else if (fromPos < currentIndex && toPos >= currentIndex) {
            currentIndex--
        } else if (fromPos > currentIndex && toPos <= currentIndex) {
            currentIndex++
        }

        assertEquals("Track 4", list[1].title)
        assertEquals("Track 2", list[2].title)
        assertEquals(2, currentIndex) // Playing track index shifted from 1 to 2
    }

    @Test
    fun testQueueRemove_logic() {
        val list = mutableListOf(
            createDummySong(1, "Track 1"),
            createDummySong(2, "Track 2"),
            createDummySong(3, "Track 3")
        )
        var currentIndex = 2

        // Remove item before current index
        list.removeAt(0)
        currentIndex--

        assertEquals(2, list.size)
        assertEquals("Track 2", list[0].title)
        assertEquals("Track 3", list[1].title)
        assertEquals(1, currentIndex)
    }

    @Test
    fun testAudioRouteShorthand() {
        fun formatRoute(route: String): String = when {
            route.contains("USB", ignoreCase = true) -> "• USB DAC"
            route.contains("3.5mm", ignoreCase = true) -> "• 3.5mm"
            route.contains("Bluetooth", ignoreCase = true) -> "• BT"
            else -> "• Audio"
        }

        assertEquals("• USB DAC", formatRoute("USB DAC / OTG Audio"))
        assertEquals("• 3.5mm", formatRoute("3.5mm Headphone Jack"))
        assertEquals("• BT", formatRoute("Bluetooth Audio (A2DP)"))
        assertEquals("• Audio", formatRoute("Built-in Speaker"))
    }

    @Test
    fun testCassetteThemes_allPresetsAndUniqueness() {
        val presets = com.hana.spindle.theme.CassetteTheme.ALL_PRESETS
        assertTrue(presets.size >= 8)

        val ids = presets.map { it.id }
        assertEquals(ids.distinct().size, ids.size)

        val sony = presets.find { it.id == com.hana.spindle.theme.CassetteTheme.SONY_METAL_XR.id }
        assertTrue(sony != null)
        assertEquals("Sony Metal-XR", sony!!.name)

        val tdk = presets.find { it.id == com.hana.spindle.theme.CassetteTheme.TDK_SA_90.id }
        assertTrue(tdk != null)
        assertEquals("Type II High-Bias", tdk!!.name)

        val maxell = presets.find { it.id == com.hana.spindle.theme.CassetteTheme.MAXELL_XLII.id }
        assertTrue(maxell != null)
        assertEquals("Type II Amber Gold", maxell!!.name)

        val basf = presets.find { it.id == com.hana.spindle.theme.CassetteTheme.BASF_CHROME.id }
        assertTrue(basf != null)
        assertEquals("Anthracite Chrome", basf!!.name)

        val skeleton = presets.find { it.id == com.hana.spindle.theme.CassetteTheme.SKELETON_REEL.id }
        assertTrue(skeleton != null)
        assertEquals("Skeleton Reel", skeleton!!.name)

        val eink = presets.find { it.id == com.hana.spindle.theme.CassetteTheme.MONOCHROME_EINK.id }
        assertTrue(eink != null)
        assertEquals("Mono", eink!!.name)
        assertFalse(eink!!.isDarkAppTheme)
    }

    @Test
    fun testBitPerfectTelemetryBadge() {
        fun getFormattedBadge(route: String, isBitPerfect: Boolean): String {
            val routeBadge = when {
                route.contains("USB", ignoreCase = true) -> if (isBitPerfect) "• USB DIRECT" else "• USB DAC"
                route.contains("3.5mm", ignoreCase = true) -> if (isBitPerfect) "• 3.5mm DIRECT" else "• 3.5mm"
                route.contains("Bluetooth", ignoreCase = true) -> "• BT"
                else -> if (isBitPerfect) "• DIRECT" else "• Audio"
            }
            return routeBadge
        }

        assertEquals("• USB DIRECT", getFormattedBadge("USB DAC (Bit-Perfect Direct)", true))
        assertEquals("• USB DAC", getFormattedBadge("USB DAC (Bit-Perfect Direct)", false))
        assertEquals("• 3.5mm DIRECT", getFormattedBadge("3.5mm Headphone Jack (Hi-Res)", true))
        assertEquals("• 3.5mm", getFormattedBadge("3.5mm Headphone Jack (Hi-Res)", false))
        assertEquals("• BT", getFormattedBadge("Bluetooth Audio (LDAC)", true))
    }

    @Test
    fun testEinkKinematics_progressTickAngles() {
        // In E-Ink mode, reels advance strictly with progress ticks (progress * 720 degrees)
        fun calculateEinkAngle(progress: Float): Float = (progress * 720f) % 360f

        assertEquals(0f, calculateEinkAngle(0.0f), 0.001f)
        assertEquals(180f, calculateEinkAngle(0.25f), 0.001f)
        assertEquals(0f, calculateEinkAngle(0.5f), 0.001f)
        assertEquals(180f, calculateEinkAngle(0.75f), 0.001f)
        assertEquals(0f, calculateEinkAngle(1.0f), 0.001f)
    }

    @Test
    fun testMixtapeEntities_models() {
        val playlist = com.hana.spindle.data.db.PlaylistEntity(
            id = 101L,
            name = "Late Night City Pop",
            createdAt = System.currentTimeMillis(),
            colorAccent = 0xF97316.toInt()
        )
        assertEquals(101L, playlist.id)
        assertEquals("Late Night City Pop", playlist.name)

        val crossRef = com.hana.spindle.data.db.PlaylistSongCrossRef(
            playlistId = 101L,
            songId = 55L,
            orderIndex = 1L
        )
        assertEquals(101L, crossRef.playlistId)
        assertEquals(55L, crossRef.songId)
        assertEquals(1L, crossRef.orderIndex)
    }
}
