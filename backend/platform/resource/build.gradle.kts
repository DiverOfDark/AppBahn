plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
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
    implementation(libs.fabric8.kubernetes.client)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
