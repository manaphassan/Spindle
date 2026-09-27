package com.hana.spindle.data

import com.hana.spindle.data.db.FtsQueryBuilder
import com.hana.spindle.data.db.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FtsSearchTest {

    @Test
    fun testFtsQueryBuilderSingleWord() {
        val query = FtsQueryBuilder.buildPrefixQuery("beethoven")
        assertEquals("beethoven*", query)
    }

    @Test
    fun testFtsQueryBuilderMultiWord() {
        val query = FtsQueryBuilder.buildPrefixQuery("ludwig van beethoven")
        assertEquals("ludwig* van* beethoven*", query)
    }

    @Test
    fun testFtsQueryBuilderSpecialCharacters() {
        val query = FtsQueryBuilder.buildPrefixQuery("Pink Floyd - The Wall (2011 Remaster)")
        assertEquals("Pink* Floyd* The* Wall* 2011* Remaster*", query)
    }

    @Test
    fun testFtsQueryBuilderPunctuationAndSlash() {
        val query = FtsQueryBuilder.buildPrefixQuery("AC/DC: Back In Black!")
        assertEquals("AC* DC* Back* In* Black*", query)
    }

    @Test
    fun testFtsQueryBuilderEmptyOrInvalid() {
        assertNull(FtsQueryBuilder.buildPrefixQuery(""))
        assertNull(FtsQueryBuilder.buildPrefixQuery("   "))
        assertNull(FtsQueryBuilder.buildPrefixQuery("---***:::"))
        assertNull(FtsQueryBuilder.buildPrefixQuery(null))
        assertFalse(FtsQueryBuilder.isValidQuery("???"))
        assertTrue(FtsQueryBuilder.isValidQuery("Bach"))
    }

    @Test
    fun testFtsQueryBuilderReservedKeyword() {
        val single = FtsQueryBuilder.buildPrefixQuery("and")
        assertNotNull(single)
        assertTrue(single!!.contains("and"))

        val multi = FtsQueryBuilder.buildPrefixQuery("Rock and Roll")
        assertEquals("Rock* \"and\"* Roll*", multi)
    }

    @Test
    fun testMultiTokenSearchMatchingSemantics() {
        val tracks = listOf(
            TrackEntity(id = 1, title = "Symphony No. 5 in C Minor, Op. 67", artist = "Berlin Philharmonic", album = "Beethoven Symphonies", composer = "Ludwig van Beethoven", genre = "Classical", durationMs = 420000, path = "/1.flac", fileFormat = "FLAC"),
            TrackEntity(id = 2, title = "Moonlight Sonata", artist = "Glenn Gould", album = "Beethoven Piano Sonatas", composer = "Ludwig van Beethoven", genre = "Classical", durationMs = 310000, path = "/2.flac", fileFormat = "FLAC"),
            TrackEntity(id = 3, title = "Clair de Lune", artist = "Pascal Rogé", album = "Suite Bergamasque", composer = "Claude Debussy", genre = "Impressionist", durationMs = 300000, path = "/3.flac", fileFormat = "FLAC"),
            TrackEntity(id = 4, title = "Time", artist = "Pink Floyd", album = "The Dark Side of the Moon", composer = "Nick Mason, David Gilmour, Roger Waters, Richard Wright", genre = "Progressive Rock", durationMs = 425000, path = "/4.flac", fileFormat = "FLAC")
        )

        // Search query: "beethoven 5"
        val query1 = "beethoven 5"
        val tokens1 = query1.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val results1 = tracks.filter { track ->
            tokens1.all { t ->
                track.title.contains(t, ignoreCase = true) ||
                track.artist.contains(t, ignoreCase = true) ||
                track.album.contains(t, ignoreCase = true) ||
                track.composer?.contains(t, ignoreCase = true) == true ||
                track.genre?.contains(t, ignoreCase = true) == true
            }
        }
        assertEquals(1, results1.size)
        assertEquals("Symphony No. 5 in C Minor, Op. 67", results1[0].title)

        // Search query: "classical"
        val query2 = "classical"
        val tokens2 = query2.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val results2 = tracks.filter { track ->
            tokens2.all { t ->
                track.title.contains(t, ignoreCase = true) ||
                track.artist.contains(t, ignoreCase = true) ||
                track.album.contains(t, ignoreCase = true) ||
                track.composer?.contains(t, ignoreCase = true) == true ||
                track.genre?.contains(t, ignoreCase = true) == true
            }
        }
        assertEquals(2, results2.size)

        // Search query: "floyd moon"
        val query3 = "floyd moon"
        val tokens3 = query3.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val results3 = tracks.filter { track ->
            tokens3.all { t ->
                track.title.contains(t, ignoreCase = true) ||
                track.artist.contains(t, ignoreCase = true) ||
                track.album.contains(t, ignoreCase = true) ||
                track.composer?.contains(t, ignoreCase = true) == true ||
                track.genre?.contains(t, ignoreCase = true) == true
            }
        }
        assertEquals(1, results3.size)
        assertEquals("Time", results3[0].title)
    }
}
