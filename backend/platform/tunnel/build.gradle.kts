plugins {
    java
    alias(libs.plugins.spring.boot) apply false
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":platform:common"))
    implementation(project(":platform:api-spec"))
    implementation(project(":platform:workspace"))
    implementation(project(":platform:resource"))
    implementation(project(":platform:user"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.uuid.creator)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.awaitility)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.postgresql)
}
