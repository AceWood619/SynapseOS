# Stratus C8: boot, DSU install and recovery notes
Device: Cloud Mobile Stratus C8 (MT6765, bootloader product `w20_x65_c8`, slot a). Last updated 2026-10-07 05:55 PDT.
Legend: ✅ verified on device · ⚠️ caveat · ❓ unconfirmed

## Current state
- ✅ SynapseOS base = patched LineageOS 21 GSI ("v5"), running as a **persistent DSU** (Dynamic System Update). Stock Android 13 (`Stratus_C8_V212`) remains underneath.
- ✅ DSU userdata = **7 GiB** (7516192768 B). That's the max: gsid refuses any install leaving < 8792064000 B free on stock /data (19.3 GB free → system 2.55 GB + userdata ≤ ~7.97 GB).
- ⚠️ **Every boot shows the bootloader's "dm-verity corruption" warning.** You must **tap Power within 5 s** or the phone powers off (and, on USB power, drops to the charging screen). This, not the DSU, is the "mystery power-off".

## dm-verity warning: what we know
- ✅ LK runs AVB "managed restart/EIO" mode (`androidboot.veritymode.managed=yes`). It stores the vbmeta digest in **seccfg** (`avb.managed_verity_mode`). While the stored digest == the current vbmeta digest → `veritymode=eio` → warning on every boot.
- ✅ The current kernel shows **no** live verity errors. The flag is leftover from earlier failed experiments.
- ✅ `reboot 'dm-verity enforcing'` does **not** clear it.
- ✅ Flashing a re-signed vbmeta and booting **recovery** did **not** clear it (managed mode probably only runs on normal boot) ❓
- ❓ Next test: boot normally with the re-signed vbmeta (`vbmeta_reset.img`, same AOSP test key cdbb7717…, with an extra prop). This also tells us whether TrustKernel cares about the vbmeta digest (key fact for a permanent install).

## Proven persistent-DSU recipe
1. On stock: `adb shell "reboot 'dm-verity enforcing'"`
2. Push patched image → `/storage/emulated/0/Download/system_raw.gz`
3. `am start-activity -n com.android.dynsystem/com.android.dynsystem.VerificationActivity -a android.os.image.action.START_INSTALL -d file:///storage/emulated/0/Download/system_raw.gz --el KEY_SYSTEM_SIZE 2554204160 --el KEY_USERDATA_SIZE 7516192768`
4. Wait for logcat `DynamicSystemInstallationService ... READY`, then `am start-service -n com.android.dynsystem/.DynamicSystemInstallationService -a com.android.dynsystem.ACTION_REBOOT_TO_DYN_SYSTEM`
5. In DSU: `setprop service.adb.root 1` → `adb usb` → `gsi_tool enable` → `echo -n ok > /metadata/gsi/dsu/install_status` → `rm -f /metadata/gsi/dsu/one_shot_boot`
6. To remove: in DSU `gsi_tool wipe`, then reboot → stock deletes it.
- Re-enter an installed DSU from stock without reinstalling: `ACTION_REBOOT_TO_DYN_SYSTEM` (one-shot).

## PC connection quirks (Windows)
- ⚠️ LK fastboot and normal-mode ADB share USB ID `0E8D:201C` **and the same serial**, so Windows uses one device entry for both.
  - We bound WinUSB to it with Zadig. As a result, `fastboot.exe` and `adb.exe` over USB **can't see the phone**.
  - Use the tools below instead.
- ✅ **Fastboot (LK):** `python C:\gsiL\fb.py getvar|flash <part> <file>|reboot` (pyusb + libusb). Max download 0x8000000.
- ✅ **ADB over USB:** `python C:\gsiL\uadb.py "<cmd>"` (adb-shell + libusb).
- ✅ **ADB over Wi-Fi:** `persist.adb.tcp.port=5555` is set in the DSU → `adb connect <phone-ip>:5555`. Set a DHCP reservation for the phone.
- fastbootd (recovery, `18D1:4EE0`) works with stock `fastboot.exe` but **can't flash vbmeta** ("No such file or directory").
- Recovery ADB (`18D1:D001`) = unauthorized.

## Never do
- Don't flash the GSI permanently to system_a with modified vbmeta + `-w` (it booted to "Can't load Android system").
- Don't undo the v5 prop spoofing or re-enable TrebleDroid `fixSPL` (TrustKernel / `/data` breaks).
- Don't tap "Factory reset / Wipe data" in recovery.
- Keep the stock backup (`C:\c8backup`, SHA-256 verified) safe.
