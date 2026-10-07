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
_Last updated: 2026-10-07 13:20 UTC (BRAIN)_
- **Done:** research report; coordination system; merged everything to `main` (Mason OK'd).
- **Now:** building **Synapse Core v0.1** (Android app: kiosk launcher + ambient mode + node sensors → HA + local control API) with a GitHub Actions build. Cloud can't reach dl.google.com, so APKs are built in CI and committed to `builds/`.
- **Next:** provisioning script (R-005), HA package YAML, then R-101/102/103 and the v6 image design (needs R-004).
- **Blocked on:** nothing.
