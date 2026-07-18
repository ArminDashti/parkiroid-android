# How to Use

Step-by-step guide for operating Dogan. No technical setup knowledge required beyond having a server address from your administrator.

## Before you begin

You will need:

1. An **Android phone** (Android 10 or newer) with a **rear camera**
2. The **Dogan app** installed on that phone
3. A **Dogan server** running and reachable from the phone (usually on your network or a hosted URL)

**Important:** The app is designed for a **physical phone**, not a typical emulator.

## First-time setup

### 1. Install and open the app

Open Dogan. The main screen is a button hub: mode rows (Copilot / Spotter / Watchman with settings gears), Connectivity, Record, Settings, Logs, Exit.

### 2. Grant permissions

When prompted, allow **Camera**, microphone, and location as requested.

### 3. Configure Connectivity

Tap **Connectivity** (or its gear) and enter:

- **Username / password** — server login (defaults match repo-root `credentials.txt`: `armin` / `dogan123`)
- **API URL** — Dogan server as `host:port` (example: `192.168.1.10:8090`)
- **Stream URL** — LiveKit as `host:port` (often same host, port `7880`)

Above the Connect button you see **ping**, **API**, and **LiveKit** status. The button itself is only **Connected** or **Disconnected**. The app starts **disconnected**; connect manually. Failures show an error near the button. Changes save automatically.

### 4. Pick a mode

On the main screen, tap **Copilot**, **Spotter**, or **Watchman**. Tap the active mode again to turn **OFF**. Open **Preview** from that mode’s settings gear to verify the camera angle (bounding boxes; Watchman also shows jolt/sound).

## Daily operation

### While a mode is active

- CaptureService runs in the background with an ongoing notification.
- Detection frames are kept in **History frames** (open from Copilot / Spotter / Watchman settings).
- Jolts and sharp sounds can raise alerts in Watchman/Spotter.

### Stop monitoring

Select the active mode again (re-tap) to turn **OFF**, or tap **Exit** to close the app completely.

## Recommended practices

| Practice | Reason |
|----------|--------|
| Use **Preview** in mode settings to check framing before leaving the vehicle | Ensures the monitored area is in view |
| Keep the phone **plugged in** for long parking sessions | Sustains monitoring without draining the battery |
| Confirm **Connectivity** before leaving | Avoids silent upload failures |
| Review **Recording** retention (max 12 hours) | Limits local disk use |

## Troubleshooting (non-technical)

See [current-status-and-limitations.md](current-status-and-limitations.md) for known limits. Prefer **Diagnostics** (if available from Logs/tools) for Internet / Server / LiveKit checks.
