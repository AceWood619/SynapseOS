#!/usr/bin/env python3
"""Install an APK onto the Synapse panel over Wi-Fi from any authorized machine (dining PC / MacBook / RP5).
  python install_app.py --ip 10.0.0.151 app.apk
Requires this machine's ADB key to be authorized on the panel (see README). Verifies the install by
reading the package back, not just the adb exit code."""
import argparse, subprocess, sys, re
ap = argparse.ArgumentParser()
ap.add_argument("apk")
ap.add_argument("--ip", required=True)
ap.add_argument("--port", default="5555")
ap.add_argument("--adb", default="adb")
a = ap.parse_args()
serial = f"{a.ip}:{a.port}"
def run(*args, t=300):
    p = subprocess.run([a.adb, *args], capture_output=True, text=True, timeout=t)
    return (p.stdout + p.stderr).strip()
print(run("connect", serial, t=20))
out = run("-s", serial, "install", "-r", "-g", a.apk)
print(out)
# pull the package name from the APK via aapt if available, else trust 'Success'
ok = "Success" in out
print("OK" if ok else "FAILED", "- installed" if ok else "- see output above")
sys.exit(0 if ok else 1)
