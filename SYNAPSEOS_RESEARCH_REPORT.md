# SynapseOS — Research Report (fills gaps in Handoff v2.7 + Hardware Audit)
Date: 2026-10-07 · Scope: everything both handoffs marked UNKNOWN or left as "research later"
Legend: **CONFIRMED** = checked against source code or official docs · **INFERRED** = my reasoning, needs verifying on the device

---

## 0. TL;DR — the 6 findings that change the plan

| # | Finding | Impact |
|---|---|---|
| 1 | **The 1.8 GB /data is just the DSU default size (2 GiB), not a choice made at install.** `gsi_tool install` defaults `--userdata-size` to 2 GiB. | Fix = reinstall with `--userdata-size` / `KEY_USERDATA_SIZE`. Do it **before** any app data lands. |
| 2 | **The official HA Companion app (minimal / no-Google flavor) now has on-device wake word** (microWakeWord, HA 2026.3+). The code is in the shared `main` source set, not the `full` (Google) flavor, so the no-Google build gets it too. | Phase 3 (voice) may need **zero custom code**. Just test it once the audio works. |
| 3 | **Ava (brownard/Ava, Apache-2.0)** turns an Android 8+ device into an **ESPHome voice satellite** and ships a reusable **Kotlin ESPHome native-API module** (`esphomeproto`). It uses bundled LiteRT (no Google services needed). | Synapse Core could **speak the ESPHome protocol** instead of a custom MQTT setup. HA would auto-discover the phone as a device. |
| 4 | **Ava-Pro (knoop7)** already does a BLE proxy, kiosk, camera, sensors and intercom on Android 5–16, but its license is **CC BY-NC-ND** (no forks, no commercial use). | Fine to *use* for testing. **Don't fork it or ship it inside synapseOS.** Fork brownard/Ava instead. |
| 5 | **FreeKiosk (MIT, Android 8+)** already does Device Owner kiosk mode, a REST API with 40+ endpoints, and MQTT with HA discovery. | Covers Phase 1's kiosk work package. Use it as a reference, or as a stopgap while Synapse Core is built. |
| 6 | **The `aw87xxx … Unsupport mixer ctl type 6` error may be harmless noise.** In tinyalsa, type 6 = `MIXER_CTL_TYPE_UNKNOWN` (INFERRED from the tinyalsa enum). The vendor audio HAL runs against VNDK-v31 libraries either way. | **Before chasing it, boot stock and check whether stock logs the same error.** If it does, it isn't the cause. Procedure in §1. |

---

## 1. Audio (mic silent / speaker unconfirmed): the #1 blocker

### What the research found
- No public fix exists for this exact device or error. Searches across XDA, Hovatek, the TrebleDroid source and the LineageOS issue tracker turned up nothing for aw87xxx on GSI.
- TrebleDroid (android-14.0 branch) has **no MediaTek-specific mic/speaker property**. The audio-related `persist.sys.phh.*` switches that exist are:

| Property / PHH Settings toggle | What it does | Worth trying? |
|---|---|---|
| `persist.sys.phh.caf.audio_policy` | Swaps which `audio_policy_configuration.xml` is bind-mounted ("Use alternate audio policy") | **Yes.** It fixed silent-audio cases on other devices |
| `persist.sys.phh.disable_audio_effects` | Sets `ro.audio.ignore_effects`, bypassing vendor DSP effects | **Yes.** Cheap to try |
| `persist.sys.phh.disable_soundvolume_effect` | Blanks `libvolumelistener.so` | Maybe |
| "Disable Voice Call In route" (MTK misc) | Voice-call routing only | No |
| Force-disable A2DP offload | Bluetooth audio only | No |

Each toggle restarts `audioserver` and the audio HAL, so you can A/B test without rebooting.

- tinyalsa control-type enum: BOOL=0, INT=1, ENUM=2, BYTE=3, IEC958=4, INT64=5, **UNKNOWN=6**. So the HAL found a control it didn't understand, or a control that failed type lookup. INFERRED: the aw87xxx speaker amp profile control is probably an enum that MTK's config parser doesn't handle. That would also happen on stock.

### Diagnostic sequence (in order, stop when found)
1. **Baseline on stock.** Boot stock and run `adb logcat -b all | grep -iE "aw87|mixer ctl|AudioALSA"` while playing audio. If the same error appears, it's harmless and you can move on.
2. **SELinux check on the DSU.** `getenforce`, then record and run `logcat | grep avc | grep -iE "audio|mtk|vendor"`. Denials on `/dev/snd/*`, `/proc/asound`, `vendor.audio` props or the `audio_param` files are the classic GSI mic killer.
3. **Bypass the HAL (ADB root).** List controls with `tinymix` and capture with `tinycap /data/local/tmp/t.wav -D 0 -d <pcm#> -c 1 -r 48000 -b 16`, using the PCM from `cat /proc/asound/pcm`. If `tinycap` hears you, the hardware is fine and the problem is in the HAL or policy. If `tinycap` is missing, push a static arm64 build.
   - ⚠️ Writing mixer controls with `tinymix` changes amp state. Keep the volume low and change one control at a time. Some amps can be damaged if driven with no load. INFERRED risk, but cheap to avoid.
4. **Toggle the PHH properties** from the table above, one at a time, re-testing between each.
5. **Diff the configs** between stock and the DSU: `/vendor/etc/audio_policy_configuration.xml`, `/vendor/etc/audio_device.xml` (MTK), `/vendor/etc/audio_param/`, plus anything TrebleDroid bind-mounts over `/vendor/etc/audio*` (check `mount | grep audio`).
6. **Try a different input source.** Test `MIC`, `VOICE_RECOGNITION` and `UNPROCESSED`. MTK routes these differently, and a voice-satellite app will typically use `VOICE_RECOGNITION`.

### Controlled test (unchanged from the handoff, refined)
Play a 1 kHz tone at about 50% volume through the speaker while recording, holding the phone 10 cm from a second phone, and run ffmpeg `volumedetect` on the result.
- Pass: mean level above −40 dB.
- Fail: −85 dB means the input path is dead.
- Run it once on stock too. That's your reference number.

---

## 2. Storage: reinstall the DSU bigger (do this second)

**CONFIRMED (AOSP gsi_tool source):** `gsi_tool install --gsi-size <bytes> --userdata-size <bytes>`. `--userdata-size` defaults to 2 GiB, which explains the 1.8 GB you're seeing. The tool refuses to run while a GSI is booted ("use `gsi_tool disable` or `wipe` and reboot first").

Two install paths:
- **From stock with the DynamicSystem activity (no root needed):**
  ```
  adb shell am start-activity \
    -n com.android.dynsystem/com.android.dynsystem.VerificationActivity \
    -a android.os.image.action.START_INSTALL \
    -d file:///storage/emulated/0/Download/system_raw.gz \
    --el KEY_SYSTEM_SIZE <bytes of raw system.img> \
    --el KEY_USERDATA_SIZE 10737418240     # 10 GiB
  ```
- **DSU Sideloader app (VegaBobo).** It has a userdata-size field.

Checklist:
- [ ] Use the **same patched "v5" system image**. A fresh GSI loses the props spoofing and breaks TrustKernel / `/data` decryption.
- [ ] Do the "reset reboot" (`reboot 'dm-verity enforcing'`) first, per your handoff.
- [ ] Re-create `/metadata/gsi/dsu/install_status=ok` for persistent boot, the same way you did before.
- [x] **Done on device (2026-10-07): 7 GiB.** Correction to my earlier 10 GiB suggestion: gsid refuses any install that leaves < ~8.8 GB free on stock /data, so ~7.97 GB is the ceiling. See `devices/stratus-c8/BOOT_AND_INSTALL_NOTES.md`.
- [ ] Everything currently in DSU `/data` gets wiped. Today that's just test files.

**Untested recovery path:** from fastbootd (`fastboot reboot fastboot`), run `fastboot gsi disable` (boot stock next time) or `fastboot gsi wipe` (delete the DSU). Test `gsi disable` once **on purpose** while everything works, so you know the escape hatch works before you need it.

---

## 3. Build-vs-reuse map (what already exists, no Google services needed)

| Synapse need | Existing option | License | Google services needed? | Verdict |
|---|---|---|---|---|
| HA sensors (battery, light, pressure, proximity, Wi-Fi, etc.) | **HA Companion app, minimal flavor** (GitHub APK or F-Droid) | Apache-2.0 | No | **Use now** for Phase 2. Loses location tracking, Matter commissioning and activity sensors. Notifications arrive over the local WebSocket instead of push |
| Wake word + Assist | Same app: microWakeWord ("Hey Nabu / Jarvis / Mycroft"), needs Companion 2026.2.3+ and HA set as default assistant | Apache-2.0 | No (native C++ module) | **Test first.** Docs warn it's battery-heavy, which doesn't matter on a wall-powered panel |
| Full voice satellite (timers, announcements, media) | **brownard/Ava** (ESPHome protocol, Android 8+, 2 wake words, custom models, Tasker) | Apache-2.0 | No (bundled LiteRT) | **Best base to fork** for Synapse voice |
| BLE proxy → room presence | **Ava-Pro** (ESPHome BLE proxy incl. IRK resolution) + **Bermuda** integration in HA (~1–3 m, room-level) | Ava-Pro: CC BY-NC-ND | Not stated | Use for testing. Building your own means adding a `bluetooth_proxy` to an Ava fork |
| Kiosk / Device Owner | **FreeKiosk** (Device Owner, REST, MQTT + HA discovery, Android 8+) | MIT | Not stated | Use as reference or stopgap for Phase 1 |
| Dashboard / smart display | **VACA** (View Assist Companion): WebView, wake word, sensors, motion detection | Check repo | Not stated | Alternative to the HA app if you want the View Assist ecosystem |
| TTS on the phone | **SherpaTTS** (Piper/Coqui voices, F-Droid, v3.4 Jul 2026, Android 10+) | Open source | No | **Install.** It fills the "no TTS engine" gap |
| Phone camera → Frigate | Nothing confirmed. Ava-Pro claims a native camera pipeline. "ONVIF Camera" on F-Droid looked like a *viewer*, not a server | — | — | Still open. Fallback: HA app camera snapshots or a small RTSP server in Synapse Core |

**Architecture suggestion (INFERRED, your call):** make Synapse Core an **ESPHome-native-API device**, built on Ava's Apache-licensed `esphomeproto` module. Here's why:
- HA auto-discovers it (no tokens on the phone, no MQTT broker needed).
- Sensors, voice, BLE proxy and buttons all fit ESPHome entity types you already know from ESP32 work.
- Bermuda works on day one once the BLE proxy exists.

One caution: Ava's README warns the ESPHome port is **unauthenticated**. Set an API encryption key, and keep the phone on a trusted VLAN or LAN.

---

## 4. Battery: running 24/7 on the charger

The device has an `mt6370_pmu`, so these MediaTek charge-control files are known from the ACC project's switch list. **CONFIRMED that they exist in ACC. Not confirmed on this phone.**

| Control file | Off / on values |
|---|---|
| `/proc/mtk_battery_cmd/current_cmd` | `0 0` = charge, `0 1` = stop (optionally with `en_power_path 1/0`) |
| `/sys/devices/platform/charger/bypass_charger` | `0` / `1` |
| `/sys/class/power_supply/battery/input_suspend` | `0` / `1` (often absent on MTK) |

How to use them:
- Check which ones exist on the DSU with `ls`.
- Test by hand with ADB root: write the "stop" value and confirm in `dumpsys battery` that the status goes to "Not charging" **while the phone stays powered**.
- Because ADB root resets on every reboot, the automation has to live in an **init script in the image**. Alternative: Synapse publishes battery % to HA, and HA toggles a **smart plug** at 40/80%. **The smart plug is the simplest and safest route, with no root needed.**
- ⚠️ Safety:
  - Lithium cells kept at 100% and warm for months can swell. Mount the panel so the back isn't sealed against drywall, and keep an eye on `mtktsbattery` temperature. Alert if it goes above 45 °C.
  - Use a proper 5V/2A wall adapter, not a PC port.

---

## 5. Kiosk / Device Owner on this device

- `dpm set-device-owner <pkg>/<receiver>` needs **no accounts** on the device. Yours has none, so you're good.
- Do it **after** the DSU reinstall, because the reinstall wipes `/data` and with it the Device Owner setting.
- Lock Task Mode needs the Device Owner app to allow-list packages. Add the HA app and SherpaTTS if Synapse launches them.
- Always build an authenticated exit. Test ADB access as the escape route **before** you lock the device down.

---

## 6. Still-unknown items (couldn't be answered by research; they need the device)

- [ ] Whether stock also logs the `aw87xxx` error (decides how to approach the audio fix)
- [ ] Whether `tinycap` captures sound (hardware vs software)
- [ ] Front camera, light and pressure sensor readings, GPS fix, BLE scan, vibration, OTG, headphone jack
- [ ] Which charge-control file exists
- [ ] No TWRP or custom recovery found for the Stratus C8 / `w20_x65_c8`. Assume none exists.
- [ ] RTSP/ONVIF camera-server app for Frigate that needs no Google services (not verified)

## 7. Revised build order
1. Audio diagnosis (§1)
2. DSU reinstall at 10 GiB (§2) + test `fastboot gsi disable`
3. Install the HA Companion minimal app + SherpaTTS → sensors in HA same day, then test wake word
4. Install brownard/Ava and compare it with the HA app's voice
5. Set Device Owner and kiosk (FreeKiosk or your own)
6. Decide: build Synapse Core as an ESPHome-API device (Ava fork) vs a custom MQTT app
7. Bermuda + BLE proxy for presence; charge limiting via smart plug

---

## Sources
- AOSP gsi_tool.cpp (`--gsi-size`, `--userdata-size`, 2 GiB default): https://android.googlesource.com/platform/system/gsid/+/refs/heads/main/gsi_tool.cpp
- Android DSU docs (`KEY_USERDATA_SIZE`): https://developer.android.com/topic/dsu
- DSU Sideloader: https://github.com/VegaBobo/DSU-Sideloader
- fastbootd `gsi wipe/disable`: https://gerrit.omnirom.org/plugins/gitiles/android_system_core/+/1d504e3342b2960066739ea66fac6625b07560fb%5E%21
- TrebleDroid prop handler (read directly): https://github.com/TrebleDroid/device_phh_treble
- tinyalsa mixer types: https://android.googlesource.com/platform/external/tinyalsa/+/jb-dev/mixer.c
- HA Companion flavors: https://companion.home-assistant.io/docs/core/android-flavors
- HA Android source (microwakeword module): https://github.com/home-assistant/android
- HA wake word on Android (XDA): https://www.xda-developers.com/home-assistant-march-beta-wake-word-detection/
- Ava: https://github.com/brownard/Ava · Ava-Pro: https://github.com/knoop7/Ava
- VACA: https://github.com/msp1974/ViewAssist_Companion_App
- FreeKiosk: https://github.com/RushB-fr/freekiosk
- SherpaTTS: https://f-droid.org/packages/org.woheller69.ttsengine/
- Bermuda: https://github.com/agittins/bermuda/wiki
- ACC charge-control files: https://github.com/VR-25/acc
- Nokia 7 Plus "alternate audio policy" fix: https://community.e.foundation/t/nokia-7-plus-eos-gsi-no-incall-audio/39976
