package com.hana.spindle.data

import com.hana.spindle.data.db.TrackEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class M3uManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testRelativePathCalculation() {
        val baseDir = File("/storage/000B-B400/Music/Playlists")
        val trackFile = File("/storage/000B-B400/Music/Radiohead/OK Computer/01 Airbag.flac")

        val relPath = M3uManager.calculateRelativePath(baseDir, trackFile)
        assertEquals("../Radiohead/OK Computer/01 Airbag.flac", relPath)
    }

    @Test
    fun testExportMixtapeFormat() {
        val root = tempFolder.newFolder("ExportTest")
        val playlistsDir = File(root, "Playlists").apply { mkdirs() }
        val albumDir = File(root, "Steely Dan/Aja").apply { mkdirs() }
        val songFile = File(albumDir, "01 Black Cow.flac").apply { writeText("dummy flac") }

        val track = TrackEntity(
            id = 1,
            title = "Black Cow",
            artist = "Steely Dan",
            album = "Aja",
            durationMs = 310000L,
            path = songFile.absolutePath,
            fileFormat = "FLAC"
        )

        val targetM3u = File(playlistsDir, "Yacht Rock.m3u8")
        kotlinx.coroutines.runBlocking {
            val success = M3uManager.exportMixtape(
                file = targetM3u,
                mixtapeName = "Yacht Rock",
                tracks = listOf(track),
                useRelativePaths = true
            )
            assertTrue(success)
        }

        assertTrue(targetM3u.exists())
        val lines = targetM3u.readLines(Charsets.UTF_8)
        assertEquals("#EXTM3U", lines[0])
        assertEquals("#PLAYLIST:Yacht Rock", lines[1])
        assertEquals("#SPINDLE-TAPE-NAME:Yacht Rock", lines[2])
        assertEquals("#SPINDLE-TAPE-FORMULATION:TYPE_I_FERRIC", lines[3])
        assertEquals("#SPINDLE-TAPE-CAPACITY:90", lines[4])
        assertEquals("#SPINDLE-TAPE-SPLIT-INDEX:1", lines[5])
        assertEquals("#SPINDLE-TAPE-SIDE:A", lines[6])
        assertEquals("#EXTINF:310,Steely Dan - Black Cow", lines[7])
        // Should be relative path: ../Steely Dan/Aja/01 Black Cow.flac
        assertEquals("../Steely Dan/Aja/01 Black Cow.flac", lines[8])
    }

    @Test
    fun testExportMixtapeWithCustomTapeCapacityAndSplit() {
        val root = tempFolder.newFolder("CustomTapeExport")
        val playlistsDir = File(root, "Playlists").apply { mkdirs() }
        val albumDir = File(root, "Music").apply { mkdirs() }
        val f1 = File(albumDir, "01.flac").apply { writeText("track1") }
        val f2 = File(albumDir, "02.flac").apply { writeText("track2") }
        val f3 = File(albumDir, "03.flac").apply { writeText("track3") }

        val tracks = listOf(
            TrackEntity(id = 1, title = "Track One", artist = "Artist A", album = "Alb", durationMs = 180000L, path = f1.absolutePath, fileFormat = "FLAC"),
            TrackEntity(id = 2, title = "Track Two", artist = "Artist B", album = "Alb", durationMs = 210000L, path = f2.absolutePath, fileFormat = "FLAC"),
            TrackEntity(id = 3, title = "Track Three", artist = "Artist C", album = "Alb", durationMs = 240000L, path = f3.absolutePath, fileFormat = "FLAC")
        )

        val targetM3u = File(playlistsDir, "Night Drive.m3u8")
        kotlinx.coroutines.runBlocking {
            val success = M3uManager.exportMixtape(
                file = targetM3u,
                mixtapeName = "Night Drive",
                tracks = tracks,
                useRelativePaths = true,
                tapeCapacity = MixtapePartition.TapeCapacity.C60,
                tapeFormulation = "TYPE_II_CHROME",
                splitIndex = 2
            )
            assertTrue(success)
        }

        val lines = targetM3u.readLines(Charsets.UTF_8)
        assertEquals("#EXTM3U", lines[0])
        assertEquals("#PLAYLIST:Night Drive", lines[1])
        assertEquals("#SPINDLE-TAPE-NAME:Night Drive", lines[2])
        assertEquals("#SPINDLE-TAPE-FORMULATION:TYPE_II_CHROME", lines[3])
        assertEquals("#SPINDLE-TAPE-CAPACITY:60", lines[4])
        assertEquals("#SPINDLE-TAPE-SPLIT-INDEX:2", lines[5])

        // Track 1 should be Side A
        assertEquals("#SPINDLE-TAPE-SIDE:A", lines[6])
        assertEquals("#EXTINF:180,Artist A - Track One", lines[7])
        assertEquals("../Music/01.flac", lines[8])

        // Track 2 is still Side A
        assertEquals("#EXTINF:210,Artist B - Track Two", lines[9])
        assertEquals("../Music/02.flac", lines[10])

        // Track 3 (index 2) starts Side B
        assertEquals("#SPINDLE-TAPE-SIDE:B", lines[11])
        assertEquals("#EXTINF:240,Artist C - Track Three", lines[12])
        assertEquals("../Music/03.flac", lines[13])
    }
}
