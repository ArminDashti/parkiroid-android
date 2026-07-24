# Pin AGP / reject Studio auto-upgrade

Android Studio often offers to bump `com.android.application` in the root `build.gradle.kts`. That change is easy to accept accidentally and can break the build via incomplete `aapt2` downloads.

**Idea:** Document in README / AGENTS that AGP stays at **8.5.2** unless someone intentionally upgrades Gradle + AGP together and verifies `assembleDebug`. Optionally add a CI check that fails if AGP ≠ 8.5.2.

**Effort:** Low (docs) / Medium (CI assertion).
