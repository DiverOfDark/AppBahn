plugins {
    java
    alias(libs.plugins.spring.boot) apply false
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.swagger.annotations)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.validation.api)
    implementation(libs.jakarta.annotation.api)
    // The tunnel DTOs reference shared.crd.ResourceCrd, which extends fabric8's CustomResource.
    implementation(libs.fabric8.kubernetes.client)
}
