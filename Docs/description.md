# Dogan — Project Description

Dogan is a Kotlin Android app for multi-mode vehicle monitoring and driving assistance (Watchman, Spotter, Copilot, OFF). It captures camera frames, always runs on-device YOLO26 NCNN object detection, uploads telemetry to a Dogan server, and can stream via LiveKit.

## Tech stack

- Kotlin, Android 10+ (`minSdk` 29, `targetSdk` 33)
- CameraX, DataStore, OkHttp, LiveKit Android SDK
- Tencent NCNN via JNI (`dogan_ncnn`) for on-device detection

## How to run

1. Ensure `ai-models/` contains YOLO26 NCNN exports (`yolo26{n,s,m}_ncnn_model/model.ncnn.{param,bin}`).
2. Build with Android Studio or `./gradlew :app:assembleDebug` (Gradle copies models into assets).
3. Install on a physical Android 10+ device with a rear camera.
4. Open **Connectivity**, enter username/password and API/Stream URLs, Connect manually, then pick a mode on the main grid (or Preview from settings).

## Main UI

The main screen is a dark Material button hub (no embedded preview). Mode rows (Copilot / Spotter / Watchman) with settings gears; re-tap active mode for OFF. **Preview** opens from section settings. Other buttons open Recording, General Settings, Connectivity, Logs, or Exit (full process kill). Default mode is OFF; Connectivity starts disconnected (manual connect only).

Settings section order: Configure → Retention → Browse → Danger.

## Embedded models

YOLO26 Nano / Small / Medium (~124 MB total) are packaged in the APK from repo `ai-models/`. Alert sounds download from the server when connected. Detection bounding boxes show on Preview and history frames; Watchman Preview also shows live jolt/sound values.
