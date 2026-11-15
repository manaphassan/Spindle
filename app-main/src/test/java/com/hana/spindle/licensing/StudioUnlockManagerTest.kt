package com.hana.spindle.licensing

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class StudioUnlockManagerTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private val b32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun toRaw(der: ByteArray): ByteArray {
        var i = 3
        val rl = der[i++].toInt() and 0xff
        val r = der.copyOfRange(i, i + rl); i += rl + 1
        val sl = der[i++].toInt() and 0xff
        val s = der.copyOfRange(i, i + sl)
        val out = ByteArray(64)
        fun put(v: ByteArray, off: Int) {
            val u = java.math.BigInteger(1, v).toByteArray()
            val len = minOf(u.size, 32)
            System.arraycopy(u, u.size - len, out, off + 32 - len, len)
        }
        put(r, 0); put(s, 32)
        return out
    }

    private fun encode32(d: ByteArray): String {
        val sb = StringBuilder(); var buf = 0; var bits = 0
        for (x in d) {
            buf = (buf shl 8) or (x.toInt() and 0xff); bits += 8
            while (bits >= 5) { sb.append(b32[(buf shr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) sb.append(b32[(buf shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun sign(id: String, key: KeyPair = keyPair): String {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(key.private)
        s.update("SPINDLE-STUDIO-V1:$id".toByteArray())
        return "SPINDLE-STUDIO-$id-${encode32(toRaw(s.sign()))}"
    }

    @Test
    fun validSignedTokenVerifies() {
        repeat(20) { n ->
            val token = sign("SPONSOR$n")
            assertTrue(StudioUnlockManager.verifyOfflineToken(token, keyPair.public))
            // case/whitespace tolerant, as typed by users
            assertTrue(StudioUnlockManager.verifyOfflineToken(" ${token.lowercase()}\n", keyPair.public))
        }
    }

    @Test
    fun formerMasterAndLegacyTokensAreRejected() {
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-GOLD-PASS"))
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-LIFETIME-2026"))
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-ABCD-0000"))
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-GOLD-PASS", keyPair.public))
    }

    @Test
    fun tamperedOrForeignTokensAreRejected() {
        val token = sign("ABCD1234")
        val parts = token.split("-")
        // different ID, same signature
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-ZZZZ9999-${parts[3]}", keyPair.public))
        // flipped signature char
        val sig = parts[3]
        val flipped = (if (sig[5] == 'A') 'B' else 'A').toString()
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-ABCD1234-" + sig.replaceRange(5, 6, flipped), keyPair.public))
        // signed by another key
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        assertFalse(StudioUnlockManager.verifyOfflineToken(sign("ABCD1234", other), keyPair.public))
        // not signed by the embedded key
        assertFalse(StudioUnlockManager.verifyOfflineToken(token))
    }

    @Test
    fun malformedTokensAreRejected() {
        for (t in listOf("INVALID-KEY", "SPINDLE-STUDIO-123", "SPINDLE-STUDIO-12345-ABCD", "", "SPINDLE-STUDIO-AB-CD",
            "SPINDLE-STUDIO-ABCD1234-!!!!")) {
            assertFalse(t, StudioUnlockManager.verifyOfflineToken(t, keyPair.public))
        }
    }

    @Test
    fun studioFeaturesEnum() {
        assertEquals(5, StudioUnlockManager.StudioFeature.values().size)
    }
}
