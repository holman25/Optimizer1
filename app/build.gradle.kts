plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.statusclaro.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.statusclaro.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.2"
    }

    buildFeatures { compose = true; buildConfig = true }

    // Android Studio uses JDK 21; Java and Kotlin must target the same bytecode level.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("androidx.media3:media3-transformer:1.11.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
