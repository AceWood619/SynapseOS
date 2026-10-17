# Findings: quick facts each chat must know
Newest first. One or two lines each, with a link to the detail.

- ✅ **USB ADB to the C8 now needs a signed key.** `uadb.py` (rsa_keys=None) fails with DeviceAuthError. Use `tools/pc/kadb.py` (libusb + the PC's `~/.android/adbkey`). Stock `adb.exe` still can't see USB (WinUSB/Zadig). Phone Wi-Fi IP is currently **10.0.0.150**. (BRAIN, 2026-10-16)
- ⚠️ **Two different APKs both claimed 0.3.54 / versionCode 54** (one installed 10-14, sha `7b8e…`; CI's sha `7c4c…`). Always compare SHA-256 with `builds/synapse-core-latest.json`, never trust versionName. (BRAIN)
- ⚠️ **Don't run `adb start-server`/`kill-server` through the desktop shell tool** — the spawned daemon holds the pipe and the call hangs. Start it with `Start-Process -WindowStyle Hidden` + redirected output. (BRAIN)

- ✅ **HA `volume_up`/`volume_down` need VOLUME_SET *or* VOLUME_STEP; `volume_set` needs VOLUME_SET; `volume_mute` needs VOLUME_MUTE** (HA core `media_player/services.py`, read 2026-10-15). Gate media keys only through `MediaCaps`; never re-check the bits per screen. (BRAIN)
- ✅ **HA persistent notifications are not entities (since 2023.6).** Use WS `persistent_notification/subscribe` (first event `current`, then `added`/`updated`/`removed`, payload in `event.notifications`) and service `persistent_notification.dismiss` with `notification_id`, no target. (BRAIN → `SystemNotices.kt`)
- ⚠️ **Maven Central 429s from the BRAIN container.** Local builds use a user-level Gradle init script that rewrites Central to the Google mirror (`~/.gradle/init.d/mirror.gradle`, not in the repo). CI is unaffected. (BRAIN)

- 🐛→✅ **CI was publishing a STALE APK since run 5** (builds/ stuck at 0.1.5). The publish step committed then failed `git pull --rebase` on build-tree noise and swallowed the error. Fixed: hard-reset to origin/main, re-lay APK, push with loud retries. builds/ is now 0.3.x. HANDS: always check `builds/synapse-core-latest.json` version before installing. (BRAIN)
- ✅ **End-to-end JVM simulation passes** (an evening, minute-by-minute): presence holds on BLE+activity, screen dims when idle, no HA flooding (battery ~1 post/min), state resyncs after a 3-min HA outage. 25 unit tests. Catches integration bugs before the phone. (BRAIN → `SimulationTest`)

- ⚠️ **Overnight rule: no reboots while Mason sleeps.** Every boot shows the dm-verity screen and needs a Power tap within 5 s, or the phone powers off. (BRAIN)
- ✅ **Charge limiter runs on the phone:** the cgroup escape moved the PID, and hold wrote `0 1` at 100 %. Drain vs flat is still being measured (R-006). (HANDS, 06:37 PT)
- ⚠️ **Windows checkout = CRLF, which breaks phone shell scripts.** Fixed with `.gitattributes` (LF). Re-check-out `*.sh` on the PC. (HANDS found, BRAIN fixed)
- ⚠️ **Android hides iBeacons from `neverForLocation` BLE scans.** Synapse asks for location instead, and provision turns on location services. (BRAIN)

- ✅ **Synapse Core APK is built by GitHub Actions** (the cloud can't reach dl.google.com) and committed to `builds/`. `git pull` = newest APK. (BRAIN)
- ✅ **TrebleDroid `phh-on-boot.sh` runs as root (permissive `phhsu_daemon`) after every boot.** Its VNDK-27 block is dead code here, so v6 swaps it for a hook into `/data/adb/synapse/boot.sh`. (BRAIN → `os/v6`)
- ⚠️ **init kills a oneshot service's whole process group when it exits** (vendor API ≥ R). Long-running jobs started from the hook must leave the cgroup; `boot.sh` does this. (BRAIN)
- ✅ **HANDS: `/proc/mtk_battery_cmd/current_cmd` exists** (`0 0` = charging). The charge limiter writes `0 1` to hold. (HANDS R-002)

- ✅ **TrustKernel ignores the vbmeta digest.** Re-signed vbmeta boots with `/data` and keystore intact. The permanent-install path is open again. (HANDS → `devices/stratus-c8/BOOT_AND_INSTALL_NOTES.md`)
- ✅ **The dm-verity warning is a plain seccfg flag, not keyed to the vbmeta digest.** Re-signing doesn't clear it. (HANDS)
- ✅ **DSU userdata max ≈ 7.97 GB** (gsid keeps 8.8 GB free on stock). (HANDS)
- ⚠️ **ADB TCP port resets on reboot.** Run `uroot.py` after each boot until R-102 lands. (HANDS)
- ✅ **Mic + speaker work at 44.1 kHz.** 8 kHz silent, 16 kHz untested (see R-001). (HANDS)
- ✅ **HA Companion *minimal* (no-Google) includes on-device wake word** (microWakeWord, shared source set). (BRAIN → research report §3)
- ⚠️ **Ava-Pro is CC BY-NC-ND: use it, don't fork or ship it.** brownard/Ava is Apache-2.0 and forkable. (BRAIN)
- ✅ **SherpaTTS (F-Droid) fills the missing TTS engine.** Needs Android 10+. (BRAIN)
