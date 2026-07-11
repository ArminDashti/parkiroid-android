# How to Use

Step-by-step guide for operating Dogan. No technical setup knowledge required beyond having a server address and API key from your administrator.

## Before you begin

You will need:

1. An **Android phone** (Android 10 or newer) with a **rear camera**
2. The **Dogan app** installed on that phone
3. A **Dogan server** running and reachable from the phone (usually on your network or a hosted URL)
4. An **API key** provided by whoever manages the server

**Important:** The app is designed for a **physical phone**, not a typical emulator. Emulators often lack a real rear camera and may not install or run the app correctly.

## First-time setup

### 1. Install and open the app

Open Dogan. The main screen shows monitoring controls and camera status.

### 2. Grant camera permission

When prompted, allow **Camera** access. Monitoring cannot start without it.

### 3. Configure Settings

Tap **Settings** and enter:

- **Server address** — The base URL of your Dogan server (example format: `http://192.168.1.10:8080` or a hosted HTTPS URL). Your administrator will provide the correct value.
- **API key** — Must match the key configured on the server.
- **Frame upload interval** — How often photos are sent for server-side detection (see [Settings Guide](settings-guide.md)).

Tap **Save**.

### 4. Position the phone

Mount or place the phone so the **rear camera** has a clear view of the area you want to monitor (typically behind or beside the parked vehicle). Use the live preview on the main screen to verify the angle.

## Daily operation

### Start monitoring

1. Open the app (or leave it open after setup).
2. Tap **Start Monitoring**.
3. Confirm the status shows **Running in background** and the *Dogan running* notification appears.

You may leave the app or lock the screen; monitoring continues.

### While monitoring

- The notification shows upload activity (frames sent at the configured interval).
- If the phone detects a **bump or jolt**, you receive a separate **motion alert** notification and an extra photo is captured and uploaded.
- Battery and photos are sent to the server on the configured schedule.

### Stop monitoring

Tap **Stop Monitoring** on the main screen. The background notification disappears and scheduled captures stop.

## Recommended practices

| Practice | Reason |
|----------|--------|
| Use a **15-second or longer** capture interval when possible | Reduces battery drain and network use |
| Keep the phone **plugged in** for long parking sessions | Sustains monitoring without draining the battery |
| Confirm **server address and API key** before leaving the vehicle | Avoids silent upload failures |
| Test **camera angle** with live preview before starting | Ensures the monitored area is actually in frame |

## Troubleshooting (non-technical)

| Symptom | What to check |
|---------|----------------|
| Monitoring won’t start | Camera permission granted? Server address configured? |
| No data on server | Server address correct? Phone on same network or internet? API key matches server? |
| Camera preview fails | Rear camera available and not in use by another app? |
| Battery drains quickly | Increase capture interval; keep phone charging |

For server-side issues (missing frames, auth errors, detection results), contact whoever operates the Dogan server.
