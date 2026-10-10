plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ua.lujek.monetizekit.mediation.unity"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":ads-admob"))
    implementation(project(":ads-unity-sdk"))
    implementation(libs.google.ads.mediation.unity)
}
