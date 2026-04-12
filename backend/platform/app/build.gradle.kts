plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.git.properties)
}

extra["dockerImageName"] = "platform"

gitProperties {
    dotGitDirectory.set(rootProject.layout.projectDirectory.dir("../.git"))
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":platform:api-spec"))
    implementation(project(":platform:common"))
    implementation(project(":platform:workspace"))
    implementation(project(":platform:resource"))
    implementation(project(":platform:git"))
    implementation(project(":platform:observability"))
    implementation(project(":platform:user"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.starter)
    implementation(libs.scalar.spring)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.fabric8.kubernetes.client)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.oauth2.resource.server)
    testImplementation(libs.spring.boot.starter.oauth2.client)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.classgraph)
    testRuntimeOnly(libs.junit.platform.launcher)
}
