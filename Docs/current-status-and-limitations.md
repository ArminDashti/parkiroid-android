# Current Status & Limitations

Snapshot of where the product stands today and what to expect when planning pilots or releases.

## Release information

| Item | Value |
|------|--------|
| **Product name** | Parkiroid |
| **Version** | 1.0 |
| **Platform** | Android 10 and newer |
| **Hardware requirement** | Rear camera (required) |

## What is included today

- [x] Background vehicle monitoring with rear-camera photos
- [x] Configurable capture / upload interval (1–60 seconds in server detection mode; 15 seconds default)
- [x] Battery level and temperature reporting to server
- [x] Motion / bump detection with alert notification and extra capture
- [x] On-device object detection with optional live preview overlays
- [x] Server-side object detection mode (upload frames for remote analysis)
- [x] Dedicated Settings screen for server, detection, and interval options
- [x] Persistent notification while monitoring
- [x] Live camera preview on main screen during monitoring

## What is not included (yet)

These items are **out of scope** for the current app version:

| Gap | Impact |
|-----|--------|
| **In-app photo gallery or history** | Users cannot scroll through past captures on the phone; history depends on the server |
| **GPS / location reporting** | No map or geofencing; server cannot see where the vehicle was parked from the app alone |
| **User accounts or login in the app** | Identity is device + API key only; no end-user sign-in flow |
| **Remote start/stop from server** | Monitoring is controlled only from the phone |
| **Multi-camera support** | Rear camera only |
| **iOS version** | Android only |
| **Built-in web dashboard** | Viewing data requires the separate Parkiroid server or custom tooling |

Roadmap priorities should be confirmed with the product owner; the list above reflects the codebase as of version 1.0.

## Known constraints

### Hardware and environment

- Requires a **physical Android device** with a working rear camera.
- Typical **emulators are not suitable** for full testing or deployment.
- Monitoring quality depends on **phone placement**, lighting, and camera field of view.

### Battery and connectivity

- Continuous camera and background operation **consume battery**. Long sessions should use power adapter when possible.
- Shorter capture intervals increase battery and mobile data use.
- Uploads require **network reachability** to the configured server; offline periods mean gaps in server-side data.

### Operational

- Server address and API key must be **correct and kept in sync** with server configuration.
- Motion detection sensitivity is tuned for **gentle bumps and parking-lot contacts**; extreme environments may need field validation.
- On-device detection summarizes object **types** in notifications; detailed analytics depend on server mode or future tooling.

## Suggested use cases for pilots

Good fits for early deployment:

- Single-vehicle parking monitoring with a dedicated spare phone
- Proof-of-concept for bump detection in controlled parking environments
- Comparing on-device vs server-side object detection for your use case
- Battery and uptime testing over multi-hour sessions

Less ideal without further product work:

- Fleet-wide map tracking (no GPS in app)
- End-customer self-service review of historical video (no local gallery; server tooling needed)
- Unattended deployment with no server (uploads disabled; limited remote visibility)

## Version history note

Version **1.0** is the initial feature-complete release described in this documentation set. Subsequent releases may add gallery, location, or dashboard features—check release notes when upgrading.
