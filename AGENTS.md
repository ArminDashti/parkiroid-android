## Learned User Preferences

- Use the `parkiroid` name and `/parkiroid/` API path prefix (not `samroid`)
- Prefer a dedicated Settings screen for server address, API key, and capture interval rather than inline configuration on the main screen
- Do not commit git changes unless explicitly asked

## Learned Workspace Facts

- Parkiroid is a Kotlin Android app for background parked-vehicle monitoring (front/rear camera photos, microphone sound monitoring, battery telemetry, cloud object detection via server uploads)
- Targets Android 10+ (`minSdk` 29, `targetSdk` 33); rear camera is required in the manifest, front camera and microphone optional
- Main screen: server connection status, Front/Rear Camera buttons, Settings, Logs
- Settings screen: server connect/disconnect, AI model, capture interval (ms), on-device detection toggle, screen-wake interval, detection/sending image quality, realtime FPS
- HTTP API prefix is `{base}/parkiroid/api/v1/`; client uses `POST /auth`, `POST /frame`, and `POST /device-metrics` per parkiroid-server
- Device identity is the Android ID sent as `device_id` on frame and metrics requests
- Settings persist in DataStore: server URL, API key (default `parkiroid-dev-key`), and capture period (default 15 s)
- APK export scripts: `scripts/export-apk.ps1` (Windows) and `scripts/export-apk.sh` copy builds to `exports/` with versioned filenames
- Root `.gitignore` excludes build outputs, `.gradle/`, `.idea/`, `.cursor/`, `exports/`, and `local.properties`
- Install and testing require a physical Android 10+ device with rear camera; game emulators often fail install due to required camera hardware

## Cursor Cloud specific instructions

Environment: this is a Gradle Android project (no server module). JDK 17 is the default `java` and the Android SDK lives at `~/android-sdk`. The startup update script (re)writes `local.properties` with `sdk.dir=$HOME/android-sdk`, so `./gradlew` works without setting `JAVA_HOME`/`ANDROID_HOME`. If a future SDK/JDK issue appears, `sdkmanager` is at `~/android-sdk/cmdline-tools/latest/bin` and JDK 17 is at `/usr/lib/jvm/java-17-openjdk-amd64`.

Standard commands (run from repo root):
- Build (dev): `./gradlew :app:assembleDebug` → APK at `app/build/outputs/apk/debug/app-debug.apk`.
- Lint: `./gradlew :app:lintDebug`. Note: lint currently aborts on a pre-existing `MissingPermission` error in `AudioCapture.kt`; that is a real code finding, not an env problem.
- Unit tests: `./gradlew :app:testDebugUnitTest` (there are currently no unit-test sources, so this is `NO-SOURCE`).

Running the app on the headless emulator (no `/dev/kvm`, so the emulator runs in slow software/TCG mode):
- An AVD named `parkiroid_test` (system image `android-33;google_apis;x86_64`) is pre-created. Start headless with: `~/android-sdk/emulator/emulator -avd parkiroid_test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -accel off`. First boot does slow first-boot dexopt and can take 5-10 min; poll `adb shell getprop sys.boot_completed` until it returns `1`.
- Because the emulator is CPU-starved, System UI raises repeated ANR dialogs that steal input focus. Run `adb shell settings put global hide_error_dialogs 1` so those dialogs stop blocking automation.
- `adb shell input tap` uses real device pixels (1080x2340), NOT the scaled `screencap` image size. Scale screenshot coordinates up (~2.28x) before tapping. `screencap` also lags a few seconds behind the actual render; add sleeps and re-capture if a shot looks blank/stale.
- Driving text entry via many `adb shell input keyevent` calls is extremely slow under software emulation. To preset settings deterministically, write the DataStore file directly (app is debuggable): the file is `files/datastore/parkiroid_settings.preferences_pb` (androidx preferences protobuf) accessed via `run-as com.parkiroid`.
- `CaptureService` is not exported, so it cannot be started from `adb`; monitoring is only started from within the app (`MainActivity` starts it once `ServerConnectionManager` reports connected).
- Known app behavior: the Settings "CONNECT" button calls `ParkiroidApiClient.testConnection` synchronously on the main thread (via `lifecycleScope` = Main), which trips `NetworkOnMainThreadException` (silently caught) and always reports "Connection failed" even when the server is reachable. Emulator→host networking itself works (host loopback is `http://10.0.2.2:8080`). This is a pre-existing code issue, not an environment problem.
- To exercise the server upload path against a local stand-in, point the server address at `http://10.0.2.2:8080` and run any simple HTTP server implementing `GET /parkiroid/api/v1/health`, `POST /parkiroid/api/v1/auth` (return JSON `{"token": ...}`), `POST /parkiroid/api/v1/frame`, and `POST /parkiroid/api/v1/device-metrics`.
