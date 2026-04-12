plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    group = "eu.appbahn"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

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


    pluginManager.withPlugin("org.springframework.boot") {
        tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootBuildImage> {
            val registryProp = project.providers.gradleProperty("dockerRegistry")
            val tagProp = project.providers.gradleProperty("dockerTag")
            val extraTagsProp = project.providers.gradleProperty("dockerExtraTags")
            val imgName = project.providers.provider { project.extra["dockerImageName"] as String }

            val baseImage = registryProp.zip(imgName) { reg, name ->
                "${reg.lowercase()}/appbahn/${name}"
            }
            imageName.set(baseImage.zip(tagProp) { base, tag -> "${base}:${tag}" })
            tags.set(extraTagsProp.map { csv ->
                csv.split(",").map { tag -> "${baseImage.get()}:${tag.trim()}" }
            }.orElse(listOf()))
            publish.set(project.hasProperty("publishImage"))
            docker {
                publishRegistry {
                    username.set(project.providers.gradleProperty("dockerUsername").orElse(""))
                    password.set(project.providers.gradleProperty("dockerPassword").orElse(""))
                    url.set(registryProp.map { "https://${it.substringBefore("/").lowercase()}" }.orElse(""))
                }
            }
        }
    }

    pluginManager.withPlugin("io.spring.dependency-management") {
        the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
            imports {
                mavenBom("org.springframework.boot:spring-boot-dependencies:${rootProject.libs.versions.spring.boot.get()}")
            }
        }
    }
}
