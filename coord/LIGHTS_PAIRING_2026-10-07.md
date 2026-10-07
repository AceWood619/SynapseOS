# Light inventory + local↔cloud pairing (HANDS, 2026-10-07 10:36 PDT, read-only via HA /api/template)
Source: `states.light` + `area_name()` + `device_attr()`. **MACs, Bluetooth addresses and Cync account/device IDs are redacted** because the repo is public. HANDS keeps the raw dump on the PC (`C:\gsiL\lights_raw.txt`).

| entity_id | friendly name (HA) | area | device model / mfr | state now | twin |
|---|---|---|---|---|---|
| light.cync_lan_…_22 | LED Strip | Master bedroom | Light Strip (Savant/cync_lan, MQTT) | on | ↔ zz_cloud_bedroom_led_strip |
| light.zz_cloud_bedroom_led_strip | "LED Strip␠" (trailing space) | Master bedroom | GE Lighting (cloud) | **unavailable** | ↔ cync_lan_…_22 |
| light.cync_lan_…_245 | Master Bedroom Lamp Top | Master bedroom | Full Color Light | on | ↔ zz_cloud_mb_lamp_top |
| light.zz_cloud_mb_lamp_top | Master Bedroom Lamp Top | Master bedroom | GE Lighting | **unavailable** | ↔ cync_lan_…_245 |
| light.cync_lan_…_185 | Master Bedroom Switch | Master bedroom | Paddle Switch | off | — (no cloud twin) |
| light.bedroom | Master Bedroom Lamps | Master bedroom | (no device; likely a group/template) | on | — |
| light.master_bedroom_listening_light | Master Bedroom Voice Node … Listening Light | Master bedroom | ESPHome voice node | off | — (exclude from pads: indicator LED) |
| light.cync_lan_…_188 | Kids Bedroom Light | **Kids Room** | Full Color Light | off | ↔ zz_cloud_kids_bedroom_light |
| light.zz_cloud_kids_bedroom_light | "Kids Bedroom Light␠" | Kids Room | GE Lighting | **unavailable** | ↔ cync_lan_…_188 |
| light.kids_bedroom_listening_light | Kids Bedroom Voice Node … Listening Light | Kids Room | ESPHome voice node | off | — (exclude) |
| light.cync_lan_…_102 | Dining Room Light | **Dining Room** | Full Color Light | on | ↔ zz_cloud_dining_room_light |
| light.zz_cloud_dining_room_light | Dining Room Light | Dining Room | GE Lighting | **unavailable** | ↔ cync_lan_…_102 |
| light.cync_lan_…_239 | Hallway Light | Hallway | Full Color Light | on | ↔ zz_cloud_hallway_light |
| light.zz_cloud_hallway_light | Hallway Light | Hallway | GE Lighting | **unavailable** | ↔ cync_lan_…_239 |
| light.lr_lamp | Living Room Lamp | Living Room | (no device) | off | — |

## Findings for BRAIN
1. **Name match works for every twin** once you `strip()` the names (two cloud names have a trailing space). Match on the **device name** (`device_attr(d,'name')`), not the entity name. **No shared hardware ID:** cloud uses `('cync', '<acct>-<id>')`, local uses mac/bluetooth + `('mqtt','<acct>_<n>')`, and the numbers don't line up. So name matching is the way to go.
2. Master bedroom = 2 twin pairs (LED Strip, Lamp Top) + Paddle Switch (local only) + `light.bedroom` group. Unambiguous after strip().
3. **All zz_cloud lights are `unavailable` right now** (the cloud integration is down). Your "prefer local" rule is correct; the cloud path is currently dead.
4. **Exclude `*_listening_light`** (voice-node indicator LEDs) from light pads and the "N lights on" count.
5. **Kids Room and Dining Room DO have lights in those areas** (cync_lan_188 / _102, area set via device). Yet 0.3.28 showed no Kids/Dining channels. ❓ Maybe the channel builder drops a room when its twin is `unavailable`, or its area comes only from the device. Please check against this data.
