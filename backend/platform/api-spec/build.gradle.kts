plugins {
    java
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
}

// API specs live in the top-level /api directory
val specFile = rootProject.layout.projectDirectory.file("../api/public-api.yaml")
val generatedDir = layout.buildDirectory.dir("generated/spring")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(specFile.asFile.absolutePath)
    outputDir.set(generatedDir.map { it.asFile.absolutePath })
    apiPackage.set("eu.appbahn.platform.api")
    modelPackage.set("eu.appbahn.platform.api.model")
    invokerPackage.set("eu.appbahn.platform.api")
    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot3" to "true",
        "useTags" to "true",
        "skipDefaultInterface" to "false",
        "openApiNullable" to "true",
        "additionalModelTypeAnnotations" to "",
        "documentationProvider" to "none",
        "serializationLibrary" to "jackson",
        "dateLibrary" to "java8",
        "generateBuilders" to "true",
    ))
    // Reuse shared CRD types instead of generating duplicates
    schemaMappings.set(mapOf(
        "ResourceConfig" to "eu.appbahn.shared.crd.ResourceConfig",
        "HostingConfig" to "eu.appbahn.shared.crd.ResourceConfig.Hosting",
        "NetworkingConfig" to "eu.appbahn.shared.crd.ResourceConfig.Networking",
        "PortConfig" to "eu.appbahn.shared.crd.ResourceConfig.PortConfig",
        "HealthCheckConfig" to "eu.appbahn.shared.crd.ResourceConfig.HealthCheck",
        "ProbeConfig" to "eu.appbahn.shared.crd.ResourceConfig.Probe",
        "HttpGetAction" to "eu.appbahn.shared.crd.ResourceConfig.HttpGetAction",
        "TcpSocketAction" to "eu.appbahn.shared.crd.ResourceConfig.TcpSocketAction",
        "ExecAction" to "eu.appbahn.shared.crd.ResourceConfig.ExecAction",
        "SourceConfig" to "eu.appbahn.shared.crd.Source",
        "DockerSource" to "eu.appbahn.shared.crd.DockerSource",
        "GitSource" to "eu.appbahn.shared.crd.GitSource",
        "PromotionSource" to "eu.appbahn.shared.crd.PromotionSource",
        "SourceAuth" to "eu.appbahn.shared.crd.SourceAuth",
        "ResourceStatusDetail" to "eu.appbahn.shared.crd.ResourceStatus",
        "ReplicaStatus" to "eu.appbahn.shared.crd.ResourceStatus.ReplicaStatus",
        "CustomDomainStatus" to "eu.appbahn.shared.crd.ResourceStatus.CustomDomainStatus",
        "ResourceCondition" to "eu.appbahn.shared.crd.ResourceStatus.ResourceCondition",
        "LinkStatus" to "eu.appbahn.shared.crd.ResourceStatus.LinkStatus",
        "LinkConfig" to "eu.appbahn.shared.crd.ResourceSpec.ResourceLink",
    ))
}

// Internal API (operator ↔ platform)
val internalSpecFile = rootProject.layout.projectDirectory.file("../api/internal-api.yaml")
val internalGeneratedDir = layout.buildDirectory.dir("generated/spring-internal")

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerateInternal") {
    generatorName.set("spring")
    inputSpec.set(internalSpecFile.asFile.absolutePath)
    outputDir.set(internalGeneratedDir.map { it.asFile.absolutePath })
    apiPackage.set("eu.appbahn.platform.api.internal")
    modelPackage.set("eu.appbahn.platform.api.internal.model")
    invokerPackage.set("eu.appbahn.platform.api.internal")
    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot3" to "true",
        "useTags" to "true",
        "skipDefaultInterface" to "false",
        "openApiNullable" to "true",
        "documentationProvider" to "none",
        "serializationLibrary" to "jackson",
        "dateLibrary" to "java8",
    ))
    schemaMappings.set(mapOf(
        "ResourceConfig" to "eu.appbahn.shared.crd.ResourceConfig",
        "ResourceStatusDetail" to "eu.appbahn.shared.crd.ResourceStatus",
        "LinkConfig" to "eu.appbahn.shared.crd.ResourceSpec.ResourceLink",
    ))
}

sourceSets {
    main {
        java {
            srcDir(generatedDir.map { it.dir("src/main/java") })
            srcDir(internalGeneratedDir.map { it.dir("src/main/java") })
        }
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate", "openApiGenerateInternal")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.swagger.annotations)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.validation.api)
    implementation(libs.jakarta.annotation.api)
}
