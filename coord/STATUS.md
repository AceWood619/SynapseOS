# Status board
Each chat edits only its own section. Update at the start and end of every work block.

## HANDS (PC / phone chat)
_Last updated: 2026-10-07 13:12 PDT (HANDS)_
- **🤝 MANUS joined (13:05) to carry the app workload. HANDS is handing app code to MANUS** (see `coord/MANUS_ONBOARDING.md`) and goes back to device/HA work: installing CI builds, on-device checks, HA dashboard. ~~HANDS is editing the app (android/) while BRAIN is offline (until ~19:20 PDT, per Mason).** Mason (12:53): "still missing a lot of features… don't stop working… ensure all sensors get integrated into HA and Synapse and part of the remote and HA dashboard." Mason's feedback on 0.3.30: lamp toggles ✅, Roku/volume ✅, **buttons feel slow**.
- **HANDS app work in progress (files touched):** `GlassUi.kt` (press feedback helper), `HomeView.kt` (modes row, weather, now-playing, house status, apps dock), new `AppDrawerView.kt`, new `SensorsView.kt`, `MainActivity.kt` (debounced refresh, new views), `RoomPadView.kt` (touch-down feedback, optimistic toggles), `Kiosk.kt` (lock-task allowlist for dock apps + HOME feature), `HaRepository.kt` (optimistic state). **BRAIN/MANUS: please pull and review before editing these files.**~~ Pushed as 3562ea9.
- ✅ HA dashboard **Synapse Sensors** (`/synapse-sensors`): 274 live sensors (panel 14 · Kids 11 · Living 6 · Master 15 · house kinds). Synapse node entities are REST-pushed (no registry entry), so they can't be put in an HA area; grouped under "panel" instead.
- **Done today:** R-010 restore gate ✅; R-111 vbmeta flags=3 ❌ (breaks /data, reverted); R-115 oem cdms ❌ unsupported; 0.3.30 installed; light pairing data posted.
- **Phone:** v6 DSU, app 0.3.30, Wi-Fi ADB 10.0.0.151:5555. MacBook ADB key ✅, Pi key pending a reboot.

## BRAIN (research / code chat)
_Last updated: 2026-10-07 17:10 UTC (BRAIN)._
- **FULL R-107 UI BATCH SHIPPED** (one APK, see latest `builds/synapse-core-latest.json`):
  - **Native Glass home** — greeting + live hero, lights/media summary, smart-ordered room channels, scenes, Home·mic·All-off console. (HANDS confirmed it renders, 0.3.23.)
  - **R-113 #1 fixed** — greeting uses `owner_name` ("Good morning, Mason") not the node id. **HANDS: re-push config with `provision.py … --owner-name Mason` (config-only).**
  - **R-113 #2 fixed** — room channels now populate from HA's area/entity/device registries fetched live over the WS (`HaRegistry`). Kitchen auto-excluded. No hand map, no re-provision.
  - **Room pads** — tap a channel → `RoomPadView`: light keys (tap=toggle, −/+=dim), media transport+volume, **Roku D-pad**, extra toggles. Back = home.
  - **HA round-trip** — "HA" chip on home opens Lovelace; floating "⌂ SYNAPSE" button over the webview returns home (HA no longer a dead end).
- **Data layer:** `HaWsClient` (OkHttp WS, auth, reconnect, registry fetch) + `HaRepository` singleton. okhttp added to `compile-check`.
- **Tested:** ~59 core-logic unit tests green (HaRegistry, RoomControl, MediaRemote, owner_name, …); full app type-checks locally every push.
- **➡️ HANDS:** install the latest `builds/synapse-core-latest.apk` (check the json version/sha first). After update, re-assert Synapse as home (R-112 note). Then the batch is ready for Mason to use on-device.
- **⏸️ BRAIN holding** for Mason's on-device reaction before the next stack: PIN/profile lock screen (multi-user), app drawer (Camera/Settings/Jarvis/browser/music), Glass wallpaper.
- **Need from Mason:** reaction to the full batch; **master-bedroom light mapping** (several cloud+local bulbs can't be auto-paired safely — which local pairs with which cloud?); logo approval.

## MANUS (app features; working from its own sandbox)
_Last updated: 2026-10-07 14:35 PDT (MANUS; posted by HANDS because MANUS's GitHub push gets 403)_
- **Done:** R-119 fixes (landed via HANDS as bf2c8b2). Profiles + PIN first slice: `ProfileStore` (hashed profiles only), Glass `ProfileLockView`, startup gating in `MainActivity` (applied by HANDS from MANUS's patch ec342b7).
- **Done locally:** profile-aware app drawer/dock filtering from `layout.home_apps`, room-channel filtering from `layout.rooms`, and per-profile tile scale from `layout.tile_scale`; admin remains unrestricted. Tests and compile check pass.
- **Done locally:** customized Glass `SettingsActivity` with node/profile status and kiosk maintenance controls; added native in-kiosk `BrowserActivity` (HA home, address/search, back/forward/reload); added native `CameraActivity` (preview, capture, MediaStore save under Pictures/Synapse). App catalog now routes Browser, Camera, and Settings to Synapse surfaces. Tests and compile check pass.
- **R-122 Part 1 done locally:** Room Pad v3 has swipe/‹› room-channel navigation with `CH n · Room` headers, listening-light filtering, room-prefix-friendly labels, collapsed Ivy alert settings, and optimistic brightness/volume/climate/fan/cover slider attributes. Core tests and compile check pass.
- **R-122 Part 2 done locally:** Added a normalized now-playing model, HA Bearer-authenticated cached album-art loader, full Home Glass now-playing card with transport/volume controls, optimistic volume updates, room-aware TV REMOTE entry, and album art/app metadata in room media cards. Core tests and compile check pass.
- **R-122 Part 3 done locally:** Added runtime discovery of `input_select.intercom_room`, `script.intercom_send`, and the matching `input_text.*` message helper; added Glass Intercom screen with manual room selection, safe non-master default, canned messages, typed announcements, and exact HA service sequencing. Added home ANNOUNCE key and Jarvis quick action. Core tests and compile check pass.
- **R-122 Part 5 done locally:** Replaced the basic ambient clock with Glass `AmbientView` showing clock/date, weather, next active/paused timer, now-playing art/title, current profile, and a profile-picker button. Existing dimming and periodic burn-in-safe pixel shifting remain in `MainActivity`; ambient content refreshes from live HA state.
- **R-122 Part 6 done locally:** Added an admin-PIN-gated Profile Admin screen inside Synapse Settings with add/edit/delete, role selection, room/app checkboxes, validation that preserves at least one admin, and hashed-only PIN persistence. Blank PIN during edits preserves the existing hash; clear PINs never enter JSON or logs. Core tests and compile check pass.
- **R-122 Part 4 done locally:** Added a Glass home timer card for all `timer.*` entities with live remaining display, start/pause/cancel controls, 5/10/15/30-minute quick durations, and a local spoken fallback when HA has no timer entity. Core tests and compile check pass.
- **Audio/Jarvis slice done locally:** Deep room-pad media controls now expose capability-aware volume down/up and mute/unmute keys. Added an Audio app listing every HA `media_player.*`, with volume, mute, transport, and discovered `source_list` output selection, plus a button to open the same HA media browser used by Music. Replaced the external voice-intent handoff with an in-app microphone that sends speech transcripts through HA `conversation/process`, preserving the existing text Jarvis agent and conversation context. Core tests and compile check pass.
- **Next:** package Part 4 for HANDS; all R-122 parts are now implemented locally.
- **Blocked on:** GitHub write access (403 even with a fine-grained token). Until it's fixed, MANUS shares `git format-patch` files and HANDS applies them.
- **Safety:** no PINs, tokens, screenshots or device data committed.
