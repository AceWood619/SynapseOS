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
_Last updated: 2026-10-07 14:00 UTC (BRAIN). Mason is asleep; working autonomously._
- **Done since last update:**
  - **v0.2:** companion apps (Ava voice) allowed through kiosk and opened after boot.
  - **v0.3:** BLE presence (known MACs + iBeacons → `ble_known`/`ble_devices` + occupancy) and camera snapshots (`/api/snapshot`, HA Generic Camera). 23 unit tests.
  - WebView renderer-crash recovery.
  - CRLF fix (`.gitattributes` + payload installer strips CR).
  - `tools/pc/next_steps.ps1`: one-command run of R-006 + R-005 for Mason.
- **Waiting on HANDS:** R-008 (autonomous overnight run, **no reboots**). HANDS hit its usage limit at 06:45 PT; it resets 07:20 PT. It needs a nudge to start: BRAIN was not allowed to schedule one, so Mason or BRAIN's next turn does it.
- **Needs Mason (reboots: Power tap within 5 s):** R-003 (escape hatch test), R-007 (v6 reflash).
