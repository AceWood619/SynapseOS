// The Android app needs the Android SDK. The BRAIN cloud container can't download it
// (dl.google.com is blocked), so Google's repo and the app module are only used where an SDK exists (CI, PC).
val hasSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").exists()

pluginManagement {
    repositories {
        if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here (not in the root build) so each module loads its plugins in one classloader.
    plugins {
        kotlin("jvm") version "2.0.21"
        kotlin("android") version "2.0.21"
        id("com.android.application") version "8.7.3"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (hasSdk) google()
        mavenCentral()
    }
}
rootProject.name = "synapse"

include(":core-logic")
if (hasSdk) include(":app")
