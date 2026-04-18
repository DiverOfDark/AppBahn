rootProject.name = "appbahn"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS lets the node-gradle plugin register its own project-level Node.js repo
    // (unavoidable — it's hard-wired in NodePlugin when download=true) without failing the build.
    // Gradle tries the settings-level Node ivy repo declared below first, so our strict layout
    // (artifact-only metadata, tight includeModule filter) is what actually resolves org.nodejs:node.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        // node-gradle plugin downloads its Node distribution as `org.nodejs:node` from
        // https://nodejs.org/dist/. Declare it here (with a tight includeModule filter) so the
        // plugin's project-level repo-add — which FAIL_ON_PROJECT_REPOS would otherwise reject —
        // is unnecessary. Keeps the strict project-repo policy intact for everything else.
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
    }
}

// --- Subprojects ---
include("shared")

include("platform:api-spec")
include("platform:common")
include("platform:workspace")
include("platform:resource")
include("platform:git")
include("platform:observability")
include("platform:user")
include("platform:app")

include("operator")

// backend/e2e/ is stripped from the public mirror by the CI filter-repo step. Guard the include
// so the filtered public repo's Gradle build still configures — otherwise settings.gradle.kts
// fails before any task runs.
if (file("e2e/build.gradle.kts").exists()) {
    include("e2e")
}
