package eu.appbahn.platform.resource.license.tool;

import eu.appbahn.platform.resource.license.LicenseKeyCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * One-shot Ed25519 keypair generator. Refuses to overwrite either output file — regenerating the
 * production keypair invalidates every license already issued.
 *
 * <p>Invoked via {@code ./gradlew :platform:resource:generateLicenseKeys} (the Gradle task wires
 * the default output paths under {@code spec/license-keys/} and the platform JAR's
 * {@code resources/license/}; see {@code backend/platform/resource/build.gradle.kts}).
 */
public final class GenerateLicenseKeys {

    private GenerateLicenseKeys() {}

    public static void main(String[] args) throws Exception {
        Path privatePath = null;
        Path publicPath = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--private-key" -> privatePath = Path.of(args[++i]);
                case "--public-key" -> publicPath = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (privatePath == null || publicPath == null) {
            throw new IllegalArgumentException("Usage: --private-key <path> --public-key <path>");
        }
        if (Files.exists(privatePath)) {
            throw new IOException("Refusing to overwrite existing private key at " + privatePath);
        }
        if (Files.exists(publicPath)) {
            throw new IOException("Refusing to overwrite existing public key at " + publicPath);
        }

        KeyPair pair = newKeyPair();
        Files.createDirectories(privatePath.getParent());
        Files.createDirectories(publicPath.getParent());
        Files.writeString(privatePath, LicenseKeyCodec.encodePrivateKey(pair.getPrivate()), StandardCharsets.UTF_8);
        Files.writeString(publicPath, LicenseKeyCodec.encodePublicKey(pair.getPublic()), StandardCharsets.UTF_8);
        // Best-effort tighten the private key to owner-only. POSIX may not exist on Windows.
        try {
            Files.setPosixFilePermissions(
                    privatePath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem; the umask is best we can do.
        }

        System.out.println("Wrote private key to " + privatePath);
        System.out.println("Wrote public key to " + publicPath);
    }

    private static KeyPair newKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        return gen.generateKeyPair();
    }
}
