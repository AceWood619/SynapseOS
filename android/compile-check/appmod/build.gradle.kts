import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { kotlin("jvm") version "2.0.21" }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
// rootDir = compile-check/ ; its parent = android/
val android = rootDir.parentFile.absolutePath
sourceSets["main"].kotlin.srcDirs("$android/app/src/main/kotlin")
dependencies {
    implementation(project(":corelogic"))   // separate module → real cross-module smart-cast rules
    compileOnly("org.robolectric:android-all:14-robolectric-10818077")  // Android SDK stubs
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")                   // keep in sync with app/build.gradle.kts
}
