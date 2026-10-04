package com.hana.spindle.lite.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Spindle Lite's Zune Metro Jump List grid calculations and letter resolution.
 */
class LiteZuneJumpListTest {

    @Test
    fun testTileCountAndLayout() {
        val tiles = LiteZuneJumpList.TILES
        // Exactly 28 tiles for a 4x7 grid (#, A-Z, and ↑)
        assertEquals(28, tiles.size)
        assertEquals("#", tiles.first())
        assertEquals("↑", tiles.last())
        assertTrue(tiles.contains("A"))
        assertTrue(tiles.contains("M"))
        assertTrue(tiles.contains("Z"))
    }

    @Test
    fun testLetterAvailabilityResolution() {
        val availableLetters = setOf('A', 'D', 'M', 'Z', '3', '!')

        for (label in LiteZuneJumpList.TILES) {
            val isAvailable = LiteZuneJumpList.isLetterAvailable(label, availableLetters)

            when (label) {
                "↑" -> assertTrue("Arrow should always be available", isAvailable)
                "#" -> assertTrue("# should be available when non-letters exist", isAvailable)
                "A", "D", "M", "Z" -> assertTrue("$label should be available", isAvailable)
                "B", "C", "E", "X", "Y" -> assertFalse("$label should not be available", isAvailable)
            }
        }
    }

    @Test
    fun testLetterSearchMatching() {
        val artistNames = listOf("Air", "Beatles", "Chopin", "Daft Punk", "1975", "!Special")

        assertEquals(0, LiteZuneJumpList.findSectionIndex("↑", artistNames))
        assertEquals(0, LiteZuneJumpList.findSectionIndex("A", artistNames))
        assertEquals(1, LiteZuneJumpList.findSectionIndex("B", artistNames))
        assertEquals(2, LiteZuneJumpList.findSectionIndex("C", artistNames))
        assertEquals(3, LiteZuneJumpList.findSectionIndex("D", artistNames))
        assertEquals(4, LiteZuneJumpList.findSectionIndex("#", artistNames))
        assertEquals(-1, LiteZuneJumpList.findSectionIndex("Z", artistNames))
    }
}
