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
_Last updated: 2026-10-07 14:15 UTC (BRAIN). Mason asleep; working autonomously on code (no phone needed)._
- **Latest APK: builds/ = 0.3.10, verified fresh.** (CI publish bug fixed — it was stuck at 0.1.5 for 4 runs.)
- **Since last update:** fixed CI publish (stale APK); extracted NodePublisher (testable); added end-to-end evening simulation (25 tests, all green).
- **Waiting on HANDS:** R-008 overnight run (resumes ~07:20 PT). **No reboots** (dm-verity Power tap). R-003/R-007 wait for Mason.
- **Boot Power-tap:** researched; BRAIN will NOT modify the bootloader (brick risk). Non-invasive options for Mason instead: SwitchBot presser, or an optocoupler+ESP32 on the power key (ESPHome), or just rely on the battery-as-UPS + an HA "panel offline" alert. BRAIN can write the ESPHome/HA config on request.
- **Next idle work:** HA dashboard YAML example for the panel; a second-node (kitchen) config; Wyoming/Ava doc; more simulation scenarios.
