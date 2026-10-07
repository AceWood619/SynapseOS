pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "appcheck"
// Two real modules so the Kotlin compiler enforces the SAME cross-module boundary as the Android
// build (core-logic is a separate module from app). This catches cross-module smart-cast errors
// locally instead of in CI.
include(":corelogic", ":appmod")
