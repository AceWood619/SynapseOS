# Provisioning a Synapse node
Runs on the PC (HANDS). Takes a phone that's running SynapseOS and turns it into a working node in one command.

## Steps
1. `git pull` (gets the newest `builds/synapse-core-latest.apk`).
2. Put the HA long-lived token in a file **outside the repo**, e.g. `C:\secure\ha_token.txt`. Create a dedicated one in HA: Profile → Security → Long-lived tokens, named `synapse-<node>`.
3. Run:
   ```powershell
   python tools\provision\provision.py --adb C:\platform-tools\adb.exe --serial 10.0.0.166:5555 `
     --node-id livingroom-01 --room "Living Room" --ha-url http://homeassistant.local:8123 `
     --token-file C:\secure\ha_token.txt --device-owner
   ```
   Add `--extra-apk <SherpaTTS.apk>` to install a TTS engine at the same time.
4. Read the PASS/FAIL summary. Then check by eye: the dashboard shows, dims to a clock after `--idle-seconds` (default 120), and wakes on touch or a hand over the top of the phone.
5. Optional behaviour test: `python tools\provision\smoke_test.py --node-id livingroom-01 --ip 10.0.0.166 --ha-url http://homeassistant.local:8123 --speak`

## What it sets up
| Thing | Where |
|---|---|
| App + doze exemption | phone |
| Config (token, api_key, PIN) | app-private storage. The pushed file is deleted after import. |
| api_key + PIN copy | `~/.synapse/<node>.json` on the PC (**not** in git) |
| Device owner (`--device-owner`) | locks the phone to Synapse: home app, no status bar, no lock screen, screen on while plugged in |

## Getting out of kiosk mode
- 5 quick taps in the **top-left corner**, then the PIN, opens the Synapse settings screen.
- There, **Leave kiosk** gives you normal Android Settings, and **Remove device owner** fully undoes `--device-owner`.
- ADB keeps working the whole time.

## Entities created in HA
`sensor.synapse_<node>_*`: battery, battery_temp, battery_voltage, power_source, illuminance, pressure, wifi_rssi, screen, uptime, mem_free, cpu_temp, status.
`binary_sensor.synapse_<node>_*`: charging, proximity, occupancy (with `confidence` and `signals` attributes).

⚠️ These are pushed through HA's REST states API. HA forgets them on restart, and the node re-sends them within one heartbeat (60 s). That's fine for dashboards and automations. Use `ha/packages/synapse.yaml` for an "online" sensor.
