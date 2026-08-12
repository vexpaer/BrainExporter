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

rootProject.name = "BrainExporter"

include(
    ":app",
    ":sdk-core",
    ":core-runtime",
    ":platform-ble-android",
    ":plugin-device-rtbci",
    ":plugin-algorithm-basic",
    ":plugin-ui-monitor",
)
