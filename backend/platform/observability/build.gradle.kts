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

    implementation(libs.spring.boot.starter.web)

    testImplementation(libs.spring.boot.starter.test)
}
