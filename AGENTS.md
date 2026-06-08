## Learned User Preferences

- SMS must be sent on the device only; do not add server-side SMS outbox or polling APIs
- Use the `parkiroid` name and `/parkiroid/` API path prefix (not `samroid`)
- Prefer a dedicated Settings screen for server address, max shake magnitude, and SMS numbers rather than inline configuration on the main screen
- Do not commit git changes unless explicitly asked

## Learned Workspace Facts

- Parkiroid is a Kotlin Android app for background parked-vehicle monitoring (rear-camera photos, battery telemetry, motion alarms, optional SMS)
- Targets Android 10+ (`minSdk` 29, `targetSdk` 33); rear camera is required in the manifest
- HTTP API prefix is `{base}/parkiroid/api/v1/` for img upload, battery info, and jarring-noise/violent-jolt alarms
- All outbound SMS goes through `SmsSender`; alarm SMS uses comma-separated numbers from settings
- Settings persist in DataStore: server URL, max shake magnitude (5–80, default 30), alert phones, and capture period (default 15 s, not exposed in Settings UI)
- Motion alarms: violent jolt at max shake magnitude; jarring noise at 60% of that threshold; rate-limited to once every 5 seconds
- APK export scripts: `scripts/export-apk.ps1` (Windows) and `scripts/export-apk.sh` copy builds to `exports/` with versioned filenames
- Root `.gitignore` excludes build outputs, `.gradle/`, `.idea/`, `.cursor/`, `exports/`, and `local.properties`
- Install and testing require a physical Android 10+ device with rear camera; game emulators often fail install due to required camera/telephony hardware
