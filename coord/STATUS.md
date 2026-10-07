# Status board
Each chat edits only its own section. Update at the start and end of every work block.

## HANDS (PC / phone chat)
_Last updated: 2026-10-07 06:22 PDT (HANDS)_
- **Done:** DSU reinstalled with 7 GiB userdata. Audio (mic 44.1 kHz + speaker) and vibration verified. vbmeta test: TrustKernel ignores the vbmeta digest; the dm-verity warning persists. Reserved IP 10.0.0.166 on the Xfinity gateway. R-002 (charge files). R-004 (v5 pipeline → `os/v5`).
- **Now:** waiting for R-005 APK in `builds/`. R-001 (16 kHz mic) needs Mason to speak to the phone; I'll run it when he's available.
- **Phone state:** SynapseOS DSU booted; `vbmeta_a` = `vbmeta_reset.img` (re-signed, sha256 53c78754…), not factory. ADB over Wi-Fi at 10.0.0.166:5555 (re-enable after each boot via `tools/pc/uroot.py` + `ctl.restart adbd`).
- **Blocked on:** —
- **Known pain:** must tap Power within 5 s each boot; ADB TCP resets on reboot; USB adb/fastboot only through `tools/pc` (WinUSB/Zadig).

## BRAIN (research / code chat)
_Last updated: 2026-10-07 15:35 UTC (BRAIN)._
- **Fixed R-104 (TTS queries), R-105 (leave-kiosk), R-106 (run-now detach)** — pushed, CI building. Re-test on next APK.
- **Started R-107 (Mason's big ask: native remote-for-the-house UI).** Decision: Views, not Compose (lighter on GE8320, verifiable). Foundation done: HA WebSocket + EntityCache, 29 tests. Plan in `docs/R-107_NATIVE_UI_PLAN.md`.
- **Next (BRAIN):** OkHttp HaWsClient, then the remote UI phase by phase.
- **Need from Mason:** home-screen app list; room→entity mapping; logo approval.
- **Needs Mason:** R-001 (16 kHz mic), R-010 (permanent install decision — not needed for R-107).
