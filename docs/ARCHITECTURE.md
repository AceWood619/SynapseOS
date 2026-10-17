# SynapseOS architecture

This document describes the current boundaries for the native Synapse Core app. It is intentionally implementation-oriented so another contributor can continue work without guessing.

## Runtime flow

```text
HA WebSocket
  auth → state_changed / service responses / registry responses / Assist events
        ↓
HaWsClient (Android transport)
        ↓
HaRepository (connection, refresh, service helpers, forecast/media discovery)
        ↓
EntityCache + registry/room models
        ↓
Native Views (Home, Room Pad, Audio, Jarvis, Ambient, Weather, Settings)
        ↓
Optimistic service call → HA confirmation → cache update
```

The phone also has a separate node-data path:

```text
Android sensors / camera / BLE / presence fusion
        ↓
NodeService + PublishPolicy
        ↓
HA REST state publishing and optional local control API
```

## Module responsibilities

### `android/core-logic`

Pure Kotlin only. This is the preferred home for:

- JSON and Home Assistant protocol framing;
- state normalization and friendly-name rules;
- room/channel construction and filtering;
- profiles, PIN hashing, access policy, and layout rules;
- media/timer/weather models;
- presence fusion, VAD, snapshot policy, and other deterministic state machines.

Every bug fix or new parser/policy should have a focused unit test here when practical.

### `android/app`

Android Views and device integration:

- `MainActivity` owns navigation, lifecycle, ambient mode, and top-level overlays.
- `HaWsClient` and `HaRepository` bridge the pure protocol/data layer to OkHttp and Android lifecycle behavior.
- `HomeView`, `RoomPadView`, `AmbientView`, `WeatherView`, `AudioView`, Jarvis, settings, and diagnostics render the Glass UI.
- Kiosk/device-owner and companion behavior must remain explicit and testable.
- Sensor and audio capture must respect Android permissions, device-owner provisioning, and the GE8320 performance budget.

## Home Assistant integration rules

1. Read live state/attributes and registries before exposing controls.
2. Use `friendly_name` for display, but retain exact entity IDs for calls and diagnostics.
3. Skip unavailable/disabled/diagnostic entities unless a dedicated diagnostic screen is showing them.
4. For service calls that need a helper entity, discover the helper from HA state/attributes; do not hardcode an unverified `input_text` or script companion.
5. Album art is fetched from the configured HA origin with the configured bearer token and cached; tokens must never enter logs or URLs unnecessarily.
6. `weather.get_forecasts` uses HA's `return_response` call-service framing and must degrade gracefully when a weather entity or forecast type is unavailable.
7. Service calls should be optimistic only for reversible, ordinary controls and must reconcile with a later HA state update.

## Navigation and safety

The bottom console is a remote control, not a generic Android taskbar:

- **HOME** returns to the top-level house overview.
- **ROOMS** opens room/channel controls.
- **MIC** opens Jarvis push-to-talk.
- **AUDIO** opens speaker/media output controls.
- **MORE** opens the app drawer, diagnostics, settings, and secondary tools.
- **ALL OFF** remains an explicit safety action and should not be silently triggered by navigation or reconnect logic.

The master bedroom TV/lights are manual-only. Automatic discovery may display them in a diagnostic/manual control context, but background actions must not target them.

## Performance budget

The target is 360×800 dp on a PowerVR GE8320:

- favor Android Views and simple drawables;
- use one real hero blur at most, gated by observed frame time;
- pause the neuron animation in ambient/off-screen states;
- avoid rebuilding the whole home on every entity update;
- keep touch targets large and immediate;
- cache album art and forecast data with bounded refreshes;
- preserve the last good state for offline display without enabling controls.

## Verification layers

| Layer | Command/evidence |
|---|---|
| Pure logic | `cd android && ./gradlew :core-logic:test` |
| Cross-module Android type check | `cd android/compile-check && ../gradlew :appmod:compileKotlin` |
| Full APK | `cd android && ./gradlew :core-logic:test :app:assembleRelease` with SDK |
| CI artifact | `builds/synapse-core-latest.apk` and matching `.json` |
| On-device behavior | HANDS installs the CI artifact and reports UI/ADB/HA evidence in `coord/REQUESTS.md` |

A local compile is not an on-device verification. State the distinction in every handoff.
