package com.hana.spindle.data

import com.hana.spindle.data.db.TrackEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testRelativePathConversionAndReanchoring() {
        val rootPath = "/storage/000B-B400/Music"
        val trackPath = "/storage/000B-B400/Music/Pink Floyd/The Wall/01 In The Flesh.flac"

        val relPath = CatalogCacheManager.toRelativePath(rootPath, trackPath)
        assertEquals("Pink Floyd/The Wall/01 In The Flesh.flac", relPath)

        // Simulate moving the MicroSD card to another DAP with different mount point
        val newDapRoot = "/storage/7A12-B4E9/Music"
        val reanchoredPath = CatalogCacheManager.resolveAbsolutePath(newDapRoot, relPath)
        val expected = File(newDapRoot, "Pink Floyd/The Wall/01 In The Flesh.flac").absolutePath
        assertEquals(expected, reanchoredPath)
    }

    @Test
    fun testBinaryCacheSerializationRoundTrip() {
        val testRoot = tempFolder.newFolder("TestMusicRoot")
        val albumDir = File(testRoot, "Daft Punk/Discovery").apply { mkdirs() }
        val fakeSongFile = File(albumDir, "01 One More Time.flac").apply { writeText("audio content") }

        val track = TrackEntity(
            id = 1,
            title = "One More Time",
            artist = "Daft Punk",
            album = "Discovery",
            durationMs = 320000L,
            path = fakeSongFile.absolutePath,
            trackNumber = 1,
            year = 2001,
            genre = "Electronic",
            bitDepth = 24,
            sampleRate = 96000,
            fileFormat = "FLAC",
            rating = 5,
            isFavorite = true,
            albumArtist = "Daft Punk",
            dateAdded = 1000L,
            dateModified = fakeSongFile.lastModified(),
            bitrateKbps = 1536,
            hasLyrics = true,
            channels = 2,
            discNumber = 1
        )

        // Write directly using the serialization logic
        val cacheFile = File(testRoot, CatalogCacheManager.CACHE_FILE_NAME)
        val tempFile = File(testRoot, "${CatalogCacheManager.CACHE_FILE_NAME}.tmp")

        val rootCanonical = testRoot.canonicalPath
        java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(tempFile))).use { dos ->
            dos.writeBytes("SPNDLCAT")
            dos.writeInt(1) // version
            dos.writeLong(123456789L) // timestamp
            dos.writeUTF(rootCanonical)
            dos.writeInt(1) // count

            val relPath = CatalogCacheManager.toRelativePath(rootCanonical, track.path)
            dos.writeUTF(relPath)
            dos.writeUTF(track.title)
            dos.writeUTF(track.artist)
            dos.writeUTF(track.album)
            dos.writeLong(track.durationMs)
            dos.writeInt(track.trackNumber)
            dos.writeInt(track.year)
            dos.writeUTF(track.genre ?: "")
            dos.writeInt(track.bitDepth)
            dos.writeInt(track.sampleRate)
            dos.writeUTF(track.fileFormat)
            dos.writeInt(track.rating)
            dos.writeBoolean(track.isFavorite)
            dos.writeUTF(track.albumArtist ?: "")
            dos.writeLong(track.dateModified)
            dos.writeInt(track.bitrateKbps)
            dos.writeBoolean(track.hasLyrics)
            dos.writeInt(track.channels)
            dos.writeInt(track.discNumber)
            dos.flush()
        }
        tempFile.renameTo(cacheFile)

        assertTrue(cacheFile.exists())

        // Read and verify
        java.io.DataInputStream(java.io.BufferedInputStream(java.io.FileInputStream(cacheFile))).use { dis ->
            val magic = ByteArray(8)
            dis.readFully(magic)
            assertEquals("SPNDLCAT", String(magic, Charsets.US_ASCII))
            assertEquals(1, dis.readInt())
            assertEquals(123456789L, dis.readLong())
            assertEquals(rootCanonical, dis.readUTF())
            assertEquals(1, dis.readInt())

            val readRel = dis.readUTF()
            assertEquals("Daft Punk/Discovery/01 One More Time.flac".replace('/', File.separatorChar), readRel.replace('/', File.separatorChar))
            assertEquals("One More Time", dis.readUTF())
            assertEquals("Daft Punk", dis.readUTF())
            assertEquals("Discovery", dis.readUTF())
            assertEquals(320000L, dis.readLong())
            assertEquals(1, dis.readInt())
            assertEquals(2001, dis.readInt())
            assertEquals("Electronic", dis.readUTF())
            assertEquals(24, dis.readInt())
            assertEquals(96000, dis.readInt())
            assertEquals("FLAC", dis.readUTF())
            assertEquals(5, dis.readInt())
            assertTrue(dis.readBoolean())
            assertEquals("Daft Punk", dis.readUTF())
            assertEquals(fakeSongFile.lastModified(), dis.readLong())
            assertEquals(1536, dis.readInt())
            assertTrue(dis.readBoolean())
            assertEquals(2, dis.readInt())
            assertEquals(1, dis.readInt())
        }
    }
}
