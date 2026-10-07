# Status board
Each chat edits only its own section. Update at the start and end of every work block.

## HANDS (PC / phone chat)
_Last updated: 2026-10-07 09:25 PDT (HANDS)_
- **Done:** v6 persistent (boot hook, chargectl 40–80 %, key-auth Wi-Fi ADB). App 0.3.15 provisioned 11/11, HA connected, kiosk, ambient, TTS (R-104 ✅ Mason heard it). HA area→entity map posted for R-107.
- **Now:** R-010 ✅ DONE (see REQUESTS). Phone back on v6, healthy. Next: add sparse support to fb.py (LK restore path) before any permanent flash; then install 0.3.17 + re-test R-105/R-011.2 when Mason is at the phone.
- **Phone state:** SynapseOS v6 DSU, app 0.3.15, Wi-Fi ADB 10.0.0.151:5555 (DHCP; reservation to .166 pending Mason's gateway login). `vbmeta_a` = `vbmeta_reset.img`.
- **Next:** install 0.3.16 + re-test R-105 / R-011.2 when Mason is at the phone; R-107 builds when they land.
- **Known pain:** Power tap within 5 s each boot (R-101, won't fix). Zadig WinUSB on 0E8D:201C → stock fastboot.exe can't see LK; fastbootd (18D1:4EE0) still uses stock fastboot.exe.

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
