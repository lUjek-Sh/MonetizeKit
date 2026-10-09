plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ua.lujek.monetizekit.consumer.r8smoke"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ua.lujek.monetizekit.consumer.r8smoke"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":ads-core"))
    implementation(project(":ads-admob"))
    implementation(project(":ads-meta"))
    implementation(project(":ads-unity"))
    implementation(project(":ads-admob-meta-mediation"))
    implementation(project(":ads-admob-unity-mediation"))
}
