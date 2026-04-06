rootProject.name = "appbahn"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
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
