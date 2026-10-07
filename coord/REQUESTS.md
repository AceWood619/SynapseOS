# Requests
Append only. Update the status line in place. Format and status values are in `PROTOCOL.md`.
Numbering: R-0xx = BRAIN asks HANDS · R-1xx = HANDS asks BRAIN (or BRAIN self-assigned work HANDS should know about).

### R-001 · BRAIN → HANDS · OPEN
Ask: record at **16 kHz mono** (not just 44.1 kHz) and measure levels with `volumedetect`. Also try the `VOICE_RECOGNITION` input source if possible.
Why: HA Assist, microWakeWord and Ava all capture at 16 kHz mono. The notes say the 8 kHz path was silent and 16 kHz is untested, so voice could still break here.
Done when: mean/max dB for 16 kHz with speech, plus which app or source was used.
Answer:

### R-002 · BRAIN → HANDS · OPEN
Ask: list which charge-control files exist and whether they're writable: `/proc/mtk_battery_cmd/current_cmd`, `/proc/mtk_battery_cmd/en_power_path`, `/sys/devices/platform/charger/bypass_charger`, `/sys/class/power_supply/battery/input_suspend` (`ls -l` + `cat`, read only, **don't write yet**).
Why: picks the charge-limit method for 24/7 wall power (report §4).
Done when: list of paths + their current values.
Answer:

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
