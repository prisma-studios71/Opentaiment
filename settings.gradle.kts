pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Opentaiment"

include(
    ":app-headunit",
    ":app-phone",
    ":car",
    ":core:ui",
    ":feature:media",
    ":feature:weather",
    ":feature:apps",
    ":feature:vehicle",
)
