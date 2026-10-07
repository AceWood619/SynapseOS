#!/usr/bin/env python3
"""Same-length in-place byte patches on the raw ext4 system image (no fs resize -> AVB stays consistent).
Why: TrebleDroid's rw-system.sh fixSPL() redirects keymaster/teed props to fake values (patch 2025-06-01,
brand "Android"), which TrustKernel rejects ("Invalid Root of Trust") -> vold can't mount /data.
We disable fixSPL and make the system report the stock Android 13 / 2026-03-05 identity, and hide
product.* props so vendor values win."""
import mmap, sys
REPS = [
 (b"ro.build.version.release=14\n", b"ro.build.version.release=13\n"),
 (b"ro.build.version.release_or_codename=14\n", b"ro.build.version.release_or_codename=13\n"),
 (b"ro.build.version.security_patch=2026-09-01\n", b"ro.build.version.security_patch=2026-03-05\n"),
 (b"ro.product.product.brand=google\n", b"ro.product.product.zzzzz=google\n"),
 (b"ro.product.product.device=tdgsi_arm64_ab\n", b"ro.product.product.zzzzzz=tdgsi_arm64_ab\n"),
 (b"ro.product.product.manufacturer=unknown\n", b"ro.product.product.zzzzzzzzzzzz=unknown\n"),
 (b"ro.product.product.model=TrebleDroid vanilla\n", b"ro.product.product.zzzzz=TrebleDroid vanilla\n"),
 (b"ro.product.product.name=lineage_arm64_bvN\n", b"ro.product.product.zzzz=lineage_arm64_bvN\n"),
 (b"\nfixSPL\n", b"\n#ixSPL\n"),
]
EXPECT = [3, 1, 3, 1, 1, 1, 1, 1, 1]   # counts seen on the 2026-09-18 input
f = open(sys.argv[1], "r+b"); m = mmap.mmap(f.fileno(), 0)
for (a, b), exp in zip(REPS, EXPECT):
    assert len(a) == len(b), a
    i = n = 0
    while (i := m.find(a, i)) >= 0:
        m[i:i+len(a)] = b; n += 1; i += len(a)
    print(f"{a!r} -> {n} (expected {exp})")
    if n != exp: sys.exit(f"unexpected count for {a!r}; input image differs, stop")
m.flush(); m.close(); f.close()
