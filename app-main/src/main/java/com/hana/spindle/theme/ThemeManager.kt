package com.hana.spindle.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeManager(context: Context) {

    private val prefs = context.getSharedPreferences("spindle_themes", Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(loadInitialTheme())
    val currentTheme: StateFlow<CassetteTheme> = _currentTheme.asStateFlow()

    private fun loadInitialTheme(): CassetteTheme {
        val themeVer = prefs.getInt("theme_version", 0)
        if (themeVer < 2) {
            prefs.edit()
                .putInt("theme_version", 2)
                .putString("selected_theme_id", CassetteTheme.SONY_METAL_XR.id)
                .apply()
            return CassetteTheme.SONY_METAL_XR
        }
        val savedId = prefs.getString("selected_theme_id", CassetteTheme.SONY_METAL_XR.id)
        return CassetteTheme.ALL_PRESETS.find { it.id == savedId } ?: CassetteTheme.SONY_METAL_XR
    }

    fun setTheme(theme: CassetteTheme) {
        prefs.edit().putString("selected_theme_id", theme.id).apply()
        _currentTheme.value = theme
    }

    fun applyDynamicPalette(
        dominantColor: Int,
        vibrantColor: Int,
        darkMutedColor: Int
    ) {
        if (_currentTheme.value.id == CassetteTheme.ALBUM_ADAPTIVE.id) {
            val adaptiveTheme = CassetteTheme.ALBUM_ADAPTIVE.copy(
                shellColor = darkMutedColor,
                labelBackgroundColor = dominantColor,
                labelAccentColor = vibrantColor,
                reelHubColor = vibrantColor
            )
            _currentTheme.value = adaptiveTheme
        }
    }
}
