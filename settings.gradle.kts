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
    }
}

rootProject.name = "modern-t9"

include(":app")
include(":engine-api")
include(":engine-trie")

// To add your own engine: create the module, implement InputEngine, include it here,
// and register it in app/src/main/kotlin/dev/t9/ime/Engines.kt
// include(":engine-touchpal")
