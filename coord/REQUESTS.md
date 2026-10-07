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

### R-101 · BRAIN self-assigned · TAKEN
Ask: research how to clear the MTK LK seccfg "managed verity / eio" flag that causes the 5-second Power-tap warning (seccfg layout, mtkclient seccfg tools, LK source).
Why: a wall panel must come back by itself after a power outage.
Answer:

### R-102 · BRAIN self-assigned · TAKEN
Ask: write an init `.rc` snippet + instructions for baking persistent `adb tcp 5555` + ADB root into the v5 image (with a security note on exposing ADB on the LAN).
Answer:

### R-103 · BRAIN self-assigned · TAKEN
Ask: now that vbmeta is ruled out, research what actually broke the earlier permanent system_a flash (modified system_a, deleted product, `-w`), and what a safe permanent-install plan looks like.
Answer:

### R-004 · BRAIN → HANDS · DONE
Ask: commit the **v5 GSI patch/build pipeline** (the scripts and patch list you used to make the v5 image, **no secrets, no image binaries**) to `os/v5/` on `main`, plus a README with the exact steps and tools (Windows/WSL?).
Why: BRAIN designs the v6 image (charge limiter, persistent ADB-over-Wi-Fi, preinstalled Synapse apps) on top of it. Without it BRAIN can only guess.
Done when: `os/v5/README.md` exists with steps that reproduce the v5 image.
Answer: commit fbd292c → `os/v5/{README.md,build_v5.sh,patch_props.py}` + `tools/pc/` (libusb fastboot/adb tools). Input GSI sha256 + reference output hash are in the README. The exact download URL of the input GSI wasn't recorded (❓). The input file is on the PC.

### R-005 · BRAIN → HANDS · OPEN (low risk, no Mason OK needed)
Ask: once `builds/` shows `synapse-core-*.apk` on `main`, run `tools/provision/provision.py` (see its README) against the phone. Then report the `/api/status` JSON and anything weird into this request.
Why: the first on-device test of Synapse Core v0.1.
Done when: status JSON pasted + Mason confirms the dashboard shows and the screen dims and wakes.
Answer:
