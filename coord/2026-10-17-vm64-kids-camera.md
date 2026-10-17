# Coord note — New HA device: Kids Bedroom Camera (Motorola VM64 / Anyka AK3918EV300)

Date: 2026-10-17 · From: camera-hack workstream · Status: LIVE in Home Assistant

## What's live now (surface these in the Synapse remote / room cards)
- **Camera (video + mic):** `camera.kids_bedroom` ("Kids Bedroom Camera"), area `kids_room`.
  - Local stream, no cloud: `rtsp://<cam-ip>:6667/blinkhd` (H.264 + PCMA mic). Also in go2rtc.
- **MQTT device `vm64_kids_cam`** (one HA device, area `kids_room`), via MQTT discovery:
  - `sensor.kids_bedroom_camera_temperature` — room temp (NTC, read with stock `ftest tempvalue`).
  - `sensor.kids_bedroom_camera_wi_fi_link_quality` — camera Wi-Fi link.
  - `sensor.kids_bedroom_camera_last_boot` — camera last-boot timestamp.
  - `binary_sensor.kids_bedroom_camera_camera_online` — camera reachability (connectivity).

## How it stays up
- Camera runs stock firmware, rooted, on Wi-Fi, reboot-proof. Parent monitor still works.
- Sensor bridge: `jarvis-vm64cam.service` (systemd, Restart=always) on the Jarvis box (10.0.0.60)
  polls the camera every 2 min and publishes via HA's `mqtt.publish` (no broker creds).

## For SynapseOS
- The camera + its sensors are standard HA entities, so the existing HA entity reader picks them up.
- Bind the video tile + temperature to the **Kids Room** channel card in the Synapse remote.

## Still pending (not yet entities — don't bind yet)
- Motion / sound events (app detection is cloud-bound; alt = motion/sound on the RTSP stream via go2rtc/Frigate).
- 2-way talkback (speaker HW works; needs app audio-inject RE).

Full writeup: AceWood619/motorola-vm64-homeassistant (README) and the camera-hack CAMERA-LIVE-STATUS.md.
