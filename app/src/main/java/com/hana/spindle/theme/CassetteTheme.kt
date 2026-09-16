package com.hana.spindle.theme

import android.graphics.Color

enum class ShellTexture {
    MATTE_PLASTIC,
    BRUSHED_METAL,
    CLEAR_ACRYLIC,
    CERAMIC_COMPOSITE
}

data class CassetteTheme(
    val id: String,
    val name: String,
    val subtitle: String,
    // WM-2 Hardware Chassis Properties
    val chassisColor: Int = Color.parseColor("#D71920"),      // Iconic WM-2 Red
    val diagonalBezelColor: Int = Color.parseColor("#1C1C1E"),// Top-right angled panel
    val dialColor: Int = Color.parseColor("#D1D5DB"),         // Knurled volume dial
    // Cassette Shell & Tape Properties
    val shellColor: Int,                                      // Cassette body color
    val shellTexture: ShellTexture = ShellTexture.MATTE_PLASTIC,
    val labelBackgroundColor: Int,                            // Tape sticker background
    val labelTextColor: Int,                                  // Song / Artist typography
    val labelAccentColor: Int,                                // Header stripe / accent
    val windowTint: Int = Color.argb(40, 255, 255, 255),      // Acrylic window tint
    val reelHubColor: Int = Color.WHITE,                      // Plastic 6-tooth spindle hub
    val tapeRibbonColor: Int = Color.parseColor("#3E2723"),   // Ferric / Chrome / Metal tape pack
    val vfdGlowColor: Int = Color.parseColor("#00E5FF"),      // VFD digital counter glow
    val isDarkAppTheme: Boolean = true
) {
    companion object {
        // 1. Hero Theme: Sony Walkman II (WM-2) Red with Official Walkman Tape
        val WM2_RED_HERO = CassetteTheme(
            id = "wm2_red_hero",
            name = "Walkman II Red",
            subtitle = "Sony WM-2 (1981) + Walkman Tape",
            chassisColor = Color.parseColor("#D71920"),
            diagonalBezelColor = Color.parseColor("#1C1C1E"),
            shellColor = Color.parseColor("#262626"),
            labelBackgroundColor = Color.parseColor("#1A1A1A"),
            labelTextColor = Color.parseColor("#FAFAFA"),
            labelAccentColor = Color.parseColor("#D71920"),
            reelHubColor = Color.parseColor("#E52521"),
            tapeRibbonColor = Color.parseColor("#2A1810"),
            vfdGlowColor = Color.parseColor("#00E676")
        )

        // 2. Sony HF-90 (Classic 1980s Normal Bias Type I)
        val SONY_HF_90 = CassetteTheme(
            id = "sony_hf_90",
            name = "Sony HF-90",
            subtitle = "Type I Normal Bias",
            chassisColor = Color.parseColor("#C8102E"),
            diagonalBezelColor = Color.parseColor("#1F1F23"),
            shellColor = Color.parseColor("#2D2D2D"),
            labelBackgroundColor = Color.parseColor("#F5F3E9"), // Textured vintage ivory
            labelTextColor = Color.parseColor("#1E1E1E"),
            labelAccentColor = Color.parseColor("#D32F2F"),
            reelHubColor = Color.WHITE,
            tapeRibbonColor = Color.parseColor("#4A2810")
        )

        // 3. Sony Metal Master (Type IV Ceramic Composite)
        val SONY_METAL_MASTER = CassetteTheme(
            id = "sony_metal_master",
            name = "Metal Master",
            subtitle = "Type IV Ceramic Composite",
            chassisColor = Color.parseColor("#18181B"), // Studio stealth chassis
            diagonalBezelColor = Color.parseColor("#101012"),
            shellColor = Color.parseColor("#E4E4E7"),   // Pure ceramic white
            shellTexture = ShellTexture.CERAMIC_COMPOSITE,
            labelBackgroundColor = Color.parseColor("#E4E4E7"),
            labelTextColor = Color.parseColor("#27272A"),
            labelAccentColor = Color.parseColor("#CA8A04"), // Gold foil
            reelHubColor = Color.WHITE,
            tapeRibbonColor = Color.parseColor("#18181B"),
            vfdGlowColor = Color.parseColor("#CA8A04")
        )

        // 4. Sony CD-IT (1990s Translucent Polycarbonate)
        val SONY_CD_IT = CassetteTheme(
            id = "sony_cd_it",
            name = "Sony CD-IT",
            subtitle = "1990s Sapphire Polycarbonate",
            chassisColor = Color.parseColor("#0F172A"),
            diagonalBezelColor = Color.parseColor("#090D16"),
            shellColor = Color.parseColor("#1E40AF"),
            shellTexture = ShellTexture.CLEAR_ACRYLIC,
            labelBackgroundColor = Color.parseColor("#1E3A8A"),
            labelTextColor = Color.parseColor("#E0E7FF"),
            labelAccentColor = Color.parseColor("#A855F7"),
            reelHubColor = Color.parseColor("#38BDF8"),
            tapeRibbonColor = Color.parseColor("#1E1B4B"),
            vfdGlowColor = Color.parseColor("#38BDF8")
        )

        // 5. BASF Chromdioxid 90 (Type II Chrome)
        val BASF_CHROME = CassetteTheme(
            id = "basf_chrome",
            name = "BASF Chromdioxid",
            subtitle = "Type II Chrome High Bias",
            chassisColor = Color.parseColor("#B91C1C"),
            diagonalBezelColor = Color.parseColor("#18181B"),
            shellColor = Color.parseColor("#27272A"),
            labelBackgroundColor = Color.parseColor("#FAFAFA"),
            labelTextColor = Color.parseColor("#18181B"),
            labelAccentColor = Color.parseColor("#EA580C"), // BASF orange
            reelHubColor = Color.parseColor("#EA580C"),
            tapeRibbonColor = Color.parseColor("#1C1917"),
            vfdGlowColor = Color.parseColor("#FB923C")
        )

        // 6. TDK SA-90 (High Bias Midnight Black & Gold)
        val TDK_SA_90 = CassetteTheme(
            id = "tdk_sa_90",
            name = "TDK SA-90",
            subtitle = "Type II Super Avilyn",
            chassisColor = Color.parseColor("#991B1B"),
            diagonalBezelColor = Color.parseColor("#18181B"),
            shellColor = Color.parseColor("#18181B"),
            labelBackgroundColor = Color.parseColor("#27272A"),
            labelTextColor = Color.parseColor("#FEF08A"),
            labelAccentColor = Color.parseColor("#EAB308"), // Gold
            reelHubColor = Color.parseColor("#EF4444"),     // Red racing hubs
            tapeRibbonColor = Color.parseColor("#09090B")
        )

        // 7. Dynamic Chameleon (Extracts from Album Art)
        val ALBUM_ADAPTIVE = CassetteTheme(
            id = "album_adaptive",
            name = "Album Adaptive",
            subtitle = "Dynamic Art Chameleon",
            chassisColor = Color.parseColor("#D71920"),
            diagonalBezelColor = Color.parseColor("#1C1C1E"),
            shellColor = Color.parseColor("#262626"),
            labelBackgroundColor = Color.parseColor("#18181B"),
            labelTextColor = Color.WHITE,
            labelAccentColor = Color.parseColor("#D71920"),
            reelHubColor = Color.WHITE,
            tapeRibbonColor = Color.parseColor("#3E2723")
        )

        val ALL_PRESETS = listOf(
            WM2_RED_HERO,
            SONY_HF_90,
            SONY_METAL_MASTER,
            SONY_CD_IT,
            BASF_CHROME,
            TDK_SA_90,
            ALBUM_ADAPTIVE
        )
    }
}
