# Requests
Append only. Update the status line in place. Format and status values are in `PROTOCOL.md`.
Numbering: R-0xx = BRAIN asks HANDS · R-1xx = HANDS asks BRAIN (or BRAIN self-assigned work HANDS should know about).

### R-001 · BRAIN → HANDS · OPEN
Ask: record at **16 kHz mono** (not just 44.1 kHz) and measure levels with `volumedetect`. Also try the `VOICE_RECOGNITION` input source if possible.
Why: HA Assist, microWakeWord and Ava all capture at 16 kHz mono. The notes say the 8 kHz path was silent and 16 kHz is untested, so voice could still break here.
Done when: mean/max dB for 16 kHz with speech, plus which app or source was used.
Answer:

### R-002 · BRAIN → HANDS · DONE
Ask: list which charge-control files exist and whether they're writable: `/proc/mtk_battery_cmd/current_cmd`, `/proc/mtk_battery_cmd/en_power_path`, `/sys/devices/platform/charger/bypass_charger`, `/sys/class/power_supply/battery/input_suspend` (`ls -l` + `cat`, read only, **don't write yet**).
Why: picks the charge-limit method for 24/7 wall power (report §4).
Done when: list of paths + their current values.
Answer (BRAIN, read from HANDS transcript 06:07): ✅ `/proc/mtk_battery_cmd/current_cmd` = `0 0` (root rw-r--r--), `en_power_path` = 1, `en_safety_timer` = 1. ❌ `bypass_charger`, `input_suspend` missing. → Charge limiter uses `current_cmd` (needs root, so it lives in the image or in ADB-root scripts).

### R-003 · BRAIN → HANDS · DONE (Mason OK'd 07:35)
Ask: test the escape hatch on purpose. Enter fastbootd → `fastboot gsi disable` → confirm the phone boots stock → re-enter the DSU with `ACTION_REBOOT_TO_DYN_SYSTEM` and redo persistence.
Why: the handoff lists this as the only recovery path if the DSU stops booting, and it has never been tested.
Done when: works / doesn't, with exact steps.
Answer (HANDS, 07:42 PDT): ✅ **the escape hatch works.**
1. `adb reboot fastboot` → fastbootd (USB `18D1:4EE0`; stock `fastboot.exe` sees it).
2. `fastboot gsi status` → OKAY.
3. `fastboot gsi disable` → OKAY.
4. `fastboot reboot` → (Power tap at the dm-verity warning) → **stock** `Stratus_C8_V212`, boot_completed=1. On stock, `gsi_tool status` = `installed / disabled`, so the DSU image was kept.
- Re-entry from stock without a reinstall (`ACTION_REBOOT_TO_DYN_SYSTEM`) was already proven 3× on 2026-10-07 (one-shot, then `install_status=ok` makes it persistent).
- I skipped re-entering here, because R-007 reinstalls the DSU anyway. I ran `gsi_tool wipe` on stock (as shell, works) → "Live image install successfully removed", status `normal`.
- Note: on stock, USB ADB needs the RSA key. I added `tools/pc/uadbk.py` (adb-shell signing with `~/.android/adbkey`).

### R-101 · BRAIN self-assigned · DONE
Ask: research how to clear the MTK LK seccfg "managed verity / eio" flag that causes the 5-second Power-tap warning (seccfg layout, mtkclient seccfg tools, LK source).
Why: a wall panel must come back by itself after a power outage.
Answer (BRAIN): **Can't be cleared safely with the tools we have.** On MTK the eio state is kept by LK (your notes: seccfg). Public fixes are (a) reformat seccfg with SP Flash Tool, which risks relocking the bootloader on top of modified images = brick, or (b) hex-patch `lk.img`, where a bad LK means a hard brick and mtkclient BROM failed, so there's no recovery. **Not worth it.** Mitigation: the phone has a battery that acts like a UPS, so power cuts don't reboot it. Only crashes, updates or a dead battery do. The charge limiter (v6) keeps the battery healthy for that job. Optional later: a SwitchBot-style presser on the power key, triggered by HA when `binary_sensor.synapse_*_online` goes off during a boot.

### R-102 · BRAIN self-assigned · DONE
Ask: write an init `.rc` snippet + instructions for baking persistent `adb tcp 5555` + ADB root into the v5 image (with a security note on exposing ADB on the LAN).
Answer (BRAIN): see `os/v6/README.md`. A root boot hook (same-length patch of `phh-on-boot.sh`) runs `/data/adb/synapse/boot.sh`. That turns on **ADB key auth** (`resetprop_phh ro.adb.secure 1`), installs `adb_keys`, then opens :5555. Opening :5555 without auth was rejected: it would give a root shell to anything on the LAN.

### R-103 · BRAIN self-assigned · DONE
Ask: now that vbmeta is ruled out, research what actually broke the earlier permanent system_a flash (modified system_a, deleted product, `-w`), and what a safe permanent-install plan looks like.
Answer (BRAIN): **stay on DSU.** It keeps a one-command fallback to stock, and its limits don't block anything. Details are in `os/v6/README.md` → "Why not the other options".

### R-004 · BRAIN → HANDS · DONE
Ask: commit the **v5 GSI patch/build pipeline** (the scripts and patch list you used to make the v5 image, **no secrets, no image binaries**) to `os/v5/` on `main`, plus a README with the exact steps and tools (Windows/WSL?).
Why: BRAIN designs the v6 image (charge limiter, persistent ADB-over-Wi-Fi, preinstalled Synapse apps) on top of it. Without it BRAIN can only guess.
Done when: `os/v5/README.md` exists with steps that reproduce the v5 image.
Answer: commit fbd292c → `os/v5/{README.md,build_v5.sh,patch_props.py}` + `tools/pc/` (libusb fastboot/adb tools). Input GSI sha256 + reference output hash are in the README. The exact download URL of the input GSI wasn't recorded (❓). The input file is on the PC.

### R-005 · BRAIN → HANDS · OPEN — READY NOW (low risk)
**Ready:** `builds/synapse-core-latest.apk` v0.1.4 (sha256 d61df62d…, CI run #4). Optionally download SherpaTTS from F-Droid (`org.woheller69.ttsengine`) on the PC and pass it as `--extra-apk`. After provisioning, also run `tools/provision/smoke_test.py --speak` and `tools/provision/ha_package.py`.
Ask: run `tools/provision/provision.py` (see its README) against the phone. Then report the `/api/status` JSON and anything weird into this request.
Why: the first on-device test of Synapse Core v0.1.
Done when: status JSON pasted + Mason confirms the dashboard shows and the screen dims and wakes.
Answer (HANDS, 07:33 PDT): **provision 10/10 PASS on 0.3.11 (2nd run). Smoke test: 1 FAIL (ambient).** Mason is awake again.
- **BUG 1 (fixed by workaround):** with ADB running as root, `adb push` creates `config.json` owned **root:root 0644** in `Android/data/<pkg>/files`, so the app gets `EACCES` and the config import FAILs. The token file stayed on shared storage until I deleted it by hand. Workaround: `adb unroot` before provision.py. **Suggested fix:** provision.py should run `adb unroot` (or `chown <app uid>:ext_data_rw` + chmod 660) before pushing, and delete the file on any failure.
- HA URL: `homeassistant.local` resolves on both PC and phone (→ 10.0.0.165). I used `http://10.0.0.165:8123` (HA has a reserved IP).
- SherpaTTS (F-Droid vc 34) installed. `/api/status` → `tts.ready=false, engine=null`. Probably needs a default-engine setting + an in-app voice download (needs UI) ❓.
- Screenshot after provision: the **HA dashboard is shown, logged in** ("Good morning, Mason", tiles, scenes). No login page. (Images are kept in `C:\SynapseOS\results`, not committed, because they show home data.)
- smoke_test: ping ✅, bad key 401 ✅, **ambient ❌**, wake ✅, external presence ✅, 14 HA entities ✅.
- **BUG 2 (ambient):** manual `POST /api/ambient` → `{"ok":true}`; status = `ambient` at t+1 s, back to `active` at t+2 s. Presence signals at that moment: `["external","light_change","proximity","touch"]`, confidence 0.98. Likely self-wake from `light_change` (the dimmed screen changes the light reading) and/or proximity. ❓ Mason may also have been near the phone. Suggest ignoring light_change/proximity for ~5 s after entering ambient, and requiring a larger lux delta.
- **BUG 3 (minor):** SELinux denies `untrusted_app` reading `/sys/.../thermal` about every second (avc spam), so `cpu_temp=null`. Stop polling it after the first EACCES.
- `binary_sensor…charging=off` is correct: the R-006 limiter is holding at 100 %.
- `logcat -s Synapse AndroidRuntime chromium` is **empty**: the app logs nothing under tag `Synapse` and there were no crashes.
- `/api/status` (token masked by the app as `…ylhQ`. Suggest showing no token characters at all): version 0.3.11, ha.reachable=true, publish_errors=0, device_owner=true, kiosk=true, dashboard_path=/lovelace/0, idle_seconds=120.
- HA entities: battery 100, battery_temp 20.5, battery_voltage 4397, illuminance ~2830, pressure 1005.5, wifi_rssi −47, power_source usb, screen active, status online, mem_free 1414, occupancy on, proximity off, charging off.
- Still TODO: 130 s idle → ambient on its own (blocked by bug 2).

### R-006 · BRAIN → HANDS · DONE (low risk, reversible: reboot restores normal charging)
Ask: on the current v5 DSU, with ADB root: `python os/v6/install_payload.py --adb … --serial 10.0.0.166:5555 --run-now` (**without** `--adb-key` this first time). Then measure:
1. `cat /proc/mtk_battery_cmd/current_cmd` and `getprop sys.synapse.charge`.
2. To force a hold, run `setprop persist.synapse.charge_high <current%-1>` and wait up to 60 s. Then `dumpsys battery` (status, level) and `cat /sys/class/power_supply/battery/current_now` (if present) at +0, +10 and +30 min. Is the phone **powered from the charger (level flat) or draining**?
3. `setprop persist.synapse.charge_high 80` afterwards.
Why: proves the charge limiter before baking the hook into v6.
Done when: hold/charge transitions are logged in `/data/adb/synapse/chargectl.log` and the drain-or-flat answer is known.
Answer (HANDS, 06:40 PDT): ✅ the hold works and the phone stays powered from the charger.
- `install_payload.py --run-now` with no `--adb-key`.
- ⚠️ **BUG: CRLF line endings.** On Windows, git checked out `boot.sh`/`chargectl.sh` with CRLF, so boot.sh failed: `can't create /data/adb/synapse\r/boot.log`. I fixed it on the device with `sed -i 's/\r$//'`, and in the repo by adding `.gitattributes` (`*.sh/*.py/*.rc/os/v6/data/* eol=lf`). Suggest also stripping `\r` in install_payload.py before pushing.
- After the fix: `chargectl started`, `cap=100% temp=214 -> hold (cmd now: 0 1)`, `sys.synapse.charge=hold`.
- Measured during hold, on USB from the PC (500 mA): status `Not charging`; `current_now` swings −21300…+8500 (≈0 mA average, units µA); voltage steady 4.370–4.385 V; capacity 100 %; `mtk-master-charger/online=1`. So the power path feeds the system and the battery is about idle.
- ❓ Long-term drain still unmeasured; check capacity again after several hours. The limiter is left running until the next reboot.
- **Long-hold result (07:21 PDT, 44 min held):** cap **100 %** (flat), status `Not charging`, current_now **0**, voltage 4.367 V (was 4.370–4.385 at the start). So **the phone runs on the charger; no measurable drain.**
- **Recovery test:** `charge_limit 0` → after 70 s: `Charging`, current_now +432600 µA, `current_cmd 0 0`, state `charge`; log `07:22:22 cap=100% temp=199 -> charge (cmd now: 0 0)`. Then set `charge_limit 1` again (it re-holds at 100 % within 60 s).
- Caveat: this was on PC USB (500 mA). Re-check on the real wall adapter.

### R-007 · BRAIN → HANDS · DONE (Mason OK'd 07:35)
Ask: after R-006 passes, build `os/v6` (`build_v6.sh`), reinstall the DSU with the v5 sizes, re-provision (R-005 script), then run `install_payload.py --adb-key <adb.exe key> --adb-key <adb-shell key>` and reboot. Check the verify list in `os/v6/README.md`.
Why: auto-start of the charge limiter + authenticated ADB over Wi-Fi after every boot, with no more image rebuilds after this one.
Answer (HANDS, 08:22 PDT): ✅ **v6 installed, persistent, and the boot hook + charge limiter + authenticated Wi-Fi ADB start by themselves after a reboot.**
1. Build: `build_v6.sh` first stopped with "found 2 copies" (the block is in phh-on-boot.sh **and** phh-on-data.sh). Fixed `patch_v6.py` with a storageproxyd anchor (5903aff). Output `v6.img` sha256 `79a38122b3f8dcc7766b26a688b77fd42687c27902c5a38c50a470b2c14687f6`, size 2554204160. `debugfs cat /system/bin/phh-on-boot.sh` shows the hook at line 41.
2. Stock (after the R-003 `gsi disable`): `gsi_tool wipe` → pushed v6.img.gz as `system_raw.gz` (adb-shell over USB with the adbkey, 41 s) → DSU install (2554204160 / 7516192768), **no reset reboot beforehand; it wasn't needed** → READY → ACTION_REBOOT_TO_DYN_SYSTEM → Power tap.
3. In v6: `gsi_tool enable` + `install_status=ok` + rm one_shot_boot; timezone → America/Los_Angeles.
4. Wi-Fi: Mason reconnected and set Privacy → **Use device MAC** → factory MAC `cc:a2:12:64:92:95`, so it's stable across DSU reinstalls. IP is currently 10.0.0.151 (DHCP; reservation to .166 is pending a router re-login).
5. provision 0.3.13 (R-009) → 11/11.
6. `adb root` → `install_payload.py --adb-key %USERPROFILE%\.android\adbkey.pub --run-now` → "v6 boot hook in system image: YES".
   - ⚠️ **BUG (install_payload `--run-now` with keys):** boot.sh runs `setprop ctl.restart adbd`, which kills the `adb shell` session that started it, so boot.sh dies right after "adb auth required" and chargectl never starts. I worked around it with `setsid sh boot.sh &` (with a temporary `disable_adb_wifi`). Fix: launch boot.sh detached (`setsid … &`) in `--run-now`.
   - The python adb-shell tools now need the key too. Use `tools/pc/uadbk.py` (it signs with `~/.android/adbkey`), so one key covers both.
7. **Reboot test (08:09, one Power tap):** uptime 678 s at check → `sys.synapse.hook=1`, `sys.synapse.charge=hold`, `ro.adb.secure=1`, adb.exe over Wi-Fi :5555 connects with the key (no root step needed), app `0.3.13` up with screen=ambient, HA reachable, publish_errors 0, device_owner true, charging false (held at 100 %). Screenshot: the ambient clock "8:20 / Wednesday, October 7 / ● LIVING ROOM" (taken before R-011's nav-bar fix).

### R-008 · BRAIN → HANDS · OPEN — RUN THIS FIRST WHEN YOU RESUME (Mason asleep: work autonomously)
Mason (asleep): "do everything while I sleep, figure out a way to make it work, get it done."
**HARD STOP:** no reboot, no flashing, no fastboot. Every boot needs a Power tap within 5 s (dm-verity screen) or the phone powers off, and nobody is awake. R-003 and R-007 wait for Mason. `adb root` is fine.
Push results after every step (into R-005/R-006 + `results/`). Never commit the token.
0. `git pull`. Re-check-out the phone scripts so they get LF: delete the files from `git ls-files '*.sh' '*.rc'`, then `git checkout --` those paths. Make sure `C:\secure\ha_token.txt` exists (the token is in BRAIN's earlier message in your chat). Find `<HA>`: `curl.exe` `/api/` with the token → 200 on http://homeassistant.local:8123, else http://10.0.0.7:8123.
1. **R-006:** `adb root`, then `install_payload.py --run-now`. Sample status, current_now, capacity, current_cmd and sys.synapse.charge every 5 min for 30 min, and answer flat vs drain. Recovery test: `charge_limit 0`, wait 70 s, expect `0 0` + Charging, then set it back to 1.
2. **R-005:** run `provision.py … --ha-url <HA> --token-file C:\secure\ha_token.txt --device-owner`, then `smoke_test.py` (no `--speak`).
   - **Screenshots replace Mason's eyes:** `adb exec-out screencap -p > results\shot_X.png`, taken after provisioning (expect the dashboard, not a login page), after `/api/ambient` (dim clock), after `/api/wake`, and after 130 s idle (should go ambient by itself). Look at each and describe it in R-005.
   - Record from `/api/status`: `tts.ready` and the engine.
   - `logcat -d -s Synapse AndroidRuntime chromium | tail -150`.
   - HA states of `*synapse_livingroom_01_*`.
3. **On failure:** post the exact error, stack trace and screenshot description, then push. BRAIN fixes it and CI publishes a new APK to `builds/` in about 3 min. `git pull` every ~15 min; when `builds/synapse-core-latest.json` changes, re-run provision.py (upgrades in place) and step 2. Loop until it passes.
4. **On pass:** run `ha_package.py > results\synapse_livingroom_01.yaml` but don't install it into HA without Mason. Write a short morning summary for Mason in your STATUS: what works, and what needs his eyes, ears or a Power tap.
Answer:

### R-009 · BRAIN → HANDS · DONE (with 2 follow-ups) — fixes for the 3 R-005 bugs (next 0.3.x APK)
BRAIN fixed all three in code (pushed; CI builds the APK). After `builds/` shows a version > 0.3.11:
1. **config push EACCES:** provision.py now drops `adb unroot` before pushing config.json (so the app can read it) and deletes it on failure. Re-run provision.py normally (no manual `adb unroot` needed). Confirm the config imports on the **first** run and no config.json is left behind.
2. **ambient self-wake:** IdleController now ignores sensor wakes for 4 s after entering ambient; real touch still wakes. Re-test: `POST /api/ambient` should stay ambient (not bounce to active in 2 s), and the **130 s idle → auto-ambient** test should now pass. Please capture the idle screenshot.
3. **thermal avc spam:** NodeService stops polling /sys/class/thermal after the first denial; `cpu_temp` will be null (expected) and the logcat avc spam should stop.
Also: `ha_token` in `/api/status` and config.result.json is now fully masked `(set)`.
**TTS (not a code bug):** SherpaTTS needs to be set as the system TTS engine and have a voice model downloaded (its in-app UI, one-time). After that, set it default: `settings put secure tts_default_synth org.woheller69.ttsengine`. Then `/api/status` tts.ready should flip true. Mason can do the voice download when he wants voice; not blocking.
Answer (HANDS, 08:05 PDT): run on **v6** (R-007 installed; see R-007), APK **0.3.13** (sha aff6e15d…), phone now on its **factory MAC** cc:a2:12:64:92:95, IP 10.0.0.151 (DHCP).
1. config EACCES: ✅ fixed in the app, but ⚠️ **new provision.py bug: over Wi-Fi ADB, `adb unroot` restarts adbd, so the TCP session drops and every later step gets `device offline`.** 1st run: 3 PASS (falsely: "uid adb.exe: device offline" counted as PASS) then FAIL install/config. Fix: after unroot, `adb disconnect` + `adb connect <serial>` and wait for `get-state == device`; check uid == 2000 numerically. 2nd run (already non-root): **11/11 PASS**, config imported first try, no config.json left.
2. ambient: ✅ **manual `POST /api/ambient` holds** (ambient at t+1…t+8 s). Screenshot = black screen, large thin "8:00", "Wednesday, October 7", green "● LIVING ROOM", Android nav bar (◀ back) still visible at the bottom ❓ (kiosk should hide it?). **Idle auto-dim ✅:** woke at 08:01:06, `screen=ambient` at 08:03:11 (idle 120 s), and the screenshot shows the same clock. ⚠️ `smoke_test.py` still reported ambient FAIL once (signals `external,touch` from the preceding /api/presence call?). Maybe the smoke-test order should be presence last, or wait 4 s.
3. thermal avc: ✅ `logcat | grep -c 'avc.*thermal'` = **0**. No AndroidRuntime crashes.
- tts: ready=false (voice download not done yet; Mason later).
- HA: 14 entities; `charging=on` (the limiter isn't installed on v6 yet; next step).

### R-010 · BRAIN → HANDS · DONE — recovery-first gate for permanent install
Mason wants to consider removing stock (permanent GSI install). Before ANY destructive flash, prove the way back. See `os/PERMANENT_INSTALL.md`.
1. R-003: `fastboot gsi disable` from fastbootd → confirm it boots stock, then re-enter the DSU. (reboot)
2. Prove restore: `fastboot flash super C:\c8backup\super.img` works from fastbootd (don't actually overwrite unless needed — at minimum confirm fastbootd sees `super` and accepts the image size; a dry `getvar partition-size:super` + `partition-type:super`). (reboot)
Report whether both work. Only after both pass does a permanent install become a reasonable option for Mason to approve. Do NOT attempt the permanent system flash in this request.
Answer (HANDS, 09:25 PDT, Mason OK'd 09:05): ✅ **both pass. The gate is met. A permanent install is now Mason's decision; nothing was flashed.**
1. Escape hatch: passed in R-003 (07:42). `gsi disable` → stock booted, DSU kept.
2. Restore from backup:
   - **Already done for real this morning.** 03:46–03:51 PDT, from **LK** fastboot with stock fastboot.exe: `fastboot flash super C:\c8backup\super.img` resparsed into 39 chunks, all OKAY, 337 s (`C:\c8backup\flash_super.log`). The phone then booted stock Stratus_C8_V212. That was the recovery from the failed permanent flash.
   - **Backup integrity:** SHA-256 of `super.img` on the PC = `cc50a7b6…c932`. This matches the on-device `/dev/block/by-name/super` hash taken at backup time. The LP geometry magic `gDla` is at 0x1000.
   - **fastbootd dry check today:** `is-userspace=yes`, `partition-size:super=0x20C0C4000` (= 8792064000 B, an exact match with the image), `is-logical:super=no` (physical, so it's flashable), `max-download-size=0x10000000` (the client resparses, same as this morning), `snapshot-update-status=none`. `partition-type:super` → "Fastboot HAL not found". That's harmless: the client only uses it for format/erase, and a raw flash doesn't need it.
   - Back to v6 via `fastboot reboot` + Power tap. boot_completed=1, chargectl is running, app 0.3.15 is up (HA reachable, TTS ready).
- ⚠️ **Toolchain caveat:** Zadig WinUSB now owns 0E8D:201C, so stock fastboot.exe **can't** reach LK anymore, and `fb.py` has no sparse support, so it can't push 8.8 GB. **The current working restore path is fastbootd (18D1:4EE0) + stock fastboot.exe.** That works only if recovery/vendor_boot still boots. If a bad flash ever kills fastbootd too, Mason needs to switch the 0E8D:201C driver back to "Android Bootloader Interface" in Device Manager, or HANDS adds sparse support to fb.py. Recommend adding sparse support to fb.py **before** any permanent flash (HANDS can do that; no device action needed).

### R-011 · BRAIN → HANDS · OPEN — fixes for the 2 R-009 findings (next APK > 0.3.13 + provision.py)
BRAIN fixed both (pushed; CI building):
1. **provision.py unroot-over-Wi-Fi false-pass:** after `adb unroot` it now reconnects, waits for `get-state==device`, and verifies `uid==2000`, aborting before pushing the token if the session doesn't come back. So provision should pass on the **first** run over Wi-Fi now (no manual 2nd run). Just `git pull` and re-run provision.py.
2. **nav bar visible in ambient:** app now reasserts immersive on ambient enter and every 5 s. Re-check the ambient screenshot — the bottom ◀ nav bar should be gone.
Also: the smoke_test ambient FAIL was a test artifact (a real touch during the 4 s grace force-wakes, which is correct). If it recurs, run smoke_test without touching the phone, or treat a `touch`/`external` signal in that window as expected. Not an app bug.
Next on your list: install the charge limiter on v6 (`install_payload.py --run-now`, then `--adb-key` for persistent ADB), and fill in the R-007 answer (what you did to install v6).
Answer:

### R-104 · HANDS → BRAIN · OPEN — TTS never initializes (likely missing `<queries>`)
Facts (08:50 PDT, v6, app 0.3.14): SherpaTTS installed, Mason downloaded an English Piper voice (`/sdcard/Android/data/org.woheller69.ttsengine/files/engUS`), logcat shows `sherpa-onnx-tts-engine: sampleRate: 16000`. `settings put secure tts_default_synth org.woheller69.ttsengine` is set, the app was restarted, and `/api/status` still says `tts: {ready:false, engine:null}`. `POST /api/speak` returns `null`. There are no TextToSpeech logs from the Synapse process.
Cause (likely, ❓): `aapt2 dump xmltree` of the APK shows **no `<queries>` element**. On targetSdk ≥ 30 the app can't see TTS engines without `<queries><intent><action android:name="android.intent.action.TTS_SERVICE"/></intent></queries>`. Also re-init TTS when the default engine changes or when init fails (retry every 30 s).
Done when: tts.ready=true and Mason hears `/api/speak`.

### R-105 · HANDS → BRAIN · OPEN — "Leave kiosk" doesn't leave lock-task
Settings screen → "Leave kiosk" opened Android Settings **inside** the locked task (`mLockTaskModeState=LOCKED`). Starting any other app gives `error code 101` (lock-task violation). Shell `am task lock stop` can't override device-owner lock-task. Workaround used: re-provision with `--companion org.woheller69.ttsengine`. Fix: "Leave kiosk" must call `stopLockTask()` (and re-pin on "Return to kiosk").

### R-106 · HANDS → BRAIN · OPEN — install_payload `--run-now` kills itself when adb_keys exist
boot.sh runs `setprop ctl.restart adbd`, which kills the `adb shell` that launched it, so it dies right after "adb auth required" and chargectl never starts. Launch it detached: `setsid sh boot.sh </dev/null >/dev/null 2>&1 &`. (Real boots via the hook are fine; verified in R-007.)

### R-107 · HANDS → BRAIN · OPEN — ⭐ MASON'S BIG ASK: Synapse launcher + native dashboard (not the HA web page)
Mason (08:46–08:48): "I need a better display than this for my dashboard view… I need synapse to have its own home screen and apps and features just like an android os." What's wrong with the current dash: **(a) it's HA's generic web page** AND **(c) it should look like the SynapseOS brand: dark, neon blue/purple, neuron/circuit logo** (logo: glowing blue neuron with circuit-trace dendrites ending in small rings, purple/blue gradient, "SynapseOS" in a clean geometric sans).
Wants:
1. **Native Synapse dashboard** (Compose, not a WebView of Lovelace): context hero (greeting, time, weather, house mode), room cards, big scene buttons, Jarvis "talk/type" panel, presence and status. Data comes from the HA WebSocket (entity cache). Use the handoff v2.7 §4 layout as the base.
2. **Synapse home screen / launcher:** app drawer (allowed apps), dock, widgets, Synapse settings, node status. It feels like its own OS while kiosk keeps strangers out. An app allowlist is managed in settings (PIN-protected).
3. Keep the HA Lovelace view as one tab ("Advanced / HA").
Constraints: 720×1600 at 320 dpi, PowerVR GE8320 → keep animations light (no heavy blur). Mason asked about permanently removing stock; HANDS advised **it's not needed for this** (launcher/UI are app work). Mason hasn't decided yet.
**Design north star (Mason, 08:50): "it needs to feel like a remote but for the house."** Read it as a TV remote for the home:
- **One-handed, thumb-reach first:** the most-used controls sit in the bottom half, big hit targets (≥ 64 dp), no hunting through menus.
- **Physical-button feel:** chunky tiles with press feedback (vibration CLICK effect — this phone supports prebaked CLICK/TICK, verified), instant visual state (on/off glow), no web-page scrolling feel.
- **Room = channel:** swipe left/right between rooms like flipping channels. Each room shows its lights/climate/media/scenes as a remote pad.
- **"Power" and "Home" buttons:** an always-visible **All off / Good night** and a **Home** button (back to the house overview), like a remote's power and home keys.
- **Media D-pad for TVs/speakers:** play/pause, volume ±, source, like a real remote when a media player is selected.
- **Mic button = Jarvis push-to-talk:** the big round button, like a voice remote.
- Fast: actions fire on the touch-down/release with optimistic UI, then confirm from the HA state.
Mason still has to say which apps go on the home screen (asked: Camera, Settings, Jarvis chat, HA, browser, music?).

### R-104/105/106 — FIXED by BRAIN (next APK > 0.3.14, + os/v6/install_payload.py)
- R-104 TTS: added `<queries><intent><action TTS_SERVICE/></queries>` (the real cause — targetSdk 34 couldn't see engines) + Speech re-inits every ~30 s until a voice is ready. After the next APK, `/api/status` tts.ready should flip true (voice already downloaded). Re-test `/api/speak`.
- R-105 leave-kiosk: `Kiosk.pauseKiosk()` stops MainActivity re-pinning, temporarily allows `com.android.settings` through lock-task, and `stopLockTask()` now sticks; added a **Return to kiosk** button. Re-test Leave → Android Settings → Return.
- R-106 run-now: `install_payload.py --run-now` now launches boot.sh detached (`setsid &`), so chargectl starts even with adb_keys present. (Real boots were already fine.)

### R-107 · BRAIN → in progress — native remote-style UI. Plan: `docs/R-107_NATIVE_UI_PLAN.md`
- **Decision (BRAIN):** build in **Android Views, not Compose** — lighter on the GE8320 (Mason's constraint), snappier touch-down feel, and BRAIN can verify it compiles (Compose would build blind). Full rationale in the plan.
- **Done:** HA WebSocket protocol + live EntityCache (core-logic, 29 tests) — the live-data layer.
- **Next (BRAIN):** OkHttp HaWsClient (connect/auth/resubscribe/optimistic call_service), then the remote UI in phases (theme+logo → RemoteHome w/ All-off & Home keys → room-as-channel swipe pads → media D-pad → Jarvis mic → launcher/app-drawer), keeping HA Lovelace as an "Advanced" tab.
- **Need from Mason:** (1) which apps on the home screen (he floated Camera, Settings, Jarvis chat, HA, browser, music); (2) room list + entities per room (or infer from HA areas); (3) approve the neuron logo BRAIN will draw. Does NOT need stock removed.
Answer (R-107 is a build, tracked here + the plan doc).

### HANDS results 09:02 PDT (APK 0.3.15 on v6, IP 10.0.0.151)
- **R-104 TTS: ✅ VERIFIED.** After reinstall `/api/status` tts = `{ready:true, engine:"org.woheller69.ttsengine"}` on the first poll. `POST /api/speak` → `{ok:true}` and **Mason heard it** ("Good morning Mason. Synapse is online…").
- R-011.1 provision: two runs over Wi-Fi, both 11/11. The phone was already non-root, so the new unroot→reconnect path wasn't exercised ❓. R-011.2 nav bar: not re-checked yet.
- R-105 Leave/Return kiosk: not tested yet (needs Mason's 5-tap + PIN).
- Charge limiter + authenticated Wi-Fi ADB on v6: ✅ installed and verified across a reboot (see R-007).
- Note: the app was provisioned with `--companion org.woheller69.ttsengine` (needed for the voice download while R-105 was broken).

### R-107 input from HANDS: HA areas → room "channels" (live from HA `/api/template`, 09:01 PDT)
| Area (id) | Controllable entities |
|---|---|
| Living Room (`living_room`) | light.lr_lamp, switch.lr_lamp, media_player.living_room_50_onn_roku_tv, ivy-lights alert switches (ring/motion/hour_ding) |
| Master bedroom (`master_bedroom`) | light.bedroom, light.cync_lan_694243630_22/185/245, light.zz_cloud_bedroom_led_strip, light.zz_cloud_mb_lamp_top, light.master_bedroom_listening_light, switch.master_bedroom_jarvis_microphone, switch.master_bedroom_jarvis_replies_on_tv, switch.led_strip_led_strip_mitm_mode, media_player.riahs_room_50_onn_roku_tv |
| Kids Room (`kids_room`) | light.cync_lan_694243630_188, light.zz_cloud_kids_bedroom_light, light.kids_bedroom_listening_light, switch.kids_bedroom_jarvis_microphone, switch.kids_bedroom_jarvis_replies_on_tv, media_player.kids_room_juniors_roku |
| Dining Room (`dining_room`) | light.cync_lan_694243630_102, light.zz_cloud_dining_room_light, switch.dining_room_dining_room_windows_mute |
| Hallway (`hallway`) | light.cync_lan_694243630_239, light.zz_cloud_hallway_light |
| Kitchen (`kitchen`) | (none assigned yet) |
| Front door, 2018 GMC Terrain (+ status monitor) | (no lights/media) |
Suggestion: build channels from HA **areas** automatically (skip empty ones). There are duplicates: `zz_cloud_*` lights look like cloud twins of the `cync_lan_*` ones, so hide `zz_*` or let Mason pick per room ❓. Each room's Roku = the D-pad target. Mason still to confirm the room order and the home-screen apps.

### R-107 inputs from Mason (2026-10-07) — CONFIRMED
- **Home-screen apps:** Camera, Settings, Jarvis chat, HA, browser, music (confirmed). Add features/apps as developed.
- **Wireless app install + access** from: **dining PC** (Windows), **Jarvis = MacBook** (macOS), **RP5** (Raspberry Pi 5). → authorize each machine's ADB key over Wi-Fi; `adb install` / scrcpy / control-API from any of them. See `tools/remote-access/README.md`.
- **Rooms:** pull from Home Assistant areas. **Exclude the kitchen** (no kitchen devices needed).
- **Design:** more futuristic/premium — a "Synapse Glass" language (like Apple's visionOS glass, but Synapse's own). Make it pop, high quality. Mason will review the result.
- **Build:** go — Mason reviews what comes out.

### R-108 · HANDS → BRAIN · OPEN — Mason thought Synapse Glass was already on the phone
Mason (09:16): "other chat said I should have the Synapse Glass on screen. I don't see it, it's just in HA. No home screen, no login… never was told any of that." Today the phone runs app 0.3.15 (soon 0.3.17), which still shows the HA Lovelace WebView. The Glass design exists only as a browser prototype.
Ask: in every message to Mason, label clearly what is a **preview (browser only)** and what is **on the phone now**, and give an ETA for the first on-device R-107 build. When an R-107 APK lands in `builds/`, HANDS installs it the same hour.

### R-109 · HANDS → BRAIN · FYI — remote-access keys installed (your ask)
MacBook "Jarvis" (ubuntu-smarthome 10.0.0.60): installed `adb` 34.0.5, key `acewood@acewood-MacBookPro11-1`. Pi 5 HA SSH add-on (`core-ssh`, 10.0.0.165): installed `android-tools`; the key lives in `/config/.android` (persistent) and `/root/.android` is symlinked to it. Both keys are in `/data/adb/synapse/adb_keys` + `/data/misc/adb/adb_keys` (3 keys total, including the PC's). ❓ They don't authenticate yet ("unauthorized"): an adbd restart wasn't enough, so I assume system_server caches keys until the next reboot. They should work after the next boot; verify then. Caveat: the add-on's apk-installed `adb` will disappear on an add-on rebuild/update; the key in /config survives.

### R-110 · HANDS → BRAIN · OPEN — system-wide Synapse Glass (Mason 09:23)
Mason: "match the glass theme … across the whole SynapseOS." HANDS split:
- **HANDS (OS):** ✅ done 09:30: system dark mode + Monet palette #49B6FF (VIBRANT). Next: Synapse Glass **boot animation** (replace `/product/media/bootanimation.zip` via bind-mount from boot hook, no reflash; baked into the image for the permanent install). Later: LK splash (`logo` partition, MTK logo.bin; risky, only after the restore path is solid).
- **BRAIN (app):** the launcher/home screen, settings screens, PIN/profile screen, lock/ambient screen and **wallpaper** (device owner can set it via WallpaperManager) all in Glass. Please send boot-animation art direction (or frames) if you want it to match exactly; otherwise HANDS builds it from `design/synapse-glass/` (neuron logo breathing on the deep-night radial ground).

### R-111 · HANDS self · DONE (❌ failed, reverted) — Power-tap fix, software-only
Untested idea: the eio warning may be skipped when vbmeta has **HASHTREE_DISABLED | VERIFICATION_DISABLED (flags=3)**, the usual MTK fix for "dm-verity corruption". Plan: patch byte 123 of `vbmeta_reset.img` → `vbmeta_flags3.img`, flash it from LK with fb.py, then reboot. Rollback: flash `vbmeta_reset.img` from LK. Risk ❓: DSU or /data might not mount; LK fastboot stays reachable (it runs before Android). **Waiting for Mason's "go."**

R-111 result (HANDS, 09:50 PDT, Mason OK'd 09:40): ❌ **vbmeta flags=3 does NOT clear the warning, and it breaks /data.**
- The warning still showed (Mason tapped Power).
- DSU booted to the boot animation, but **zygote never started**. keystore2 reported `KEYMINT_NOT_CONFIGURED`, then vold: `decryptWithKeystoreKey failed` / `read_key failed in mountFstab`. Cause: with verification disabled, LK doesn't hand KeyMint its root of trust, so FBE keys can't unwrap.
- Reverted: adb-shell `reboot(fastboot=True)` over USB, then fb.py flashed `vbmeta_reset.img` (53c78754) to vbmeta_a and rebooted. ✅ boot_completed=1, `ro.crypto.state=encrypted`, chargectl running, app up. No data loss.
- **RULE (add to Never do): never set vbmeta flags 1/2/3 on this phone.** /data becomes undecryptable. This applies to the permanent install too: keep verification enabled and sign with the test key, as vbmeta_reset does.
- veritymode is still `eio`. No safe software fix is left among the known ones (R-101). Next idea, read-only: list LK `oem` commands at the next planned LK visit.

R-109 update: after reboot, MacBook ADB ✅ works. Pi ❌ still unauthorized. system_server rewrote `/data/misc/adb/adb_keys` from its own store (adb_temp_keys.xml) after the first connect and dropped the Pi key. Set `adb_allowed_connection_time=0` (keys never expire). ❓ Next boot: have the Pi connect first, then confirm it sticks.

### R-112 · HANDS → BRAIN · FYI — 0.3.23 native Glass home is installed (09:50 PDT)
- `adb install -r` over Wi-Fi: Success, sha 5f1684db…, versionName 0.3.23.
- ⚠️ Right after the install, **SherpaTTS's MainActivity had focus**, not Synapse (`am start` said "intent delivered to top-most instance"). A forced `am start -W -S -n …/.MainActivity` brought Synapse back. Suggest: after an update, the app (device owner) should re-assert itself as home and re-pin lock task. Also, why would the TTS companion activity come to the front? Check whether the app launches it for voice data.
- logcat: `HA ws open` at 09:49:48, `TTS ready`. Screen focus = Synapse MainActivity.
- **Screenshots will NOT be pushed: the repo is PUBLIC** (`gh api repos/AceWood619/SynapseOS -q .private` → false), and the home screen shows Mason's home data (greeting, rooms, devices). Saved locally at `C:\SynapseOS\results\native_home_0323.png` (results/ isn't committed). Mason is reviewing it on the phone itself. ⚠️ Also check that nothing else committed holds home data (entity maps in REQUESTS are probably fine, but consider making the repo private).

### R-113 · HANDS → BRAIN · OPEN — 0.3.23 on-device findings (uiautomator dump 09:51)
The native home renders (SYNAPSE · HOME, clock, profile pill "L / ADMIN", hero orb, "Home · 3 lights on · 1 playing", 6 HA scenes, console HOME/●/ALL OFF). Bugs:
1. **Greeting says "Good morning, Livingroom"**. It uses the node name instead of the person. Should be "Mason" (the admin profile name). The profile pill shows "L", so the profile name is probably the node id too.
2. **ROOM CHANNELS is empty**: no channel tiles under the header. Probably HA areas aren't fetched over WS (`config/area_registry/list` + `config/entity_registry/list`, or `device_registry` for area-by-device). The map is in my earlier R-107 area→entity post.
3. Mason said (09:51) he's "still waiting for the glass". Tell us what he should see that's missing (room pads? wallpaper? glass panels?), so HANDS can verify each item on the device.

### R-113 answer · BRAIN → HANDS · FIXED (pushed, in next CI APK)
Both on-device bugs fixed in code; both ship in the next `builds/` APK (CI building now):
1. **Greeting name.** The greeting no longer uses the node id. Added `owner_name` to the node config; the home screen now says "Good morning, <owner_name>" and the profile pill shows that initial ("M"/ADMIN). If `owner_name` is blank it just says "Good morning" (no wrong name). **HANDS action:** re-push config with the new flag — `provision.py … --owner-name Mason` (config-only; no reinstall needed, the app reloads config on launch). The greeting-name display also needs the new APK (HomeView reads the field).
2. **Empty ROOM CHANNELS.** Root cause confirmed: rooms were never populated. Fixed properly — on WS auth the app now fetches HA's **area + entity + device registries** (`config/area_registry/list`, `.../entity_registry/list`, `.../device_registry/list`), resolves each entity's area (own area_id, else its device's), skips disabled/hidden/config/diagnostic, and builds the channels. Kitchen is auto-excluded (RoomsConfig default). No hand-written map, no re-provision needed — it learns the house from HA. **HANDS action:** just install the next APK; channels should appear within a second of "HA ws open". If they don't, grab `adb logcat -d -s Synapse | grep -i "area build failed"` and the WS may be denying registry access (token scope) — tell me.
Pure logic is unit-tested (HaRegistry + RoomControl + MediaRemote, all green). Also built this round: room-pad control model + Roku D-pad mapping (the next UI build consumes them).
### R-114 · HANDS → BRAIN · OPEN — ⭐ Mason (09:57): "fix room channels and add more features, buttons, controls, homepage with apps / app drawer, etc."
Priority order (HANDS' read of Mason's ask):
1. **Fix room channels** (R-113 #2: empty). Pull areas over WS, kitchen excluded, with smart order.
2. **Room pad (room = channel):** swipe ‹ › between rooms. Per room: light tiles (toggle + long-press brightness/color), climate, media D-pad + volume, room scenes, "room off".
3. **Home page with apps + app drawer:** a dock (Camera, Settings, Jarvis chat, HA, Browser, Music, per Mason's confirmed list) plus a swipe-up drawer of allowlisted apps. Launching an app must work under lock task (add it to the lock-task allowlist and keep the Synapse home button to come back).
4. **More controls:** quick toggles row (Do-not-disturb / Night mode / Away), a media "now playing" card with album art, a timer/alarm button, weather glance, intercom/announce button (TTS to rooms).
5. **Greeting name fix** (R-113 #1).
Mason wants to see progress on the phone ASAP. Ship in small builds; HANDS installs each one within the hour and posts uiautomator/text findings (screenshots stay local; the repo is public).

### R-115 · HANDS self · DONE — `fastboot oem cdms` on LK: ❌ `FAILunknown command` (10:00)
It's reported to clear the dm-verity state on other MTK and Xiaomi phones; this LK (w20_x65_c8-f6400970dd0-20250922110750-2) doesn't implement it. `getvar all` saved (read-only): unlocked=yes, secure=no, slot a/b both successful, lk has A/B slots (lk_a/lk_b). Nothing changed. The deep-research run couldn't start (usage limit); retry in ~3 h.

### R-116 · HANDS → BRAIN · OPEN — 0.3.26 on device (10:07): rooms ✅, greeting still ❌
- Installed via provision.py `--owner-name Mason --device-owner --companion org.woheller69.ttsengine`: **11/11 PASS**. `/api/status.config.owner_name = "Mason"` ✅.
- ✅ **Room channels populate:** CH 1 Living Room (all off), CH 2 Hallway (1 on), CH 3 Master bedroom (3 on · TV). ❓ Only 3 are visible in the dump. Are Kids Room and Dining Room behind a horizontal scroll, or missing? Please confirm they're built (kids: `media_player.kids_room_juniors_roku`, etc.).
- ❌ **Greeting still says just "Good morning"** and the profile pill shows **"S"** (not "M"), even after `am start -S`. owner_name is in config but HomeView isn't reading it. Is the active profile ("S" = default "Synapse"?) taking priority over owner_name? Suggest: the admin profile's display name defaults to owner_name.
- Mason hasn't tested room-pad taps yet; next.

### R-117 · HANDS → BRAIN · OPEN — 0.3.28 on device (10:12)
- Installed (sha 13e03b12…), then `am start -W -S` re-asserted it. Focus = Synapse.
- ✅ Greeting "Good morning, Mason"; pill "M / ADMIN"; "HA" chip present.
- ✅ Room pad opens. Living Room: "1 light · 1 media", light key "Lr Lamp", media transport + vol, Roku D-pad, MORE toggles. BACK returns home.
- ❌ **Only 3 channels: Living Room, Hallway, Master bedroom.** **Kids Room and Dining Room are missing** (checked after a horizontal swipe too). Per my R-107 map, Kids has `media_player.kids_room_juniors_roku` and Dining has lights. Their area is probably set on the *device* while the entity has area_id=null, or a filter (hidden/entity_category, or the zz_cloud twins) drops them. Please log per-area counts (`Synapse: area <name> -> n entities`) so I can read them over adb.
- 🟡 Polish:
  - Raw names like "Lr Lamp" and "Living Room Ivy Lights Ivy Ring Alerts" (Ivy *switch* entities under MORE). Strip the room prefix, use friendly names.
  - Ivy alert/ding/motion switches are settings, not controls. Put them under a settings drawer.
  - "Living Room Light" and "Lr Lamp" both show; per Mason's floor plan the living room has one lamp (Wemo plug). Ask Mason before merging.
- Not yet tested by Mason: actual toggles/D-pad driving the devices.
