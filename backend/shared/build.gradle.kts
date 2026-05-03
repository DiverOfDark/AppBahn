plugins {
    java
    alias(libs.plugins.spring.boot) apply false
}

val crdOutputDir = layout.projectDirectory.dir("../../helm/appbahn/templates/crds")

tasks.register<Copy>("copyCrds") {
    dependsOn("compileJava")
    from(layout.buildDirectory.dir("classes/java/main/META-INF/fabric8")) {
        include("*-v1.yml")
        rename("(.+)\\.appbahn\\.eu-v1\\.yml", "$1-crd.yaml")
    }
    into(crdOutputDir)
}

val schemaSnapshotsDir = layout.projectDirectory.dir("src/test/resources/schema-snapshots")
val serializationFixturesDir = layout.projectDirectory.dir("src/test/resources/serialization-fixtures")

/**
 * Rewrites every committed schema snapshot under
 * {@code backend/shared/src/test/resources/schema-snapshots/}. Use after intentional widening
 * changes — narrowings should ship with a CR migration first, then a snapshot regen.
 */
tasks.register<JavaExec>("updateSchemaSnapshots") {
    group = "verification"
    description = "Regenerate schema snapshots for shared-wire types after intentional widening changes."
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("eu.appbahn.shared.schema.SchemaSnapshotUpdater")
    args(schemaSnapshotsDir.asFile.absolutePath)
}

/**
 * Generates populated round-trip fixtures under
 * {@code backend/shared/src/test/resources/serialization-fixtures/}. For every shared-wire type,
 * Instancio creates a populated instance (deterministic per-type seed) which is then serialized
 * via the production {@link com.fasterxml.jackson.databind.ObjectMapper}. Existing fixtures are
 * overwritten — re-runs with the same SHA reproduce the same JSON byte-for-byte.
 *
 * <p>Per-type generation is fail-soft: if Instancio can't produce a sensible instance for a given
 * type (abstract type, missing default constructor in a transitive fabric8 internal, etc.) the
 * seeder falls back to writing {@code {}} for that type.
 *
 * <p>Args: {@code -PfixtureSha=<short-sha>} (defaults to "HEAD" placeholder when absent — the
 * task runs without git in scope).
 */
tasks.register<JavaExec>("seedSerializationFixtures") {
    group = "verification"
    description = "Generate populated round-trip fixtures tagged with the supplied short SHA."
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("eu.appbahn.shared.schema.FixtureSeeder")
    val shaProvider = providers.gradleProperty("fixtureSha").orElse("HEAD")
    args(serializationFixturesDir.asFile.absolutePath, shaProvider.get())
}

tasks.register("verifyCrds") {
    dependsOn("compileJava")
    val generatedDir = layout.buildDirectory.dir("classes/java/main/META-INF/fabric8")
    val committedDir = crdOutputDir
    doLast {
        val generated = generatedDir.get().asFile
        val committed = committedDir.asFile
        generated.listFiles()?.filter { it.name.endsWith("-v1.yml") }?.forEach { gen ->
            val crdName = gen.name.replace(Regex("(.+)\\.appbahn\\.eu-v1\\.yml"), "$1-crd.yaml")
            val committedFile = committed.resolve(crdName)
            if (!committedFile.exists()) {
                error("CRD not found in Helm chart: ${crdName}. Run './gradlew :shared:copyCrds' to update.")
            }
            if (gen.readText() != committedFile.readText()) {
                error("CRD out of sync: ${crdName}. Run './gradlew :shared:copyCrds' to update.")
            }
        }
        println("All CRDs in sync with Helm chart.")
    }
}

dependencies {
    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.fabric8.generator.annotations)
    annotationProcessor(libs.fabric8.crd.generator.apt)

    implementation(libs.uuid.creator)
    implementation(libs.jackson.databind.nullable)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.swagger.annotations)

    implementation(libs.jackson.datatype.jsr310)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.instancio.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
