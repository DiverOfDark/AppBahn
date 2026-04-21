package eu.appbahn.operator.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Operator's own ServiceAccount identity, read once at startup from the projected token at
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/token}. The token's {@code sub} claim
 * ({@code system:serviceaccount:<namespace>:<sa-name>}) is the exact string Kubernetes later
 * puts into {@code AdmissionRequest.userInfo.username} when this operator applies its own
 * CRDs — so the admission webhook can do an equality match instead of hardcoding the
 * namespace.
 *
 * <p>Token rotation does not change {@code sub} — that's the ServiceAccount's stable
 * identity, rebuilt from metadata, not the token's ephemeral fields — so reading once and
 * caching is safe.
 *
 * <p>Outside a pod (local dev, unit tests) the file is absent and {@link #username()} is
 * empty; the admission self-bypass then degrades to "disabled".
 */
@Component
public class OperatorIdentity {

    private static final Logger log = LoggerFactory.getLogger(OperatorIdentity.class);
    private static final Path DEFAULT_TOKEN_FILE = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");

    private final String username;

    @Autowired
    public OperatorIdentity(ObjectMapper objectMapper) {
        this(DEFAULT_TOKEN_FILE, objectMapper);
    }

    OperatorIdentity(Path tokenFile, ObjectMapper objectMapper) {
        this.username = readSubjectFromToken(tokenFile, objectMapper);
        if (username != null) {
            log.info("Operator identity resolved: {}", username);
        } else {
            log.info("No projected ServiceAccount token at {}; admission self-bypass disabled", tokenFile);
        }
    }

    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    private static String readSubjectFromToken(Path file, ObjectMapper om) {
        try {
            String jwt = Files.readString(file).trim();
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) {
                log.warn("Projected token at {} is not a three-segment JWT", file);
                return null;
            }
            JsonNode claims = om.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String sub = claims.path("sub").asText(null);
            return sub == null || sub.isBlank() ? null : sub;
        } catch (IOException e) {
            return null;
        } catch (IllegalArgumentException e) {
            log.warn("Projected token payload at {} is not base64url: {}", file, e.getMessage());
            return null;
        }
    }
}
