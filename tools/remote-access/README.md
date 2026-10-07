# Wireless install & access to the Synapse panel
From the **dining PC (Windows)**, **Jarvis MacBook (macOS)**, and **RP5 (Raspberry Pi 5)** you can
install apps onto the panel, mirror its screen, and hit its control API — all over Wi-Fi.

The panel runs **authenticated ADB over Wi-Fi** (v6: `ro.adb.secure=1`, port 5555). Each machine must
have its ADB public key authorized once.

## 1. Authorize each machine (one-time)
On each machine, get its ADB public key:
- Windows: `type %USERPROFILE%\.android\adbkey.pub`   (run `adb keygen %USERPROFILE%\.android\adbkey` first if missing)
- macOS/Linux (MacBook, RP5): `cat ~/.android/adbkey.pub`   (install `android-platform-tools`; a first `adb` run creates the key)

Collect all three `.pub` files on the PC that runs provisioning (HANDS), then add them to the panel
(adb root once, over the PC's already-authorized key):
```
python os/v6/install_payload.py --adb <adb> --serial <phone-ip>:5555 \
    --adb-key diningpc.pub --adb-key macbook.pub --adb-key rp5.pub --adb-key %USERPROFILE%\.android\adbkey.pub
```
`install_payload.py` writes all keys to `/data/adb/synapse/adb_keys`; the boot hook installs them every boot,
so authorization survives reboots.

## 2. From any authorized machine
```
adb connect <phone-ip>:5555
adb install -r synapse-core-latest.apk      # or any app APK  -> appears on the Synapse home screen
scrcpy                                        # mirror/control the panel's screen (optional, install scrcpy)
curl -H "X-Synapse-Key: <api_key>" http://<phone-ip>:8765/api/status   # control API
```
`tools/remote-access/install_app.py <apk>` wraps connect + install + verify for any of the three machines.

## Notes
- Keep the phone on its **reserved IP** (Privacy → Use device MAC) so `<phone-ip>` is stable.
- Only machines whose key is in `adb_keys` can connect — a stranger on the LAN cannot.
- For a true "install from anywhere" (off-LAN), route through Jarvis/RP5 over your own VPN; don't expose 5555 to the internet.
