# Settings Guide

All configurable options live on the **Settings** screen. Changes are saved to the phone and apply the next time monitoring runs or on the next capture cycle.

## Server connection

### Server address

The web address of your Parkiroid server—the place where photos and battery readings are sent.

- Enter the base URL only (no path after the host).
- Examples: `http://192.168.1.10:8080`, `https://parkiroid.example.com`
- Trailing slashes are optional; the app normalizes the value.

A valid server address is **required** before monitoring can start. Photos and battery data are uploaded to the server for cloud-side object detection.

### API key

A shared secret that must **match the server**. Think of it as a password for the app to talk to your backend. Your server administrator provides this value. A default development key may be used in test environments only—production deployments should use a unique key.

## Frame upload interval

How often photos are sent to the server for analysis.

| Option | Typical use |
|--------|-------------|
| 1–5 seconds | High-frequency monitoring; higher battery and data use |
| 15 seconds (default) | Balanced default for most parking scenarios |
| 30–60 seconds | Longer sessions; lower battery and bandwidth |

Shorter intervals give more frequent updates but drain the phone faster.

## Default values (reference)

| Setting | Default |
|---------|---------|
| Server address | Pre-filled with a development host in some builds; should be replaced for production |
| API key | Development default; replace for production |
| Capture / upload interval | 15 seconds |
