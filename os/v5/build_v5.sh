#!/usr/bin/env bash
# Build the SynapseOS "v5" base image: AndyYan LineageOS 21 TD GSI (arm64_bvN, 2026-09-18)
# + same-length byte patches that make TrustKernel (TEE) on the Stratus C8 accept the system,
# then re-sign the AVB hashtree footer with the public AOSP test key.
# Usage: ./build_v5.sh <input td.img.gz> <workdir>     (Linux/WSL; needs python3, gzip, curl)
set -euo pipefail
IN=${1:?input gz}; W=${2:?workdir}; mkdir -p "$W"; cd "$W"
HERE=$(cd "$(dirname "$0")" && pwd)
[ -f avbtool.py ] || curl -sL "https://android.googlesource.com/platform/external/avb/+/refs/heads/main/avbtool.py?format=TEXT" | base64 -d > avbtool.py
# Public AOSP test key (NOT a secret; it is the key the original GSI was signed with: sha1 cdbb7717…)
[ -f testkey_rsa2048.pem ] || curl -sL "https://android.googlesource.com/platform/external/avb/+/refs/heads/main/test/data/testkey_rsa2048.pem?format=TEXT" | base64 -d > testkey_rsa2048.pem
echo "input sha256: $(sha256sum "$IN" | cut -d' ' -f1)  (expected 7e77b4096ecfb4ec0cec7118b49a5449454cb3cd57725e811f8640163bbb23cb)"
gunzip -c "$IN" > v5.img
python3 "$HERE/patch_props.py" v5.img
python3 avbtool.py erase_footer --image v5.img
python3 avbtool.py add_hashtree_footer --image v5.img --partition_name system \
  --hash_algorithm sha256 --partition_size 2554204160 --do_not_generate_fec \
  --algorithm SHA256_RSA2048 --key testkey_rsa2048.pem --rollback_index 1788220800 \
  --prop com.android.build.system.os_version:13 \
  --prop com.android.build.system.fingerprint:google/lineage_arm64_bvN/tdgsi_arm64_ab:14/UQ1A.240205.004/crossgate09181258:userdebug/test-keys \
  --prop com.android.build.system.security_patch:2026-03-05
echo "v5.img sha256: $(sha256sum v5.img | cut -d' ' -f1)  (reference build da454296bc68a41595e4d84c013c3dd317f9ce321deec89c473b7300e819c438)"
gzip -1 -c v5.img > v5.img.gz
echo "v5.img.gz size $(stat -c %s v5.img.gz) (reference 1215217745) — push as system_raw.gz for DSU"
