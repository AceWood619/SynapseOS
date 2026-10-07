# MANUS onboarding: start here (written by HANDS, 2026-10-07 13:10 PDT)
Mason added you to carry the **app workload** while BRAIN is offline (back ~19:20 PDT). Welcome. Read this, then `coord/PROTOCOL.md`, `coord/STATUS.md`, and the last ~10 entries of `coord/REQUESTS.md`.

## What SynapseOS is (60-second version)
- An old **Cloud Mobile Stratus C8** phone (MT6765, 720×1600 @ 320 dpi → **360×800 dp**, PowerVR GE8320, so keep it light: no heavy blur or animation loops) runs a patched **LineageOS 21 GSI** as a persistent DSU ("SynapseOS v6").
- Our app **Synapse Core** (`android/`, package `com.acewood.synapse.core`) is the home screen and kiosk (device owner + lock task). It is a **"remote for the house"** for Mason's Home Assistant (HA). Look: the **Synapse Glass** design language (`design/synapse-glass/`, `GlassUi.kt`), dark night ground, neon blue/indigo/violet/mint.
- Data: live HA over WebSocket (`HaWsClient` → `HaRepository` → `EntityCache`). Rooms come from HA areas (`HaRegistry`, `Rooms`, `RoomOrder`). Pure logic lives in `android/core-logic` (unit tested); views live in `android/app`.

## How code reaches the phone
1. Push to `main` (paths `android/**`). CI (`.github/workflows/android.yml`) runs `:core-logic:test` and `:app:assembleRelease`, then commits `builds/synapse-core-latest.apk` + `.json` (version auto-bumps).
2. **HANDS** (the chat on Mason's PC) installs it on the phone, re-asserts kiosk, reads the screen with uiautomator, and posts findings in `REQUESTS.md`. You can't reach the phone; HANDS is your hands.
3. Without the Android SDK you can still type-check: `cd android/compile-check && ../gradlew :appmod:compileKotlin`. Please run that plus `cd android && ./gradlew :core-logic:test` before pushing. A red CI build blocks everyone.

## Current state (app 0.3.31 on the phone; 3562ea9 home v2 is building)
✅ Glass home: greeting "Good morning, Mason", weather chip, now-playing, **house modes** (Away/Sleep/Movie/Quiet/Guest/Party/Game → `input_boolean.*`), 7 smart-ordered **room channels**, scenes, **house status pills**, **app dock**, console (Home · Apps · mic · Jarvis · All off).
✅ Room pad (`RoomPadView`): lights (toggle, brightness, colour swatches, warm↔cool), media + volume + Roku D-pad, climate, fans, covers, locks, switches, sensors.
✅ New in 3562ea9 (HANDS): touch-down press feedback (`View.tap {}`), optimistic toggles, coalesced refresh, `AppDrawerView` + `AppCatalog` (kiosk-allowed apps, admin items PIN-gated), `JarvisView` (text chat with HA Assist via `conversation/process`), `SensorsView` (every sensor, grouped).
✅ HA side: dashboard **"Synapse Sensors"** (`/synapse-sensors`) built by `tools/ha/build_sensor_dashboard.py`.

## Mason's open asks: please take these, top first
1. **Profiles + PIN lock screen.** The model already exists in `core-logic/Profiles.kt` (admin/user/child/guest, hashed PINs, per-profile layout). Build the Glass lock/profile-picker screen and apply per-profile rooms/apps. **Only Mason is admin.**
2. **Glass wallpaper + visual polish** to match `design/synapse-glass/synapse-glass.html` (one real blur max, gated by a frame-time check).
3. **Room-pad polish** (R-117): friendly names ("Lr Lamp" → "Lamp"; strip the room prefix), move Ivy alert/ding/motion switches into a collapsed "Settings" section, hide `*_listening_light`. **Swipe ‹ › between rooms** like changing channels.
4. **Intercom/announce** button: `input_select.intercom_room` + `script.intercom_send` (look up the message input_text in HA states via HANDS if you need the id).
5. **Timers/alarms** on the home (HA `timer.*`, or open Clock), plus a media **now-playing card** with art (`entity_picture`) and transport.
6. After any app update, the app should **re-assert itself as home and re-pin lock task** by itself (R-112).

## Rules (Mason's, non-negotiable)
- **Never commit secrets** (HA token, API keys, PINs, Wi-Fi SSID). **The repo is PUBLIC.** No screenshots of Mason's home, no MACs, no device IDs, no shelter/work data.
- **Jarvis and automations must never touch the master bedroom** TV/lights on their own (manual controls in the remote are fine). No location/commute announcements. Work stays out of the house.
- Flag uncertainty; don't state guesses as facts. Small commits; `git pull --rebase` before you push; never force-push.
- Claim files in your `STATUS.md` section before editing them. HANDS just edited `HomeView`, `MainActivity`, `RoomPadView`, `GlassUi`, `HaRepository`, `HaWsClient`, `Kiosk`, `AppCatalog`, `AppDrawerView`, `JarvisView`, `SensorsView`. They're all yours now, but pull first.
- Phone, PC, LAN and flashing are **HANDS-only**. Ask through `REQUESTS.md` (`MANUS → HANDS`).

## Add yourself
Add a `## MANUS` section to `coord/STATUS.md` (what you're on, files you're touching, what you need from HANDS).
