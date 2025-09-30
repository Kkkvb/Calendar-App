
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.schedule"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.schedule"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        // You use both View system (Fragments/BottomNav) and Compose. That's OK.
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // ----- Compose (keep for future Compose UI) -----
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")

    // ----- View system (needed for your current MainActivity/Fragments/BottomNav) -----
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0") // <-- This provides Theme.MaterialComponents.*
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // if used in your layouts

    // Navigation for Fragments + BottomNavigationView
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.2")

    // Room (as you already had)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Collections & time
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")

    // WorkManager (optional)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}

