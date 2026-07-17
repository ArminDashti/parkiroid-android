# Dogan Documentation

This folder describes the Dogan Android app in plain language for product owners, managers, and stakeholders who need to understand what the app does today—without implementation details.

## Documents

| Document | Description |
|----------|-------------|
| [Product Overview](product-overview.md) | What Dogan is, who it is for, and the problem it addresses |
| [Features & Capabilities](features-and-capabilities.md) | What the app can do in its current version |
| [How to Use](how-to-use.md) | Setup and day-to-day operation |
| [Settings Guide](settings-guide.md) | What each setting means and when to change it |
| [System Context](system-context.md) | How the app fits with the Dogan server and what data flows where |
| [Current Status & Limitations](current-status-and-limitations.md) | What is included, what is not, and known constraints |
| [Description](description.md) | Tech stack and how to build/run |
| [Endpoints](endpoints.md) | Android client API usage |
| [Directory tree](dir-tree.md) | Repo layout overview |

## At a glance

**Dogan** · Version 1.0

Dogan is an Android app that monitors a parked vehicle in the background. While monitoring is active, it periodically captures photos from the phone’s rear camera, reports battery health, can detect sudden motion (such as a bump), and optionally identifies objects in the scene—either on the phone or on a connected server.

**Platform:** Android 10 and newer · **Requires:** Rear camera · **Companion:** Dogan server (configured separately)
