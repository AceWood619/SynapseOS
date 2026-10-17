// Root is intentionally empty — the two subprojects (:corelogic, :appmod) do the type-checking,
// compiled as separate Kotlin modules so cross-module smart-cast rules match the real Android build.
// Run:  ../gradlew :appmod:compileKotlin
