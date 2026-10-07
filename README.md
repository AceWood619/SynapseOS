# SynapseOS
**Turns an old Android phone into a smart-home "sense organ" for Home Assistant: a wall panel, a sensor node and a voice satellite.**
Test device: Cloud Mobile Stratus C8 (MT6765, 3 GB RAM).

## How the pieces fit
```
            ┌────────────────────────── Home Assistant (the nervous system) ─────────────────────────┐
            │  REST /api/states ◄── node sensors         rest_command ──► node control API :8765    │
            │  ESPHome integration ◄──────────── Ava voice satellite :6053 (wake word, Assist)      │
            └───────────────▲───────────────────────────────────▲──────────────────────────────────┘
                            │ Wi-Fi                             │
┌───────────────────────────┴─── phone running SynapseOS ───────┴──────────────────────────────────┐
│ Synapse Core app (android/)                                                                       │
│  • MainActivity: HA dashboard (auto-login via HA's external-auth bridge), ambient clock, kiosk      │
│  • NodeService: light / pressure / proximity / motion / battery / Wi-Fi / temp → HA; presence fusion│
│  • Control API: /api/status, wake, ambient, speak (TTS), presence, reload                           │
│  • Companion apps (Ava) allowed through kiosk and started after boot                                │
│ OS layer (os/)                                                                                      │
│  • v5: LineageOS 21 GSI patched so the TrustKernel TEE accepts it (runs as a persistent DSU)       │
│  • v6: + root boot hook → /data/adb/synapse/boot.sh (charge limiter 40–80 %, ADB over Wi-Fi w/ auth)│
└─────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

## Where things are
| Path | What |
|---|---|
| `android/` | Synapse Core app. `core-logic/` = pure-Kotlin logic with unit tests; `app/` = Android app; `compile-check/` = SDK-free type check |
| `builds/` | **Latest APK, built by GitHub Actions on every push** (`synapse-core-latest.apk` + `.json` with sha256) |
| `tools/provision/` | `provision.py` (one-command node setup), `smoke_test.py` (behaviour tests), `ha_package.py` (HA YAML generator) |
| `tools/pc/` | Windows libusb fastboot/adb helpers for the Stratus C8 (WinUSB quirk) |
| `os/v5/`, `os/v6/` | System image pipelines (same-length byte patches + AVB re-sign) and the boot payload |
| `devices/stratus-c8/` | Verified hardware audit, boot/install notes, "never do" list |
| `SYNAPSEOS_RESEARCH_REPORT.md` | Research: build-vs-reuse map, audio diagnosis, charge limiting |
| `coord/` | **Two-chat coordination**: protocol, status board, request queue, decisions, findings |

## Status (keep this short; details in `coord/STATUS.md`)
| Piece | State |
|---|---|
| SynapseOS v5 base (DSU) | ✅ running on the phone; audio, camera, sensors verified |
| Synapse Core app | ✅ builds in CI, 18 unit tests pass · ⏳ first on-device test (R-005) |
| Voice (Ava) | ⏳ integration written (companion apps), needs device test |
| Charge limiter / v6 image | ⏳ written; test on v5 by hand first (R-006), then v6 (R-007) |
| dm-verity "tap Power" warning | ❌ won't fix (brick risk); battery rides through power cuts |

## Rules
- Mark facts **verified / inferred / unknown**.
- Never commit secrets: HA tokens, Wi-Fi names or passwords, API keys, shelter guest names. Node secrets live in `~/.synapse/` on the PC.
- Two chats work in parallel. Read `coord/PROTOCOL.md` first.
