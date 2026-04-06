plugins {
    java
}

val crdOutputDir = rootProject.layout.projectDirectory.dir("../helm/appbahn/templates/crds")

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
    doLast {
        val generated = layout.buildDirectory.dir("classes/java/main/META-INF/fabric8").get().asFile
        val committed = crdOutputDir.asFile
        generated.listFiles()?.filter { it.name.endsWith("-v1.yml") }?.forEach { gen ->
            val crdName = gen.name.replace(Regex("(.+)\\.appbahn\\.eu-v1\\.yml"), "$1-crd.yaml")
            val committed_file = committed.resolve(crdName)
            if (!committed_file.exists()) {
                error("CRD not found in Helm chart: ${crdName}. Run './gradlew :shared:copyCrds' to update.")
            }
            if (gen.readText() != committed_file.readText()) {
                error("CRD out of sync: ${crdName}. Run './gradlew :shared:copyCrds' to update.")
            }
        }
        println("All CRDs in sync with Helm chart.")
    }
}

dependencies {
    implementation(libs.fabric8.kubernetes.client)
    annotationProcessor(libs.fabric8.crd.generator.apt)

    implementation(libs.uuid.creator)
    implementation(libs.jackson.databind.nullable)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
