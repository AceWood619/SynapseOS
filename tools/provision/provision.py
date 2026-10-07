#!/usr/bin/env python3
"""
Provision a phone as a Synapse node. Idempotent and resumable: safe to re-run.

Run on Mason's PC (HANDS chat). Needs: Python 3.9+, adb (platform-tools), the phone
reachable over ADB (USB or Wi-Fi), and a Home Assistant long-lived token.

The token is NEVER taken from the command line (shell history). Give it via the
SYNAPSE_HA_TOKEN env var or --token-file. Per-node secrets (api_key) are kept in
~/.synapse/<node>.json on the PC, never in the repo.

Example (PowerShell):
  $env:SYNAPSE_HA_TOKEN = Get-Content C:\\secure\\ha_token.txt
  python tools\\provision\\provision.py --serial 10.0.0.166:5555 --node-id livingroom-01 `
      --room "Living Room" --ha-url http://homeassistant.local:8123 --device-owner

Every step is checked by reading the result back (not by exit codes).
"""
import argparse
import hashlib
import json
import os
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

PKG = "com.acewood.synapse.core"
ACTIVITY = f"{PKG}/.MainActivity"
ADMIN = f"{PKG}/.AdminReceiver"
EXT_DIR = f"/sdcard/Android/data/{PKG}/files"
REPO = Path(__file__).resolve().parents[2]

results = []  # (step, ok, detail)


def step(name, ok, detail=""):
    results.append((name, ok, detail))
    mark = "PASS" if ok else "FAIL"
    print(f"[{mark}] {name}" + (f" — {detail}" if detail else ""), flush=True)
    return ok


class Adb:
    def __init__(self, adb, serial):
        self.adb, self.serial = adb, serial

    def run(self, *args, check=False, timeout=120):
        cmd = [self.adb] + (["-s", self.serial] if self.serial else []) + list(args)
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        out = (p.stdout + p.stderr).strip()
        if check and p.returncode != 0:
            raise RuntimeError(f"{' '.join(cmd)} -> {out}")
        return p.returncode, out

    def sh(self, cmd, timeout=60):
        return self.run("shell", cmd, timeout=timeout)[1]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def http_json(url, headers=None, data=None, method=None, timeout=8):
    req = urllib.request.Request(url, headers=headers or {}, data=data, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, json.loads(r.read().decode() or "null")


def load_secrets(node):
    p = Path.home() / ".synapse" / f"{node}.json"
    if p.exists():
        return p, json.loads(p.read_text())
    return p, {}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--adb", default="adb", help="path to adb(.exe)")
    ap.add_argument("--serial", default=None, help="adb serial, e.g. 10.0.0.166:5555")
    ap.add_argument("--node-id", required=True)
    ap.add_argument("--room", required=True)
    ap.add_argument("--ha-url", required=True, help="as seen FROM THE PHONE, e.g. http://homeassistant.local:8123")
    ap.add_argument("--ha-url-pc", default=None, help="HA URL as seen from this PC, if different (for verification)")
    ap.add_argument("--token-file", default=None)
    ap.add_argument("--dashboard-path", default="/lovelace/0")
    ap.add_argument("--idle-seconds", type=int, default=120)
    ap.add_argument("--pin", default=None, help="settings PIN (4+ digits). Default: keep existing or generate")
    ap.add_argument("--apk", default=str(REPO / "builds" / "synapse-core-latest.apk"))
    ap.add_argument("--extra-apk", action="append", default=[], help="other APKs to install (SherpaTTS, HA Companion minimal…)")
    ap.add_argument("--device-owner", action="store_true", help="make Synapse the device owner (kiosk lockdown)")
    ap.add_argument("--no-kiosk", action="store_true", help="don't lock the screen to Synapse")
    ap.add_argument("--ble", action="append", default=[], metavar="ID=NAME",
                    help="known BLE device for presence: MAC (aa:bb:..) or iBeacon uuid:major:minor, e.g. --ble \"aa:bb:cc:dd:ee:ff=Mason watch\"")
    ap.add_argument("--camera", choices=["back", "front"], default=None,
                    help="enable /api/snapshot from this camera (off by default for privacy)")
    ap.add_argument("--companion", action="append", default=[],
                    help="package allowed through kiosk + opened once after boot (e.g. com.example.ava for Ava voice)")
    a = ap.parse_args()

    token = os.environ.get("SYNAPSE_HA_TOKEN", "").strip()
    if a.token_file:
        token = Path(a.token_file).read_text().strip()
    if len(token) < 20:
        sys.exit("No HA token. Set SYNAPSE_HA_TOKEN or pass --token-file (never paste it on the command line).")

    sec_path, sec = load_secrets(a.node_id)
    sec.setdefault("api_key", secrets.token_hex(16))
    if a.pin:
        sec["pin"] = a.pin
    sec.setdefault("pin", f"{secrets.randbelow(10**6):06d}")
    sec_path.parent.mkdir(parents=True, exist_ok=True)
    sec_path.write_text(json.dumps(sec, indent=2))
    print(f"Node secrets (api_key, PIN) kept in {sec_path}. PIN = {sec['pin']}")

    adb = Adb(a.adb, a.serial)

    # 1. connect. Provision runs as the ADB *shell* user (uid 2000), never root: a config.json
    # pushed while adbd is root lands root-owned in the app's external dir and the app gets EACCES
    # (found on 0.3.11). None of the commands below need root, so drop root up front.
    if a.serial and ":" in a.serial:
        subprocess.run([a.adb, "connect", a.serial], capture_output=True, text=True, timeout=20)
    if adb.sh("id -u").strip() == "0":
        adb.run("unroot", timeout=20)
        time.sleep(2)
        if a.serial and ":" in a.serial:
            subprocess.run([a.adb, "connect", a.serial], capture_output=True, text=True, timeout=20)
        step("adb dropped to shell user (so config.json is app-readable)", adb.sh("id -u").strip() != "0", "uid " + adb.sh("id -u").strip())
    model = adb.sh("getprop ro.product.vendor.model")
    if not step("phone reachable over adb", bool(model) and "error" not in model.lower(), model):
        return finish()

    # 2. APK integrity
    apk = Path(a.apk)
    meta_p = apk.with_suffix(".json")
    if apk.exists() and meta_p.exists():
        meta = json.loads(meta_p.read_text())
        step("APK sha256 matches CI record", sha256(apk) == meta.get("sha256"), f"version {meta.get('version')}")
    elif not step("APK present", apk.exists(), str(apk)):
        return finish()

    # 3. install (+ extras)
    for extra in a.extra_apk:
        rc, out = adb.run("install", "-r", "-g", extra, timeout=300)
        step(f"install {Path(extra).name}", "Success" in out, out.splitlines()[-1] if out else "")
    rc, out = adb.run("install", "-r", "-g", str(apk), timeout=300)
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out:
        step("install synapse-core", False, "signature differs from installed copy: uninstall it first (loses its config)")
        return finish()
    vers = adb.sh(f"dumpsys package {PKG} | grep versionName")
    step("install synapse-core", "Success" in out and "versionName" in vers, vers.strip())

    # 4. battery-optimization exemption + notifications
    adb.sh(f"dumpsys deviceidle whitelist +{PKG}")
    step("doze exemption", PKG in adb.sh("dumpsys deviceidle whitelist"))
    for comp in a.companion:
        if comp not in adb.sh(f"pm list packages {comp}"):
            step(f"companion {comp} installed", False, "install it first (e.g. --extra-apk Ava.apk)")
            continue
        adb.sh(f"dumpsys deviceidle whitelist +{comp}")
        adb.sh(f"pm grant {comp} android.permission.RECORD_AUDIO")
        adb.sh(f"pm grant {comp} android.permission.POST_NOTIFICATIONS")
        step(f"companion {comp} ready", comp in adb.sh("dumpsys deviceidle whitelist"))
    adb.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    if a.ble:
        for perm in ("BLUETOOTH_SCAN", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"):
            adb.sh(f"pm grant {PKG} android.permission.{perm}")
        adb.sh("cmd location set-location-enabled true")
        adb.sh("svc bluetooth enable")
        step("BLE ready (location services on)", "true" in adb.sh("cmd location is-location-enabled").lower())
    if a.camera:
        adb.sh(f"pm grant {PKG} android.permission.CAMERA")
        step("camera permission", "CAMERA: granted=true" in adb.sh(f"dumpsys package {PKG} | grep 'android.permission.CAMERA'"))

    # 5. start the app once so it creates its external files dir, then push config
    adb.sh(f"am start -n {ACTIVITY}")
    time.sleep(4)
    cfg = {
        "node_id": a.node_id, "room": a.room, "ha_url": a.ha_url.rstrip("/"), "ha_token": token,
        "dashboard_path": a.dashboard_path, "idle_seconds": a.idle_seconds, "pin": sec["pin"],
        "api_port": 8765, "api_key": sec["api_key"], "kiosk": not a.no_kiosk,
        "companion_apps": a.companion,
        "ble_known": dict(x.split("=", 1) if "=" in x else (x, x) for x in a.ble),
        "camera": a.camera or "",
    }
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as tf:
        json.dump(cfg, tf)
        tmp = tf.name
    try:
        adb.sh(f"mkdir -p {EXT_DIR}; rm -f {EXT_DIR}/config.result.json")
        rc, out = adb.run("push", tmp, f"{EXT_DIR}/config.json")
    finally:
        os.unlink(tmp)
    step("config pushed", rc == 0, out.splitlines()[-1] if out else "")

    # 6. wait for the app to import it (it deletes config.json and writes config.result.json)
    res = None
    for _ in range(20):
        time.sleep(3)
        raw = adb.sh(f"cat {EXT_DIR}/config.result.json 2>/dev/null")
        if raw.startswith("{"):
            res = json.loads(raw)
            break
    leftover = adb.sh(f"ls {EXT_DIR}/config.json 2>/dev/null")
    imported = bool(res and res.get("ok"))
    step("config imported by app", imported, json.dumps(res.get("errors") if res and not res.get("ok") else "") if res else "no result file after 60 s")
    if not imported and "config.json" in leftover:
        adb.sh(f"rm -f {EXT_DIR}/config.json")   # never leave the token on shared storage after a failure
        leftover = adb.sh(f"ls {EXT_DIR}/config.json 2>/dev/null")
    step("token file not left on shared storage", "config.json" not in leftover)

    # 7. home app + device owner
    if a.device_owner:
        owners = adb.sh("dpm list-owners")
        if PKG in owners:
            step("device owner", True, "already set")
        else:
            accounts = adb.sh("dumpsys account | grep -i 'Accounts:'")
            out = adb.sh(f"dpm set-device-owner {ADMIN}")
            step("device owner", PKG in adb.sh("dpm list-owners"), out.splitlines()[-1] if out else accounts)
    else:
        adb.sh(f"cmd package set-home-activity {ACTIVITY}")
    adb.sh(f"am start -n {ACTIVITY}")

    # 8. verify the node from the PC: control API, then HA entities (observed, not assumed)
    last = None
    ip = adb.sh("ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2").strip()
    status = None
    for _ in range(10):
        time.sleep(3)
        try:
            _, status = http_json(f"http://{ip}:8765/api/status", {"X-Synapse-Key": sec["api_key"]})
            break
        except Exception as e:
            last = e
    step("control API answers", status is not None, f"http://{ip}:8765" if status else str(last))
    if status:
        print(json.dumps({k: status.get(k) for k in ("version", "ha", "screen", "presence", "tts", "device_owner")}, indent=1))

    ha = (a.ha_url_pc or a.ha_url).rstrip("/")
    slug = "".join(c if c.isalnum() else "_" for c in a.node_id.lower()).strip("_")
    while "__" in slug:
        slug = slug.replace("__", "_")
    ent = f"sensor.synapse_{slug}_battery"
    seen = None
    for _ in range(12):
        time.sleep(5)
        try:
            _, seen = http_json(f"{ha}/api/states/{ent}", {"Authorization": f"Bearer {token}"})
            break
        except urllib.error.HTTPError as e:
            if e.code != 404:
                last = e
        except Exception as e:
            last = e
    step(f"HA has {ent}", seen is not None, f"state={seen.get('state')} updated={seen.get('last_updated')}" if seen else "not seen after 60 s")
    return finish()


def finish():
    fails = [r for r in results if not r[1]]
    print("\n==== SUMMARY ====")
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'}  {name}")
    print(f"{len(results) - len(fails)}/{len(results)} passed")
    print("\nStill needs a human: confirm the dashboard shows, dims after the idle time, and wakes on touch or a hand wave.")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
