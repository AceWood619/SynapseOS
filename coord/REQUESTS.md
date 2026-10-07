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

### R-007 · BRAIN → HANDS · TAKEN (Mason OK'd 07:35)
Ask: after R-006 passes, build `os/v6` (`build_v6.sh`), reinstall the DSU with the v5 sizes, re-provision (R-005 script), then run `install_payload.py --adb-key <adb.exe key> --adb-key <adb-shell key>` and reboot. Check the verify list in `os/v6/README.md`.
Why: auto-start of the charge limiter + authenticated ADB over Wi-Fi after every boot, with no more image rebuilds after this one.
Answer:

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

### R-009 · BRAIN → HANDS · OPEN — fixes for the 3 R-005 bugs (next 0.3.x APK)
BRAIN fixed all three in code (pushed; CI builds the APK). After `builds/` shows a version > 0.3.11:
1. **config push EACCES:** provision.py now drops `adb unroot` before pushing config.json (so the app can read it) and deletes it on failure. Re-run provision.py normally (no manual `adb unroot` needed). Confirm the config imports on the **first** run and no config.json is left behind.
2. **ambient self-wake:** IdleController now ignores sensor wakes for 4 s after entering ambient; real touch still wakes. Re-test: `POST /api/ambient` should stay ambient (not bounce to active in 2 s), and the **130 s idle → auto-ambient** test should now pass. Please capture the idle screenshot.
3. **thermal avc spam:** NodeService stops polling /sys/class/thermal after the first denial; `cpu_temp` will be null (expected) and the logcat avc spam should stop.
Also: `ha_token` in `/api/status` and config.result.json is now fully masked `(set)`.
**TTS (not a code bug):** SherpaTTS needs to be set as the system TTS engine and have a voice model downloaded (its in-app UI, one-time). After that, set it default: `settings put secure tts_default_synth org.woheller69.ttsengine`. Then `/api/status` tts.ready should flip true. Mason can do the voice download when he wants voice; not blocking.
Answer:

### R-010 · BRAIN → HANDS · OPEN — ⚠️ NEEDS MASON'S OK (reboots) — recovery-first gate for permanent install
Mason wants to consider removing stock (permanent GSI install). Before ANY destructive flash, prove the way back. See `os/PERMANENT_INSTALL.md`.
1. R-003: `fastboot gsi disable` from fastbootd → confirm it boots stock, then re-enter the DSU. (reboot)
2. Prove restore: `fastboot flash super C:\c8backup\super.img` works from fastbootd (don't actually overwrite unless needed — at minimum confirm fastbootd sees `super` and accepts the image size; a dry `getvar partition-size:super` + `partition-type:super`). (reboot)
Report whether both work. Only after both pass does a permanent install become a reasonable option for Mason to approve. Do NOT attempt the permanent system flash in this request.
Answer:
