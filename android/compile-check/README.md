# compile-check (no Android SDK needed)
Type-checks the app's Kotlin against Robolectric's `android-all` jar (the real framework classes, from Maven Central).
It's meant for environments that can't download the Android SDK (the BRAIN cloud container). It doesn't build an APK. CI does that.
```
cd android/compile-check && ../gradlew -p . -q compileKotlin
```
