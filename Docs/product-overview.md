# Product Overview

## What is Dogan?

Dogan is a mobile app designed to watch over a parked vehicle using an Android phone left in or near the car. Once monitoring is started, the app works in the background—even when the screen is off—taking photos from the rear camera and sending updates to a Dogan server you control.

The goal is simple: give the vehicle owner remote visibility into what is happening around the car while it is parked, along with signs that something unusual may have occurred (such as a bump or contact).

## Who is it for?

Dogan is intended for:

- **Vehicle owners** who want peace of mind while their car is parked
- **Operators or teams** who run a Dogan server and collect data from one or more phones acting as in-car monitors
- **Early adopters and pilots** evaluating parked-vehicle monitoring before broader rollout

## What problem does it solve?

When a car is parked, the owner typically has no ongoing view of the surroundings. Dogan addresses that gap by:

1. **Periodic visual check-ins** — Regular photos from the rear camera show the area behind or around the vehicle.
2. **Device health visibility** — Battery level and temperature are reported so operators know the monitoring phone is still functioning.
3. **Motion awareness** — The phone’s motion sensors can detect bumps or jolts (for example, another vehicle touching the car or a parking-lot impact) and trigger an immediate photo plus an on-phone alert.
4. **Optional object awareness** — The app can highlight or report detected objects (such as other vehicles or people) either on the phone itself or on the server, depending on configuration.

## How it fits in the bigger picture

Dogan is the **field device** (the phone in the car). It pairs with a separate **Dogan server** that receives photos and telemetry, stores them, and can support review or downstream processing. The app alone does not provide a web dashboard or long-term archive—that role belongs to the server and any tools built around it.

## Design principles

- **Background-first** — Monitoring is meant to run continuously while parked, not only when the app is open on screen.
- **Low-friction control** — Start and stop monitoring from a single main screen; detailed options live in Settings.
- **Configurable backend** — Each deployment can point to its own server address and access key.
- **Battery-conscious** — Capture frequency and image quality are tuned for longer sessions, with guidance to use longer intervals when possible.
