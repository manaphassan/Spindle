package com.hana.spindle.util

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPrefsMigrationTest {

    @Test
    fun deckStyleValuesAreRenamed() {
        assertEquals("faceplate_silver", LegacyPrefsMigration.deckStyle("walkman_silver"))
        assertEquals("faceplate_black", LegacyPrefsMigration.deckStyle("walkman_black"))
        assertEquals("classic", LegacyPrefsMigration.deckStyle("classic"))
        assertNull(LegacyPrefsMigration.deckStyle(null))
    }

    @Test
    fun storedSettingsCarryOver() {
        val app = MapPrefs()
        val themes = MapPrefs()
        app.edit().putString("pref_deck_style", "walkman_black").putBoolean("pref_walkman_hold_lock_keys", false)
            .apply()
        themes.edit().putString("selected_theme_id", "theme_sony_hf_90").apply()

        LegacyPrefsMigration.migrate(app, themes)

        assertEquals("faceplate_black", app.getString("pref_deck_style", null))
        assertFalse(app.getBoolean("pref_hold_lock_keys", true))
        assertFalse(app.contains("pref_walkman_hold_lock_keys"))
        assertEquals("theme_red_normal_type1", themes.getString("selected_theme_id", null))
    }

    @Test
    fun currentValuesAreLeftAlone() {
        val app = MapPrefs()
        val themes = MapPrefs()
        app.edit().putString("pref_deck_style", "faceplate_silver").putBoolean("pref_hold_lock_keys", true).apply()
        themes.edit().putString("selected_theme_id", "theme_metal_xr").apply()

        LegacyPrefsMigration.migrate(app, themes)

        assertEquals("faceplate_silver", app.getString("pref_deck_style", null))
        assertTrue(app.getBoolean("pref_hold_lock_keys", false))
        assertEquals("theme_metal_xr", themes.getString("selected_theme_id", null))
    }

    /** Minimal in-memory SharedPreferences. */
    private class MapPrefs : SharedPreferences {
        private val map = HashMap<String, Any?>()
        override fun getAll(): Map<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
            (map[key] as? Set<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = Editor()

        private inner class Editor : SharedPreferences.Editor {
            private val puts = HashMap<String, Any?>()
            private val removes = HashSet<String>()
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                puts[key!!] = value; return this
            }

            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
                puts[key!!] = values; return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                puts[key!!] = value; return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                puts[key!!] = value; return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                puts[key!!] = value; return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                puts[key!!] = value; return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                removes += key!!; return this
            }

            override fun clear(): SharedPreferences.Editor {
                map.clear(); return this
            }

            override fun commit(): Boolean {
                apply(); return true
            }

            override fun apply() {
                removes.forEach { map.remove(it) }
                map.putAll(puts)
            }
        }
    }
}
