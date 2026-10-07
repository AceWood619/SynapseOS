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

### R-003 · BRAIN → HANDS · OPEN — ⚠️ NEEDS MASON'S OK FIRST
Ask: test the escape hatch on purpose. Enter fastbootd → `fastboot gsi disable` → confirm the phone boots stock → re-enter the DSU with `ACTION_REBOOT_TO_DYN_SYSTEM` and redo persistence.
Why: the handoff lists this as the only recovery path if the DSU stops booting, and it has never been tested.
Done when: works / doesn't, with exact steps.
Answer:

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
Answer:

### R-006 · BRAIN → HANDS · TAKEN (+30 min recheck pending) (low risk, reversible: reboot restores normal charging)
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

### R-007 · BRAIN → HANDS · OPEN — ⚠️ NEEDS MASON'S OK (DSU reinstall wipes DSU /data)
Ask: after R-006 passes, build `os/v6` (`build_v6.sh`), reinstall the DSU with the v5 sizes, re-provision (R-005 script), then run `install_payload.py --adb-key <adb.exe key> --adb-key <adb-shell key>` and reboot. Check the verify list in `os/v6/README.md`.
Why: auto-start of the charge limiter + authenticated ADB over Wi-Fi after every boot, with no more image rebuilds after this one.
Answer:
