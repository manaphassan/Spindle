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
        assertEquals("#EXTINF:310,Steely Dan - Black Cow", lines[2])
        // Should be relative path: ../Steely Dan/Aja/01 Black Cow.flac
        assertEquals("../Steely Dan/Aja/01 Black Cow.flac", lines[3])
    }
}
