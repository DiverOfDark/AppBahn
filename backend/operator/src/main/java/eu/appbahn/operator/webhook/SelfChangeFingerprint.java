package eu.appbahn.operator.webhook;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Layer-3 audit loop guard. Anything in the operator that patches a Resource CR spec
 * (defaulter webhooks, future self-healing) should call {@link #recordOperatorWrite} with
 * the resourceVersion K8s returns from the write; {@link ResourceAdmissionController} then
 * checks {@link #isSelfChange} and skips audit emission for that specific version.
 *
 * <p>Layer 2 (operator ServiceAccount username filter in the admission handler) already
 * covers every CR write path the operator performs today, so this cache currently runs
 * empty. It is deliberately defence-in-depth for features that would impersonate the user.
 *
 * <p>Entries self-expire on next access after TTL; a hard cap prevents runaway growth if
 * record/read cadence goes out of balance. The map is expected to stay tiny in practice.
 */
@Component
public class SelfChangeFingerprint {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final int MAX_SIZE = 10_000;

    private final Map<String, Instant> recentWrites = new ConcurrentHashMap<>();

    public void recordOperatorWrite(String namespace, String name, String resourceVersion) {
        if (namespace == null || name == null || resourceVersion == null || resourceVersion.isEmpty()) {
            return;
        }
        if (recentWrites.size() >= MAX_SIZE) {
            prune();
        }
        recentWrites.put(key(namespace, name, resourceVersion), Instant.now().plus(TTL));
    }

    public boolean isSelfChange(String namespace, String name, String resourceVersion) {
        if (namespace == null || name == null || resourceVersion == null || resourceVersion.isEmpty()) {
            return false;
        }
        String k = key(namespace, name, resourceVersion);
        Instant expiry = recentWrites.get(k);
        if (expiry == null) {
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            recentWrites.remove(k, expiry);
            return false;
        }
        return true;
    }

    private void prune() {
        Instant now = Instant.now();
        recentWrites.entrySet().removeIf(e -> now.isAfter(e.getValue()));
    }

    private static String key(String namespace, String name, String resourceVersion) {
        return namespace + "/" + name + "@" + resourceVersion;
    }
}
