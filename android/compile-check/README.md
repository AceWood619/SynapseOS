# compile-check (no Android SDK needed)
Type-checks the app's Kotlin against Robolectric's `android-all` jar (the real framework classes, from Maven Central).
It's meant for environments that can't download the Android SDK (the BRAIN cloud container). It doesn't build an APK. CI does that.

**Two real modules** (`:corelogic` + `:appmod`, app depends on core-logic) so the Kotlin compiler enforces the
**same cross-module boundary as the Android build**. This is deliberate: a nullable public property from
`core-logic` cannot be smart-cast inside `app` (bind it to a local `val` first). A single merged module would
hide those errors and let them fail in CI instead. Run:
```
cd android/compile-check && ../gradlew :appmod:compileKotlin
```
Keep `appmod/build.gradle.kts` deps (okhttp, robolectric) in sync with `app/build.gradle.kts`.
