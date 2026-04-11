plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
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

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
