#!/usr/bin/env python3
"""
Install/update the SynapseOS boot payload (/data/adb/synapse) on the phone. Idempotent.
Needs adb root (userdebug): run `adb root` first, or tools/pc/uroot.py.
  python os/v6/install_payload.py --adb C:\\platform-tools\\adb.exe --serial 10.0.0.166:5555 \
      --adb-key %USERPROFILE%\\.android\\adbkey.pub [--adb-key <uadb.py key .pub>] [--run-now]
--adb-key: public key(s) allowed to use ADB once v6 turns on auth. Include EVERY key you use
(adb.exe and the python adb-shell key) or that tool will be refused over Wi-Fi.
USB still works with the on-screen "Allow USB debugging" prompt as a fallback.
"""
import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent / "data"
D = "/data/adb/synapse"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--adb", default="adb")
    ap.add_argument("--serial")
    ap.add_argument("--adb-key", action="append", default=[], help=".pub key file(s) to authorize")
    ap.add_argument("--run-now", action="store_true", help="run boot.sh immediately (no reboot needed)")
    a = ap.parse_args()
    base = [a.adb] + (["-s", a.serial] if a.serial else [])

    def sh(cmd):
        p = subprocess.run(base + ["shell", cmd], capture_output=True, text=True, timeout=60)
        return (p.stdout + p.stderr).strip()

    if sh("id -u") != "0":
        sys.exit("adb is not root. Run `adb root` (or tools/pc/uroot.py) first.")
    sh(f"mkdir -p {D}; chmod 0700 {D}; chown root:root {D}")
    for name in ("boot.sh", "chargectl.sh"):
        # Strip Windows line endings (a CRLF checkout breaks /system/bin/sh), then push.
        data = (HERE / name).read_bytes().replace(b"\r\n", b"\n")
        with tempfile.NamedTemporaryFile("wb", delete=False, suffix=".sh") as tf:
            tf.write(data)
        subprocess.run(base + ["push", tf.name, f"{D}/{name}"], check=True, capture_output=True)
        Path(tf.name).unlink()
        sh(f"chmod 0700 {D}/{name}; chown root:root {D}/{name}")
    if a.adb_key:
        keys = "".join(Path(k).read_text().strip() + "\n" for k in a.adb_key)
        with tempfile.NamedTemporaryFile("w", delete=False, suffix=".keys") as tf:
            tf.write(keys)
        subprocess.run(base + ["push", tf.name, f"{D}/adb_keys"], check=True, capture_output=True)
        Path(tf.name).unlink()
        sh(f"chmod 0600 {D}/adb_keys")
    print(sh(f"ls -la {D}"))
    hook = sh("grep -c 'data/adb/synapse/boot.sh' /system/bin/phh-on-boot.sh")
    print(f"v6 boot hook in system image: {'YES' if hook.strip() == '1' else 'NO (v5 image: payload installed but will not auto-run)'}")
    if a.run_now:
        print(sh(f"sh {D}/boot.sh; tail -n 15 {D}/boot.log"))
        print("charge state:", sh("getprop sys.synapse.charge"), "| current_cmd:", sh("cat /proc/mtk_battery_cmd/current_cmd"))


if __name__ == "__main__":
    main()
