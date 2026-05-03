package eu.appbahn.operator.migrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Applies the per-kind chain of {@link CrMigration} beans to a raw {@code spec} and reports the
 * resulting version. The runner has two callers — the operator's startup sweep and the mutating
 * admission webhook — both of which feed it the raw {@code spec} object plus the version they
 * read from the CR's {@code appbahn.eu/resource-schema-version} annotation.
 *
 * <p>The runner itself stays small and stateless: collect beans, validate the chains at startup
 * (no version gaps, no duplicates), and slice the chain on demand. The decision of "what to do
 * when the annotation is absent" is the caller's responsibility — only the caller knows the
 * typed model class to attempt deserialization against; see {@link #detectFromVersion}.
 */
@Component
public class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final ObjectMapper objectMapper;
    private final Map<String, List<CrMigration>> chainsByKind;

    public MigrationRunner(List<CrMigration> migrations, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Map<String, List<CrMigration>> grouped = new HashMap<>();
        for (CrMigration m : migrations) {
            grouped.computeIfAbsent(m.kind(), k -> new ArrayList<>()).add(m);
        }
        Map<String, List<CrMigration>> sealed = new HashMap<>();
        for (Map.Entry<String, List<CrMigration>> e : grouped.entrySet()) {
            List<CrMigration> chain = new ArrayList<>(e.getValue());
            chain.sort((a, b) -> Integer.compare(a.version(), b.version()));
            validateChain(e.getKey(), chain);
            sealed.put(e.getKey(), Collections.unmodifiableList(chain));
        }
        this.chainsByKind = Collections.unmodifiableMap(sealed);
    }

    @PostConstruct
    void logRegisteredMigrations() {
        if (chainsByKind.isEmpty()) {
            log.info("No CR schema migrations registered");
            return;
        }
        chainsByKind.forEach((kind, chain) -> log.info(
                "CR schema migrations for kind {}: current version {} ({} step(s))",
                kind,
                currentVersion(kind),
                chain.size()));
    }

    /**
     * Highest version registered for the given kind, or {@code 0} if no migrations exist (the
     * spec is in its original/baseline shape and nothing has ever migrated it).
     */
    public int currentVersion(String kind) {
        List<CrMigration> chain = chainsByKind.get(kind);
        if (chain == null || chain.isEmpty()) {
            return 0;
        }
        return chain.get(chain.size() - 1).version();
    }

    /**
     * Apply the chain {@code (fromVersion, currentVersion]} to {@code spec}. {@code fromVersion}
     * may be {@code null} (treated as 0 — run the entire chain). Returns the migrated spec
     * (same instance — mutated in place — for caller convenience), the new version, and a
     * {@code changed} flag indicating whether anything actually ran.
     */
    public MigratedResult migrate(String kind, ObjectNode spec, Integer fromVersion) {
        int from = fromVersion == null ? 0 : fromVersion;
        int target = currentVersion(kind);
        if (from >= target) {
            return new MigratedResult(spec, target, false);
        }
        List<CrMigration> chain = chainsByKind.getOrDefault(kind, List.of());
        for (CrMigration m : chain) {
            if (m.version() <= from) {
                continue;
            }
            log.debug("Applying migration {} v{} ({}) to {}", kind, m.version(), m.description(), spec);
            m.apply(spec);
        }
        return new MigratedResult(spec, target, true);
    }

    /**
     * Decide the {@code fromVersion} for a CR that lacks the schema-version annotation. If the
     * raw spec deserializes cleanly into the typed model the CR is already current — return
     * {@link #currentVersion(String)} for that kind, no migration needed. Otherwise treat it as
     * v0 and let the full chain run. Pass the raw {@code spec} (not the whole CR) and the typed
     * spec class.
     */
    public int detectFromVersion(String kind, ObjectNode spec, Class<?> typedSpecModel) {
        try {
            objectMapper.treeToValue(spec, typedSpecModel);
            return currentVersion(kind);
        } catch (Exception e) {
            log.debug(
                    "Spec for kind {} does not match typed model {} (will run full chain): {}",
                    kind,
                    typedSpecModel.getSimpleName(),
                    e.getMessage());
            return 0;
        }
    }

    /** Read the schema-version annotation off the CR's metadata; returns {@code null} if absent. */
    public Integer readVersionAnnotation(JsonNode cr, String annotationKey) {
        if (cr == null) {
            return null;
        }
        JsonNode metadata = cr.get("metadata");
        if (metadata == null) {
            return null;
        }
        JsonNode annotations = metadata.get("annotations");
        if (annotations == null) {
            return null;
        }
        JsonNode value = annotations.get(annotationKey);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException nfe) {
            log.warn("Ignoring non-integer {} annotation value: {}", annotationKey, value.asText());
            return null;
        }
    }

    private static void validateChain(String kind, List<CrMigration> chain) {
        for (int i = 0; i < chain.size(); i++) {
            CrMigration m = chain.get(i);
            int expected = i + 1;
            if (m.version() == expected) {
                continue;
            }
            if (i > 0 && chain.get(i - 1).version() == m.version()) {
                throw new IllegalStateException(
                        "Duplicate migration version for kind " + kind + " v" + m.version() + ": "
                                + chain.get(i - 1).getClass().getName() + " and "
                                + m.getClass().getName());
            }
            throw new IllegalStateException("Migration chain for kind " + kind + " has a gap: expected v" + expected
                    + " but found v" + m.version() + " (class " + m.getClass().getName() + ")");
        }
    }

    /** Outcome of a {@link #migrate} call. */
    public record MigratedResult(ObjectNode migratedSpec, int newVersion, boolean changed) {}
}
