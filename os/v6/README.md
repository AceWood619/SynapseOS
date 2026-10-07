# SynapseOS v6: v5 + root boot hook
**One more reflash, then no more image rebuilds for boot-time features.**

## The idea (in plain terms)
v5 proved you can safely edit the system image if every edit is **exactly the same length** (the filesystem never changes size, so nothing else shifts).
v6 uses the same trick once more. TrebleDroid's `phh-on-boot.sh` runs as root about 10 s after every boot, and it has a block that only runs on old VNDK-27 phones. That block is dead code on this phone (VNDK 31). We overwrite it, same length, with:
```
[ -f /data/adb/synapse/boot.sh ] && sh /data/adb/synapse/boot.sh
```
Think of it as adding a light switch to an existing junction box instead of rewiring the house. After that, everything SynapseOS needs at boot lives in `/data/adb/synapse/`, which is root-only and updated over ADB.

| File | Why |
|---|---|
| `patch_v6.py` | The same-length hook patch. Refuses to run unless it finds exactly one copy of the block. |
| `build_v6.sh` | v5 pipeline + `patch_v6.py`. Same size, key, rollback index and props as v5. |
| `data/boot.sh` | Boot payload: ADB over Wi-Fi **with key auth**, starts the charge limiter. |
| `data/chargectl.sh` | Keeps the battery at 40–80 % on wall power via `/proc/mtk_battery_cmd/current_cmd`. Always charges below 20 %. Stops at ≥ 45 °C. |
| `install_payload.py` | Pushes the payload + authorized ADB keys to the phone (needs `adb root`) |

## Order of operations (HANDS)
1. **Test the payload on v5 first, no reflash (R-006):**
   - Run `install_payload.py --run-now`, without `--adb-key` the first time.
   - This proves the charge control works: watch `dumpsys battery` and `sys.synapse.charge`.
2. **Then build and install v6 (R-007, needs Mason's OK, wipes DSU /data):**
   - Build: `./build_v6.sh /mnt/c/gsiL/td.img.gz ~/gsiwork6`
   - Install with the v5 DSU recipe (same sizes), re-provision with `tools/provision/provision.py`, then run `install_payload.py --adb-key …`, then reboot.
   - Verify after reboot:
     - `getprop sys.synapse.hook` = 1
     - `/data/adb/synapse/boot.log` shows "boot payload done"
     - chargectl is still running 2 min later (`ps -A | grep chargectl`), which proves the cgroup escape works
     - ADB over Wi-Fi works only from authorized keys

## Safety notes
- ⚠️ **ADB lockout:** `boot.sh` turns on ADB auth only if `adb_keys` exists. Include **every** key you use: `adb.exe`'s `%USERPROFILE%\.android\adbkey.pub` **and** the python adb-shell key used by `tools/pc/*.py`. Fallbacks:
  - USB with the on-screen "Allow USB debugging?" prompt
  - Create `/data/adb/synapse/disable_adb_wifi`
- ⚠️ **Charging:** if `chargectl` dies or the phone reboots, the kernel goes back to charging ON. The failure mode is "battery sits at 100 %", not "phone dies". Check whether `0 1` keeps the phone running from the charger or drains the battery (R-006 measures this).
- ⚠️ The hook runs anything in `/data/adb/synapse/boot.sh` as root. The folder is `0700 root`, so only ADB root can write to it.
- Rollback: reinstall the v5 DSU (or stock fallback via `fastboot gsi disable`).

## Why not the other options (R-102 / R-103 notes)
- *Make `persist.adb.tcp.port` stick + no auth:* rejected. With `ro.adb.secure=0` on a userdebug build, anyone on the LAN could get a root shell. v6 turns on key auth (`resetprop_phh ro.adb.secure 1`) before opening port 5555.
- *Grow the ext4 image to add files:* v5 notes say resize/debugfs broke boot (likely the `shared_blocks` dedup feature + losing the AVB footer). The same-length hook avoids touching filesystem structure at all.
- *Permanent install to `system_a`:* possible now that vbmeta is ruled out, but DSU keeps a one-command fallback to stock, and its limits (≤ 7.97 GB data) don't block anything we're building. **Recommendation: stay on DSU.** Revisit only if 7 GB becomes a real limit.
