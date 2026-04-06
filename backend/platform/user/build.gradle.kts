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

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)

    testImplementation(libs.spring.boot.starter.test)
}
