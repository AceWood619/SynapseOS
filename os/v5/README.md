# SynapseOS v5 base image: build + install pipeline (Stratus C8)
Scripts only. **No image binaries, no secrets** (the AVB key used is the *public* AOSP test key).

## Input
- LineageOS 21 TD GSI by AndyYan, `lineage-21.0-20260918-UNOFFICIAL-arm64_bvN` (TrebleDroid-based, userdebug), raw system image gzipped as `td.img.gz`.
  - SHA-256 of the gz used: `7e77b4096ecfb4ec0cec7118b49a5449454cb3cd57725e811f8640163bbb23cb` (1154313641 B).
  - Copy on Mason's PC: `C:\gsiL\td.img.gz`.
  - ❓ Exact download URL wasn't recorded (AndyYan's GSI SourceForge project). If re-downloading, check the hash.
- Original AVB footer: `orig_info.txt` facts — partition_size 2554204160, rollback_index 1788220800, test key sha1 cdbb77177f731920bbe0a0f94f84d9038ae0617d.

## Build (Linux / WSL)
```
./build_v5.sh /mnt/c/gsiL/td.img.gz ~/gsiwork
```
- What it does: `patch_props.py` (same-length byte swaps, aborts if counts differ) → `avbtool erase_footer` → `add_hashtree_footer` (same size/key/rollback as the original, props say Android 13 / patch 2026-03-05) → gzip.
- Reference output: `v5.img` sha256 `da454296bc68a41595e4d84c013c3dd317f9ce321deec89c473b7300e819c438`, `v5.img.gz` 1215217745 B (gzip output may differ byte-wise; compare the raw img).

### Things proven NOT to work (don't retry)
- resize2fs / e2fsck / debugfs edits of the image (old `patch.sh`): broke boot / removed the AVB footer.
- Unsigned hashtree footer.
- vbmeta flags=2 inside a DSU zip.
- TrebleDroid AOSP 14 `ci-20240508` and AOSP 13 `ci-20230905` vanilla: stuck on the boot animation.

## Install as persistent DSU
See `devices/stratus-c8/BOOT_AND_INSTALL_NOTES.md` → "Proven persistent-DSU recipe".
- KEY_SYSTEM_SIZE = **2554204160** (raw v5.img size).
- KEY_USERDATA_SIZE ≤ ~7.97 GB. We use **7516192768**.

## PC helper tools (`tools/pc/`)
The phone's LK-fastboot and ADB modes share USB `0E8D:201C` + serial. After Zadig bound WinUSB to it, stock adb/fastboot can't see it over USB. These talk libusb directly (`pip install pyusb libusb adb-shell[usb]`):

| Tool | What it does |
|---|---|
| `fb.py getvar X` / `flash <part> <file>` / `reboot` | Minimal LK fastboot client |
| `uadb.py "<cmd>"` | ADB shell over USB |
| `uroot.py` | ADB root + enable TCP 5555 (then `setprop ctl.restart adbd` via `uadb.py`) |
| `gpt.py` | Print partition names + PARTUUIDs from a 64-sector GPT dump |

The libusb DLL folder must be on PATH (see `uadb.py` usage in the notes).
