package eu.appbahn.platform.resource.license;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Read/write Ed25519 keys as PEM (PKCS#8 for private, X.509 SubjectPublicKeyInfo for public) and
 * convert them to Nimbus's {@link OctetKeyPair} for JWS signing/verification.
 *
 * <p>The JDK's built-in {@code "Ed25519"} {@link KeyFactory} accepts the standard PKCS#8 / X.509
 * encodings — no BouncyCastle provider registration needed for the codec step.
 */
public final class LicenseKeyCodec {

    private static final String EDDSA_ALGORITHM = "Ed25519";

    private LicenseKeyCodec() {}

    public static String encodePrivateKey(PrivateKey key) {
        return wrapPem("PRIVATE KEY", key.getEncoded());
    }

    public static String encodePublicKey(PublicKey key) {
        return wrapPem("PUBLIC KEY", key.getEncoded());
    }

    public static PrivateKey readPrivateKeyPem(Path path) throws IOException {
        byte[] der = decodePem(Files.readString(path, StandardCharsets.UTF_8), "PRIVATE KEY");
        try {
            return KeyFactory.getInstance(EDDSA_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IOException("Failed to decode Ed25519 private key from " + path, e);
        }
    }

    public static PublicKey readPublicKeyPem(InputStream in) throws IOException {
        byte[] der = decodePem(new String(in.readAllBytes(), StandardCharsets.UTF_8), "PUBLIC KEY");
        try {
            return KeyFactory.getInstance(EDDSA_ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IOException("Failed to decode Ed25519 public key", e);
        }
    }

    public static OctetKeyPair toOctetKeyPair(java.security.KeyPair pair) throws JOSEException {
        return new OctetKeyPair.Builder(Curve.Ed25519, base64Url(rawPublic(pair.getPublic())))
                .d(base64Url(rawPrivate(pair.getPrivate())))
                .build();
    }

    public static OctetKeyPair toOctetKeyPair(PrivateKey privateKey, PublicKey publicKey) {
        return new OctetKeyPair.Builder(Curve.Ed25519, base64Url(rawPublic(publicKey)))
                .d(base64Url(rawPrivate(privateKey)))
                .build();
    }

    public static OctetKeyPair toOctetKeyPair(PublicKey publicKey) {
        return new OctetKeyPair.Builder(Curve.Ed25519, base64Url(rawPublic(publicKey))).build();
    }

    /**
     * Extract the 32-byte raw Ed25519 public scalar from an X.509 SubjectPublicKeyInfo encoding.
     * The DER prefix is fixed (12 bytes: SEQUENCE / SEQUENCE / OID 1.3.101.112 / BIT STRING tag),
     * so the raw key sits at the tail.
     */
    private static byte[] rawPublic(PublicKey key) {
        byte[] enc = key.getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(enc, enc.length - 32, raw, 0, 32);
        return raw;
    }

    /**
     * Extract the 32-byte raw Ed25519 seed from a PKCS#8 PrivateKeyInfo encoding. Like the
     * public-side prefix, the wrapper is fixed-size (PKCS#8 with the OctetString-of-OctetString
     * wrapper RFC 8410 specifies), so the seed sits at the last 32 bytes.
     */
    private static byte[] rawPrivate(PrivateKey key) {
        byte[] enc = key.getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(enc, enc.length - 32, raw, 0, 32);
        return raw;
    }

    private static com.nimbusds.jose.util.Base64URL base64Url(byte[] raw) {
        return com.nimbusds.jose.util.Base64URL.encode(raw);
    }

    private static String wrapPem(String label, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
    }

    private static byte[] decodePem(String pem, String label) {
        String beginMarker = "-----BEGIN " + label + "-----";
        String endMarker = "-----END " + label + "-----";
        int begin = pem.indexOf(beginMarker);
        int end = pem.indexOf(endMarker);
        if (begin < 0 || end < 0) {
            throw new IllegalArgumentException("Missing PEM markers for " + label);
        }
        String body = pem.substring(begin + beginMarker.length(), end).replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }
}
