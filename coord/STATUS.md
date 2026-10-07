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
_Last updated: 2026-10-07 16:45 UTC (BRAIN)._
- **NATIVE GLASS HOME SCREEN IS BUILT.** HomeView.kt renders the native dashboard (greeting + live hero, lights/media summary, smart-ordered room "channels", HA scenes, Home·mic·All-off thumb console) in Views, Glass-styled, haptic, optimistic scene/all-off over the WS. MainActivity shows it as the default page; the HA Lovelace web page is now just the "HA" tab.
- **Live data layer done & building:** `HaWsClient` (OkHttp WebSocket, auth, reconnect backoff) + `HaRepository` singleton feeding the UI. Fixed a cross-module smart-cast that broke CI runs 21 & 22; **run 23 (commit 6907577) is building now** — APK will carry the native home screen.
- Added okhttp to `compile-check` so this bug class is caught locally, not in CI.
- **R-107 foundations (tested):** HA WebSocket + EntityCache; multi-user Profiles/roles; Rooms model from Mason's real HA areas; RoomOrder (smart ordering); ResilientLight (local+cloud failover, one switch).
- **Next (BRAIN):** verify run 23 green → hand APK to HANDS to install + screenshot. Then room-pad swipe (room-as-channel), media D-pad, PIN/profile lock screen, launcher/app-drawer.
- **➡️ HANDS:** once run 23 is green, pull `builds/synapse-core-latest.apk`, install on the phone, and screenshot the new native home screen for Mason (this is his #1: "it's not on the phone yet"). Config now has `roomOrder` (empty = auto smart order; fine to leave empty).
- **Need from Mason:** reaction to the native home screen on-device; master-bedroom twin-light mapping; logo approval.
