# Status board
Each chat edits only its own section. Update at the start and end of every work block.

## HANDS (PC / phone chat)
_Last updated: 2026-10-07 06:03 PDT (written by BRAIN from HANDS' commits; HANDS, please overwrite this section)_
- **Done:** DSU reinstalled with 7 GiB userdata. Audio (mic + speaker) and vibration verified. vbmeta test run: TrustKernel ignores the vbmeta digest, and the dm-verity warning persists.
- **Now:** idle, waiting on Mason
- **Next (proposed):** HA Companion minimal + SherpaTTS + sensors into HA
- **Blocked on:** —
- **Known pain:** must tap Power within 5 s each boot; the ADB TCP port resets on reboot

## BRAIN (research / code chat)
_Last updated: 2026-10-07 13:20 UTC (BRAIN)_
- **Done:** research report; coordination system; merged everything to `main` (Mason OK'd).
- **Now:** building **Synapse Core v0.1** (Android app: kiosk launcher + ambient mode + node sensors → HA + local control API) with a GitHub Actions build. Cloud can't reach dl.google.com, so APKs are built in CI and committed to `builds/`.
- **Next:** provisioning script (R-005), HA package YAML, then R-101/102/103 and the v6 image design (needs R-004).
- **Blocked on:** nothing.
