# System Context

How Parkiroid fits into the overall product and what moves between the phone and the server.

## Components

```mermaid
flowchart LR
    subgraph field ["In the vehicle"]
        Phone["Android phone\n(Parkiroid app)"]
    end

    subgraph backend ["Your infrastructure"]
        Server["Parkiroid server"]
        Storage["Stored frames\n& metrics"]
    end

    Phone -->|"Photos, battery data\n(authenticated)"| Server
    Server --> Storage
```

| Component | Role |
|-----------|------|
| **Parkiroid app (this project)** | Runs on an Android phone in or near the vehicle. Captures photos, reads battery state, detects motion, optional on-device object detection. |
| **Parkiroid server (separate project)** | Receives uploads, stores the latest frames and device metrics, can run server-side object detection, exposes APIs for retrieval and administration. |

The app is not a standalone cloud product—it is the **edge client** that feeds a server you deploy and operate (or host via a provider).

## Data flow (conceptual)

### When monitoring is active

1. User starts monitoring on the phone.
2. On each interval (and on motion events):
   - A photo may be taken.
   - Battery level and temperature may be recorded.
3. If a server is configured:
   - Photos and metrics are sent securely using the API key.
   - Each phone is identified by a stable **device ID** (derived from the phone itself) so the server can distinguish multiple vehicles or deployments.
4. If on-device detection is enabled:
   - The phone analyzes frames locally and shows summaries in the notification (and optionally on the preview).
5. If server detection is enabled:
   - Photos are uploaded for the server to analyze; results are not shown on the phone beyond upload status.

### Authentication

The phone authenticates to the server using the **API key** configured in Settings. The server must accept the same key. This prevents unauthorized devices from submitting data.

## Device identity

Each installation is treated as one **device** on the server, identified automatically by the phone. Operators use this ID on the server side to tell which vehicle or phone sent a given frame or metric reading. No manual device registration is required in the app.

## Deployment scenarios

| Scenario | Description |
|----------|-------------|
| **Home / lab** | Phone and server on the same Wi‑Fi; server address is a local IP. |
| **Hosted server** | Server on the public internet; phone uses HTTPS URL; requires stable connectivity from the parking location. |
| **On-device only (partial)** | Object detection and motion alerts work on the phone; uploads disabled if no server is set. Full remote visibility still requires the server. |

## Privacy and data considerations

- **Camera data** — Photos are captured only while monitoring is active. They are sent only to the server address the user configures.
- **Location** — The app does **not** report GPS or location in the current version.
- **Local history** — The app does **not** keep a browsable gallery of past photos on the phone; frames are transient unless the server stores them.
- **Permissions** — Camera and network access are required for full operation; users must explicitly grant camera permission.

Stakeholders should align server retention, access control, and privacy policies with how frames and metrics are stored on the backend.

## Relationship to other documentation

- **This folder** — Product and operational view of the Android app.
- **Parkiroid server documentation** — API details, administration, and storage (maintained in the server repository).
- **Project README** — Developer-oriented build and run instructions (not intended for managers).
