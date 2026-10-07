# Findings: quick facts each chat must know
Newest first. One or two lines each, with a link to the detail.

- ✅ **TrustKernel ignores the vbmeta digest.** Re-signed vbmeta boots with `/data` and keystore intact. The permanent-install path is open again. (HANDS → `devices/stratus-c8/BOOT_AND_INSTALL_NOTES.md`)
- ✅ **The dm-verity warning is a plain seccfg flag, not keyed to the vbmeta digest.** Re-signing doesn't clear it. (HANDS)
- ✅ **DSU userdata max ≈ 7.97 GB** (gsid keeps 8.8 GB free on stock). (HANDS)
- ⚠️ **ADB TCP port resets on reboot.** Run `uroot.py` after each boot until R-102 lands. (HANDS)
- ✅ **Mic + speaker work at 44.1 kHz.** 8 kHz silent, 16 kHz untested (see R-001). (HANDS)
- ✅ **HA Companion *minimal* (no-Google) includes on-device wake word** (microWakeWord, shared source set). (BRAIN → research report §3)
- ⚠️ **Ava-Pro is CC BY-NC-ND: use it, don't fork or ship it.** brownard/Ava is Apache-2.0 and forkable. (BRAIN)
- ✅ **SherpaTTS (F-Droid) fills the missing TTS engine.** Needs Android 10+. (BRAIN)
