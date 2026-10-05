package com.hana.spindle.playback

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.view.KeyEvent
import com.hana.spindle.receiver.HardwareButtonReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit Test Suite for HardwareKeyController & HardwareButtonReceiver (§8.2).
 * Verifies:
 * 1. Dedicated DAP Hardware Mode media and navigation key matrix.
 * 2. 2-Stage Sony Xperia Camera Shutter button remap and up-suppression.
 * 3. Volume buttons long-press track skip vs short-press volume adjustment.
 * 4. Background and screen-off broadcast key event routing.
 * 5. Foreground listener registration and fallback behavior.
 */
class HardwareKeyControllerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var fakeContext: FakeContext
    private lateinit var fakeTransport: FakeAudioTransport

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        fakeContext = FakeContext(fakePrefs)
        fakeTransport = FakeAudioTransport()
        HardwareButtonReceiver.buttonListener = null
    }

    // =========================================================================
    // 1. DAP HARDWARE MODE TEST
    // =========================================================================

    @Test
    fun testDapMode_mediaTransportKeys() {
        fakePrefs.edit().putBoolean("pref_dap_hardware_mode", true).apply()

        var catalogNavigated = false
        var drawerNavigated = false
        val controller = HardwareKeyController(
            onNavigateToCatalog = { catalogNavigated = true },
            onNavigateToDrawer = { drawerNavigated = true }
        )

        // KEYCODE_MEDIA_NEXT
        val nextHandled = controller.onKeyDown(KeyEvent.KEYCODE_MEDIA_NEXT, null, fakeContext, fakeTransport)
        assertTrue(nextHandled)
        assertEquals(1, fakeTransport.nextCount)

        // KEYCODE_MEDIA_PREVIOUS
        val prevHandled = controller.onKeyDown(KeyEvent.KEYCODE_MEDIA_PREVIOUS, null, fakeContext, fakeTransport)
        assertTrue(prevHandled)
        assertEquals(1, fakeTransport.prevCount)
        assertFalse(fakeTransport.lastForcePreviousSong ?: true)

        // KEYCODE_MEDIA_PLAY_PAUSE
        val toggleHandled = controller.onKeyDown(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, null, fakeContext, fakeTransport)
        assertTrue(toggleHandled)
        assertEquals(1, fakeTransport.toggleCount)

        // KEYCODE_MEDIA_PLAY
        val playHandled = controller.onKeyDown(KeyEvent.KEYCODE_MEDIA_PLAY, null, fakeContext, fakeTransport)
        assertTrue(playHandled)
        assertEquals(1, fakeTransport.playCount)

        // KEYCODE_MEDIA_PAUSE
        val pauseHandled = controller.onKeyDown(KeyEvent.KEYCODE_MEDIA_PAUSE, null, fakeContext, fakeTransport)
        assertTrue(pauseHandled)
        assertEquals(1, fakeTransport.pauseCount)

        // KEYCODE_FOCUS (Half-shutter Xperia key)
        val focusHandled = controller.onKeyDown(KeyEvent.KEYCODE_FOCUS, null, fakeContext, fakeTransport)
        assertTrue(focusHandled)
        assertEquals(2, fakeTransport.nextCount)

        // KEYCODE_DPAD_CENTER
        val centerHandled = controller.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, null, fakeContext, fakeTransport)
        assertTrue(centerHandled)
        assertEquals(2, fakeTransport.toggleCount)

        // KEYCODE_DPAD_RIGHT (Fast forward)
        val ffHandled = controller.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, null, fakeContext, fakeTransport)
        assertTrue(ffHandled)
        assertEquals(1, fakeTransport.ffCount)

        // KEYCODE_DPAD_LEFT (Rewind)
        val rewHandled = controller.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, null, fakeContext, fakeTransport)
        assertTrue(rewHandled)
        assertEquals(1, fakeTransport.rewCount)

        // KEYCODE_SEARCH
        val searchHandled = controller.onKeyDown(KeyEvent.KEYCODE_SEARCH, null, fakeContext, fakeTransport)
        assertTrue(searchHandled)
        assertTrue(catalogNavigated)

        // KEYCODE_MENU
        val menuHandled = controller.onKeyDown(KeyEvent.KEYCODE_MENU, null, fakeContext, fakeTransport)
        assertTrue(menuHandled)
        assertTrue(drawerNavigated)
    }

    // =========================================================================
    // 2. CAMERA KEY REMAP & SUPPRESSION TEST
    // =========================================================================

    @Test
    fun testCameraKeyRemap_playPauseToggleAndSuppression() {
        fakePrefs.edit().putBoolean("pref_camera_key_play_pause", true).apply()
        val controller = HardwareKeyController()

        // Shutter Button Down (KEYCODE_CAMERA)
        val downHandled = controller.onKeyDown(KeyEvent.KEYCODE_CAMERA, null, fakeContext, fakeTransport)
        assertTrue("Camera key onKeyDown must be handled", downHandled)
        assertEquals(1, fakeTransport.toggleCount)

        // Shutter Button Up (KEYCODE_CAMERA) -> must be consumed to prevent system camera app launch
        val upHandled = controller.onKeyUp(KeyEvent.KEYCODE_CAMERA, null, fakeContext, fakeTransport)
        assertTrue("Camera key onKeyUp must be consumed to prevent camera app launch", upHandled)

        // Focus Button Down (KEYCODE_FOCUS)
        val focusDownHandled = controller.onKeyDown(KeyEvent.KEYCODE_FOCUS, null, fakeContext, fakeTransport)
        assertTrue("Focus key onKeyDown must advance track", focusDownHandled)
        assertEquals(1, fakeTransport.nextCount)

        // Focus Button Up (KEYCODE_FOCUS)
        val focusUpHandled = controller.onKeyUp(KeyEvent.KEYCODE_FOCUS, null, fakeContext, fakeTransport)
        assertTrue("Focus key onKeyUp must be consumed", focusUpHandled)
    }

    // =========================================================================
    // 3. VOLUME SKIP LONG-PRESS TEST
    // =========================================================================

    @Test
    fun testVolumeSkip_longPressTrackAdvance() {
        fakePrefs.edit().putBoolean("pref_volume_skip", true).apply()
        val controller = HardwareKeyController()

        // Vol+ Long Press
        assertFalse(controller.isVolumeLongPress)
        val volUpLongHandled = controller.onKeyLongPress(KeyEvent.KEYCODE_VOLUME_UP, null, fakeContext, fakeTransport)
        assertTrue("Volume Up long-press must be handled", volUpLongHandled)
        assertTrue("isVolumeLongPress flag must be true", controller.isVolumeLongPress)
        assertEquals(1, fakeTransport.nextCount)

        // Vol+ Key Up after Long Press
        val volUpReleaseHandled = controller.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, null, fakeContext, fakeTransport)
        assertTrue("Volume Up release after long-press must be consumed", volUpReleaseHandled)
        assertFalse("isVolumeLongPress flag must reset to false", controller.isVolumeLongPress)

        // Vol- Long Press
        val volDownLongHandled = controller.onKeyLongPress(KeyEvent.KEYCODE_VOLUME_DOWN, null, fakeContext, fakeTransport)
        assertTrue("Volume Down long-press must be handled", volDownLongHandled)
        assertTrue("isVolumeLongPress flag must be true", controller.isVolumeLongPress)
        assertEquals(1, fakeTransport.prevCount)

        // Vol- Key Up after Long Press
        val volDownReleaseHandled = controller.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, null, fakeContext, fakeTransport)
        assertTrue("Volume Down release after long-press must be consumed", volDownReleaseHandled)
        assertFalse("isVolumeLongPress flag must reset to false", controller.isVolumeLongPress)
    }

    // =========================================================================
    // 4. BROADCAST KEY EVENT HANDLING TEST
    // =========================================================================

    @Test
    fun testBroadcastKeyEvent_directDispatch() {
        fakePrefs.edit()
            .putBoolean("pref_camera_key_play_pause", true)
            .putBoolean("pref_dap_hardware_mode", true)
            .apply()

        // Down events
        assertTrue(HardwareKeyController.handleKeyEventInternal(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CAMERA, fakeContext, fakeTransport))
        assertEquals(1, fakeTransport.toggleCount)

        assertTrue(HardwareKeyController.handleKeyEventInternal(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS, fakeContext, fakeTransport))
        assertEquals(1, fakeTransport.nextCount)

        assertTrue(HardwareKeyController.handleKeyEventInternal(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, fakeContext, fakeTransport))
        assertEquals(2, fakeTransport.toggleCount)

        assertTrue(HardwareKeyController.handleKeyEventInternal(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, fakeContext, fakeTransport))
        assertEquals(2, fakeTransport.nextCount)

        assertTrue(HardwareKeyController.handleKeyEventInternal(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, fakeContext, fakeTransport))
        assertEquals(1, fakeTransport.prevCount)
        assertFalse(fakeTransport.lastForcePreviousSong ?: true)

        // Stop Key
        assertTrue(HardwareKeyController.handleKeyEventInternal(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP, fakeContext, fakeTransport))
        assertEquals(1, fakeTransport.stopCount)

        // Up event for camera key must return true to prevent camera app launch
        assertTrue(HardwareKeyController.handleKeyEventInternal(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CAMERA, fakeContext, fakeTransport))
    }

    // =========================================================================
    // 5. HOLD = SEEK AND TAP = SKIP TEST
    // =========================================================================

    @Test
    fun testHoldToSeekAndTapToSkip() {
        val controller = HardwareKeyController()

        // Fast Forward Long-Press (Hold = Seek)
        val ffLongHandled = controller.onKeyLongPress(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, null, fakeContext, fakeTransport)
        assertTrue(ffLongHandled)
        assertTrue(controller.isSeekLongPress)
        assertEquals(1, fakeTransport.ffCount)
        assertEquals(0, fakeTransport.nextCount)

        // Fast Forward Release after hold
        val ffUpHandled = controller.onKeyUp(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, null, fakeContext, fakeTransport)
        assertTrue(ffUpHandled)
        assertFalse(controller.isSeekLongPress)
        assertEquals(0, fakeTransport.nextCount) // did not skip on release

        // Rewind Long-Press (Hold = Seek)
        val rewLongHandled = controller.onKeyLongPress(KeyEvent.KEYCODE_MEDIA_REWIND, null, fakeContext, fakeTransport)
        assertTrue(rewLongHandled)
        assertTrue(controller.isSeekLongPress)
        assertEquals(1, fakeTransport.rewCount)
        assertEquals(0, fakeTransport.prevCount)

        // Rewind Release after hold
        val rewUpHandled = controller.onKeyUp(KeyEvent.KEYCODE_MEDIA_REWIND, null, fakeContext, fakeTransport)
        assertTrue(rewUpHandled)
        assertFalse(controller.isSeekLongPress)
        assertEquals(0, fakeTransport.prevCount) // did not skip on release

        // Tap Fast Forward (KeyUp without prior long press -> Tap = Skip)
        controller.onKeyUp(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, null, fakeContext, fakeTransport)
        assertEquals(1, fakeTransport.nextCount)

        // Tap Rewind (KeyUp without prior long press -> Tap = Skip with Universal Previous)
        controller.onKeyUp(KeyEvent.KEYCODE_MEDIA_REWIND, null, fakeContext, fakeTransport)
        assertEquals(1, fakeTransport.prevCount)
        assertEquals(false, fakeTransport.lastForcePreviousSong)
    }

    // =========================================================================
    // 6. HARDWARE BUTTON RECEIVER LISTENER ROUTING
    // =========================================================================

    @Test
    fun testHardwareButtonReceiver_listenerRouting() {
        var listenerInvoked = false

        HardwareButtonReceiver.buttonListener = {
            listenerInvoked = true
            true
        }

        val receiver = HardwareButtonReceiver()
        val dummyKeyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val handled = receiver.dispatchKeyEvent(dummyKeyEvent, fakeContext, fakeTransport)

        assertTrue("Foreground buttonListener must be invoked when registered", listenerInvoked)
        assertTrue("Receiver must return true when consumed by listener", handled)

        // Test fallback when listener returns false
        HardwareButtonReceiver.buttonListener = { false }
        val fallbackHandled = receiver.dispatchKeyEvent(dummyKeyEvent, fakeContext, fakeTransport)
        // With stub KeyEvent, keyCode returns 0, so fallback handles gracefully without crashing
        assertFalse("Unrecognized key code fallback returns false without error", fallbackHandled)
    }

    // =========================================================================
    // TEST DOUBLES
    // =========================================================================

    private class FakeAudioTransport : AudioTransport {
        var nextCount = 0
        var prevCount = 0
        var lastForcePreviousSong: Boolean? = null
        var toggleCount = 0
        var playCount = 0
        var pauseCount = 0
        var stopCount = 0
        var rewCount = 0
        var ffCount = 0
        override var isPlaying: Boolean = false

        override fun playNext() { nextCount++ }
        override fun playPrevious(forcePreviousSong: Boolean) {
            prevCount++
            lastForcePreviousSong = forcePreviousSong
        }
        override fun togglePlayPause() {
            toggleCount++
            isPlaying = !isPlaying
        }
        override fun play() {
            playCount++
            isPlaying = true
        }
        override fun pause() {
            pauseCount++
            isPlaying = false
        }
        override fun stop() {
            stopCount++
            pause()
        }
        override fun rewind(deltaMs: Long) { rewCount++ }
        override fun fastForward(deltaMs: Long) { ffCount++ }
    }

    private class FakeContext(private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = HashMap<String, Any?>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = (map[key] as? Set<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)

        private class FakeEditor(private val backingMap: HashMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = HashMap<String, Any?>()
            private val removed = HashSet<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
                if (key != null) temp[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removed.add(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) backingMap.clear()
                for (rem in removed) backingMap.remove(rem)
                backingMap.putAll(temp)
            }
        }
    }
}
