#!/usr/bin/env python3
"""Build (or rebuild) the "Synapse Sensors" Home Assistant dashboard: every live sensor in the house,
grouped by room (HA areas) and then by kind, plus the Synapse panel's own sensors.

Additive and idempotent: creates the dashboard `synapse-sensors` if missing, then overwrites ONLY that
dashboard's config. It never edits other dashboards, automations or entities.

Usage (PC):  python tools/ha/build_sensor_dashboard.py --ha http://10.0.0.165:8123 --token-file C:\\secure\\ha_token.txt
Needs: pip install websocket-client
"""
import argparse, json, re
import websocket

KINDS = [
    ("Network", "mdi:lan", lambda e, dc, u: dc == "connectivity" or any(k in e for k in ("router", "wan", "internet", "wifi", "speedtest")) or u in ("Mbit/s", "ms")),
    ("Presence", "mdi:home-account", lambda e, dc, u: dc in ("presence", "occupancy", "motion") or "presence" in e or e.endswith("_home")),
    ("Climate", "mdi:thermometer", lambda e, dc, u: dc in ("temperature", "humidity", "pressure", "atmospheric_pressure", "illuminance") or u in ("°F", "°C")),
    ("Power & energy", "mdi:flash", lambda e, dc, u: dc in ("power", "energy", "battery", "battery_charging", "voltage", "current", "plug") or u in ("W", "kWh", "V", "A")),
    ("Security", "mdi:shield-home", lambda e, dc, u: dc in ("door", "window", "opening", "lock", "safety", "smoke", "gas", "moisture", "tamper", "problem", "sound")),
]


def kind_of(eid, attrs):
    e = eid.lower(); dc = (attrs.get("device_class") or "").lower(); u = attrs.get("unit_of_measurement") or ""
    if any(k in e for k in ("terrain", "gmc", "_car_")): return "Car", "mdi:car"
    if any(k in e for k in ("iphone", "_phone", "pixel")): return "Phones", "mdi:cellphone"
    for name, icon, test in KINDS:
        if test(e, dc, u): return name, icon
    if any(k in e for k in ("update", "version", "cpu", "memory", "disk", "uptime", "_load")): return "System", "mdi:server"
    return "Other", "mdi:dots-horizontal"


class HA:
    def __init__(self, url, token):
        ws_url = re.sub(r"^http", "ws", url.rstrip("/")) + "/api/websocket"
        self.ws = websocket.create_connection(ws_url, timeout=30)
        assert json.loads(self.ws.recv())["type"] == "auth_required"
        self.ws.send(json.dumps({"type": "auth", "access_token": token}))
        r = json.loads(self.ws.recv())
        if r["type"] != "auth_ok": raise SystemExit("HA auth failed")
        self.i = 0

    def call(self, msg):
        self.i += 1; msg = dict(msg, id=self.i); self.ws.send(json.dumps(msg))
        while True:
            r = json.loads(self.ws.recv())
            if r.get("id") == self.i and r.get("type") == "result":
                if not r.get("success"): raise RuntimeError(f"{msg['type']}: {r.get('error')}")
                return r.get("result")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ha", required=True); ap.add_argument("--token-file", required=True)
    ap.add_argument("--node", default="livingroom_01")
    a = ap.parse_args()
    ha = HA(a.ha, open(a.token_file, encoding="utf-8").read().strip())

    states = {s["entity_id"]: s for s in ha.call({"type": "get_states"})}
    areas = {x["area_id"]: x["name"] for x in ha.call({"type": "config/area_registry/list"})}
    ents = {x["entity_id"]: x for x in ha.call({"type": "config/entity_registry/list"})}
    devs = {x["id"]: x for x in ha.call({"type": "config/device_registry/list"})}

    def area_of(eid):
        r = ents.get(eid)
        if not r: return None
        aid = r.get("area_id") or (devs.get(r.get("device_id")) or {}).get("area_id")
        return areas.get(aid) if aid else None

    def usable(eid):
        s = states[eid]; r = ents.get(eid) or {}
        if not eid.startswith(("sensor.", "binary_sensor.")): return False
        if r.get("disabled_by") or r.get("hidden_by"): return False
        if s["state"] in ("unavailable", "unknown", ""): return False
        return "_supports_" not in eid and "_firmware" not in eid

    panel, by_area, by_kind = [], {}, {}
    for eid in sorted(states):
        if not usable(eid): continue
        if f"synapse_{a.node}" in eid: panel.append(eid); continue
        ar = area_of(eid)
        if ar: by_area.setdefault(ar, []).append(eid)
        else:
            k, icon = kind_of(eid, states[eid]["attributes"])
            by_kind.setdefault((k, icon), []).append(eid)

    def card(title, ids, icon=None):
        c = {"type": "entities", "title": f"{title} ({len(ids)})", "state_color": True, "entities": ids}
        if icon: c["icon"] = icon
        return c

    rooms_cards = [card(n, ids, "mdi:door") for n, ids in sorted(by_area.items())]
    kind_order = ["Network", "Presence", "Climate", "Power & energy", "Security", "Phones", "Car", "System", "Other"]
    house_cards = [card(k, by_kind[(k, i)], i) for k in kind_order for (kk, i) in list(by_kind) if kk == k]
    panel_cards = [card("Synapse living room panel", panel, "mdi:tablet-dashboard")] if panel else []
    glance = [e for e in ("binary_sensor.internet", "binary_sensor.xfinity_router", "binary_sensor.someone_home",
                          "binary_sensor.dining_pc_online", "binary_sensor.ps5_power", "binary_sensor.raspberry_pi_power_status")
              if e in states]
    top = [{"type": "glance", "title": "House status", "state_color": True, "entities": glance}] if glance else []

    config = {"title": "Synapse Sensors", "views": [
        {"title": "Rooms", "path": "rooms", "icon": "mdi:floor-plan", "cards": top + panel_cards + rooms_cards},
        {"title": "House", "path": "house", "icon": "mdi:home-analytics", "cards": house_cards},
    ]}

    dashboards = ha.call({"type": "lovelace/dashboards/list"})
    if not any(d.get("url_path") == "synapse-sensors" for d in dashboards):
        ha.call({"type": "lovelace/dashboards/create", "url_path": "synapse-sensors", "title": "Synapse Sensors",
                 "icon": "mdi:access-point-network", "mode": "storage", "show_in_sidebar": True, "require_admin": False})
        print("created dashboard synapse-sensors")
    ha.call({"type": "lovelace/config/save", "url_path": "synapse-sensors", "config": config})
    total = len(panel) + sum(map(len, by_area.values())) + sum(map(len, by_kind.values()))
    print(f"saved: {total} sensors | panel {len(panel)} | rooms {', '.join(f'{k} {len(v)}' for k, v in sorted(by_area.items()))}"
          f" | house {', '.join(f'{k[0]} {len(v)}' for k, v in by_kind.items())}")


if __name__ == "__main__":
    main()
