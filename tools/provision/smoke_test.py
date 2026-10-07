#!/usr/bin/env python3
"""
Behavioural smoke test for a provisioned Synapse node. Run from the PC:
  python tools/provision/smoke_test.py --node-id livingroom-01 --ip 10.0.0.166 --ha-url http://homeassistant.local:8123
Reads the api_key from ~/.synapse/<node>.json and the HA token from SYNAPSE_HA_TOKEN.
Each check triggers something and then READS BACK the effect.
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


def call(url, key=None, token=None, body=None, method="GET"):
    h = {}
    if key: h["X-Synapse-Key"] = key
    if token: h["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    if data is not None: h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    with urllib.request.urlopen(req, timeout=8) as r:
        return json.loads(r.read().decode() or "null")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--node-id", required=True)
    ap.add_argument("--ip", required=True)
    ap.add_argument("--ha-url", required=True)
    ap.add_argument("--speak", action="store_true", help="also test TTS (a human must listen)")
    a = ap.parse_args()
    key = json.loads((Path.home() / ".synapse" / f"{a.node_id}.json").read_text())["api_key"]
    token = os.environ.get("SYNAPSE_HA_TOKEN", "")
    base = f"http://{a.ip}:8765"
    ok = True

    def check(name, cond, detail=""):
        nonlocal ok
        ok &= bool(cond)
        print(f"[{'PASS' if cond else 'FAIL'}] {name} {detail}")

    check("ping (no auth)", call(f"{base}/api/ping").get("ok"))
    try:
        call(f"{base}/api/status", key="wrong-key-wrong-key")
        check("bad key rejected", False)
    except urllib.error.HTTPError as e:
        check("bad key rejected", e.code == 401)

    call(f"{base}/api/ambient", key, method="POST"); time.sleep(2)
    check("ambient command dims the screen", call(f"{base}/api/status", key)["screen"] == "ambient")
    call(f"{base}/api/wake", key, method="POST"); time.sleep(2)
    check("wake command brings dashboard back", call(f"{base}/api/status", key)["screen"] == "active")

    call(f"{base}/api/presence", key, body={"source": "smoke_test"}, method="POST"); time.sleep(1)
    st = call(f"{base}/api/status", key)
    check("external presence registers", "external" in st["presence"]["signals"], str(st["presence"]))

    if token:
        slug = "".join(c if c.isalnum() else "_" for c in a.node_id.lower()).strip("_")
        while "__" in slug: slug = slug.replace("__", "_")
        states = call(f"{a.ha_url.rstrip('/')}/api/states", token=token)
        mine = {s["entity_id"]: s["state"] for s in states if f"synapse_{slug}_" in s["entity_id"]}
        check("HA entities present", len(mine) >= 8, f"({len(mine)} entities)")
        for k, v in sorted(mine.items()):
            print(f"      {k} = {v}")
    else:
        print("[SKIP] HA entity check (set SYNAPSE_HA_TOKEN)")

    if a.speak:
        r = call(f"{base}/api/speak", key, body={"text": "Synapse node online. This is a speaker test."}, method="POST")
        check("speak accepted", r.get("ok"), "— ask Mason: did you hear it?")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
