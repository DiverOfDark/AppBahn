plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.git.properties)
}

extra["dockerImageName"] = "operator"

gitProperties {
    dotGitDirectory.set(rootProject.layout.projectDirectory.dir("../.git"))
    // Matches platform/app: worktrees ship `.git` as a file, so don't hard-fail
    // when the git-properties plugin can't resolve a repo.
    failOnNoGitDirectory = false
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":tunnel-api"))

    implementation(libs.spring.boot.starter.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)
    implementation(libs.josdk.spring.boot.starter)
    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.okhttp)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.java.util)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotation.api)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.fabric8.kube.api.test)
    testImplementation(libs.fabric8.crd.generator.api)
}
