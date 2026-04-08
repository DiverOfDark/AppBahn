plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
}

extra["dockerImageName"] = "operator"

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

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.josdk.spring.boot.starter)
    implementation(libs.fabric8.kubernetes.client)

    // Generated client dependencies
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotation.api)

    testImplementation(libs.spring.boot.starter.test)
}
