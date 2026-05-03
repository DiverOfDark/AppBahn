package eu.appbahn.shared.crd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CRD root type with the kind name + current schema version of its {@code spec} shape.
 * Bumped exactly when a narrowing change ships and a matching {@code CrMigration} (kind +
 * version) is added on the operator side.
 *
 * <p>Two protective layers consume this annotation:
 * <ul>
 *   <li>The schema-snapshot diff (under {@code backend/shared/src/test/java/.../schema/}) refuses
 *       to accept a narrowing change unless the {@code version} on the enclosing CRD root has
 *       been bumped relative to the committed snapshot.
 *   <li>{@code MigrationVersionContractTest} on the operator side asserts that the runtime
 *       migration chain advertises a {@code currentVersion(kind)} consistent with the annotation
 *       on every CRD root.
 * </ul>
 *
 * <h2>Version semantics</h2>
 *
 * Version 1 is the bootstrap state — the schema as originally shipped, no migrations needed.
 * {@code MigrationRunner.currentVersion(kind)} returns 0 for a kind with no registered
 * migrations, and the contract test treats {@code @CrdSchemaVersion(version = 1)} as compatible
 * with that. The first narrowing bumps the annotation to {@code version = 2} and ships a
 * {@code CrMigration} declaring {@code version() == 2} that mutates the old shape into the new.
 * Beyond v1 the annotation and {@code currentVersion(kind)} must agree exactly.
 *
 * <p>Apply only to CRD root types ({@code ResourceCrd}, {@code ImageSourceCrd},
 * {@code ResourceTypeDefinitionCrd}). The schema layer propagates the annotation to every type
 * reachable from the root by field reference, so individual nested types do not annotate
 * themselves. Types that cross the wire but are not CRDs (the operator-side sync payloads) carry
 * no annotation — they have no migration mechanism, the sync flow re-emits on every interval.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CrdSchemaVersion {

    /** CRD kind, must match {@code CrMigration.kind()} on the operator side. */
    String kind();

    /** Current schema version of this kind. Bumped when a narrowing migration ships. */
    int version();
}
