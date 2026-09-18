package com.hana.spindle.launcher

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentAppsManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var manager: RecentAppsManager

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        manager = RecentAppsManager(prefs = fakePrefs)
    }

    @Test
    fun testRecordAppLaunch_movesToFront() {
        manager.recordAppLaunch("com.sony.walkman")
        manager.recordAppLaunch("com.hiby.music")
        manager.recordAppLaunch("com.foobar2000")

        val recents = manager.getRecentPackageNames()
        assertEquals(listOf("com.foobar2000", "com.hiby.music", "com.sony.walkman"), recents)
    }

    @Test
    fun testRecordAppLaunch_deduplicatesAndBumpsToFront() {
        manager.recordAppLaunch("app.a")
        manager.recordAppLaunch("app.b")
        manager.recordAppLaunch("app.c")
        manager.recordAppLaunch("app.a") // Re-launch app.a

        val recents = manager.getRecentPackageNames()
        assertEquals(listOf("app.a", "app.c", "app.b"), recents)
    }

    @Test
    fun testRecordAppLaunch_enforcesCapOf20() {
        for (i in 1..25) {
            manager.recordAppLaunch("app.pkg.$i")
        }

        val recents = manager.getRecentPackageNames()
        assertEquals(20, recents.size)
        assertEquals("app.pkg.25", recents.first())
        assertEquals("app.pkg.6", recents.last())
    }

    @Test
    fun testGetRecentApps_resolvesInstalledAppsInOrder() {
        manager.recordAppLaunch("app.music")
        manager.recordAppLaunch("app.radio")

        val dummyApp1 = AppInfo("Radio", "app.radio", "app.radio.MainActivity", null)
        val dummyApp2 = AppInfo("Music", "app.music", "app.music.MainActivity", null)
        val dummyApp3 = AppInfo("Gallery", "app.gallery", "app.gallery.MainActivity", null)

        val resolved = manager.getRecentApps(listOf(dummyApp1, dummyApp2, dummyApp3))
        assertEquals(2, resolved.size)
        assertEquals("app.radio", resolved[0].packageName)
        assertEquals("app.music", resolved[1].packageName)
    }

    @Test
    fun testClearRecentApps() {
        manager.recordAppLaunch("app.test")
        manager.clearRecentApps()
        assertTrue(manager.getRecentPackageNames().isEmpty())
    }

    /**
     * Minimal in-memory SharedPreferences implementation for hermetic unit testing.
     */
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
