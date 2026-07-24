# Directory tree

```
parkiroid/
├── AGENTS.md                          # Agent preferences and workspace facts
├── Docs/
│   ├── description.md                 # Project overview
│   ├── dir-tree.md                    # This file
│   ├── endpoints.md                   # Client API notes (if present)
│   ├── features-and-capabilities.md   # Feature list
│   ├── settings-guide.md              # Settings sections and keys
│   ├── system-context.md              # Phone ↔ server context
│   ├── modules/
│   │   ├── detection-overlay.md       # Bounding box overlay
│   │   ├── main-ui-settings.md        # Main grid, settings, history
│   │   └── model-assets.md            # Embedded NCNN models
│   ├── suggestion/                    # Improvement ideas
│   └── potentional-bugs/              # Known risks
├── ai-models/                         # YOLO26 NCNN source models
├── app/
│   ├── build.gradle.kts
│   ├── download_models.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/                       # NCNN JNI detector
│       ├── java/com/dogan/
│       │   ├── MainActivity.kt        # Button-grid hub
│       │   ├── CameraActivity.kt      # Preview + overlays + sensor HUD
│       │   ├── DetectionOverlayView.kt # Green/red bbox + confidence
│       │   ├── DetectionOverlayBridge.kt
│       │   ├── SensorHudBridge.kt     # Live jolt/sound for Watchman Preview
│       │   ├── AppLogger.kt           # Section-tagged log ring buffer
│       │   ├── LogSection.kt          # Spotter/Watchman/Recording/Connectivity
│       │   ├── LogsActivity.kt        # Filtered or full Logs UI
│       │   ├── SensitivityLevel.kt    # Low/Medium/High/Custom
│       │   ├── SettingsActivity.kt    # Section settings + auto-save
│       │   ├── SettingsPublisher.kt   # Local save + PUT /settings
│       │   ├── SettingsStore.kt       # DataStore preferences
│       │   ├── HistoryFramesActivity.kt
│       │   ├── HistoryFrameViewerActivity.kt  # Full-screen prev/next
│       │   ├── FrameHistoryStore.kt   # Local frame ring buffer
│       │   ├── ServerConnectionManager.kt
│       │   ├── SessionCredentials.kt # Username/password for API+LiveKit
│       │   ├── LiveKitStatusCache.kt
│       │   ├── CaptureService.kt      # Detection FG service (duty-cycle camera)
│       │   ├── DoganCamera.kt         # CameraX bind / still / preview
│       │   ├── ModeController.kt      # Mode dispatch (incl. OFF)
│       │   ├── OperatingMode.kt       # Copilot/Spotter/Watchman/OFF
│       │   └── …                      # Engines, camera, API, telemetry
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── activity_camera.xml
│           │   ├── activity_settings.xml
│           │   ├── activity_history_frames.xml
│           │   ├── activity_history_frame_viewer.xml
│           │   ├── item_history_frame.xml
│           │   ├── section_*.xml      # Per-section settings layouts
│           │   └── include_fps_stepper.xml
│           ├── drawable/ic_*.xml      # Main-grid and FPS icons
│           └── values/                # colors, themes, strings
├── credentials.txt                    # Dev username/password (gitignored)
├── install-on-phone-directly.ps1      # ADB install over USB debugging (Windows)
└── scripts/                           # APK export + USB/ADB install helpers
    ├── export-apk.ps1                 # Build and copy APK to exports/
    ├── export-apk.sh                  # Bash counterpart of export-apk.ps1
    └── install-apk.sh                 # Bash ADB install counterpart
```
