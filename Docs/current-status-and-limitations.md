# Current Status & Limitations

Snapshot of where the product stands today and what to expect when planning pilots or releases.

## Release information

| Item | Value |
|------|--------|
| **Product name** | Dogan |
| **Version** | 1.0 |
| **Platform** | Android 10 and newer |
| **Hardware requirement** | Rear camera (required) |

## What is included today

- [x] Background vehicle monitoring with rear-camera photos
- [x] Configurable capture / upload interval (1–60 seconds; 15 seconds default)
- [x] Battery level and temperature reporting to server
- [x] Motion / bump detection with alert notification and extra capture
- [x] Cloud object detection (upload frames for server-side analysis)
- [x] Dedicated section Settings screens (Connectivity, Recording, modes, General)
- [x] Persistent notification while monitoring
- [x] Camera eye activity for live preview (from section Preview, not on main hub)
- [x] Local history frames gallery with bounding boxes
- [x] Mode selection on main screen; re-tap active mode → OFF

## What is not included (yet)

These items are **out of scope** for the current app version:

| Gap | Impact |
|-----|--------|
| **GPS / location reporting** | No map or geofencing; server cannot see where the vehicle was parked from the app alone |
| **User accounts or login in the app** | Username + password on Connectivity (`POST /auth`); same token used for API and LiveKit |
| **Embedded device API key login** | Android no longer sends `api_key`; use username/password only |
| **Remote start/stop from server** | Monitoring is controlled only from the phone |
| **iOS version** | Android only |
| **Built-in web dashboard** | Viewing data requires the separate Dogan server or custom tooling |

Roadmap priorities should be confirmed with the product owner; the list above reflects the codebase as of version 1.0.

## Known constraints

### Hardware and environment

- Requires a **physical Android device** with a working rear camera.
- Typical **emulators are not suitable** for full testing or deployment.
- Monitoring quality depends on **phone placement**, lighting, and camera field of view.

### Battery and connectivity

- Continuous camera and background operation **consume battery**. Long sessions should use power adapter when possible.
- By default the camera is **duty-cycled** (open only to take each frame, then closed). Continuous camera applies while Preview is open or Recording is enabled.
- Shorter capture intervals / higher FPS increase battery and mobile data use.
- Uploads require **network reachability** to the configured server; offline periods mean gaps in server-side data.

### Operational

- Server address and username/password must be **correct and kept in sync** with server admin credentials.
- A valid server address is **required** to start monitoring.
- **HTTP and HTTPS** server URLs are supported (`http://` works on Android 10+ via network security config for local/dev servers).
- Motion detection sensitivity is tuned for **gentle bumps and parking-lot contacts**; extreme environments may need field validation.
- Object detection uses embedded YOLO26 COCO-80 weights (person/car/motorcycle/truck, etc.). Copilot `speed_camera` / `speed_limit_sign` classes are not in COCO and will not fire until a fine-tuned model is embedded.
- Object detection results are available on the server; the app shows upload status only.

### Android 10 compatibility

- **Minimum supported version:** Android 10 (API 29). The app is built and tested against this floor.
- **Background camera:** Capture is bound to the foreground service, not the activity, so monitoring continues when the phone screen is off or the user switches apps.
- **Permissions on Android 10:** Only `CAMERA` is required at runtime (no notification permission on Android 10; that applies on Android 13+).
- **Cleartext HTTP:** Allowed so local `http://` server addresses in Settings work on older devices without TLS.

## Suggested use cases for pilots

Good fits for early deployment:

- Single-vehicle parking monitoring with a dedicated spare phone
- Proof-of-concept for bump detection in controlled parking environments
- Server-side object detection and analytics via the Dogan backend
- Battery and uptime testing over multi-hour sessions

Less ideal without further product work:

- Fleet-wide map tracking (no GPS in app)
- End-customer self-service review of historical video (no local gallery; server tooling needed)
- Unattended deployment with no server (monitoring cannot start without a server)

## Version history note

Version **1.0** is the initial feature-complete release described in this documentation set. Subsequent releases may add gallery, location, or dashboard features—check release notes when upgrading.
