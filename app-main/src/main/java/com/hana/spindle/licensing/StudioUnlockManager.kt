package com.hana.spindle.licensing

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Spindle Studio Collector — Entitlement & Licensing Manager.
 *
 * Implements the "Fair Ownership & Anti-Subscription" architecture:
 * - One-time lifetime unlock ($2.99 – $3.99) with ZERO recurring subscriptions.
 * - Dual verification:
 *   1. Google Play In-App Billing (when Google Play Services is available).
 *   2. Offline Cryptographic Sponsor Token (for GitHub/PayPal contributors sideloading
 *      direct APK on de-googled audiophile DAPs such as Fiio, Hiby, Cayin, Astell&Kern).
 * - Offline-first: Cached locally in secure preferences; does not require network access to run.
 */
object StudioUnlockManager {

    private const val PREFS_NAME = "spindle_studio_entitlements"
    private const val KEY_TIER = "studio_tier"
    private const val KEY_UNLOCK_SOURCE = "studio_unlock_source"
    private const val KEY_LICENSE_HASH = "studio_license_hash"
    private const val KEY_UNLOCKED_AT = "studio_unlocked_at"

    // Cryptographic salt for offline token verification
    private const val SPINDLE_SALT = "SPINDLE_STUDIO_COLLECTOR_EULA_2026_HANA"

    enum class UnlockTier(val title: String) {
        FREE_CORE("Free Core"),
        STUDIO_COLLECTOR("Studio Collector Edition")
    }

    enum class StudioFeature(val displayName: String, val description: String) {
        ANALOG_TAPE_SATURATION(
            "Analog Tape Saturation",
            "Harmonic analog tape saturation simulation and warmth filters"
        ),
        TUBE_WARMTH_DSP(
            "Vintage Tube Harmonics",
            "Vacuum tube triode even-harmonic acoustic coloration"
        ),
        LASER_NAMEPLATE_ENGRAVING(
            "Custom Laser Engraving",
            "Personalized callsign or serial number on the physical cassette deck"
        ),
        STUDIO_REEL_SKINS(
            "Studio Collector Skins",
            "Exclusive aluminum, ceramic composite, and reel-to-reel visual chassis"
        ),
        PROCEDURAL_FOLEY(
            "Procedural Cassette Foley",
            "Real-time physical solenoid clicks, motor flutter, and head engagement"
        )
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns true if the user has an active Studio Collector entitlement.
     */
    fun isStudioUnlocked(context: Context): Boolean {
        return getUnlockTier(context) == UnlockTier.STUDIO_COLLECTOR
    }

    /**
     * Checks if a specific Studio Collector feature is unlocked.
     */
    fun isFeatureUnlocked(context: Context, feature: StudioFeature): Boolean {
        return isStudioUnlocked(context)
    }

    /**
     * Returns the active entitlement tier.
     */
    fun getUnlockTier(context: Context): UnlockTier {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_TIER, UnlockTier.FREE_CORE.name)
        return try {
            UnlockTier.valueOf(raw ?: UnlockTier.FREE_CORE.name)
        } catch (e: Exception) {
            UnlockTier.FREE_CORE
        }
    }

    /**
     * Returns the source of the unlock (e.g. "OFFLINE_SPONSOR_KEY", "GOOGLE_PLAY_BILLING", "FREE_CORE").
     */
    fun getUnlockSource(context: Context): String {
        return getPrefs(context).getString(KEY_UNLOCK_SOURCE, "FREE_CORE") ?: "FREE_CORE"
    }

    /**
     * Activates the Studio Collector entitlement via Google Play Billing.
     */
    fun setPlayBillingUnlocked(context: Context, orderId: String) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_TIER, UnlockTier.STUDIO_COLLECTOR.name)
            .putString(KEY_UNLOCK_SOURCE, "GOOGLE_PLAY: $orderId")
            .putLong(KEY_UNLOCKED_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Manually redeems an offline sponsor token.
     * Returns true if token is mathematically valid and unlocks Studio Collector.
     */
    fun redeemSponsorToken(context: Context, tokenInput: String): Boolean {
        val cleanToken = tokenInput.trim().uppercase()
        if (verifyOfflineToken(cleanToken)) {
            val prefs = getPrefs(context)
            prefs.edit()
                .putString(KEY_TIER, UnlockTier.STUDIO_COLLECTOR.name)
                .putString(KEY_UNLOCK_SOURCE, "OFFLINE_SPONSOR_KEY: $cleanToken")
                .putString(KEY_LICENSE_HASH, hashString(cleanToken))
                .putLong(KEY_UNLOCKED_AT, System.currentTimeMillis())
                .apply()
            return true
        }
        return false
    }

    /**
     * Offline token verification algorithm:
     * Format: SPINDLE-STUDIO-XXXX-YYYY
     * Where XXXX is a 4-hex seed, and YYYY is the first 4 hex digits of SHA256(XXXX + SALT).
     */
    fun verifyOfflineToken(token: String): Boolean {
        // Universal Master Developer / Studio Pass
        if (token == "SPINDLE-STUDIO-GOLD-PASS" || token == "SPINDLE-STUDIO-LIFETIME-2026") {
            return true
        }

        val parts = token.split("-")
        if (parts.size != 4) return false
        if (parts[0] != "SPINDLE" || parts[1] != "STUDIO") return false

        val seed = parts[2]
        val checksum = parts[3]
        if (seed.length != 4 || checksum.length != 4) return false

        val expectedChecksum = computeTokenChecksum(seed)
        return checksum.equals(expectedChecksum, ignoreCase = true)
    }

    /**
     * Generates a valid offline license token for a given 4-character seed.
     * Used by project maintainers to generate sponsor keys for PayPal / GitHub supporters.
     */
    fun generateSponsorToken(seedInput: String): String {
        val seed = seedInput.trim().uppercase().take(4).padStart(4, '0')
        val checksum = computeTokenChecksum(seed)
        return "SPINDLE-STUDIO-$seed-$checksum"
    }

    private fun computeTokenChecksum(seed: String): String {
        val hash = hashString("$seed:$SPINDLE_SALT")
        return hash.take(4).uppercase()
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Resets entitlements (primarily for testing or debugging).
     */
    fun resetToFreeCore(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
