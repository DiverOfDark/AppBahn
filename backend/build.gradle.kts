plugins {
    java
    `jacoco-report-aggregation`
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    alias(libs.plugins.bmuschko.docker) apply false
}

allprojects {
    group = "eu.appbahn"
    version = "0.1.0-SNAPSHOT"
}

// `jacoco-report-aggregation` resolves each aggregated subproject's runtime classpath at the root,
// so the Spring BOM has to be in scope here too — otherwise BOM-managed deps (declared without
// versions in subprojects) fail with "Could not find ...:.". Mirrors the BOM import each
// subproject does in `subprojects {}`.
the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

// JaCoCo exclusions applied uniformly across the aggregate report and per-module reports.
// Spring Boot main classes, OpenAPI-generated clients, and the e2e helper module are not
// production code we measure coverage of. The e2e helpers themselves live in src/main but
// are test-infrastructure (cluster lifecycle, Playwright, JUnit extensions) — covering them
// would be measuring coverage of test code, not coverage of production code by tests.
val jacocoExcludes = listOf(
    "**/generated/**",
    // OpenAPI-generated tunnel client compiled into the operator's regular classes dir
    // (the generator emits into build/generated/openapi-tunnel/src/main/java but its package
    // isn't `generated`, so the compiled output sits next to hand-written classes).
    "eu/appbahn/operator/tunnel/client/**",
    "eu/appbahn/e2e/client/**",
    "**/AppBahnApplication.class",
    "**/OperatorApplication.class",
)

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    configure<JacocoPluginExtension> {
        toolVersion = rootProject.libs.versions.jacoco.get()
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        classDirectories.setFrom(
            classDirectories.files.map {
                fileTree(it) { exclude(jacocoExcludes) }
            }
        )
    }

    tasks.withType<Test>().configureEach {
        finalizedBy(tasks.withType<JacocoReport>())
    }

    spotless {
        java {
            target("src/**/*.java")
            palantirJavaFormat()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        "compileOnly"(rootProject.libs.lombok)
        "annotationProcessor"(rootProject.libs.lombok)
    }


    pluginManager.withPlugin("com.bmuschko.docker-remote-api") {
        val registryProp = project.providers.gradleProperty("dockerRegistry")
        val tagProp = project.providers.gradleProperty("dockerTag")
        val extraTagsProp = project.providers.gradleProperty("dockerExtraTags")
        val imgName = project.providers.provider { project.extra["dockerImageName"] as String }

        val baseImage = registryProp.zip(imgName) { reg, name ->
            "${reg.lowercase()}/appbahn/${name}"
        }
        // Default image set: <registry>/appbahn/<name>:<tag> + one entry per
        // -PdockerExtraTags csv. e2e overrides `images` to a single fixed e2e
        // tag, so this provider chain is only resolved on release builds.
        val primaryImage = baseImage.zip(tagProp) { base, tag -> "${base}:${tag}" }
        val extraImages = baseImage.zip(extraTagsProp.orElse("")) { base, csv ->
            if (csv.isBlank()) emptyList()
            else csv.split(",").map { "${base}:${it.trim()}" }
        }
        val defaultImages = primaryImage.zip(extraImages) { primary, extras -> listOf(primary) + extras }

        val bootJar = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar")
        val dockerSrc = layout.projectDirectory.dir("src/main/docker")
        val dockerStageDir = layout.buildDirectory.dir("docker")

        val stageDockerContext = tasks.register<Sync>("stageDockerContext") {
            description = "Stage Dockerfile + bootJar into build/docker/ as the docker build context."
            group = "docker"
            from(dockerSrc)
            from(bootJar.flatMap { it.archiveFile }) {
                rename { "application.jar" }
            }
            into(dockerStageDir)
        }

        tasks.register<com.bmuschko.gradle.docker.tasks.image.DockerBuildImage>("dockerBuildImage") {
            description = "Build the OCI image (multi-stage Dockerfile, AOT cache training run)."
            group = "docker"
            inputDir.set(dockerStageDir)
            images.set(defaultImages)
            dependsOn(stageDockerContext)
        }

        tasks.register<com.bmuschko.gradle.docker.tasks.image.DockerPushImage>("dockerPushImage") {
            description = "Push the OCI image (and any extra tags) to the configured registry."
            group = "docker"
            images.set(defaultImages)
            registryCredentials {
                url.set(registryProp.map { "https://${it.substringBefore("/").lowercase()}" }.orElse(""))
                username.set(project.providers.gradleProperty("dockerUsername").orElse(""))
                password.set(project.providers.gradleProperty("dockerPassword").orElse(""))
            }
            dependsOn(tasks.named("dockerBuildImage"))
            onlyIf("requires -PpublishImage") { project.hasProperty("publishImage") }
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${rootProject.libs.versions.spring.boot.get()}")
        }
    }
}

// Aggregate JaCoCo report consumed by Codecov (flag=java), produced via Gradle's first-party
// `jacoco-report-aggregation` plugin. :e2e is omitted — its `test` task is disabled (the suite
// runs through `e2eTest` against a real K3s cluster) and its `src/main` holds test infrastructure,
// not production code. The :e2e module produces its own JaCoCo report (flag=java-e2e) via the
// `e2eCodeCoverageReport` task, instrumenting the platform/operator JVMs running inside K3s and
// uploading the resulting XML to Codecov as a separate flag — Codecov takes the union per file.
dependencies {
    jacocoAggregation(project(":shared"))
    jacocoAggregation(project(":platform:api-spec"))
    jacocoAggregation(project(":platform:common"))
    jacocoAggregation(project(":platform:resource"))
    jacocoAggregation(project(":platform:tunnel"))
    jacocoAggregation(project(":platform:user"))
    jacocoAggregation(project(":platform:workspace"))
    jacocoAggregation(project(":platform:app"))
    jacocoAggregation(project(":operator"))
}

// The aggregation plugin pulls each module's raw class output via its outgoing variant, so the
// per-module `JacocoReport` exclusions configured in `subprojects {}` don't carry over. Re-apply
// `jacocoExcludes` here so the aggregate XML/HTML matches the per-module reports.
tasks.named<JacocoReport>("testCodeCoverageReport") {
    classDirectories.setFrom(
        classDirectories.files.map {
            fileTree(it) { exclude(jacocoExcludes) }
        }
    )
}
