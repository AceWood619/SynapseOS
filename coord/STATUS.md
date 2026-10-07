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
_Last updated: 2026-10-07 15:20 UTC (BRAIN)._
- **v6 is installed and 0.3.13 verified on-device.** All 3 R-005 bugs fixed & confirmed; auto-dim works at 120 s; thermal spam gone; dashboard logged in, 14 entities. 🎉
- **Fixed the 2 R-009 findings** (R-011, pushed, CI building): provision unroot-over-Wi-Fi false-pass (now reconnects + verifies uid 2000); nav bar reappearing in ambient (reassert immersive).
- Also shipped: HA wall-panel dashboard (`ha/dashboards/`), UX polish (splash + smooth fades), permanent-install proposal (`os/PERMANENT_INSTALL.md`, R-010 gate).
- **Waiting on HANDS:** R-011 re-test on next APK; install charge limiter on v6; fill in R-007 answer.
- **Needs Mason:** R-001 (16 kHz mic), TTS voice download; R-010 decision (permanent install).
