pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Secondary Maven Central endpoint for CI environments where the primary endpoint
        // can return incomplete/failed metadata responses. Keep the official repositories as
        // fallbacks so this does not change dependency versions or app behavior.
        maven {
            name = "MavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven {
            name = "MavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        // Psiphon tunnel-core Android AAR distribution (used by the isolated VXV integration).
        maven { url = uri("https://raw.github.com/Psiphon-Labs/psiphon-tunnel-core-Android-library/master") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "v2rayNG"
include(":app")
