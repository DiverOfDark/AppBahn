package eu.appbahn.platform.resource.license.tool;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.appbahn.platform.resource.license.LicenseClaims;
import eu.appbahn.platform.resource.license.LicenseKeyCodec;
import eu.appbahn.platform.resource.license.LicenseLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Sign a customer license. Reads the private key (PKCS#8 PEM) and matching public key (X.509
 * SubjectPublicKeyInfo PEM), builds a {@link JWTClaimsSet}, and writes the compact JWS to disk.
 *
 * <p>Invoked via {@code ./gradlew :platform:resource:signLicense -PcustomerId=... -PmaxResources=...
 * -PvalidDays=... -PoutputFile=...}; the Gradle task forwards the properties to this main and
 * defaults the keypair to the committed pair under {@code spec/license-keys/}.
 */
public final class SignLicense {

    private SignLicense() {}

    public static void main(String[] args) throws Exception {
        Path privatePath = null;
        Path publicPath = null;
        String customerId = null;
        Integer maxResources = null;
        Integer validDays = null;
        Path outputFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--private-key" -> privatePath = Path.of(args[++i]);
                case "--public-key" -> publicPath = Path.of(args[++i]);
                case "--customer-id" -> customerId = args[++i];
                case "--max-resources" -> maxResources = Integer.parseInt(args[++i]);
                case "--valid-days" -> validDays = Integer.parseInt(args[++i]);
                case "--output-file" -> outputFile = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (privatePath == null
                || customerId == null
                || maxResources == null
                || validDays == null
                || outputFile == null) {
            throw new IllegalArgumentException("Usage: --private-key <path> [--public-key <path>] "
                    + "--customer-id <id> --max-resources <int> --valid-days <int> --output-file <path>");
        }
        if (publicPath == null) {
            // Default: sibling `public-key.pem` next to the private key file.
            publicPath = privatePath.resolveSibling("public-key.pem");
        }
        if (customerId.isBlank()) {
            throw new IllegalArgumentException("--customer-id must not be blank");
        }
        if (maxResources <= 0) {
            throw new IllegalArgumentException("--max-resources must be positive");
        }
        if (validDays <= 0) {
            throw new IllegalArgumentException("--valid-days must be positive");
        }

        PrivateKey privateKey = LicenseKeyCodec.readPrivateKeyPem(privatePath);
        PublicKey publicKey = readPublicKey(publicPath);
        OctetKeyPair signingKey = LicenseKeyCodec.toOctetKeyPair(privateKey, publicKey);

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(validDays * 86400L);
        UUID licenseId = UUID.randomUUID();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(LicenseClaims.ISSUER)
                .subject(customerId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("maxResources", maxResources)
                .claim("licenseId", licenseId.toString())
                .build();

        SignedJWT jws = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                        .type(new JOSEObjectType(LicenseLoader.LICENSE_TYP))
                        .build(),
                claims);
        jws.sign(new Ed25519Signer(signingKey));

        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        Files.writeString(outputFile, jws.serialize() + "\n", StandardCharsets.UTF_8);

        System.out.println("Wrote license to " + outputFile);
        System.out.println("  customer:     " + customerId);
        System.out.println("  maxResources: " + maxResources);
        System.out.println("  issuedAt:     " + now);
        System.out.println("  expiresAt:    " + expiresAt);
        System.out.println("  licenseId:    " + licenseId);
    }

    private static PublicKey readPublicKey(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return LicenseKeyCodec.readPublicKeyPem(in);
        }
    }
}
