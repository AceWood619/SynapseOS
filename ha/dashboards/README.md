# Synapse HA dashboards
Wall-panel Lovelace dashboards tuned for the phone (tall, dark, glanceable, big touch targets).
Built-in cards only — no HACS required.

## Generate for a node
```
python tools/provision/dashboard.py --node-id livingroom-01 --room "Living Room" --weather weather.home \
    > ha/dashboards/synapse_livingroom_01.yaml
```

## Install in Home Assistant
1. Settings → Dashboards → **Add dashboard** → New dashboard from scratch → give it a name.
2. Open it → top-right pencil (Edit) → three-dot menu → **Raw configuration editor**.
3. Paste the file's contents → Save.
4. In the Synapse node's config, point `dashboard_path` at it, e.g. `/synapse-panel/panel`
   (re-run provision.py with `--dashboard-path`, or edit and `POST /api/reload`).

## What's live vs TODO
- **Live now** (correct entity ids): the node's own sensors — presence, light, pressure,
  Wi-Fi, battery, charging, memory, status, nearby BLE, panel screen state.
- **TODO (point at your entities):** the three scene buttons (`scene.evening/movie/good_night`)
  and the room light (`light.<room>`). Edit those lines to match your house.

The node also exposes `script.synapse_<node>_say/wake/ambient` (see tools/provision/ha_package.py)
so HA automations can speak on the panel, wake it, or dim it.
