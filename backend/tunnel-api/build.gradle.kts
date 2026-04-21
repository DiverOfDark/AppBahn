plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.protobuf)
    // `build.buf` plugin (for bufLint / bufBreaking) deferred: v0.11 applies STANDARD lint
    // rules that conflict with sprint-spec names (`OperatorTunnel`, `HeartbeatAck`,
    // `tunnel-api.proto`), and its v2 config except-list wasn't honoured by the bundled
    // CLI. Config files (buf.yaml, buf.gen.yaml) are checked in so future reintegration
    // is a one-line plugins-block change.
}

// The canonical .proto lives in the top-level api/ dir next to the OpenAPI specs.
// Register it as a proto srcDir so `generateProto` picks it up.
sourceSets {
    main {
        proto {
            srcDir(rootProject.layout.projectDirectory.dir("../api"))
        }
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    // Default Java codegen is enabled automatically — no generateProtoTasks block needed.
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.java.util)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.okhttp)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.awaitility)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Generated protobuf sources shouldn't be linted.
spotless {
    java {
        targetExclude("build/generated/**")
    }
}
