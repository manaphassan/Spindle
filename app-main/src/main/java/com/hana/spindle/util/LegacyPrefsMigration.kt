package com.hana.spindle.util

import android.content.Context
import android.content.SharedPreferences

/**
 * One-time rename of preference keys and values that were stored up to v1.7.0 under names
 * containing third-party product names. The legacy strings below exist only so those saved
 * settings carry over; nothing new is ever written with them.
 */
object LegacyPrefsMigration {

    const val PREF_DECK_STYLE = "pref_deck_style"
    const val PREF_HOLD_LOCK = "pref_hold_lock_keys"
    const val PREF_THEME_ID = "selected_theme_id"

    private const val LEGACY_HOLD_LOCK = "pref_walkman_hold_lock_keys"

    private val DECK_STYLES = mapOf(
        "walkman_silver" to "faceplate_silver",
        "walkman_black" to "faceplate_black"
    )

    private val THEME_IDS = mapOf(
        "theme_sony_hf_90" to "theme_red_normal_type1",
        "theme_sony_duad_type3" to "theme_ferrichrome_type3"
    )

    /** Current deck-style value for [value], translating a legacy one. */
    fun deckStyle(value: String?): String? = value?.let { DECK_STYLES[it] ?: it }

    /** Current theme id for [value], translating a legacy one. */
    fun themeId(value: String?): String? = value?.let { THEME_IDS[it] ?: it }

    fun migrate(context: Context) {
        migrate(
            context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE),
            context.getSharedPreferences("spindle_themes", Context.MODE_PRIVATE)
        )
    }

    fun migrate(appPrefs: SharedPreferences, themePrefs: SharedPreferences) {
        val app = appPrefs.edit()
        var appChanged = false
        appPrefs.getString(PREF_DECK_STYLE, null)?.let { old ->
            val new = deckStyle(old)
            if (new != old) {
                app.putString(PREF_DECK_STYLE, new); appChanged = true
            }
        }
        if (appPrefs.contains(LEGACY_HOLD_LOCK)) {
            if (!appPrefs.contains(PREF_HOLD_LOCK)) {
                app.putBoolean(PREF_HOLD_LOCK, appPrefs.getBoolean(LEGACY_HOLD_LOCK, true))
            }
            app.remove(LEGACY_HOLD_LOCK)
            appChanged = true
        }
        if (appChanged) app.apply()

        themePrefs.getString(PREF_THEME_ID, null)?.let { old ->
            val new = themeId(old)
            if (new != old) themePrefs.edit().putString(PREF_THEME_ID, new).apply()
        }
    }
}
