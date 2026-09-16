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

data class CassetteTheme(
    val id: String,
    val name: String,
    val subtitle: String,
    val chassisStyle: ChassisStyle = ChassisStyle.VERTICAL_DECK,
    // Chassis Properties
    val chassisColor: Int = Color.parseColor("#18181A"),
    val diagonalBezelColor: Int = Color.parseColor("#121214"),
    val dialColor: Int = Color.parseColor("#D1D5DB"),
    // Cassette Shell & Tape Properties
    val shellColor: Int,
    val shellTexture: ShellTexture = ShellTexture.MATTE_PLASTIC,
    val labelBackgroundColor: Int,
    val labelTextColor: Int,
    val labelAccentColor: Int,
    val windowTint: Int = Color.argb(40, 255, 255, 255),
    val reelHubColor: Int = Color.WHITE,
    val tapeRibbonColor: Int = Color.parseColor("#3E2723"),
    val vfdGlowColor: Int = Color.parseColor("#00E5FF"),
    val isDarkAppTheme: Boolean = true
) {
    companion object {
        // 1. Dark Theme (Studio Slate / Charcoal & Deep Smoky Shell)
        val DARK = CassetteTheme(
            id = "theme_dark",
            name = "Dark Theme",
            subtitle = "Matte Slate & Smoky Shell",
            chassisStyle = ChassisStyle.VERTICAL_DECK,
            chassisColor = Color.parseColor("#141518"),
            diagonalBezelColor = Color.parseColor("#0E0F12"),
            dialColor = Color.parseColor("#D1D5DB"),
            shellColor = Color.parseColor("#16181C"),
            shellTexture = ShellTexture.MATTE_PLASTIC,
            labelBackgroundColor = Color.parseColor("#1C1E24"),
            labelTextColor = Color.parseColor("#F1F5F9"),
            labelAccentColor = Color.parseColor("#E53935"), // Red racing accent
            windowTint = Color.argb(40, 255, 255, 255),
            reelHubColor = Color.parseColor("#CBD5E1"),
            tapeRibbonColor = Color.parseColor("#38231B"),
            vfdGlowColor = Color.parseColor("#00E676"),
            isDarkAppTheme = true
        )

        // 2. Light Theme (Braun / Dieter Rams Minimalist Off-White)
        val LIGHT = CassetteTheme(
            id = "theme_light",
            name = "Light Theme",
            subtitle = "Braun Off-White & Minimalist",
            chassisStyle = ChassisStyle.VAPORWAVE_80S,
            chassisColor = Color.parseColor("#F1F3F5"),
            diagonalBezelColor = Color.parseColor("#E2E5E9"),
            dialColor = Color.parseColor("#475569"),
            shellColor = Color.parseColor("#FAFAFA"),
            shellTexture = ShellTexture.MATTE_PLASTIC,
            labelBackgroundColor = Color.parseColor("#FFFFFF"),
            labelTextColor = Color.parseColor("#0F172A"),
            labelAccentColor = Color.parseColor("#E53935"),
            windowTint = Color.argb(25, 0, 0, 0),
            reelHubColor = Color.parseColor("#64748B"),
            tapeRibbonColor = Color.parseColor("#1E1B4B"),
            vfdGlowColor = Color.parseColor("#0F172A"),
            isDarkAppTheme = false
        )

        // 3. Monochrome E-Ink Theme (Pure 1-Bit High-Contrast Black & White)
        val MONOCHROME_EINK = CassetteTheme(
            id = "theme_monochrome_eink",
            name = "Monochrome E-Ink",
            subtitle = "High-Contrast 1-Bit B&W",
            chassisStyle = ChassisStyle.VERTICAL_DECK,
            chassisColor = Color.BLACK,
            diagonalBezelColor = Color.BLACK,
            dialColor = Color.WHITE,
            shellColor = Color.WHITE,
            shellTexture = ShellTexture.MATTE_PLASTIC,
            labelBackgroundColor = Color.WHITE,
            labelTextColor = Color.BLACK,
            labelAccentColor = Color.BLACK,
            windowTint = Color.argb(0, 0, 0, 0),
            reelHubColor = Color.BLACK,
            tapeRibbonColor = Color.BLACK,
            vfdGlowColor = Color.WHITE,
            isDarkAppTheme = true
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
