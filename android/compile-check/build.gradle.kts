import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { kotlin("jvm") version "2.0.21" }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
val repo = rootDir.parentFile.absolutePath
sourceSets["main"].kotlin.srcDirs("$repo/app/src/main/kotlin", "$repo/core-logic/src/main/kotlin")
dependencies { compileOnly("org.robolectric:android-all:14-robolectric-10818077") }
