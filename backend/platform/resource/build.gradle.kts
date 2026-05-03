plugins {
    java
    alias(libs.plugins.spring.boot) apply false
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":platform:common"))
    implementation(project(":platform:api-spec"))
    implementation(project(":platform:workspace"))
    implementation(project(":shared"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.retry)
    implementation(libs.fabric8.kubernetes.client)
    // springdoc's @Hidden keeps the internal admin endpoint out of the public OpenAPI surface.
    // Runtime springdoc comes from :platform:app; resource only needs the annotation at compile.
    compileOnly(libs.springdoc.openapi.starter)

    // Ed25519 signed-license loading. nimbus-jose-jwt arrives transitively via Spring Security
    // OAuth2; declared explicitly to keep the loader's compileClasspath honest. Tink supplies
    // the EdDSA primitives Nimbus's Ed25519Signer/Verifier delegate to.
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.tink)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// One-shot keypair generator. Refuses to overwrite existing files — the committed keypair under
// spec/license-keys/ + the public key under src/main/resources/license/ are the production
// keypair; regenerating either invalidates every license already issued.
tasks.register<JavaExec>("generateLicenseKeys") {
    group = "license"
    description =
        "Generate the Ed25519 license keypair. Refuses to overwrite existing private/public keys."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("eu.appbahn.platform.resource.license.tool.GenerateLicenseKeys")
    val privateKey =
        project.providers.gradleProperty("privateKey")
            .orElse(rootProject.layout.projectDirectory.file("../spec/license-keys/private-key.pem").asFile.absolutePath)
    val publicKey =
        project.providers.gradleProperty("publicKey")
            .orElse(layout.projectDirectory.file("src/main/resources/license/public-key.pem").asFile.absolutePath)
    args = listOf("--private-key", privateKey.get(), "--public-key", publicKey.get())
}

// Sign a license file with the committed private key. Run by ops when issuing a customer licence.
//
//   ./gradlew :platform:resource:signLicense \
//     -PcustomerId="acme-corp" -PmaxResources=100 -PvalidDays=365 \
//     -PoutputFile=/tmp/acme-license.jws
tasks.register<JavaExec>("signLicense") {
    group = "license"
    description = "Sign a customer license. Inputs via -P properties; emits a compact JWS."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("eu.appbahn.platform.resource.license.tool.SignLicense")
    val customerId = project.providers.gradleProperty("customerId")
    val maxResources = project.providers.gradleProperty("maxResources")
    val validDays = project.providers.gradleProperty("validDays")
    val outputFile = project.providers.gradleProperty("outputFile")
    val privateKey =
        project.providers.gradleProperty("privateKey")
            .orElse(rootProject.layout.projectDirectory.file("../spec/license-keys/private-key.pem").asFile.absolutePath)
    val publicKey =
        project.providers.gradleProperty("publicKey")
            .orElse(layout.projectDirectory.file("src/main/resources/license/public-key.pem").asFile.absolutePath)
    doFirst {
        listOf(
            "customerId" to customerId,
            "maxResources" to maxResources,
            "validDays" to validDays,
            "outputFile" to outputFile,
        ).forEach { (name, prop) ->
            if (!prop.isPresent) {
                error("Missing required Gradle property: -P$name=...")
            }
        }
    }
    args = listOf(
        "--private-key", privateKey.get(),
        "--public-key", publicKey.get(),
        "--customer-id", customerId.orElse("").get(),
        "--max-resources", maxResources.orElse("").get(),
        "--valid-days", validDays.orElse("").get(),
        "--output-file", outputFile.orElse("").get(),
    )
}
