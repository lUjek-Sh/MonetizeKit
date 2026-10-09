plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ua.lujek.monetizekit.admob"
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
    api(project(":ads-core"))
    implementation(libs.google.mobile.ads)
    implementation(libs.kotlinx.coroutines.android)
}
