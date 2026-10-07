import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.acewood.synapse.core"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.acewood.synapse.core"
        minSdk = 29
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        // Dev key committed on purpose so every CI build can upgrade the previous one in place.
        // It only protects a sideloaded home-lab app; swap for a private key before any public release.
        create("synapse") {
            storeFile = rootProject.file("keystore/synapse-dev.jks")
            storePassword = "synapse-dev"
            keyAlias = "synapse"
            keyPassword = "synapse-dev"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("synapse")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("synapse")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core-logic"))
}
