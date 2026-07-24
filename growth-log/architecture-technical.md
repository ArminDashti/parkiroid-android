# Growth log — architecture (technical)

Install scripts resolve `adb` from PATH or Android SDK `platform-tools`, wait up to 60s for a device in `device` state (handling unauthorized/offline and multi-device via serial). Windows `install-on-phone-directly.ps1` rebuilds via `scripts/export-apk.ps1` by default (unless `-SkipBuild` or `-Apk`), then installs the fresh Gradle APK with `adb install -r` after `am force-stop`, and optionally `am start` for `com.dogan/.MainActivity`.

Capture path: `CaptureService.scheduleAnalysisLoop` duty-cycles via `DoganCamera.captureBitmap` at mode FPS. Continuous `bindForMonitoring` is used only for Preview (`DetectionOverlayBridge.listener`) or when `recording_enabled` is true. Recording is gated by `recording_enabled` (default false).
