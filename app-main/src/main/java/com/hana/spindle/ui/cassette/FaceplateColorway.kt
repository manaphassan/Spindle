package com.hana.spindle.ui.cassette

import android.graphics.Color

/**
 * Colorways for [FaceplateDeckView]. Values come from the approved faceplate mockup
 * (design/mockups/faceplate-deck) and the brand palette in docs/BRANDING.md.
 */
enum class FaceplateColorway(
    val prefValue: String,
    val displayName: String,
    val bodyTop: Int,
    val bodyBottom: Int,
    val brushLightAlpha: Float,
    val brushDarkAlpha: Float,
    val band: Int,
    val wordmark: Int,
    val bandSub: Int,
    val print: Int,
    val printMuted: Int,
    val doorFrame: Int,
    val glass: Int,
    val keyTop: Int,
    val keyTopEdge: Int,
    val keyFront: Int,
    val keyGlyph: Int,
    val playTop: Int,
    val playTopEdge: Int,
    val playFront: Int,
    val playGlyph: Int,
    val recess: Int,
    val knob: Int,
    val knobGroove: Int,
    val ledOff: Int,
    val ledOn: Int,
    val ledHot: Int,
    val lcdText: Int,
    val lcdSub: Int,
    val hint: Int
) {
    SILVER(
        prefValue = "faceplate_silver",
        displayName = "FACEPLATE · SILVER '81",
        bodyTop = Color.parseColor("#DADDE2"),
        bodyBottom = Color.parseColor("#B6BBC4"),
        brushLightAlpha = 0.22f,
        brushDarkAlpha = 0.05f,
        band = Color.parseColor("#2A2E45"),
        wordmark = Color.parseColor("#FAFAF9"),
        bandSub = Color.parseColor("#B0B4CE"),
        print = Color.parseColor("#2A2E45"),
        printMuted = Color.parseColor("#4A4F6A"),
        doorFrame = Color.parseColor("#2A2E45"),
        glass = Color.parseColor("#0E0F14"),
        keyTop = Color.parseColor("#ECEEF1"),
        keyTopEdge = Color.parseColor("#FFFFFF"),
        keyFront = Color.parseColor("#8E95A3"),
        keyGlyph = Color.parseColor("#2A2E45"),
        playTop = Color.parseColor("#F97316"),
        playTopEdge = Color.parseColor("#FDBA74"),
        playFront = Color.parseColor("#B4500C"),
        playGlyph = Color.parseColor("#2A2E45"),
        recess = Color.parseColor("#12131A"),
        knob = Color.parseColor("#D5D9DF"),
        knobGroove = Color.parseColor("#8E95A3"),
        ledOff = Color.parseColor("#272A36"),
        ledOn = Color.parseColor("#FDE68A"),
        ledHot = Color.parseColor("#FB7185"),
        lcdText = Color.parseColor("#FDE68A"),
        lcdSub = Color.parseColor("#B0B4CE"),
        hint = Color.parseColor("#4A4F6A")
    ),
    BLACK(
        prefValue = "faceplate_black",
        displayName = "FACEPLATE · PRO BLACK",
        bodyTop = Color.parseColor("#2A2B32"),
        bodyBottom = Color.parseColor("#17181D"),
        brushLightAlpha = 0.05f,
        brushDarkAlpha = 0.10f,
        band = Color.parseColor("#0F1014"),
        wordmark = Color.parseColor("#E9C98B"),
        bandSub = Color.parseColor("#A1A1AA"),
        print = Color.parseColor("#C9CAD1"),
        printMuted = Color.parseColor("#A1A1AA"),
        doorFrame = Color.parseColor("#0B0C10"),
        glass = Color.parseColor("#08090C"),
        keyTop = Color.parseColor("#34363F"),
        keyTopEdge = Color.parseColor("#4A4D58"),
        keyFront = Color.parseColor("#1F2027"),
        keyGlyph = Color.parseColor("#E4E4E7"),
        playTop = Color.parseColor("#F97316"),
        playTopEdge = Color.parseColor("#FDBA74"),
        playFront = Color.parseColor("#9A4309"),
        playGlyph = Color.parseColor("#1A1B22"),
        recess = Color.parseColor("#08090C"),
        knob = Color.parseColor("#4A4D58"),
        knobGroove = Color.parseColor("#1A1B22"),
        ledOff = Color.parseColor("#1E2029"),
        ledOn = Color.parseColor("#FDE68A"),
        ledHot = Color.parseColor("#FB7185"),
        lcdText = Color.parseColor("#FDE68A"),
        lcdSub = Color.parseColor("#A1A1AA"),
        hint = Color.parseColor("#A1A1AA")
    );

    companion object {
        const val PREF_DECK_STYLE = "pref_deck_style"
        const val STYLE_CLASSIC = "classic"
        val DEFAULT_STYLE = SILVER.prefValue

        fun fromPref(value: String?): FaceplateColorway? {
            val current = com.hana.spindle.util.LegacyPrefsMigration.deckStyle(value)
            return values().firstOrNull { it.prefValue == current }
        }
    }
}
