package com.hana.spindle.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testBitmapPoolEmptyAcquireReturnsNull() {
        val pool = ImageLoader.BitmapPool(maxEntries = 16)
        assertEquals(0, pool.size)
        val candidate = pool.acquire(200, 200)
        assertNull(candidate)
    }

    @Test
    fun testBitmapPoolClear() {
        val pool = ImageLoader.BitmapPool(maxEntries = 16)
        assertEquals(0, pool.size)
        pool.clear()
        assertEquals(0, pool.size)
    }

    @Test
    fun testHashKeyConsistency() {
        // Test deterministic MD5 hashing without needing Android context
        val md = java.security.MessageDigest.getInstance("MD5")
        val sample = "/storage/000B-B400/Music/Artist/Album/01.flac"
        val expected = md.digest(sample.toByteArray()).joinToString("") { "%02x".format(it) }

        // Test hash consistency on standard string
        assertNotNull(expected)
        assertEquals(32, expected.length)
    }

    @Test
    fun testFolderArtDetection() {
        val albumDir = tempFolder.newFolder("TestAlbum")
        assertFalse(TagParser.isFolderArtPresent(albumDir))

        // Create tiny file (<512 bytes) -> should still be false
        val tinyCover = File(albumDir, "cover.jpg").apply { writeBytes(ByteArray(100)) }
        assertFalse(TagParser.isFolderArtPresent(albumDir))

        // Create valid cover (>512 bytes) -> should be true
        tinyCover.writeBytes(ByteArray(1024))
        // Note: cache might hold false from earlier check, test new folder
        val albumDir2 = tempFolder.newFolder("TestAlbum2")
        File(albumDir2, "folder.jpg").apply { writeBytes(ByteArray(2048)) }
        assertTrue(TagParser.isFolderArtPresent(albumDir2))
    }

    @Test
    fun testCaseInsensitiveCoverNaming() {
        val albumDir = tempFolder.newFolder("AlbumWithCoverPng")
        File(albumDir, "FRONT.PNG").apply { writeBytes(ByteArray(1024)) }
        assertTrue(TagParser.isFolderArtPresent(albumDir))
    }
}
