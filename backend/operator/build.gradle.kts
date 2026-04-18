plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.git.properties)
}

extra["dockerImageName"] = "operator"

gitProperties {
    dotGitDirectory.set(rootProject.layout.projectDirectory.dir("../.git"))
    // Matches platform/app: worktrees ship `.git` as a file, so don't hard-fail
    // when the git-properties plugin can't resolve a repo.
    failOnNoGitDirectory = false
}

val internalSpecFile = rootProject.layout.projectDirectory.file("../api/internal-api.yaml")
val generatedDir = layout.buildDirectory.dir("generated/internal-client")

openApiGenerate {
    generatorName.set("java")
    inputSpec.set(internalSpecFile.asFile.absolutePath)
    outputDir.set(generatedDir.map { it.asFile.absolutePath })
    apiPackage.set("eu.appbahn.operator.client.api")
    modelPackage.set("eu.appbahn.operator.client.model")
    invokerPackage.set("eu.appbahn.operator.client")
    configOptions.set(mapOf(
        "library" to "native",
        "dateLibrary" to "java8",
        "serializationLibrary" to "jackson",
        "openApiNullable" to "false",
        "useJakartaEe" to "true",
    ))
    schemaMappings.set(mapOf(
        "LinkConfig" to "eu.appbahn.shared.crd.ResourceSpec.ResourceLink",
        "ResourceConfig" to "eu.appbahn.shared.crd.ResourceConfig",
        "ResourceStatusDetail" to "eu.appbahn.shared.crd.ResourceStatus",
    ))
}

sourceSets {
    main {
        java {
            srcDir(generatedDir.map { it.dir("src/main/java") })
        }
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.spring.boot.starter.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.josdk.spring.boot.starter)
    implementation(libs.fabric8.kubernetes.client)

    // Generated client dependencies
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotation.api)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.fabric8.kube.api.test)
    testImplementation(libs.fabric8.crd.generator.api)
}
