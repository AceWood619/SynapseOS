#!/usr/bin/env bash
# Build SynapseOS v6 = v5 (TrustKernel prop patches) + root boot hook (patch_v6.py).
# Usage: ./build_v6.sh <input td.img.gz> <workdir>     (Linux/WSL; needs python3, gzip, curl)
# Same partition size, key, rollback index and props as v5, so install exactly like v5:
#   KEY_SYSTEM_SIZE=2554204160, KEY_USERDATA_SIZE=7516192768 (DSU reinstall WIPES DSU /data).
set -euo pipefail
IN=${1:?input gz}; W=${2:?workdir}; mkdir -p "$W"; cd "$W"
HERE=$(cd "$(dirname "$0")" && pwd)
V5="$HERE/../v5"
[ -f avbtool.py ] || curl -sL "https://android.googlesource.com/platform/external/avb/+/refs/heads/main/avbtool.py?format=TEXT" | base64 -d > avbtool.py
[ -f testkey_rsa2048.pem ] || curl -sL "https://android.googlesource.com/platform/external/avb/+/refs/heads/main/test/data/testkey_rsa2048.pem?format=TEXT" | base64 -d > testkey_rsa2048.pem
echo "input sha256: $(sha256sum "$IN" | cut -d' ' -f1)  (expected 7e77b4096ecfb4ec0cec7118b49a5449454cb3cd57725e811f8640163bbb23cb)"
gunzip -c "$IN" > v6.img
python3 "$V5/patch_props.py" v6.img
python3 "$HERE/patch_v6.py" v6.img
python3 avbtool.py erase_footer --image v6.img
python3 avbtool.py add_hashtree_footer --image v6.img --partition_name system \
  --hash_algorithm sha256 --partition_size 2554204160 --do_not_generate_fec \
  --algorithm SHA256_RSA2048 --key testkey_rsa2048.pem --rollback_index 1788220800 \
  --prop com.android.build.system.os_version:13 \
  --prop com.android.build.system.fingerprint:google/lineage_arm64_bvN/tdgsi_arm64_ab:14/UQ1A.240205.004/crossgate09181258:userdebug/test-keys \
  --prop com.android.build.system.security_patch:2026-03-05
python3 avbtool.py info_image --image v6.img | head -20
echo "v6.img sha256: $(sha256sum v6.img | cut -d' ' -f1)  size $(stat -c %s v6.img) (must be 2554204160)"
gzip -1 -c v6.img > v6.img.gz
echo "push v6.img.gz as /storage/emulated/0/Download/system_raw.gz and follow the DSU recipe"
