plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.git.properties)
    alias(libs.plugins.openapi.generator)
}

extra["dockerImageName"] = "operator"

gitProperties {
    dotGitDirectory.set(rootProject.layout.projectDirectory.dir("../.git"))
    // Matches platform/app: worktrees ship `.git` as a file, so don't hard-fail
    // when the git-properties plugin can't resolve a repo.
    failOnNoGitDirectory = false
}

// ---------------------------------------------------------------------------
// Generated tunnel client
// ---------------------------------------------------------------------------
// Produces a typed Java client for `api/tunnel-api.yaml` using openapi-generator's
// `native` library (JDK `java.net.http` + Jackson — matches the operator's existing
// ObjectMapper). Paths, query parameters, polymorphic body shapes all come straight from
// the springdoc-emitted spec, so there's nothing to keep in sync by hand on this side.
val tunnelSpec = rootProject.layout.projectDirectory.file("../api/tunnel-api.yaml")
val generatedTunnelClientDir = layout.buildDirectory.dir("generated/openapi-tunnel")

// Schemas in tunnel-api.yaml that already have hand-written equivalents in :shared (the
// fabric8-CRD type tree) or in fabric8's stock Kubernetes API model. We tell openapi-generator
// to skip emitting Java classes for them and reuse the existing types instead — that's how the
// operator's reconciler can pass an `ApplyResource.getResource()` straight to the K8s client
// without any conversion at the boundary.
val sharedCrdSchemaMappings = mapOf(
    // CRD top-level
    "ResourceCrd" to "eu.appbahn.shared.crd.ResourceCrd",
    "ResourceSpec" to "eu.appbahn.shared.crd.ResourceSpec",
    "ResourceConfig" to "eu.appbahn.shared.crd.ResourceConfig",
    "ResourceConfigSource" to "eu.appbahn.shared.crd.Source",
    "ResourceStatusDetail" to "eu.appbahn.shared.crd.ResourceStatusDetail",
    // Nested classes inside ResourceConfig / ResourceSpec / ResourceStatusDetail
    "HostingConfig" to "eu.appbahn.shared.crd.ResourceConfig.HostingConfig",
    "NetworkingConfig" to "eu.appbahn.shared.crd.ResourceConfig.NetworkingConfig",
    "HealthCheckConfig" to "eu.appbahn.shared.crd.ResourceConfig.HealthCheckConfig",
    "ProbeConfig" to "eu.appbahn.shared.crd.ResourceConfig.ProbeConfig",
    "PortConfig" to "eu.appbahn.shared.crd.ResourceConfig.PortConfig",
    "HttpGetAction" to "eu.appbahn.shared.crd.ResourceConfig.HttpGetAction",
    "TcpSocketAction" to "eu.appbahn.shared.crd.ResourceConfig.TcpSocketAction",
    "ExecAction" to "eu.appbahn.shared.crd.ResourceConfig.ExecAction",
    "LinkConfig" to "eu.appbahn.shared.crd.ResourceSpec.LinkConfig",
    "LinkStatus" to "eu.appbahn.shared.crd.ResourceStatusDetail.LinkStatus",
    "ReplicaStatus" to "eu.appbahn.shared.crd.ResourceStatusDetail.ReplicaStatus",
    "ResourceCondition" to "eu.appbahn.shared.crd.ResourceStatusDetail.ResourceCondition",
    "CustomDomainStatus" to "eu.appbahn.shared.crd.ResourceStatusDetail.CustomDomainStatus",
    // Source polymorphism
    "SourceConfig" to "eu.appbahn.shared.crd.Source",
    "DockerSource" to "eu.appbahn.shared.crd.DockerSource",
    "GitSource" to "eu.appbahn.shared.crd.GitSource",
    "PromotionSource" to "eu.appbahn.shared.crd.PromotionSource",
    "SourceAuth" to "eu.appbahn.shared.crd.SourceAuth",
    // BuildConfig polymorphism
    "BuildConfig" to "eu.appbahn.shared.crd.BuildConfig",
    "BuildpackBuildConfig" to "eu.appbahn.shared.crd.BuildpackBuildConfig",
    "DockerfileBuildConfig" to "eu.appbahn.shared.crd.DockerfileBuildConfig",
    "PeelboxBuildConfig" to "eu.appbahn.shared.crd.PeelboxBuildConfig",
    "RailpackBuildConfig" to "eu.appbahn.shared.crd.RailpackBuildConfig",
    // ImageSource CRD top-level + nested
    "ImageSourceCrd" to "eu.appbahn.shared.crd.imagesource.ImageSourceCrd",
    "ImageSourceSpec" to "eu.appbahn.shared.crd.imagesource.ImageSourceSpec",
    "ImageSourceStatus" to "eu.appbahn.shared.crd.imagesource.ImageSourceStatus",
    "ImageSourceGitSpec" to "eu.appbahn.shared.crd.imagesource.ImageSourceGitSpec",
    "ImageSpec" to "eu.appbahn.shared.crd.imagesource.ImageSpec",
    "ImageSourceBuildSpec" to "eu.appbahn.shared.crd.imagesource.ImageSourceBuildSpec",
    "DockerfileBuildOptions" to "eu.appbahn.shared.crd.imagesource.DockerfileBuildOptions",
    "BuildpackBuildOptions" to "eu.appbahn.shared.crd.imagesource.BuildpackBuildOptions",
    "ImageSourceTrigger" to "eu.appbahn.shared.crd.imagesource.ImageSourceTrigger",
    "ImageSourcePoll" to "eu.appbahn.shared.crd.imagesource.ImageSourcePoll",
    "LatestArtifact" to "eu.appbahn.shared.crd.imagesource.LatestArtifact",
    "ImageSourceCondition" to "eu.appbahn.shared.crd.imagesource.ImageSourceCondition",
    // Fabric8 K8s stock types
    "ManagedFieldsEntry" to "io.fabric8.kubernetes.api.model.ManagedFieldsEntry",
    "ObjectMeta" to "io.fabric8.kubernetes.api.model.ObjectMeta",
    "OwnerReference" to "io.fabric8.kubernetes.api.model.OwnerReference",
    "FieldsV1" to "io.fabric8.kubernetes.api.model.FieldsV1",
)

// The `native` library generates a `toUrlQueryString(prefix)` method on every model, plus
// recursive calls into children. For models that we map to external types via schemaMappings
// (shared.crd.* + fabric8 ObjectMeta), the call site refers to a method the external type
// doesn't have — and the method is never invoked from anywhere except other models. Strip the
// method (declaration + every call site) once generation finishes.
val patchTunnelClient = tasks.register("patchTunnelClient") {
    description = "Strip toUrlQueryString from the generated tunnel client (unused)."
    val generatedDir = generatedTunnelClientDir.map { it.dir("src/main/java/eu/appbahn/operator/tunnel/client/model") }
    dependsOn("generateTunnelClient")
    inputs.dir(generatedDir)
    outputs.dir(generatedDir)
    doLast {
        val rootDir = generatedDir.get().asFile
        val files = rootDir.walk().filter { it.isFile && it.extension == "java" }.toList()
        val methodHeader = Regex("""(?m)^ {2}public String toUrlQueryString\(.*?\)""")
        files.forEach { file ->
            var text = file.readText()
            // Each model has TWO `toUrlQueryString` overloads (no-arg + one-arg). Loop until
            // we've stripped all of them.
            while (true) {
                val match = methodHeader.find(text) ?: break
                val openBrace = text.indexOf('{', match.range.last)
                if (openBrace < 0) break
                var depth = 1
                var i = openBrace + 1
                while (i < text.length && depth > 0) {
                    val c = text[i]
                    if (c == '{') depth++ else if (c == '}') depth--
                    i++
                }
                var end = i
                while (end < text.length && (text[end] == '\n' || text[end] == ' ')) end++
                text = text.removeRange(match.range.first, end)
            }
            file.writeText(text)
        }
    }
}

val generateTunnelClient = tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>(
    "generateTunnelClient",
) {
    description = "Generate Java client for api/tunnel-api.yaml (operator-side)"
    group = "build"
    generatorName.set("java")
    library.set("native")
    inputSpec.set(tunnelSpec.asFile.absolutePath)
    outputDir.set(generatedTunnelClientDir.map { it.asFile.absolutePath })
    apiPackage.set("eu.appbahn.operator.tunnel.client.api")
    modelPackage.set("eu.appbahn.operator.tunnel.client.model")
    invokerPackage.set("eu.appbahn.operator.tunnel.client")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    schemaMappings.set(sharedCrdSchemaMappings)
    importMappings.set(sharedCrdSchemaMappings)
    configOptions.set(mapOf(
        "openApiNullable" to "false",
        "hideGenerationTimestamp" to "true",
        "useBeanValidation" to "false",
        "performBeanValidation" to "false",
        "dateLibrary" to "java8",
        "useJakartaEe" to "true",
    ))
    inputs.file(tunnelSpec)
    outputs.dir(generatedTunnelClientDir)
    finalizedBy(patchTunnelClient)
}

sourceSets {
    main {
        java.srcDir(generatedTunnelClientDir.map { it.dir("src/main/java") })
    }
}

tasks.named("compileJava") { dependsOn(patchTunnelClient) }
tasks.matching { it.name.startsWith("spotless") }.configureEach { mustRunAfter(patchTunnelClient) }

dependencies {
    implementation(project(":shared"))

    implementation(libs.spring.boot.starter.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)
    implementation(libs.josdk.spring.boot.starter)
    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.okhttp)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotation.api)
    implementation(libs.jgit)
    implementation(libs.jgit.http)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.fabric8.kube.api.test)
    testImplementation(libs.fabric8.crd.generator.api)
}
