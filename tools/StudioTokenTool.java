import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

/**
 * Maintainer-only tool for Spindle Studio sponsor tokens (ECDSA P-256).
 * The PRIVATE key must never be committed or shipped; the app only embeds the public key.
 *
 *   java tools/StudioTokenTool.java keygen <private-key-out-file>
 *   java tools/StudioTokenTool.java sign   <private-key-file> <ID 4-16 chars A-Z0-9>
 *
 * Token format: SPINDLE-STUDIO-<ID>-<BASE32(r||s)>  (case-insensitive, no dashes in base32)
 * Signed message: "SPINDLE-STUDIO-V1:" + ID
 */
public class StudioTokenTool {
    static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static void main(String[] a) throws Exception {
        if (a.length >= 2 && a[0].equals("keygen")) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = g.generateKeyPair();
            Files.writeString(Path.of(a[1]), Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
            System.out.println("PUBLIC_KEY_X509_B64=" + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
        } else if (a.length == 3 && a[0].equals("sign")) {
            String id = a[2].toUpperCase();
            if (!id.matches("[A-Z0-9]{4,16}")) throw new IllegalArgumentException("ID must be 4-16 chars A-Z0-9");
            byte[] pk = Base64.getDecoder().decode(Files.readString(Path.of(a[1])).trim());
            PrivateKey priv = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pk));
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(priv);
            s.update(("SPINDLE-STUDIO-V1:" + id).getBytes(StandardCharsets.UTF_8));
            System.out.println("SPINDLE-STUDIO-" + id + "-" + b32(derToRaw(s.sign())));
        } else {
            System.err.println("usage: keygen <out> | sign <privfile> <ID>");
            System.exit(2);
        }
    }

    static byte[] derToRaw(byte[] der) {
        int i = 3, rl = der[i++] & 0xff;
        byte[] r = java.util.Arrays.copyOfRange(der, i, i + rl); i += rl + 1;
        int sl = der[i++] & 0xff;
        byte[] s = java.util.Arrays.copyOfRange(der, i, i + sl);
        byte[] out = new byte[64];
        copyFixed(r, out, 0); copyFixed(s, out, 32);
        return out;
    }
    static void copyFixed(byte[] v, byte[] out, int off) {
        byte[] u = new BigInteger(1, v).toByteArray();
        int len = Math.min(u.length, 32);
        System.arraycopy(u, u.length - len, out, off + 32 - len, len);
    }
    static String b32(byte[] d) {
        StringBuilder sb = new StringBuilder();
        int buf = 0, bits = 0;
        for (byte x : d) { buf = (buf << 8) | (x & 0xff); bits += 8;
            while (bits >= 5) { sb.append(ALPHA.charAt((buf >> (bits - 5)) & 31)); bits -= 5; } }
        if (bits > 0) sb.append(ALPHA.charAt((buf << (5 - bits)) & 31));
        return sb.toString();
    }
}
