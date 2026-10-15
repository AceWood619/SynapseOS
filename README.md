# SynapseOS

**SynapseOS turns an Android phone into a one-handed smart-home remote, sensor node, and voice satellite for [Home Assistant](https://www.home-assistant.io/).**

The primary target is the Cloud Mobile Stratus C8 running a persistent LineageOS 21 DSU. The native launcher is **Synapse Core**, an Android Views application designed around the **Synapse Glass** visual language: dark grounds, neon accents, glass-like panels, large thumb-friendly controls, and fast optimistic feedback.

> **Project status:** the native remote is actively evolving. The phone is a real device target, not a simulator. Device installation and Home Assistant/LAN operations are performed by HANDS; app and documentation work is performed in the repository by MANUS and Claude/BRAIN.

## What SynapseOS provides

- **Native kiosk launcher:** Synapse Core can act as the home screen and device-owner kiosk.
- **Home overview:** house modes, scenes, weather, now playing, timers, system status, profile picker, and app drawer.
- **Room-as-channel remote:** swipe between Home Assistant areas like changing TV channels; control lights, media, climate, fans, covers, switches, and scenes.
- **Audio and media:** now-playing card, album art, transport controls, per-speaker output selection, volume/mute, and TV remote entry points.
- **Jarvis/Assist:** text and microphone interaction through Home Assistant Assist; the native microphone uses the HA Assist pipeline when Android speech services are unavailable.
- **Ambient mode:** low-power Glass clock with weather, timer, now-playing, profile state, dimming, and burn-in-safe movement.
- **Sensor node:** battery, temperature, pressure, illuminance, proximity, motion/presence, Wi-Fi, BLE, camera, and system telemetry can be published to HA.
- **Operational tools:** provisioning, smoke tests, a control API, HA dashboard helpers, and OS installation notes.

## Architecture

```text
Home Assistant
  ▲  WebSocket: states, registries, service calls, Assist, forecasts
  ▲  REST: node sensors and optional control API
  │
  └── Synapse Core (android/app)
        ├── MainActivity and native Views
        ├── HaWsClient / HaRepository / EntityCache
        ├── Profiles, kiosk, ambient and app routing
        └── NodeService + local HTTP control API
              │
              └── core-logic (pure Kotlin, unit tested)

SynapseOS device layer (os/)
  ├── LineageOS 21 DSU notes and install payloads
  └── boot hooks, charge control, ADB and permanent-install guidance
```

### Module boundaries

| Module/path | Responsibility |
|---|---|
| `android/core-logic/` | Pure Kotlin models, HA protocol/data parsing, profiles, room/media logic, validation, and unit tests. Keep device/framework-free. |
| `android/app/` | Android Views, lifecycle, kiosk/device-owner behavior, sensors, audio capture, WebSocket transport, and Glass UI. |
| `android/compile-check/` | SDK-free Kotlin boundary check using `android-all`; catches cross-module type errors before CI. |
| `ha/` | Home Assistant dashboards and package/configuration helpers. |
| `os/` | DSU/permanent-install documentation and device-side payloads. |
| `tools/provision/` | PC-side provisioning, config import, HA state publishing, and smoke tests. |
| `tools/remote-access/` | Remote installation/control helpers. |
| `coord/` | Append-only multi-agent coordination: status, requests, decisions, findings, and contributor notes. |
| `design/` | Glass language and remote interaction references. |
| `devices/stratus-c8/` | Hardware facts, verified procedures, and never-do warnings. |
| `builds/` | CI-published APK and build metadata for HANDS to install. |

## Quick start for contributors

### Prerequisites

- JDK 17 or newer.
- Git and a working copy of this repository.
- For the full APK: Android SDK and accepted Android build-tools/platform packages.
- For app type-checking without an Android SDK: internet access to Maven Central and the included Gradle wrapper.

### Run the required checks

From the repository root:

```bash
cd android
./gradlew :core-logic:test
cd ../android/compile-check
../gradlew :appmod:compileKotlin
```

The first command runs the pure-logic tests. The second checks the Android app against the same two-module boundary without requiring a local Android SDK. See [`android/compile-check/README.md`](android/compile-check/README.md).

With an Android SDK available, build the release APK with:

```bash
cd android
./gradlew :core-logic:test :app:assembleRelease
```

CI runs the tests and release build on pushes to `main`, then publishes the APK and SHA-256 metadata under `builds/`.

### Provisioning a phone

Provisioning is a HANDS/device operation. Start with [`tools/provision/README.md`](tools/provision/README.md) and keep all tokens, PINs, API keys, and device-local secrets outside Git. The repository is public.

## Design and performance rules

- Target viewport: **360×800 dp** on a 720×1600 display at 320 dpi.
- Target GPU: **PowerVR GE8320**. Prefer Android Views and simple drawables over heavy Compose/blur stacks.
- Use large thumb targets (normally at least 64 dp), touch-down feedback, haptics when enabled, and optimistic state for ordinary controls.
- Use at most one real blur, gated by frame-time checks; layered translucent drawables are the normal Glass fallback.
- Pause or reduce animation in ambient/off-screen states. Avoid unbounded animation loops and unnecessary rebuilds.
- Keep Home Assistant entity IDs as implementation details; show friendly names in user-facing controls.

See [`design/synapse-glass/README.md`](design/synapse-glass/README.md) and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Safety boundaries

These rules are part of the product contract:

- **Never commit secrets**: HA tokens, GitHub tokens, API keys, PINs, Wi-Fi credentials, private URLs with credentials, screenshots of the home, MAC addresses, or device identifiers.
- **Master bedroom TV/lights are manual-only.** Jarvis, automations, demos, and background refreshes must never target them automatically. A person may select a manual control explicitly.
- HANDS owns physical phone, PC, LAN, Home Assistant, ADB, fastboot, flashing, and on-device verification.
- Do not flash, wipe, change bootloader/AVB state, alter security/access, or make other hard-to-reverse device changes without Mason's explicit approval.
- Treat device facts as **verified**, **inferred**, or **unknown** and leave reproducible evidence in `coord/`.

Read [`SECURITY.md`](SECURITY.md) and [`coord/PROTOCOL.md`](coord/PROTOCOL.md) before working on the project.

## Coordination model

This is a multi-agent project:

| Contributor | Primary responsibility |
|---|---|
| **Mason** | Product decisions and approval for risky/hard-to-reverse changes. |
| **HANDS** | Phone, PC, LAN, Home Assistant, installation, and on-device verification. |
| **MANUS** | Synapse Core app features/UI, pure logic, HA config/dashboard files, and project documentation. |
| **Claude/BRAIN** | Research, source-code investigation, architecture/code review, and code/docs contributions. |
| **SPINE** | Architecture, hardware reality, data-contract integrity, and review evidence. |

The repository is the shared memory. Start with [`coord/MANUS_ONBOARDING.md`](coord/MANUS_ONBOARDING.md), then [`coord/PROTOCOL.md`](coord/PROTOCOL.md), [`coord/STATUS.md`](coord/STATUS.md), and the newest entries in [`coord/REQUESTS.md`](coord/REQUESTS.md). Do not erase another contributor's evidence or edit another contributor's status section.

## Current roadmap

The active Remote v4 batch is tracked in [`coord/REQUESTS.md`](coord/REQUESTS.md):

1. Lighting FX and holiday modes — implemented.
2. Weather screen and ambient hourly forecast — implemented in the current MANUS work block; review/build verification is tracked in the coordination board.
3. Admin panel settings — idle timeout, brightness, night dim, haptics, and spoken-reply defaults.
4. System notices — persistent notifications and pending-update badge.
5. Offline resilience — last-good snapshot, disabled controls, reconnecting state, and reboot recovery.
6. Glass polish — low-FPS neuron orb, wallpaper, frame-time-gated hero blur, and true per-tile scale.
7. Optional boot animation generator.

The roadmap is deliberately incremental: each feature should have focused commits, pure-logic tests where applicable, an app compile check, and an on-device verification handoff.

## Documentation map

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — branch, test, commit, coordination, and handoff workflow.
- [`SECURITY.md`](SECURITY.md) — secret handling, public-repository rules, and device safety.
- [`CHANGELOG.md`](CHANGELOG.md) — feature history, validation evidence, and release handoffs.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — runtime data flow and module ownership.
- [`docs/DATA_CONTRACT.md`](docs/DATA_CONTRACT.md) — shared data/bus contract.
- [`tools/provision/README.md`](tools/provision/README.md) — PC-side provisioning.
- [`devices/stratus-c8/HARDWARE_AUDIT.md`](devices/stratus-c8/HARDWARE_AUDIT.md) — verified hardware facts.
- [`os/PERMANENT_INSTALL.md`](os/PERMANENT_INSTALL.md) — permanent-install notes and warnings.
- [`coord/REQUESTS.md`](coord/REQUESTS.md) — active requests and device findings.

## License and third-party code

Check the repository history and individual directories for applicable licensing before redistributing code, APKs, OS images, or third-party assets. SynapseOS is designed for Mason's personal smart-home node; this README does not grant permission to redistribute proprietary Home Assistant configuration or device data.
