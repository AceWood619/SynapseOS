# Changelog

All notable SynapseOS changes are recorded here. Device verification is separate from local build verification and is attributed to HANDS in `coord/REQUESTS.md`.

## Unreleased — R-124 Part 3

### Added

- Admin-gated **Panel settings** editor inside Synapse Settings.
- Local persistence for idle timeout, ambient brightness, overnight night-dim schedule, night brightness, touch haptics, and Jarvis speak-replies default.
- Pure-Kotlin validation for ranges and `HH:MM` schedules, including schedules crossing midnight.
- Live reload through `NodeBus.CONFIG_CHANGED`; ambient brightness is refreshed periodically while ambient mode is visible.

### Safety and compatibility

- Settings are local non-secret preferences; HA tokens and provisioned config are not copied into the panel-settings store.
- Master-bedroom TV/lights behavior is unchanged and remains manual-only.
- Existing defaults preserve the previous behavior: 120-second idle timeout, 2% ambient brightness, haptics enabled, and spoken replies enabled.
- Device installation and on-device verification remain a HANDS task.

### Verification

- `cd android && ./gradlew :core-logic:test`
- `cd android/compile-check && ../gradlew :appmod:compileKotlin`
- `git diff --check`

## R-124 Part 2

- Added HA `weather.get_forecasts` return-response framing and normalized forecast parsing.
- Added full Glass weather view and ambient hourly forecast strip.
- Commit: `157a211`; local tests and compile check passed.

## R-124 Part 1

- Added HA-driven Lighting FX, Ivy apply, Holiday mode selection, and guarded missing-entity behavior.

## R-123

- Replaced unavailable Android speech recognition with HA Assist pipeline microphone streaming using 16 kHz mono PCM, VAD, and regression-tested framing.

## R-122

- Shipped room-channel navigation, friendly labels, now-playing/audio, intercom, timers, ambient v2, and profile administration in focused increments.
