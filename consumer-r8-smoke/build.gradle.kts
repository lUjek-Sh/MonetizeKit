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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // This module verifies code shrinking and consumer keep rules. Resource shrinking is
            // intentionally out of scope and currently prevents AGP's standalone R8 analyzer
            // from producing its report for an APK with no app resources.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
}
