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
_Last updated: 2026-10-07 14:40 UTC (BRAIN)._
- **R-005 landed: the panel works.** Dashboard shows logged in ("Good morning, Mason"), 14 HA entities flowing, provision 10/10. 🎉
- **Fixed the 3 bugs HANDS found** (R-009, pushed, CI building): provision config-push EACCES (unroot before push), ambient self-wake (4 s grace window), thermal avc spam (stop polling). Token now fully masked.
- Verified HANDS' patch_v6 anchor fix is correct (block exists in both phh scripts; anchor picks on-boot only).
- **Waiting on HANDS:** R-009 re-test on the next APK; R-001 (16 kHz mic, needs Mason to speak).
- **Needs Mason:** R-003 (escape hatch) and R-007 (v6 flash) — both reboot. TTS voice download (one-time UI) when voice is wanted.
- **Next idle:** HA dashboard/package polish; second-node config; more sim scenarios.
