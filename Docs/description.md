# Dogan — Project Description

Dogan is a Kotlin Android app for multi-mode vehicle monitoring and driving assistance (Watchman, Spotter, Copilot, OFF). It captures camera frames on a duty cycle (open → still → close), always runs on-device YOLO26 NCNN object detection when a mode is active, uploads telemetry to a Dogan server, and can stream via LiveKit.

## Tech stack

- Kotlin, Android 10+ (`minSdk` 29, `targetSdk` 33)
- Android Gradle Plugin **8.5.2** (do not bump via Studio’s AGP upgrade prompt without clearing/re-downloading `aapt2` and aligning the Gradle wrapper)
- CameraX, DataStore, OkHttp, LiveKit Android SDK
- Tencent NCNN via JNI (`dogan_ncnn`) for on-device detection

## How to run

1. Ensure `ai-models/` contains YOLO26 NCNN exports (`yolo26{n,s,m}_ncnn_model/model.ncnn.{param,bin}`).
2. Build with Android Studio or `./gradlew :app:assembleDebug` (Gradle copies models into assets).
3. Install on a physical Android 10+ device with a rear camera (USB debugging + ADB):
   - Windows: `.\install-on-phone-directly.ps1` (rebuilds from current sources by default; add `-Launch` to open the app, `-SkipBuild` to reuse an existing APK)
   - macOS/Linux: `./scripts/install-apk.sh --build --launch`
   - Or export only: `.\scripts\export-apk.ps1` / `./scripts/export-apk.sh`, then `adb install -r <apk>`
4. Open **Connectivity** (defaults: `dogan-api.xaigrok.ir:443`, `dogan-livekit.xaigrok.ir:443`, `armin`/`dogan123`), Connect manually if needed, then pick a mode on the main grid (or Preview from settings).

## Main UI

The main screen is a dark Material button hub (no embedded preview). Mode rows (Copilot / Spotter / Watchman) with settings gears; re-tap active mode for OFF. **Preview** opens from section settings only when a mode is already active. Other buttons open Recording, General Settings, Connectivity, Logs, or Exit (full process kill). Defaults: mode OFF, recording off, Connectivity disconnected (manual connect only). While monitoring, the camera stays powered only for each scheduled capture (and while Preview or recording is on).

Settings section order: Configure → Retention → Browse → Danger.

## Embedded models

YOLO26 Nano / Small / Medium (~124 MB total) are packaged in the APK from repo `ai-models/`. Alert sounds download from the server when connected. Detection bounding boxes (green cars / red persons + confidence) show on Preview and history frames; Watchman Preview also shows live jolt/sound values.
