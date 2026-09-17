
package com.hana.spindle.theme

import android.graphics.Color

enum class ShellTexture {
    MATTE_PLASTIC,
    BRUSHED_METAL,
    CLEAR_ACRYLIC,
    CERAMIC_COMPOSITE
}

enum class ChassisStyle {
    VERTICAL_DECK,     // Dark brushed aluminum faceplate, vertical cassette, linear ruler, 4 bottom buttons
    VAPORWAVE_80S,     // Minimalist light faceplate
    WM2_RED            // 1981 WM-2 Red horizontal cut chassis
}

/**
 * Complete Audiophile 60:30:10 Design Tokens
 */
data class CassetteTheme(
    val id: String,
    val name: String,
    val subtitle: String,
    val chassisStyle: ChassisStyle = ChassisStyle.VERTICAL_DECK,
    // 60% Dominant Base
    val chassisColor: Int = Color.parseColor("#2A2E45"),
    val diagonalBezelColor: Int = Color.parseColor("#1F2233"),
    val dialColor: Int = Color.parseColor("#FAFAF9"),
    // 30% Secondary Structural (Surfaces, Cards, Dividers, Secondary Text)
    val surfaceColor: Int = Color.parseColor("#202334"),
    val cardBorderColor: Int = Color.parseColor("#3B405D"),
    val textPrimaryColor: Int = Color.parseColor("#FAFAF9"),
    val textSecondaryColor: Int = Color.parseColor("#B0B4CE"),
    // 10% High-Impact Accent
    val accentColor: Int = Color.parseColor("#F97316"),
    // Cassette Shell & Tape Properties
    val shellColor: Int,
    val shellTexture: ShellTexture = ShellTexture.MATTE_PLASTIC,
    val labelBackgroundColor: Int,
    val labelTextColor: Int,
    val labelAccentColor: Int,
    val windowTint: Int = Color.argb(40, 250, 250, 249),
    val reelHubColor: Int = Color.WHITE,
    val tapeRibbonColor: Int = Color.parseColor("#2E1F1A"),
    val vfdGlowColor: Int = Color.parseColor("#FDE68A"),
    val isDarkAppTheme: Boolean = true
) {
    companion object {
        // 1. Dark Theme (Indigo Mixtape & Warm Orange) - 60:30:10 Brand Benchmark
        val DARK = CassetteTheme(
            id = "theme_dark",
            name = "Dark Theme",
            subtitle = "Dark Indigo & Mixtape Accent",
            chassisStyle = ChassisStyle.VERTICAL_DECK,
            chassisColor = Color.parseColor("#2A2E45"), // 60% Dark Indigo Chassis
            diagonalBezelColor = Color.parseColor("#1F2233"),
            dialColor = Color.parseColor("#FAFAF9"),
            surfaceColor = Color.parseColor("#202334"), // 30% Deep Indigo Surfaces
            cardBorderColor = Color.parseColor("#3B405D"),
            textPrimaryColor = Color.parseColor("#FAFAF9"), // Crisp Pale Warm Stone
            textSecondaryColor = Color.parseColor("#B0B4CE"),
            accentColor = Color.parseColor("#F97316"), // 10% Warm Tangerine Orange Hero
            shellColor = Color.parseColor("#1E2132"),
            shellTexture = ShellTexture.MATTE_PLASTIC,
            labelBackgroundColor = Color.parseColor("#FDE68A"), // Butter Yellow Tape Label
            labelTextColor = Color.parseColor("#2A2E45"),      // Dark Indigo Ink
            labelAccentColor = Color.parseColor("#F97316"),
            windowTint = Color.argb(35, 250, 250, 249),
            reelHubColor = Color.parseColor("#FAFAF9"),
            tapeRibbonColor = Color.parseColor("#2E1F1A"),
            vfdGlowColor = Color.parseColor("#FDE68A"), // Butter Yellow Warm Glow
            isDarkAppTheme = true
        )

        // 2. Light Theme (Sunny Mixtape & Dark Indigo Frame) - 60:30:10 Light
        val LIGHT = CassetteTheme(
            id = "theme_light",
            name = "Light Theme",
            subtitle = "Sunny Mixtape & Dark Indigo Frame",
            chassisStyle = ChassisStyle.VAPORWAVE_80S,
            chassisColor = Color.parseColor("#FAFAF9"), // 60% Pale Warm Stone Base
            diagonalBezelColor = Color.parseColor("#E5E5E2"),
            dialColor = Color.parseColor("#2A2E45"),
            surfaceColor = Color.parseColor("#FFFFFF"),
            cardBorderColor = Color.parseColor("#E5E5E2"),
            textPrimaryColor = Color.parseColor("#2A2E45"), // Dark Indigo High Contrast
            textSecondaryColor = Color.parseColor("#5A5E78"),
            accentColor = Color.parseColor("#F97316"), // 10% Warm Tangerine Orange Hero
            shellColor = Color.parseColor("#FAFAF9"),
            shellTexture = ShellTexture.MATTE_PLASTIC,
            labelBackgroundColor = Color.parseColor("#FDE68A"), // Butter Yellow Tape Label
            labelTextColor = Color.parseColor("#2A2E45"),      // Dark Indigo Ink
            labelAccentColor = Color.parseColor("#F97316"),
            windowTint = Color.argb(20, 42, 46, 69),
            reelHubColor = Color.parseColor("#2A2E45"),
            tapeRibbonColor = Color.parseColor("#38231B"),
            vfdGlowColor = Color.parseColor("#F97316"),
            isDarkAppTheme = false
        )

        // 3. Monochrome E-Ink Theme (Pure 1-Bit Monochrome Black & White on White Background)
        val MONOCHROME_EINK = CassetteTheme(
            id = "theme_monochrome_eink",
            name = "Monochrome E-Ink",
            subtitle = "Pure 1-Bit B&W White Canvas",
            chassisStyle = ChassisStyle.VERTICAL_DECK,
            chassisColor = Color.WHITE, // Pure white background for E-Ink screen
            diagonalBezelColor = Color.parseColor("#E0E0E0"),
            dialColor = Color.BLACK,
            surfaceColor = Color.WHITE,
            cardBorderColor = Color.BLACK,
            textPrimaryColor = Color.BLACK,
            textSecondaryColor = Color.BLACK,
            accentColor = Color.BLACK,
            shellColor = Color.WHITE,
            shellTexture = ShellTexture.MATTE_PLASTIC,
            labelBackgroundColor = Color.WHITE,
            labelTextColor = Color.BLACK,
            labelAccentColor = Color.BLACK,
            windowTint = Color.argb(0, 0, 0, 0),
            reelHubColor = Color.BLACK,
            tapeRibbonColor = Color.BLACK,
            vfdGlowColor = Color.BLACK,
            isDarkAppTheme = false
        )

        // Three Curated Themes
        val ALL_PRESETS = listOf(
            DARK,
            LIGHT,
            MONOCHROME_EINK
        )

        // Backward compatibility aliases for legacy references
        val VERTICAL_STUDIO_DECK = DARK
        val VAPORWAVE_80S = LIGHT
        val WM2_RED_HERO = DARK
        val SONY_HF_90 = DARK
        val SONY_METAL_MASTER = LIGHT
        val SONY_CD_IT = DARK
        val BASF_CHROME = DARK
        val TDK_SA_90 = DARK
        val ALBUM_ADAPTIVE = DARK
    }
}
