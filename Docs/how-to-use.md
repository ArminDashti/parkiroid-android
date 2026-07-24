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

From a PC with USB debugging enabled on the phone:

- Windows: `.\install-on-phone-directly.ps1 -Launch`
- macOS/Linux: `./scripts/install-apk.sh --build --launch`

Then open Dogan. The main screen is a button hub: mode rows (Copilot / Spotter / Watchman with settings gears), Connectivity, Record, Settings, Logs, Exit.

### 2. Grant permissions

When prompted, allow **Camera**, microphone, and location as requested.

### 3. Configure Connectivity

Tap **Connectivity** (or its gear) and enter:

- **Username / password** — defaults `armin` / `dogan123` (also in repo-root `credentials.txt`)
- **API URL** — Dogan server as `host:port` (default `dogan-api.xaigrok.ir:443`; local Docker example `192.168.1.10:8090`)
- **Stream URL** — LiveKit as `host:port` (default `dogan-livekit.xaigrok.ir:443`)

Above the Connect button you see **ping**, **API**, and **LiveKit** status. The button itself is only **Connected** or **Disconnected**. The app starts **disconnected**; connect manually. Failures show an error near the button. Changes save automatically.

### 4. Pick a mode

On the main screen, tap **Copilot**, **Spotter**, or **Watchman**. Activating Spotter/Watchman starts monitoring and fills History + Logs immediately. Tap the active mode again to turn **OFF**. Open **Preview** from that mode’s settings gear (mode must already be active) to verify the camera angle (green/red bounding boxes + confidence; Watchman also shows jolt/sound). Recording stays off until you enable **Record video** in Recording settings.

## Daily operation

### While a mode is active

- CaptureService runs in the background with an ongoing notification.
- The camera opens only for each detection still (duty-cycle), unless Preview or recording is on.
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
