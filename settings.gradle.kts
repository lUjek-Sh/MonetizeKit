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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MonetizeKit"
include(":app")
include(":ads-core")
include(":ads-admob")
include(":ads-meta")
include(":ads-unity")
include(":ads-admob-meta-mediation")
include(":ads-admob-unity-mediation")
include(":ads-compose")
include(":billing-core")
include(":billing-google-play")
include(":billing-ads")
