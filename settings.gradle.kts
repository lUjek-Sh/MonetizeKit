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

rootProject.name = "MonetizeKit"
include(":app")
include(":consumer-r8-smoke")
include(":ads-core")
include(":ads-admob")
include(":ads-meta")
include(":ads-unity-sdk")
include(":ads-unity")
include(":ads-admob-meta-mediation")
include(":ads-admob-unity-mediation")
include(":ads-compose")
include(":billing-core")
include(":billing-google-play")
include(":billing-ads")
