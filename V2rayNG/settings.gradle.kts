pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Psiphon tunnel-core Android AAR distribution (used by the isolated VXV integration).
        maven { url = uri("https://raw.github.com/Psiphon-Labs/psiphon-tunnel-core-Android-library/master") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "v2rayNG"
include(":app")
