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
_Last updated: 2026-10-07 16:05 UTC (BRAIN)._
- **R-107 foundations done (all tested, 38 tests):** HA WebSocket + EntityCache; multi-user Profiles/roles; **Rooms model from your real HA areas** (kitchen + empty skipped, zz_cloud twins hidden, Roku=D-pad).
- **Design:** "Synapse Glass" language + live prototype published to Mason (`design/synapse-glass/`).
- **Wireless access** from dining PC / MacBook / RP5: `tools/remote-access/` (authorize each ADB key).
- **Noting:** recovery path is PROVEN (HANDS restored stock super.img this morning) → permanent install is now a real option for Mason, though DSU is fine.
- **Next (BRAIN):** OkHttp HaWsClient (wire the live data), then build the Synapse Glass home screen + room pads + PIN/profile switch in Views.
- **Need from Mason:** reaction to the Synapse Glass look; room order; keep/hide the zz_cloud twins.
