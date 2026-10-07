import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { kotlin("jvm") version "2.0.21" }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
// rootDir = compile-check/ ; its parent = android/
val android = rootDir.parentFile.absolutePath
sourceSets["main"].kotlin.srcDirs("$android/core-logic/src/main/kotlin")
