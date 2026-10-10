import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}

val isJitPackBuild = providers.environmentVariable("JITPACK").isPresent

group = if (isJitPackBuild) {
    "com.github.lUjek-Sh.MonetizeKit"
} else {
    "ua.lujek.monetizekit"
}
version = if (isJitPackBuild) {
    providers.environmentVariable("VERSION").orNull
        ?: providers.gradleProperty("monetizekit.version").get()
} else {
    providers.gradleProperty("monetizekit.version").get()
}

val publishedLibraryModules = setOf(
    "ads-core",
    "ads-admob",
    "ads-meta",
    "ads-unity",
    "ads-admob-meta-mediation",
    "ads-admob-unity-mediation",
    "ads-compose",
    "billing-core",
    "billing-google-play",
    "billing-ads",
)

subprojects {
    if (name !in publishedLibraryModules) return@subprojects

    group = rootProject.group
    version = rootProject.version

    pluginManager.withPlugin("com.android.library") {
        pluginManager.apply("maven-publish")

        extensions.configure<LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    register<MavenPublication>("release") {
                        from(components["release"])
                        groupId = project.group.toString()
                        artifactId = project.name
                        version = project.version.toString()

                        pom {
                            name.set("MonetizeKit ${project.name}")
                            description.set(
                                "Modular Android ads, privacy, and billing toolkit for MonetizeKit.",
                            )
                            url.set("https://github.com/lUjek-Sh/MonetizeKit")
                            licenses {
                                license {
                                    name.set("Apache License, Version 2.0")
                                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                    distribution.set("repo")
                                }
                            }
                            scm {
                                connection.set("scm:git:https://github.com/lUjek-Sh/MonetizeKit.git")
                                developerConnection.set("scm:git:ssh://git@github.com/lUjek-Sh/MonetizeKit.git")
                                url.set("https://github.com/lUjek-Sh/MonetizeKit")
                            }
                            developers {
                                developer {
                                    id.set("lUjek-Sh")
                                    name.set("Lujek")
                                }
                            }
                        }
                    }
                }

                repositories {
                    maven {
                        name = "GitHubPackages"
                        val repository = providers.gradleProperty("monetizekit.githubRepository")
                            .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
                            .getOrElse("lujek-sh/MonetizeKit")
                        val owner = repository.substringBefore('/').lowercase()
                        val repositoryName = repository.substringAfter('/', missingDelimiterValue = "")
                        require(repositoryName.isNotBlank()) {
                            "monetizekit.githubRepository must be in the form OWNER/REPOSITORY."
                        }
                        url = uri("https://maven.pkg.github.com/$owner/$repositoryName")
                        credentials {
                            username = providers.gradleProperty("gpr.user").orNull
                                ?: System.getenv("GITHUB_ACTOR")
                            password = providers.gradleProperty("gpr.key").orNull
                                ?: System.getenv("GITHUB_TOKEN")
                        }
                    }
                }
            }
        }
    }
}
