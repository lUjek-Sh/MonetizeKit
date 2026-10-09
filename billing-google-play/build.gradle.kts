plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ua.lujek.monetizekit.billing.googleplay"
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
    api(project(":billing-core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.play.billing.ktx)
}
