import com.github.gradle.node.npm.task.NpmTask

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.git.properties)
    alias(libs.plugins.node.gradle)
}

extra["dockerImageName"] = "platform"

gitProperties {
    dotGitDirectory.set(rootProject.layout.projectDirectory.dir("../.git"))
    // git-properties fails hard inside a worktree where `.git` is a file, not a
    // directory. Keep the task configured so local-dev and CI still populate
    // `git.properties`, but don't block the build if resolution fails.
    failOnNoGitDirectory = false
}

// ---------------------------------------------------------------------------
// Web frontend build → Spring Boot static resources
// ---------------------------------------------------------------------------
// Production (`release.yml`) downloads a `web-dist` artifact into
// `src/main/resources/static/` before running `bootBuildImage`. We reproduce
// that flow locally so `./gradlew :platform:app:bootJar` (or bootBuildImage)
// always bakes the latest web console into the jar, without post-hoc jar
// mutation. The synced directory is build output — `.gitignore`d.
val webDir = rootProject.layout.projectDirectory.dir("../web")
val webSrcDir = webDir.dir("src")
val webDistDir = webDir.dir("dist")
val staticResourcesDir = layout.projectDirectory.dir("src/main/resources/static")

// The node-gradle plugin wires `npm install` and `npm run build` as first-class Gradle tasks
// with proper up-to-date checks. Node is downloaded into build/nodejs via the ivy repo declared
// in settings.gradle.kts so every developer (and CI) builds against the same pinned toolchain —
// and the build works on machines that don't have `npm` on PATH at all.
node {
    version.set("22.22.0")
    download.set(true)
    nodeProjectDir.set(webDir)
}

val buildWebAssets = tasks.register<NpmTask>("buildWebAssets") {
    description = "Run `npm run build` in the web module to regenerate web/dist."
    group = "build"
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
    inputs.dir(webSrcDir).withPropertyName("webSrc").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(webDir.file("package.json"))
    inputs.file(webDir.file("package-lock.json"))
    inputs.file(webDir.file("vite.config.ts")).optional()
    inputs.file(webDir.file("index.html")).optional()
    outputs.dir(webDistDir).withPropertyName("webDist")
}

val syncWebAssets = tasks.register<Sync>("syncWebAssets") {
    description = "Sync web/dist into src/main/resources/static so Spring serves the web console."
    group = "build"
    dependsOn(buildWebAssets)
    from(webDistDir)
    into(staticResourcesDir)
    // Preserve the committed `.gitignore` that keeps the directory empty in git.
    preserve { include(".gitignore") }
}

tasks.named("processResources") {
    dependsOn(syncWebAssets)
}

// Spotless declares `src/**/*.java` as its target, so Gradle's task graph sees src/main/resources
// as an input of spotlessJava / spotlessJavaCheck (it walks the shared project tree). syncWebAssets
// writes into src/main/resources/static and therefore has to be ordered before spotless — CI
// otherwise fails with "implicit dependency" validation.
tasks.matching { it.name.startsWith("spotless") }.configureEach {
    mustRunAfter(syncWebAssets)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":platform:api-spec"))
    implementation(project(":platform:common"))
    implementation(project(":platform:workspace"))
    implementation(project(":platform:resource"))
    implementation(project(":platform:git"))
    implementation(project(":platform:observability"))
    implementation(project(":platform:user"))
    implementation(project(":platform:tunnel"))

    implementation(libs.spring.boot.starter.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.starter)
    implementation(libs.scalar.spring)

    runtimeOnly(libs.postgresql)

    testImplementation(project(":tunnel-api"))
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.java.util)
    testImplementation(libs.awaitility)
    testImplementation(libs.okhttp)
    testImplementation(libs.fabric8.kubernetes.client)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.oauth2.resource.server)
    testImplementation(libs.spring.boot.starter.oauth2.client)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.classgraph)
    testRuntimeOnly(libs.junit.platform.launcher)
}
