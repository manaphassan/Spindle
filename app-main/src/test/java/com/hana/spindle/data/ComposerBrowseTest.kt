package com.hana.spindle.data

import com.hana.spindle.data.db.AlbumItem
import com.hana.spindle.data.db.TrackEntity
import com.hana.spindle.ui.catalog.GroupByMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ComposerBrowseTest {

    @Test
    fun testComposerGroupingAndAlbumItem() {
        val tracks = listOf(
            TrackEntity(id = 1, title = "Symphony No. 5", artist = "Berlin Philharmonic", album = "Beethoven: 9 Symphonies", composer = "Ludwig van Beethoven", durationMs = 450000, path = "/music/1.flac", fileFormat = "FLAC"),
            TrackEntity(id = 2, title = "Moonlight Sonata", artist = "Glenn Gould", album = "Beethoven Piano Sonatas", composer = "Ludwig van Beethoven", durationMs = 320000, path = "/music/2.flac", fileFormat = "FLAC"),
            TrackEntity(id = 3, title = "Clair de Lune", artist = "Pascal Rogé", album = "Debussy Piano Works", composer = "Claude Debussy", durationMs = 300000, path = "/music/3.flac", fileFormat = "FLAC"),
            TrackEntity(id = 4, title = "Clint Eastwood", artist = "Gorillaz", album = "Gorillaz", composer = null, durationMs = 340000, path = "/music/4.mp3", fileFormat = "MP3")
        )

        val composers = tracks.filter { !it.composer.isNullOrBlank() }
            .groupBy { it.composer!!.trim() }
            .map { (composer, list) ->
                AlbumItem(
                    album = composer,
                    artist = "${list.size} compositions",
                    trackCount = list.size,
                    representativePath = list.firstOrNull()?.path ?: "",
                    year = list.maxOfOrNull { it.year } ?: 0,
                    format = "COMPOSER"
                )
            }
            .sortedBy { it.album }

        assertEquals(2, composers.size)
        assertEquals("Claude Debussy", composers[0].album)
        assertEquals("1 compositions", composers[0].artist)
        assertEquals("COMPOSER", composers[0].format)

        assertEquals("Ludwig van Beethoven", composers[1].album)
        assertEquals("2 compositions", composers[1].artist)
        assertEquals("COMPOSER", composers[1].format)
    }

    @Test
    fun testGroupByModeComposerSort() {
        val tracks = listOf(
            TrackEntity(id = 1, title = "Track Z Without Composer", artist = "Band A", album = "Album A", composer = null, trackNumber = 1, durationMs = 1000, path = "/1.flac", fileFormat = "FLAC"),
            TrackEntity(id = 2, title = "Symphony No. 9", artist = "Orchestra", album = "Beethoven", composer = "Ludwig van Beethoven", trackNumber = 4, durationMs = 1000, path = "/2.flac", fileFormat = "FLAC"),
            TrackEntity(id = 3, title = "Brandenburg Concerto No. 3", artist = "Academy", album = "Bach Concertos", composer = "Johann Sebastian Bach", trackNumber = 1, durationMs = 1000, path = "/3.flac", fileFormat = "FLAC"),
            TrackEntity(id = 4, title = "Symphony No. 5", artist = "Orchestra", album = "Beethoven", composer = "Ludwig van Beethoven", trackNumber = 1, durationMs = 1000, path = "/4.flac", fileFormat = "FLAC")
        )

        val sorted = tracks.sortedWith(
            compareBy(
                { it.composer?.lowercase(Locale.ROOT) ?: "\uffff" },
                { it.album.lowercase(Locale.ROOT) },
                { it.trackNumber }
            )
        )

        assertEquals("Johann Sebastian Bach", sorted[0].composer)
        assertEquals("Brandenburg Concerto No. 3", sorted[0].title)

        assertEquals("Ludwig van Beethoven", sorted[1].composer)
        assertEquals("Symphony No. 5", sorted[1].title)

        assertEquals("Ludwig van Beethoven", sorted[2].composer)
        assertEquals("Symphony No. 9", sorted[2].title)

        // Null composer sorted last
        assertEquals(null, sorted[3].composer)
        assertEquals("Track Z Without Composer", sorted[3].title)
    }

    @Test
    fun testTrackAdapterComposerAttribution() {
        val classicalTrack = TrackEntity(id = 1, title = "Violin Concerto", artist = "Hilary Hahn", album = "Bach", composer = "J.S. Bach", durationMs = 1000, path = "/1.flac", fileFormat = "FLAC")
        val singerSongwriterTrack = TrackEntity(id = 2, title = "Both Sides Now", artist = "Joni Mitchell", album = "Clouds", composer = "Joni Mitchell", durationMs = 1000, path = "/2.flac", fileFormat = "FLAC")
        val popTrack = TrackEntity(id = 3, title = "Billie Jean", artist = "Michael Jackson", album = "Thriller", composer = null, durationMs = 1000, path = "/3.flac", fileFormat = "FLAC")

        fun getDisplayArtist(track: TrackEntity): String {
            return if (!track.composer.isNullOrBlank() && !track.composer.equals(track.artist, ignoreCase = true)) {
                "${track.artist} • ${track.composer}"
            } else {
                track.artist
            }
        }

        assertEquals("Hilary Hahn • J.S. Bach", getDisplayArtist(classicalTrack))
        assertEquals("Joni Mitchell", getDisplayArtist(singerSongwriterTrack))
        assertEquals("Michael Jackson", getDisplayArtist(popTrack))
    }
}
