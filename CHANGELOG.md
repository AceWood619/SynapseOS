# Changelog

All notable SynapseOS changes are recorded here. Device verification is separate from local build verification and is attributed to HANDS in `coord/REQUESTS.md`.

## 0.3.56 → 0.3.58 — Redesign B ("Remote") — BRAIN, 2026-10-16 (installed on the C8 by Claude)

Mason picked mockup B from the SynapseOS Redesign canvas and asked Claude to keep going without approvals.

- **Remote home** (`RemoteHomeView`, `RemoteHome`): a swipeable room "channel" card, VOL rocker, D-pad + OK, mute/play/back, Lights/TV/Room keys, house modes, and a House · mic · More bar. The old home is now the **House** screen.
- **Fonts:** Sora + Manrope (OFL, subset, ~180 KB) on every screen. Icons are drawn in code (`GlassIcon`).
- **Ambient:** a true-black root that never moves, a content-only burn-in nudge, five-column forecast. Fixes Home showing through and clipped edges.
- **Room pad:** new header, pill switches, chip colour grid, drawn transport/D-pad icons. The offline strip is now a solid top banner.
- **From ChatGPT's local work (landed with its review fixes):** Intercom crash fix, first-run setup wizard (SAVE gated on a successful HA test of the same URL+token), Jarvis Command entry with an offline message.
- **Boot animation** previewed on the real phone via a temporary bind mount (unmounted after; stock file sha unchanged).

Verified on the device: 0.3.58 (sha `fa7d85b8…687f`) installed in place, Synapse stays the HOME app, no crash-buffer entries, and screenshots of Remote home, room pad and boot animation reviewed. Not yet verified: physical button presses (VOL/D-pad/lights) while the household was using the TVs.

## Unreleased — R-124 Part 6 polish + boot animation — BRAIN, 2026-10-15

### Added

- **Per-profile tile scale** applied per tile (rooms, scenes, dock), clamped 0.85–1.4, so the guest profile's 1.25 gives bigger thumb targets without cropping the 360 dp screen.
- **Synapse Glass wallpaper** on every full-screen Synapse view: night radial ground + faint static neuron constellation, rendered once into a shared bitmap (one blit per draw).
- **Boot animation**: `os/bootanim/make_bootanim.py` + committed `bootanimation.zip` (~2 MB, reproducible, self-verified format, seamless border). Install steps in `os/bootanim/README.md`.

### Decided not to ship

- **RenderEffect blur**: skipped. It can't be judged without the phone, and the orb plus wallpaper already give the depth. Revisit only with on-device frame data.

### Verification

- `:core-logic:test` 118/118, compile-check OK, branch CI run `38821322006` green.
- Not on the phone yet: this session has no route to the LAN.

## Unreleased — Media gate fix + R-124 Parts 4, 5, 6 (orb) — BRAIN, 2026-10-15

Picks up MANUS's last plan (MANUS out of credits). Branch `brain/r124-remote-v4`, merged to `main`.

### Fixed

- **Volume/mute keys disappearing.** Each screen checked `supported_features` itself and required VOLUME_STEP (1024). Home Assistant accepts `volume_up`/`volume_down` for VOLUME_SET *or* VOLUME_STEP (`media_player/services.py`), so most speakers lost working keys. New shared `MediaCaps` gate is used by the Home now-playing card, Room Pad, and Audio app. The slider now needs VOLUME_SET (step-only Rokus reject `volume_set`). Roku rooms fall back to `remote.send_command` VolumeUp/Down/Mute when the player lacks the bits.
- Audio app no longer shows MUTE on players that cannot mute.

### Added

- **Home now-playing card:** VOL − / MUTE / VOL + row.
- **R-124 Part 5 — offline + resilience:** last-known state snapshot in app-private storage (sanitized: no `entity_picture`, tokens, or `token=` URLs), loaded before HA connects so a reboot shows the house immediately; amber "Offline · reconnecting" strip (tap = retry now); HA screens fade to 55% while offline; controls pressed offline are refused with on-screen feedback instead of silently dropped; reconnect skips backoff when Wi-Fi returns or the screen resumes; pending Jarvis/forecast requests fail cleanly on drop.
- **R-124 Part 4 — system notices (admin):** HA persistent notifications via `persistent_notification/subscribe` with DISMISS; pending `update.*` entities listed (no install button); amber count badge on the profile pill for admin profiles; admin-only "System notices" in More.
- **R-124 Part 6 (orb slice):** animated neuron hero orb, ≈12 fps, paused in ambient/off-screen, frozen by a FrameMetrics budget if >20% of frames exceed 20 ms.

### Not done yet

- R-124 Part 6 remainder: RenderEffect blur (none added), Glass wallpaper, per-tile profile scale. Boot animation bonus.

### Verification

- `:core-logic:test` 114/114 (29 new), `compile-check :appmod:compileKotlin` OK.
- GitHub Actions run `38814683875` (workflow_dispatch on the branch): unit tests + release APK **passed**.
- On-device behavior: **not verified** — see R-127 for HANDS.

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

CI run `38808495526` passed on merged commit `c25ce9c` and published Synapse Core **0.3.50** with SHA-256 `f4dadf7ed542540f301e4a42eb4e7eead6bafcec3929c0d20ae0b32a2c4fd7de`. HANDS still owns installation and on-device verification. CI reported only action-runtime/setup-java and future `ubuntu-latest` image migration warnings.

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
