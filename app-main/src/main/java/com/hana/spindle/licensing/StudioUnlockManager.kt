package com.hana.spindle.licensing

import android.content.Context
import android.content.SharedPreferences
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Spindle Studio Collector — Entitlement & Licensing Manager.
 *
 * Implements the "Fair Ownership & Anti-Subscription" architecture:
 * - One-time lifetime unlock ($2.99 – $3.99) with ZERO recurring subscriptions.
 * - Dual verification:
 *   1. Google Play In-App Billing (when Google Play Services is available).
 *   2. Offline ECDSA-signed Sponsor Token (for GitHub/PayPal contributors sideloading
 *      direct APK on de-googled audiophile DAPs such as Fiio, Hiby, Cayin, Astell&Kern).
 * - Offline-first: Cached locally in secure preferences; does not require network access to run.
 */
object StudioUnlockManager {

    private const val PREFS_NAME = "spindle_studio_entitlements"
    private const val KEY_TIER = "studio_tier"
    private const val KEY_UNLOCK_SOURCE = "studio_unlock_source"
    private const val KEY_LICENSE_HASH = "studio_license_hash"
    private const val KEY_UNLOCKED_AT = "studio_unlocked_at"

    private const val KEY_TOKEN = "studio_token"
    private const val SIGNED_PREFIX = "SPINDLE-STUDIO-V1:"
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    // ECDSA P-256 public key (X.509 DER, base64). Verification-only: the private key is kept
    // offline by the maintainer and is NOT in this repository. Generate tokens with
    // `java tools/StudioTokenTool.java sign <private-key-file> <ID>`.
    private const val PUBLIC_KEY_X509_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEnfEgF2zTs+RVpass+EFhaICPt9TzGrPi35d/cT2Q2EydaYzRDRpJMaaE4ffOJ3awq4rZvBtlvFRi9NG574S5lA=="

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
        if (getUnlockTier(context) != UnlockTier.STUDIO_COLLECTOR) return false
        val prefs = getPrefs(context)
        // Offline unlocks are re-verified on every check so editing the prefs file is not enough.
        if (prefs.getString(KEY_UNLOCK_SOURCE, "")?.startsWith("OFFLINE_SPONSOR_KEY") == true) {
            return verifyOfflineToken(prefs.getString(KEY_TOKEN, "") ?: "")
        }
        return true
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
     *
     * Internal on purpose: callers MUST have verified the purchase signature / acknowledged
     * state with the Play Billing library first. Nothing in the app calls this yet.
     */
    internal fun setPlayBillingUnlocked(context: Context, orderId: String) {
        getPrefs(context).edit()
            .putString(KEY_TIER, UnlockTier.STUDIO_COLLECTOR.name)
            .putString(KEY_UNLOCK_SOURCE, "GOOGLE_PLAY: $orderId")
            .remove(KEY_TOKEN)
            .putLong(KEY_UNLOCKED_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Manually redeems an offline sponsor token (ECDSA P-256 signed, see tools/StudioTokenTool.java).
     * Returns true only if the signature verifies against the embedded public key.
     */
    fun redeemSponsorToken(context: Context, tokenInput: String): Boolean {
        val cleanToken = normalizeToken(tokenInput)
        if (!verifyOfflineToken(cleanToken)) return false
        getPrefs(context).edit()
            .putString(KEY_TIER, UnlockTier.STUDIO_COLLECTOR.name)
            .putString(KEY_UNLOCK_SOURCE, "OFFLINE_SPONSOR_KEY")
            .putString(KEY_TOKEN, cleanToken)
            .putString(KEY_LICENSE_HASH, hashString(cleanToken))
            .putLong(KEY_UNLOCKED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    private fun normalizeToken(input: String): String =
        input.filterNot { it.isWhitespace() }.uppercase()

    /**
     * Verifies a token of the form SPINDLE-STUDIO-<ID>-<BASE32(r||s)> against [publicKey].
     * The signed message is "SPINDLE-STUDIO-V1:" + ID. The default key is the embedded
     * maintainer public key; the matching private key never ships with the app.
     */
    fun verifyOfflineToken(token: String, publicKey: PublicKey? = embeddedPublicKey): Boolean {
        if (publicKey == null) return false
        return try {
            val parts = normalizeToken(token).split("-")
            if (parts.size != 4 || parts[0] != "SPINDLE" || parts[1] != "STUDIO") return false
            val id = parts[2]
            if (!ID_REGEX.matches(id)) return false
            val raw = base32Decode(parts[3]) ?: return false
            if (raw.size != 64) return false
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update("$SIGNED_PREFIX$id".toByteArray(Charsets.UTF_8))
            sig.verify(rawToDer(raw))
        } catch (e: Exception) {
            false
        }
    }

    private val ID_REGEX = Regex("[A-Z0-9]{4,16}")

    private val embeddedPublicKey: PublicKey? by lazy {
        try {
            val der = Base64.getDecoder().decode(PUBLIC_KEY_X509_B64)
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
        } catch (e: Exception) {
            null
        }
    }

    private fun base32Decode(s: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buf = 0
        var bits = 0
        for (c in s) {
            val v = BASE32.indexOf(c)
            if (v < 0) return null
            buf = (buf shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buf shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    private fun rawToDer(raw: ByteArray): ByteArray {
        fun enc(off: Int): ByteArray {
            var b = raw.copyOfRange(off, off + 32).dropWhile { it == 0.toByte() }.toByteArray()
            if (b.isEmpty()) b = byteArrayOf(0)
            if (b[0] < 0) b = byteArrayOf(0) + b
            return byteArrayOf(0x02, b.size.toByte()) + b
        }
        val body = enc(0) + enc(32)
        return byteArrayOf(0x30, body.size.toByte()) + body
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
