plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

extra["dockerImageName"] = "operator"

dependencies {
    implementation(project(":shared"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.josdk.spring.boot.starter)
    implementation(libs.fabric8.kubernetes.client)

    testImplementation(libs.spring.boot.starter.test)
}
