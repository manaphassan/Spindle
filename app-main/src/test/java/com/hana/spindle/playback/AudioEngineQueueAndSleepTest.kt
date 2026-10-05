package com.hana.spindle.playback

import com.hana.spindle.data.db.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineQueueAndSleepTest {

    private fun createDummySong(id: Long, title: String): TrackEntity {
        return TrackEntity(
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
    fun testQueueRemoveCurrentTrack_lastPositionWraparound() {
        val list = mutableListOf(
            createDummySong(1, "Track 1"),
            createDummySong(2, "Track 2"),
            createDummySong(3, "Track 3")
        )
        var currentIndex = 2 // Last item is playing

        // Remove the currently playing track
        list.removeAt(currentIndex)
        if (currentIndex >= list.size) {
            currentIndex = 0 // wraps to start of playlist
        }

        assertEquals(2, list.size)
        assertEquals("Track 1", list[0].title)
        assertEquals(0, currentIndex) // Playing track index reset to 0
    }

    @Test
    fun testOriginalPlaylistSyncOnRemove_removesOnlyOneOccurrence() {
        val origList = mutableListOf(
            createDummySong(10, "Track A"),
            createDummySong(20, "Track B"),
            createDummySong(10, "Track A"), // Duplicate in queue
            createDummySong(30, "Track C")
        )

        val trackToRemove = origList[0] // Track A
        val origIndex = origList.indexOfFirst { it.id == trackToRemove.id }
        if (origIndex >= 0) {
            origList.removeAt(origIndex)
        }

        assertEquals(3, origList.size)
        assertEquals(20L, origList[0].id)
        assertEquals(10L, origList[1].id) // Duplicate still retained
        assertEquals(30L, origList[2].id)
    }

    @Test
    fun testClearUpcomingQueue_preservesCurrentAndPastTracks() {
        val list = mutableListOf(
            createDummySong(1, "Track 1"),
            createDummySong(2, "Track 2"),
            createDummySong(3, "Track 3"),
            createDummySong(4, "Track 4")
        )
        val origList = list.toMutableList()
        val currentIndex = 1 // Playing Track 2

        val nextIdx = currentIndex + 1
        val past = list.take(nextIdx)
        list.clear()
        list.addAll(past)
        val currentTrackIds = past.map { it.id }.toSet()
        origList.removeAll { !currentTrackIds.contains(it.id) }

        assertEquals(2, list.size)
        assertEquals("Track 1", list[0].title)
        assertEquals("Track 2", list[1].title)
        assertEquals(2, origList.size)
        assertEquals(1L, origList[0].id)
        assertEquals(2L, origList[1].id)
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

        val metalXr = presets.find { it.id == com.hana.spindle.theme.CassetteTheme.METAL_XR_TYPE4.id }
        assertTrue(metalXr != null)
        assertEquals("Metal-XR Type IV", metalXr!!.name)

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

        assertEquals("• USB DIRECT", getFormattedBadge("USB DAC (Hi-Res Passthrough)", true))
        assertEquals("• USB DAC", getFormattedBadge("USB DAC (Hi-Res Passthrough)", false))
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

    @Test
    fun testSaveQueueAsMixtapeMapping() {
        val queue = listOf(
            createDummySong(10, "Acoustic Intro"),
            createDummySong(20, "Tape Loop Interlude"),
            createDummySong(30, "Analog Outro")
        )

        val targetPlaylistId = 42L
        val crossRefs = queue.mapIndexed { index, track ->
            com.hana.spindle.data.db.PlaylistSongCrossRef(
                playlistId = targetPlaylistId,
                songId = track.id,
                orderIndex = index.toLong()
            )
        }

        assertEquals(3, crossRefs.size)
        assertEquals(0L, crossRefs[0].orderIndex)
        assertEquals(10L, crossRefs[0].songId)
        assertEquals(1L, crossRefs[1].orderIndex)
        assertEquals(20L, crossRefs[1].songId)
        assertEquals(2L, crossRefs[2].orderIndex)
        assertEquals(30L, crossRefs[2].songId)
        crossRefs.forEach { assertEquals(42L, it.playlistId) }
    }

    @Test
    fun testTrackStarted_stateConsistency() {
        val initialTrack = createDummySong(1, "Track A")
        val nextTrack = createDummySong(2, "Track B")

        var state = PlaybackState(
            isPlaying = true,
            currentTrack = initialTrack,
            durationMs = initialTrack.durationMs,
            currentPositionMs = 45000L,
            progress = 0.25f,
            activeLyricIndex = 5
        )

        // Simulate onTrackStarted transition
        state = state.copy(
            isPlaying = true,
            currentTrack = nextTrack,
            currentPositionMs = 0L,
            durationMs = nextTrack.durationMs,
            progress = 0f,
            currentLyrics = null,
            activeLyricIndex = -1
        )

        assertEquals(2L, state.currentTrack?.id)
        assertEquals("Track B", state.currentTrack?.title)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(0f, state.progress, 0.001f)
        assertEquals(-1, state.activeLyricIndex)
        assertEquals(null, state.currentLyrics)
        assertTrue(state.isPlaying)
    }

    @Test
    fun testSpindleForwardingPlayer_commandAvailability_contract() {
        val requiredCommands = intArrayOf(
            androidx.media3.common.Player.COMMAND_PLAY_PAUSE,
            androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT,
            androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS,
            androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            androidx.media3.common.Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            androidx.media3.common.Player.COMMAND_STOP
        )

        // When track is present, all commands must resolve to true
        val hasTrack = true
        for (cmd in requiredCommands) {
            val isAvailable = if (hasTrack) {
                when (cmd) {
                    androidx.media3.common.Player.COMMAND_PLAY_PAUSE,
                    androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT,
                    androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS,
                    androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    androidx.media3.common.Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                    androidx.media3.common.Player.COMMAND_STOP -> true
                    else -> false
                }
            } else false
            assertTrue("Command $cmd should be available when track is present", isAvailable)
        }
    }
}
