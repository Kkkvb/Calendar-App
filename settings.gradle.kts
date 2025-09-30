
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("com.android.application") version "8.5.2"      // or your current AGP
        id("org.jetbrains.kotlin.android") version "1.9.24" // <-- align to 1.9.24
        // (Optional) if you want to be explicit for kapt:
        id("org.jetbrains.kotlin.kapt") version "1.9.24"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Calendar"
include(":app")
