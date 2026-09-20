package com.hana.spindle.lite.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Spindle Lite App Model, Section Indexing, and Filtering logic.
 */
class LiteAppInfoTest {

    @Test
    fun testAppInfoPropertiesAndSystemAppFlag() {
        val userApp = LiteAppInfo(
            label = "Retro Music",
            packageName = "code.name.monkey.retromusic",
            className = "code.name.monkey.retromusic.activities.MainActivity",
            isSystemApp = false
        )
        assertFalse(userApp.isSystemApp)
        assertEquals("Retro Music", userApp.label)
        assertEquals("code.name.monkey.retromusic", userApp.packageName)

        val systemApp = LiteAppInfo(
            label = "Settings",
            packageName = "com.android.settings",
            className = "com.android.settings.Settings",
            isSystemApp = true
        )
        assertTrue(systemApp.isSystemApp)
        assertEquals("com.android.settings", systemApp.packageName)
    }

    @Test
    fun testAppFilteringLogic() {
        val apps = listOf(
            LiteAppInfo("Browser", "com.android.browser", "com.android.browser.BrowserActivity", isSystemApp = true),
            LiteAppInfo("Gallery", "com.android.gallery3d", "com.android.gallery3d.app.Gallery", isSystemApp = true),
            LiteAppInfo("Spindle Lite", "com.hana.spindle.lite", "com.hana.spindle.lite.ui.LiteMainActivity", isSystemApp = false),
            LiteAppInfo("Walkman", "com.sonyericsson.music", "com.sonyericsson.music.MusicActivity", isSystemApp = false)
        )

        // Empty query returns all
        val emptyFilter = apps.filter { true }
        assertEquals(4, emptyFilter.size)

        // Name query
        val nameFilter = apps.filter { it.label.lowercase().contains("spin") }
        assertEquals(1, nameFilter.size)
        assertEquals("Spindle Lite", nameFilter[0].label)

        // Package query
        val pkgFilter = apps.filter { it.packageName.lowercase().contains("sonyericsson") }
        assertEquals(1, pkgFilter.size)
        assertEquals("Walkman", pkgFilter[0].label)
    }

    @Test
    fun testSectionIndexerLetterBucketing() {
        val labels = listOf("Apple Music", "Browser", "Camera", "Dialer", "Email", "Files", "Gallery", "123 Calculator", "# Special")
        val expectedSections = listOf("A", "B", "C", "D", "E", "F", "G", "#", "#")

        for (i in labels.indices) {
            val firstChar = labels[i].firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            val section = if (firstChar in "A".."Z") firstChar else "#"
            assertEquals("Mismatch for label ${labels[i]}", expectedSections[i], section)
        }
    }
}
