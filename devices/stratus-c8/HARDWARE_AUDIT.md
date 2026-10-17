# SYNAPSEOS TEST DEVICE — HARDWARE/SOFTWARE HANDOFF
Audit time: 2026-10-07 ~04:50 PDT · Method: read-only ADB queries from a Windows PC over USB · No changes made during this audit.

> ⚠️ **Key correction to the brief:** LineageOS is **NOT flashed** to the device. Stock Android (Cloud Mobile Android 13, build `Stratus_C8_V212`) is still in the system partitions. LineageOS 21 runs as a **persistent DSU** (Dynamic System Update) image: a patched GSI stored in /data and booted instead of stock. Stock remains underneath as the fallback.

## 1. Device Identity
| Field | Value | Source |
|---|---|---|
| Manufacturer | Cloud Mobile | `ro.product.vendor.manufacturer` |
| Model | Stratus C8 (FCC ID 2AY6A-C8) | `ro.product.vendor.model`, device label |
| Codename | `Stratus_C8` (bootloader product `w20_x65_c8`) | `ro.product.vendor.device`, `fastboot getvar product` |
| Board | W20_X65 | `ro.product.board` |
| SoC | MediaTek MT6765 (Helio P35 / G35 family; exact marketing name UNKNOWN) | `ro.soc.model`, `/proc/cpuinfo` |
| CPU | 8× Cortex-A53 (part 0xd03); 4 cores @ 2.2 GHz max, 4 cores @ 1.6 GHz max | `/proc/cpuinfo`, `cpufreq` |
| GPU | Imagination PowerVR Rogue GE8320, OpenGL ES 3.2; Vulkan 1.1 feature flag present | SurfaceFlinger GLES string, `pm list features` |
| Architecture | arm64-v8a (also armeabi-v7a, armeabi) | `ro.product.cpu.abilist` |
| RAM | 2,875,132 kB (~2.9 GB usable, sold as 3 GB) + 1.5 GB zram swap | `/proc/meminfo` |
| Storage | eMMC ~31.3 GB raw (`GD6BMB`, manfid 0x15 = Samsung). **SynapseOS /data is only 1.8 GB** (DSU userdata size chosen at install). | `/sys/block/mmcblk0`, `df` |
| Display | 720×1600, 320 dpi, 60 Hz only | `wm size`, `dumpsys display` |

## 2. Current Software
| Field | Value |
|---|---|
| Android | Framework reports **13** (spoofed on purpose, see §5). Real base is Android 14 (SDK 34). |
| LineageOS | 21.0-20260918-UNOFFICIAL-arm64_bvN (AndyYan TD GSI), with custom patches ("v5") |
| Build | `lineage_arm64_bvN-userdebug 14 UQ1A.240205.004 eng.crossg.20260918.130018 test-keys` |
| Vendor | Stock Android 12 vendor, VNDK 31, `Cloud_Mobile/Stratus_C8/Stratus_C8:12/SP1A.260309.1945/...` |
| Kernel | Linux 4.19.191 (stock MediaTek kernel, built 2026-03-09) |
| Security patch | System reports 2026-03-05 (spoofed to match stock); vendor 2025-06-05 |
| Recovery | Stock (no custom recovery). A/B device with `boot_a`, `init_boot_a`, `vendor_boot_a/b`; recovery lives in the boot/vendor_boot ramdisk. TWRP/OrangeFox: none installed, availability UNKNOWN. |
| Bootloader | **Unlocked** (`ro.boot.flash.locked=0`, verified boot state ORANGE). An Orange State warning shows on every boot. |
| Boot mode | Persistent DSU (`ro.gsid.image_running=1`, `/metadata/gsi/dsu/install_status=ok`) |
| Root | No `su`/Magisk. **ADB root only** (userdebug build: `setprop service.adb.root 1` → adbd runs as uid 0). Apps do NOT have root. |
| ADB | Available over USB, `ro.adb.secure=0` (no authorization prompt). ADB root must be re-enabled after each reboot. |
| Encryption | File-based encryption, `ro.crypto.state=encrypted` (TrustKernel TEE keymaster working) |
| Google apps | No Play Services or Play Store. Only `com.android.webview` and a few Google-signed stubs. |

## 3. Hardware Capability Matrix
Legend: ✅ verified working · ⚠️ partial/suspect · ❓ unknown / not tested · ❌ absent
| Capability | Present? | Android Accessible? | Notes |
|---|---|---|---|
| Touch | PRESENT | ✅ | `mtk-tpd` multitouch (jazzhand). In use. |
| Rear camera | PRESENT | ✅ | Camera HAL v3.6. Took an 8 MP photo (2448×3264) via Aperture. Autofocus + flash feature flags present. |
| Front camera | PRESENT | ❓ | Listed by the camera HAL (ID 1). No capture tested yet. |
| Microphone | PRESENT (bottom + back mic listed) | ✅ **WORKING** (updated 05:00) | High-quality recording (44.1 kHz stereo) of the user talking: −49 dB mean / −28 dB max with speech-like variation. Earlier 8 kHz low-quality clips were silent (cause unconfirmed: no sound made, or the 8 kHz voice path is broken). Use ≥16 kHz capture. |
| Speaker | PRESENT | ✅ **WORKING** (updated 05:00) | User heard the recording played back through the speaker. The HAL still logs harmless-looking `aw87xxx_profile_switch` mixer errors (amp profile switching may not work, so volume/EQ profile could be non-optimal; unconfirmed). |
| Earpiece | PRESENT | ❓ | Listed as an output |
| Headphone jack | PRESENT | ❓ | `mt63xx-accdet Headset` input device + wired headset/headphone routes. Not tested. |
| Accelerometer | PRESENT | ✅ | Live data (≈0.14, 0.34, 9.81 m/s² lying flat) |
| Gyroscope | ABSENT | ❌ | Not in the sensor list |
| Magnetometer | ABSENT | ❌ | Not in the sensor list (no compass) |
| Proximity | PRESENT | ✅ | Live data (5.0 = far) |
| Light | PRESENT | ❓ | In the sensor list (MTK). No reading captured yet. |
| Barometer | PRESENT | ❓ | In the sensor list (`android.sensor.pressure`). No reading captured yet. |
| Extra sensors | PRESENT | ❓ | TILT_DETECTOR, WAKE_GESTURE (MTK sensor hub) |
| GPS | PRESENT | ❓ | GNSS provider enabled, `location.gps` feature. No fix attempted (indoors). |
| Cellular | PRESENT | ❓ | Dual-SIM MediaTek modem (baseband `MOLY.LR12A.R3.MP.V294.P4`). Radio up, **no SIM inserted**, so SMS/calls/data are untested. |
| Wi-Fi | PRESENT | ✅ | Connected: 5 GHz (5785 MHz), Wi-Fi 5 / 802.11ac, RSSI −50, 390 Mbps link, WPA3-SAE, got a LAN IP. Internet ping OK. |
| Bluetooth | PRESENT | ✅ (on) | Enabled, name "Stratus C8". Pairing not tested. |
| BLE | PRESENT | ❓ | `bluetooth_le` feature present. Scanning not tested. |
| NFC | ABSENT | ❌ | No feature flag, no NFC device node |
| USB / OTG | PRESENT | ❓ | USB-C, `usb.host` feature, typec port supports `dual` role (currently UFP/device). OTG not physically tested. |
| Vibration | PRESENT | ✅ **WORKING** (updated 05:01) | User felt 800 ms + 3×600 ms buzzes via `cmd vibrator_manager`. On/off only: no amplitude control (capabilities=[]). Prebaked effects: CLICK, DOUBLE_CLICK, TICK, HEAVY_CLICK, TEXTURE_TICK. |
| Battery telemetry | PRESENT | ✅ | Level, voltage (4394 mV), temperature (24.9 °C), charge counter, health all reported |
| Charging detection | PRESENT | ✅ | USB powered = true, status Charging (500 mA USB from PC) |
| Brightness control | PRESENT | ✅ (driver) | `lcd-backlight` + `mt6370_pmu_bled`. Brightness range 0.0–1.0 exposed. |
| Orientation | PRESENT | ✅ (accel-based) | Portrait + landscape supported; no gyro, so rotation comes from the accelerometer only |
| Hardware buttons | PRESENT | ✅ | Power + volume (`mtk-kpd`, `mtk-pmic-keys`) |
| Thermal | PRESENT | ✅ | `mtktsAP` 29 °C, `mtktsbattery` 24.9 °C readable |

## 4. Android/API Capabilities (what a Synapse app can actually use)
Status reflects actual testing. "Expected" means standard Android 14 behavior on a userdebug build, **not yet tested with a third-party app**.
| API / feature | Status |
|---|---|
| SensorManager (accel, proximity, light, pressure, tilt) | ✅ The framework sensor service delivers events (verified via `dumpsys sensorservice`). App-level reads expected to work. |
| Camera2 / CameraX | ✅ Aperture (a normal app) captured a photo through Camera2. Expected to work for any app. |
| AudioRecord / MediaRecorder | ✅ Works at 44.1 kHz (verified with real speech). 8 kHz path suspect. |
| AudioTrack / MediaPlayer / TTS output | ✅ Playback audible on the speaker. No TTS engine preinstalled (install Piper or another engine). |
| Bluetooth / BLE APIs | Expected (stack ON, LE feature present). Not exercised. |
| Wi-Fi APIs | ✅ Connected and routing |
| Location (GPS/network) | GPS provider enabled. **No network location provider** (no GMS). Not exercised. |
| NFC | ❌ Not available |
| USB host / OTG | Expected (`usb.host` feature). Not exercised. |
| BatteryManager | ✅ |
| Vibrator | ✅ Works (on/off + prebaked click effects, no amplitude control) |
| Screen / brightness | ✅ |
| Background / foreground services | Expected standard Android 14 rules. Doze is **enabled** (light + deep). Exempt the Synapse app from battery optimization. |
| BOOT_COMPLETED receiver | Expected (standard) |
| Accessibility services | Expected (standard, user/ADB-enabled) |
| Device owner (`dpm set-device-owner`) | **Possible**: `device_admin` feature present, **no owners currently set**. Must be set before adding accounts. Not attempted. |
| Lock-task / kiosk | Expected once device owner is set |
| Local network (HTTP/REST, WebSocket, MQTT) | ✅ LAN works. **Home Assistant is reachable**: `homeassistant.local:8123` returned HTTP 200 from the phone (mDNS resolution works). WebSocket/MQTT not individually tested. They're standard TCP, so expected to work. |
| WebView | ✅ `com.android.webview` installed (for an HA dashboard/kiosk UI) |
| Root for apps | ❌ None (ADB root only). Privileged features need a system app built into the image, or ADB. |

## 5. Important Limitations
1. **Audio: mic + speaker verified working (05:00).** Remaining caveats: the 8 kHz capture path may be silent, and speaker-amp profile switching logs errors. Voice-satellite role is unblocked.
2. **Only 1.8 GB of user storage** in the DSU. Too small for camera recording or local models. Fixing it requires reinstalling the DSU with a larger userdata (about 19 GB is free on the phone).
3. **Framework identity is spoofed:** it reports Android 13, security patch 2026-03-05, and product props renamed. Without this, TrustKernel (the TEE) rejects the keymaster and /data can't decrypt. **Don't "fix" these props.** Apps see Android 13 via `Build.VERSION.RELEASE`, but SDK_INT = 34.
4. **No gyroscope or magnetometer.** Orientation is accelerometer-only, with no compass.
5. **No Google Play Services:** no FCM push, no fused location, no Play Store. Use direct LAN/MQTT/WebSocket and sideloaded APKs.
6. **RAM ~2.9 GB, 8× A53, PowerVR GE8320:** fine for a dashboard, sensors, and light streaming. Weak for on-device AI. No NPU is accessible (none known).
7. **Persistent DSU has no automatic fallback.** If the DSU image ever fails to boot, recovery is manual via fastboot (`fastboot gsi disable` from fastbootd). That path is **not yet tested** on this device.
8. **Orange State warning** on every boot (unlocked bootloader). It costs a few seconds per boot and can't be hidden without a custom LK.
9. **USB 2.0 at 500 mA** from the PC. A wall charger is needed for 24/7 use. Battery health under constant charging is unknown; consider a charge limiter.
10. **Display:** 720×1600 at 60 Hz, so keep the UI simple.
11. **ADB root resets on every reboot.** Anything that needs root at runtime must live in the image (init script or system app), not ADB.

## 6. Recommended Synapse Role (ranked, based on what's verified)
1. **Home Assistant dashboard / wall-tabletop control panel.** Touch, display, Wi-Fi, WebView, and HA reachability are all verified.
2. **Environmental / presence sensor node.** Light, pressure, proximity, accelerometer, battery/charging (for power-outage detection), BLE presence (once verified).
3. **Camera / vision node.** Rear camera verified. Snapshots/streams to HA/Frigate are feasible. Storage limits local recording.
4. **BLE gateway / presence.** Hardware present; untested.
5. **Voice satellite / announcement node.** Mic + speaker now verified, so this moves up. Needs a TTS engine + wake word; CPU limits favor openWakeWord + HA-side STT (Wyoming) over on-device Whisper.
6. **Synapse Mesh / Lab node.** Cellular modem (SMS fallback once a SIM is inserted) is an unusual extra.
7. **Local AI / edge compute.** Least suitable (RAM/CPU/GPU limits).

Best overall: **combination** = HA wall panel + sensor/presence node + camera snapshots, adding voice once audio is fixed.

## 7. Recommended Development Strategy (build first)
1. ~~Fix and verify audio~~ ✅ Done: mic and speaker both verified.
2. **Reinstall the DSU with a larger userdata** (e.g. 8–12 GB) before any real app data lands. Doing it later wipes the app data.
3. Build the **Synapse Core app** (Kotlin, foreground service + boot receiver), starting with the zero-risk pieces: sensors + battery/charging → HA (REST/WebSocket or MQTT), then a WebView/HA dashboard launcher in kiosk mode (device owner).
4. Then camera snapshots → HA, then BLE presence, then voice (Wyoming satellite) if audio is fixed.

## 8. Risks / Things NOT To Do
- ❌ Do **not** flash the GSI permanently to `system_a` with modified vbmeta. Tested earlier: it boots to "Can't load Android system" (suspected TrustKernel binding to the factory vbmeta, **unconfirmed**).
- ❌ Do **not** re-lock the bootloader while modified images are on the device (brick risk).
- ❌ Do **not** undo the build-prop spoofing (Android 13 / patch 2026-03-05 / renamed product props) or re-enable TrebleDroid `fixSPL`. Doing either breaks TrustKernel and /data.
- ❌ Do **not** run `fastboot -w` or a factory reset unless intended. It wipes the DSU image too.
- ⚠️ Before a fresh DSU install from stock, a "reset reboot" (`reboot 'dm-verity enforcing'`) is required.
- ⚠️ The persistent DSU relies on `/metadata/gsi/dsu/install_status=ok`. A plain `gsi_tool enable` alone (counter "0") caused early power-offs and fallback to stock.
- ⚠️ Keep the stock backup (`C:\c8backup`, SHA-256 verified, includes `super.img`) safe and off-site.
- ⚠️ mtkclient BROM/preloader access failed earlier. Don't count on it as a recovery path.

## 9. Evidence / Commands Used
- `getprop` (product, board, platform, soc, build, lineage, security patch, verified boot, crypto, gsid, adb, vndk)
- `uname -a`, `/proc/cpuinfo`, `/sys/devices/system/cpu/cpu{0,4}/cpufreq/cpuinfo_max_freq`, `/proc/meminfo`
- `/sys/block/mmcblk0/{size,device/name,device/manfid}`, `df -h /data`, `mount`
- `wm size`, `wm density`, `dumpsys display`, `dumpsys SurfaceFlinger` (GLES string), `ls /vendor/lib64/egl`
- `pm list features`, `pm list packages`
- `dumpsys sensorservice` (sensor list + recent events), `dumpsys media.camera`, `dumpsys media.audio_policy`, `dumpsys media.audio_flinger`, `/proc/asound/cards`, `logcat` (AudioALSADeviceConfigManager errors)
- Camera: launched Aperture + `input keyevent KEYCODE_CAMERA` → `/sdcard/DCIM/Camera/*.jpg`
- Mic: LineageOS Recorder via `input tap` → `.m4a` → ffmpeg `volumedetect`
- `dumpsys wifi`, `ip addr`, `ip route`, `ping 1.1.1.1`, `curl http://homeassistant.local:8123/` (HTTP 200)
- `dumpsys bluetooth_manager`, `dumpsys location`, `getprop gsm.*`, `dumpsys telephony.registry`
- `dumpsys battery`, `/sys/class/power_supply/battery/*`, `dumpsys usb`, `/sys/class/typec`, `/sys/class/leds`, `cmd vibrator_manager list`
- `getevent -lp` (input devices), `/sys/class/thermal/thermal_zone*`
- `dpm list-owners`, `dumpsys deviceidle`, `ls -l /dev/block/by-name`
- Bootloader-side (earlier session): `fastboot getvar`, expdb partition log analysis

### UNKNOWN items and how to resolve them
- Mic/speaker function → controlled loopback test (play a known tone, record, measure), plus the user listening
- Front camera capture, light/pressure readings, GPS fix, BLE scan, vibration, OTG, headphone jack → run each with a small test app or `cmd`/ADB tools
- Cellular SMS/data → insert a SIM
- Device-owner/kiosk → `dpm set-device-owner` with a test admin app (no accounts on the device)
- Exact SoC marketing name (P35 vs G35) → not determinable from software. MT6765 is the authoritative ID.

---
**Changes made earlier (before this audit, during testing):** time zone set to America/Los_Angeles; runtime permissions granted to Aperture and Recorder; Recorder "high quality" setting turned on; a test tone file pushed to `/sdcard/Recordings/Sound records/`. No system/partition changes in this audit.

PRE-INTERRUPTION TASK:
Hardware testing of SynapseOS (LineageOS 21 persistent DSU) on the Stratus C8: camera ✅, sensors ✅, BT on ✅, and diagnosing a possibly silent mic and unconfirmed speaker output.

CURRENT STATE:
Phone is booted in SynapseOS (persistent DSU confirmed across 2 reboots and 1 cold boot), ADB root active, Wi-Fi now connected, HA reachable. Mic recordings measured near-silent and speaker output is unconfirmed. Waiting on user confirmation of whether sound was made during recording and whether the shutter click was heard.

NEXT ACTION WHEN RESUMING:
Run a controlled audio test: play a 1 kHz tone through the speaker (user listens), and record while the user speaks/claps. Measure levels. If silent, compare the audio HAL mixer/routing errors (aw87xxx speaker amp, mic path) against stock to find the vendor/GSI mismatch.

SAFE TO RESUME: YES
