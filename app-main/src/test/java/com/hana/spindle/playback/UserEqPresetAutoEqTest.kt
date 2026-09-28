package com.hana.spindle.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UserEqPresetAutoEqTest {

    private lateinit var tempDir: File
    private lateinit var manager: UserEqPresetManager

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "spindle_eq_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        manager = UserEqPresetManager(baseDir = tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testParseAutoEqGraphicEq_standardFormat() {
        val autoEqText = """
            GraphicEQ: 20 0.0; 25 0.5; 31.5 2.5; 40 3.0; 50 3.5; 63 4.0; 125 1.5; 250 0.0; 500 -1.0; 1000 -2.0; 2000 1.0; 4000 3.0; 8000 -3.0; 16000 2.0
        """.trimIndent()

        val preset = manager.parseAutoEqGraphicEq(autoEqText, "Harman IEM Target")
        assertNotNull(preset)
        assertEquals("Harman IEM Target", preset!!.name)
        assertEquals(10, preset.gainsDb.size)

        // 31.25Hz should be close to 2.5 dB
        assertTrue("31Hz gain should be > 2.0dB", preset.gainsDb[0] > 2.0f)
        // 62.5Hz should be close to 4.0 dB
        assertTrue("63Hz gain should be ~4.0dB", preset.gainsDb[1] in 3.5f..4.5f)
        // 1kHz should be close to -2.0 dB
        assertEquals(-2.0f, preset.gainsDb[5], 0.3f)
        // 4kHz should be close to 3.0 dB
        assertEquals(3.0f, preset.gainsDb[7], 0.3f)
    }

    @Test
    fun testParseAutoEqGraphicEq_clampingDecibels() {
        val extremeText = """
            GraphicEQ: 31 25.0; 63 -30.0; 125 0.0; 250 0.0; 500 0.0; 1000 0.0; 2000 0.0; 4000 0.0; 8000 0.0; 16000 0.0
        """.trimIndent()

        val preset = manager.parseAutoEqGraphicEq(extremeText, "Extreme Curve")
        assertNotNull(preset)
        // Must be clamped to [-12, +12]
        assertEquals(12.0f, preset!!.gainsDb[0], 0.01f)
        assertEquals(-12.0f, preset.gainsDb[1], 0.01f)
    }

    @Test
    fun testImportPreset_autoEqVsJson() {
        // Test JSON format
        val jsonPreset = UserEqPreset(
            name = "Studio Flat",
            gainsDb = List(10) { 0.0f }
        )
        val jsonStr = manager.exportPresetToJson(jsonPreset)
        val importedJson = manager.importPreset(jsonStr)
        assertNotNull(importedJson)
        assertEquals("Studio Flat", importedJson!!.name)

        // Test AutoEq format
        val autoEqStr = "GraphicEQ: 31 1.0; 63 2.0; 125 1.0; 250 0.0; 500 0.0; 1000 0.0; 2000 0.0; 4000 0.0; 8000 0.0; 16000 0.0"
        val importedAutoEq = manager.importPreset(autoEqStr, fallbackName = "My Headphone")
        assertNotNull(importedAutoEq)
        assertEquals("My Headphone", importedAutoEq!!.name)
    }

    @Test
    fun testSaveOrUpdatePreset_overwrite() {
        val initialPreset = UserEqPreset(
            name = "Gaming Headset",
            description = "Initial bass",
            gainsDb = listOf(5.0f, 4.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        )
        manager.savePreset(initialPreset)
        assertEquals(1, manager.getPresets().count { it.name == "Gaming Headset" })

        // Overwrite with different gains
        val updatedPreset = initialPreset.copy(
            description = "Tuned for footsteps",
            gainsDb = listOf(2.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2.0f, 4.0f, 3.0f, 1.0f)
        )
        val success = manager.saveOrUpdatePreset(updatedPreset, overwriteExistingName = true)
        assertTrue(success)

        val retrieved = manager.findPresetByName("Gaming Headset")
        assertNotNull(retrieved)
        assertEquals("Tuned for footsteps", retrieved!!.description)
        assertEquals(2.0f, retrieved.gainsDb[0], 0.01f)
        assertEquals(4.0f, retrieved.gainsDb[7], 0.01f)
        // Ensure no duplicates were created
        assertEquals(1, manager.getPresets().count { it.name == "Gaming Headset" })
    }

    @Test
    fun testRenamePreset() {
        val preset = UserEqPreset(
            name = "Old Name",
            description = "Old Desc",
            gainsDb = List(10) { 0.0f }
        )
        manager.savePreset(preset)

        val renamed = manager.renamePreset(preset.id, "New Name", "New Desc")
        assertTrue(renamed)

        assertNull(manager.findPresetByName("Old Name"))
        val updated = manager.findPresetByName("New Name")
        assertNotNull(updated)
        assertEquals("New Desc", updated!!.description)
    }
}
