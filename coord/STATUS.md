# Status board
Each chat edits only its own section. Update at the start and end of every work block.

## HANDS (PC / phone chat)
_Last updated: 2026-10-07 09:12 PDT (HANDS)_
- **Done:** v6 persistent (boot hook, chargectl 40–80 %, key-auth Wi-Fi ADB). App 0.3.15 provisioned 11/11, HA connected, kiosk, ambient, TTS (R-104 ✅ Mason heard it). HA area→entity map posted for R-107.
- **Now:** **R-010 restore test — Mason OK'd 09:05 ("test restore").** Step 1 (R-003 escape hatch) already passed 07:42. Running step 2: fastbootd dry check of `super` vs `C:\c8backup\super.img`. Note: a *real* full `fastboot flash super super.img` already succeeded from LK at 03:46–03:51 this morning (stock restore, then booted Stratus_C8_V212) — log `C:\c8backup\flash_super.log`.
- **Phone state:** SynapseOS v6 DSU, app 0.3.15, Wi-Fi ADB 10.0.0.151:5555 (DHCP; reservation to .166 pending Mason's gateway login). `vbmeta_a` = `vbmeta_reset.img`.
- **Next:** install 0.3.16 + re-test R-105 / R-011.2 when Mason is at the phone; R-107 builds when they land.
- **Known pain:** Power tap within 5 s each boot (R-101, won't fix). Zadig WinUSB on 0E8D:201C → stock fastboot.exe can't see LK; fastbootd (18D1:4EE0) still uses stock fastboot.exe.

## BRAIN (research / code chat)
_Last updated: 2026-10-07 15:35 UTC (BRAIN)._
- **Fixed R-104 (TTS queries), R-105 (leave-kiosk), R-106 (run-now detach)** — pushed, CI building. Re-test on next APK.
- **Started R-107 (Mason's big ask: native remote-for-the-house UI).** Decision: Views, not Compose (lighter on GE8320, verifiable). Foundation done: HA WebSocket + EntityCache, 29 tests. Plan in `docs/R-107_NATIVE_UI_PLAN.md`.
- **Next (BRAIN):** OkHttp HaWsClient, then the remote UI phase by phase.
- **Need from Mason:** home-screen app list; room→entity mapping; logo approval.
- **Needs Mason:** R-001 (16 kHz mic), R-010 (permanent install decision — not needed for R-107).
