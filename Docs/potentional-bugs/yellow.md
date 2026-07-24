# Minor risks / smells

**[APK size]** — Embedding nano+small+medium adds ~124 MB to the APK; Play Store / sideload installs are heavier. Consider shipping only nano+small by default.

**[AGP upgrade / aapt2 cache]** — Bumping AGP (e.g. Studio prompt to 8.13.x) can leave an incomplete `~/.gradle/caches/.../aapt2/<ver>/` entry (`.pom` only, no `*-windows.jar`), which fails `:app:compileDebugNavigationResources` / resource merge. Keep AGP at 8.5.2 unless intentionally upgrading and clearing that cache.

**[Default hosts]** — Existing installs that already saved `dogan.xaigrok.ir` keep the old host until Connectivity is edited; only fresh prefs get `dogan-api` / `dogan-livekit`.
