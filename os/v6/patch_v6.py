#!/usr/bin/env python3
"""v6 = v5 + one more same-length patch: a root boot hook.

TrebleDroid's /system/bin/phh-on-boot.sh runs as root (permissive phhsu_daemon domain)
~10 s after every boot. It contains a block that only runs on VNDK 27 devices (this phone is VNDK 31),
so it is dead code here. We overwrite that block, byte for byte the same length, with:
    [ -f /data/adb/synapse/boot.sh ] && sh /data/adb/synapse/boot.sh
plus '#' padding. The filesystem size never changes, which is what made v5 safe.

After this, every boot-time feature (charge limiter, ADB over Wi-Fi with keys, …) lives in
/data/adb/synapse/ and is updated over ADB with NO reflash. Only root can write that folder.
Usage: patch_v6.py <raw system img>   (run AFTER patch_props.py, BEFORE add_hashtree_footer)
"""
import mmap
import sys

OLD = (b'if [ "$vndk" = 27 ];then\n'
       b'    mount $minijailSrc64 /vendor/lib64/libminijail_vendor.so\n'
       b'    mount $minijailSrc /vendor/lib/libminijail_vendor.so\n'
       b'fi\n')
HOOK = b'[ -f /data/adb/synapse/boot.sh ] && sh /data/adb/synapse/boot.sh </dev/null >/dev/null 2>&1\n'
pad = len(OLD) - len(HOOK) - 1
assert pad >= 1, "hook longer than the block it replaces"
NEW = HOOK + b'#' * pad + b'\n'
assert len(NEW) == len(OLD)

f = open(sys.argv[1], "r+b")
m = mmap.mmap(f.fileno(), 0)
hits, i = [], 0
while (i := m.find(OLD, i)) >= 0:
    hits.append(i)
    i += len(OLD)
# The same block exists in BOTH /system/bin/phh-on-boot.sh (inode 524) and phh-on-data.sh (inode 525)
# on the 2026-09-18 image (HANDS, R-007). Keep only the copy in phh-on-boot.sh, identified by the
# text right before it in that script ("setprop ctl.stop storageproxyd ... sleep 10").
ANCHOR = b"setprop ctl.stop storageproxyd"
hits = [h for h in hits if m.rfind(ANCHOR, max(0, h - 400), h) >= 0]
done_before = m.find(HOOK) >= 0
if done_before and not hits:
    print("hook already present; nothing to do")
elif len(hits) != 1:
    sys.exit(f"expected exactly 1 copy of the VNDK-27 block in phh-on-boot.sh, found {len(hits)}; input differs, STOP")
else:
    m[hits[0]:hits[0] + len(OLD)] = NEW
    print(f"boot hook written at offset {hits[0]} ({len(OLD)} bytes, same length)")
m.flush(); m.close(); f.close()
