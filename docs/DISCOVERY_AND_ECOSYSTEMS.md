# Auto-discovery & the "universal smart remote" vision

> Mason's direction (2026-10-07): *"SynapseOS should initially connect to Home Assistant, Apple
> Home, Google Home, etc. at first setup, auto-detect all devices, control them, and build its
> control panel/remote accordingly — it's a smart remote for a smart house."*

This is the north star. Below is the honest architecture to get there without dead ends. Facts
marked ✅ verified / ❓ inferred (needs a source/test) / ⚠️ known hard limit.

## The big idea: don't integrate N ecosystems — integrate the hub that already did
**Home Assistant is the universal translator.** It already speaks to Google/Nest, HomeKit
accessories, Zigbee, Z-Wave, Matter, Thread, cloud clouds, ESPHome, and ~1000 more. So the clean
design is:

```
Apple Home · Google/Nest · Zigbee · Z-Wave · Matter · Thread · WiFi/cloud devices
                                   │  (HA's own integrations do the hard protocol work)
                                   ▼
                         Home Assistant  (the aggregator / nervous system)
                                   │  HA WebSocket + registries  ← ONE contract
                                   ▼
                            SynapseOS  (auto-builds the remote from HA's registry)
```

SynapseOS connects to **one** thing (HA) and inherits everything HA can reach. This is why the
auto-detect-and-build part is **already real**: v0.3.x reads HA's area/entity/device registries over
the WebSocket and builds the room "channels" + control cards automatically (`HaRegistry`,
`RoomControl`). Add a device to HA → it shows up on the remote with no code change. ✅

## Why "connect directly to Apple/Google" is the wrong first move
| Ecosystem | Can an Android app control it directly? | The real route |
|---|---|---|
| **Home Assistant** | ✅ Yes — WebSocket + REST (already done) | Direct. This is the hub. |
| **Apple HomeKit / Apple Home** | ⚠️ **No.** HomeKit needs an Apple controller (iPhone/HomePod/Apple TV as home hub) + MFi; a non-Apple app can't join a HomeKit home. | HA's **HomeKit Controller** integration pairs HomeKit accessories directly over IP ❓, **or** the accessory supports **Matter**. Either way HA holds them, SynapseOS reads them. |
| **Google Home / Nest** | ⚠️ No clean local "control every Google Home device" API for a 3rd-party app; Google's smart-home APIs are cloud/Assistant-oriented. ❓ | HA's Nest/Cast integrations, **or** Matter. |
| **Matter** | ❓ Yes in principle — Android can act as a Matter commissioner; devices are local over IP/Thread. | The real cross-ecosystem unifier (Apple, Google, Amazon, Samsung all back it). Commission into HA; long-term SynapseOS could commission directly. |
| **ESPHome nodes** | ✅ native API exists | Usually via HA; direct is possible (see SPINE's open question in `AI_CONTRIBUTOR_NOTES.md`). |

**Takeaway:** "connect to Apple Home / Google Home" = **connect to HA and let HA bring them in**, plus
**Matter** as the direct-local path over time. Trying to be a native HomeKit/Google client on a
cheap Android phone is a brick wall (Apple) or a cloud maze (Google). HA already solved it.

## First-setup wizard (full-OS target)
1. **Find the hub.** Auto-discover Home Assistant on the LAN (mDNS `_home-assistant._tcp` / default
   `homeassistant.local:8123`), or let the owner paste the URL. Pair with a long-lived token (or the
   proper OAuth indie-auth flow for the full OS). ✅ (manual URL+token already works)
2. **Offer to widen HA's reach.** If HomeKit/Google/Matter devices exist but aren't in HA yet, deep-link
   the owner to HA's "Add Integration" / Matter commissioning — SynapseOS guides, HA does the pairing. ❓
3. **Auto-build the remote.** Pull areas/entities/devices → rooms → control cards. ✅ (shipped)
4. **Optional direct Matter (later).** SynapseOS commissions Matter devices itself for LAN-only control
   that survives HA being down (offline-first). ❓ big lift; after the hub path is solid.

## What this means for the roadmap
- **Now → near:** HA is the single integration. Keep making the auto-built panel richer and prettier
  (done this week: fans/covers/climate/locks/sensors, sliders, color). Add the setup wizard's HA
  auto-discovery (mDNS) so first-run is "it just finds your house."
- **Mid:** Matter commissioning path (into HA first), HomeKit-via-HA documented, Google-via-HA documented.
- **Later / offline-first:** SynapseOS as its own local Matter controller so the remote still works if
  HA is down — ties directly to the nervous-system-bus contract (`DATA_CONTRACT.md`): HA is one source,
  not the only one.

## Open questions for SPINE (architecture/review)
- Is HA-as-sole-aggregator the right long-term spine, or do we want SynapseOS to also be a **direct
  Matter controller** for resilience? (Relates to the ESPHome-native-vs-HA-WS debate — same theme:
  how many sources does the bus have, and who's authoritative?)
- Offline-first: when HA is unreachable, what degrades vs. what must keep working locally?
