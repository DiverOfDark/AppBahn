package eu.appbahn.operator.migrations;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One forward step in the schema-migration chain for a single CRD kind.
 *
 * <p>Implementations are Spring {@code @Component} beans, collected by {@link MigrationRunner}
 * and grouped by {@link #kind()}. Each migration declares the version it produces — applying
 * the migration to a {@code spec} known to be at version {@code v - 1} leaves it at version
 * {@code v}. Versions within a kind must be a contiguous chain starting at 1; two migrations
 * sharing a {@code (kind, version)} pair is a fail-fast startup error.
 *
 * <h2>Why raw {@link ObjectNode}?</h2>
 *
 * The platform-wide rule "no {@code JsonNode}/{@code Object} in models" governs domain types:
 * CRD specs, DTOs, entity columns. Migration code is the deliberate exception — and the only
 * one. Shape-changing migrations (renames, splits, type narrowings) describe a transition
 * <em>between</em> typed shapes; by definition the input cannot deserialize into the new
 * typed model and the output cannot deserialize into the old. Holding the spec as a typed POJO
 * during migration would force the chain to depend on every historical revision of the
 * model, which defeats the purpose. The exemption stays inside this package; everywhere else
 * the typed-model rule applies.
 */
public interface CrMigration {

    /** CRD kind this migration applies to, e.g. {@code "Resource"}, {@code "ImageSource"}. */
    String kind();

    /**
     * Schema version this migration produces. A migration with {@code version() == v} consumes
     * a spec shaped at {@code v - 1} and leaves it shaped at {@code v}. Versions are 1-based
     * and must form a contiguous chain within a kind.
     */
    int version();

    /** Short human-readable summary, surfaced in startup logs. */
    String description();

    /**
     * Mutate {@code spec} in place from version {@code version() - 1} to version {@code
     * version()}. The argument is the raw {@code spec} object (not the whole CR — the runner
     * extracts it). Implementations must not assume any particular field shape beyond what the
     * previous version produced; defensive null checks belong here, not in the runner.
     */
    void apply(ObjectNode spec);
}
