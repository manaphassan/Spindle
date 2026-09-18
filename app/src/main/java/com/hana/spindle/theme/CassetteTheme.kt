
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
            name = "Mono",
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

        // 4. Type II High-Bias 90 (High Position)
        val TDK_SA_90 = CassetteTheme(
            id = "theme_type2_highbias",
            name = "Type II High-Bias",
            subtitle = "Smoked Graphite & Gold Foil",
            chassisStyle = ChassisStyle.VERTICAL_DECK,
            chassisColor = Color.parseColor("#15171C"),
            diagonalBezelColor = Color.parseColor("#1A1C22"),
            dialColor = Color.parseColor("#FAFAF9"),
            surfaceColor = Color.parseColor("#1C1E24"),
            cardBorderColor = Color.parseColor("#2E333D"),
            textPrimaryColor = Color.parseColor("#FAFAF9"),
            textSecondaryColor = Color.parseColor("#9CA3AF"),
            accentColor = Color.parseColor("#D4AF37"),
            shellColor = Color.parseColor("#1B1D22"),
            shellTexture = ShellTexture.MATTE_PLASTIC,
            labelBackgroundColor = Color.parseColor("#111317"),
            labelTextColor = Color.parseColor("#D4AF37"), // Gold foil
            labelAccentColor = Color.parseColor("#991B1B"), // Crimson accent
            windowTint = Color.argb(45, 212, 175, 55),
            reelHubColor = Color.parseColor("#F5F2EB"), // Vintage cream hubs
            tapeRibbonColor = Color.parseColor("#1A1412"), // Cobalt dark tape
            vfdGlowColor = Color.parseColor("#FBBF24"),
            isDarkAppTheme = true
        )

        // 5. Type II Amber Gold 90 (High Epitaxial)
        val MAXELL_XLII = CassetteTheme(
            id = "theme_type2_ambergold",
            name = "Type II Amber Gold",
            subtitle = "Amber Smoked & Dual Gold Spools",
            chassisStyle = ChassisStyle.VERTICAL_DECK,
            chassisColor = Color.parseColor("#1A140E"),
            diagonalBezelColor = Color.parseColor("#241C14"),
            dialColor = Color.parseColor("#FAFAF9"),
            surfaceColor = Color.parseColor("#221810"),
            cardBorderColor = Color.parseColor("#38281B"),
            textPrimaryColor = Color.parseColor("#FAFAF9"),
            textSecondaryColor = Color.parseColor("#A8998C"),
            accentColor = Color.parseColor("#F59E0B"),
            shellColor = Color.parseColor("#261B12"),
            shellTexture = ShellTexture.CLEAR_ACRYLIC,
            labelBackgroundColor = Color.parseColor("#382315"),
            labelTextColor = Color.parseColor("#FCD34D"),
            labelAccentColor = Color.parseColor("#EA580C"),
            windowTint = Color.argb(40, 245, 158, 11),
            reelHubColor = Color.parseColor("#F59E0B"), // Gold spools
            tapeRibbonColor = Color.parseColor("#2B170E"),
            vfdGlowColor = Color.parseColor("#F59E0B"),
            isDarkAppTheme = true
        )

        // 6. Type II Anthracite Chrome (Chromdioxid Extra)
        val BASF_CHROME = CassetteTheme(
            id = "theme_type2_chrome",
            name = "Anthracite Chrome",
            subtitle = "Matte Anthracite & Hazard Yellow",
            chassisStyle = ChassisStyle.VERTICAL_DECK,
            chassisColor = Color.parseColor("#121417"),
            diagonalBezelColor = Color.parseColor("#1A1D21"),
            dialColor = Color.parseColor("#FAFAF9"),
            surfaceColor = Color.parseColor("#181B20"),
            cardBorderColor = Color.parseColor("#2A2E36"),
            textPrimaryColor = Color.parseColor("#FAFAF9"),
            textSecondaryColor = Color.parseColor("#94A3B8"),
            accentColor = Color.parseColor("#EAB308"), // German Yellow
            shellColor = Color.parseColor("#1A1D22"),
            shellTexture = ShellTexture.BRUSHED_METAL,
            labelBackgroundColor = Color.parseColor("#0F1113"),
            labelTextColor = Color.parseColor("#E5E7EB"),
            labelAccentColor = Color.parseColor("#EAB308"),
            windowTint = Color.argb(35, 234, 179, 8),
            reelHubColor = Color.parseColor("#D1D5DB"), // Silver chromium
            tapeRibbonColor = Color.parseColor("#101214"), // True black CrO2
            vfdGlowColor = Color.parseColor("#EAB308"),
            isDarkAppTheme = true
        )

        // 7. Skeleton Reel-to-Reel
        val SKELETON_REEL = CassetteTheme(
            id = "theme_skeleton_reel",
            name = "Skeleton Reel",
            subtitle = "Clear Acrylic & Red Anodized Hubs",
            chassisStyle = ChassisStyle.VERTICAL_DECK,
            chassisColor = Color.parseColor("#0E1117"),
            diagonalBezelColor = Color.parseColor("#161B22"),
            dialColor = Color.parseColor("#FAFAF9"),
            surfaceColor = Color.parseColor("#161B22"),
            cardBorderColor = Color.parseColor("#262D38"),
            textPrimaryColor = Color.parseColor("#FAFAF9"),
            textSecondaryColor = Color.parseColor("#94A3B8"),
            accentColor = Color.parseColor("#E11D48"),
            shellColor = Color.parseColor("#13171F"),
            shellTexture = ShellTexture.CLEAR_ACRYLIC,
            labelBackgroundColor = Color.parseColor("#0B0E14"),
            labelTextColor = Color.parseColor("#F87171"),
            labelAccentColor = Color.parseColor("#E11D48"),
            windowTint = Color.argb(25, 225, 29, 72),
            reelHubColor = Color.parseColor("#E11D48"), // Anodized red hubs
            tapeRibbonColor = Color.parseColor("#2D1A14"),
            vfdGlowColor = Color.parseColor("#FB7185"),
            isDarkAppTheme = true
        )

        // All Curated Theme Presets
        val ALL_PRESETS = listOf(
            DARK,
            LIGHT,
            MONOCHROME_EINK,
            TDK_SA_90,
            MAXELL_XLII,
            BASF_CHROME,
            SKELETON_REEL
        )

        // Backward compatibility aliases for legacy references
        val VERTICAL_STUDIO_DECK = DARK
        val VAPORWAVE_80S = LIGHT
        val WM2_RED_HERO = DARK
        val TYPE_I_NORMAL = DARK
        val TYPE_IV_METAL = LIGHT
        val TYPE_II_CHROME = DARK
        val ALBUM_ADAPTIVE = DARK
    }
}
