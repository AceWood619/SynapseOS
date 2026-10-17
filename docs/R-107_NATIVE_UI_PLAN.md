# R-107 — Native Synapse UI: "a remote for the house"
Mason's north star: SynapseOS should feel like **a TV remote for the home**, not a web page. Its own
dashboard and launcher, branded (dark, neon blue/purple, neuron logo), fast on weak hardware.

## Key technical decision (BRAIN): **Android Views, not Jetpack Compose**
- Lighter on the PowerVR GE8320 (Mason's "keep animations light" constraint). Views + a few
  ObjectAnimators beat Compose's recomposition cost on a 3 GB A53 phone.
- Verifiable: BRAIN can compile-check Views here (Robolectric android-all); Compose would build blind.
- Snappy touch-down feedback (the "physical button" feel) is trivial with View touch listeners.
- Consistent with the existing app (MainActivity is already programmatic Views).
Trade-off: more boilerplate than Compose. Worth it for speed + verifiability. (Revisit only if Mason insists on Compose.)

## Architecture
```
HaWsClient (OkHttp WebSocket)  ──►  EntityCache (done, tested)  ──►  UI (Views)
   auth + subscribe_events                 live state              RemoteHome / RoomPad / MediaPad
   call_service (optimistic)        state_changed events          Jarvis mic panel, App drawer
```
- **Done:** `HaWs` protocol + `EntityCache` (core-logic, 29 tests).
- **Next:** `HaWsClient` (app, OkHttp) — connect, auth, resubscribe on reconnect, expose cache + a
  `callService()` for optimistic control. Then the UI.

## UI, as a remote (phases)
1. **Theme + brand:** dark bg #0A0E1A, neon blue #5AA8FF / purple #9B6CFF accents, geometric sans.
   Neuron/circuit logo drawn as a vector (no heavy assets). Haptic CLICK on every press (verified supported).
2. **RemoteHome (the house overview):** context hero (greeting, time, weather, house mode), a big
   **All off / Good night** power key and a **Home** key always visible in the thumb zone (bottom half),
   big scene buttons, room strip.
3. **Room = channel:** swipe left/right between rooms (ViewPager2). Each room is a "remote pad":
   chunky ≥64 dp tiles for lights/switches with instant on/off glow, a climate stepper, a media row.
4. **MediaPad (D-pad):** when a media_player is selected — play/pause, vol ±, next/prev, source.
5. **Jarvis button:** big round mic (push-to-talk) → Assist/Ava; also a type box.
6. **Launcher / home screen:** app drawer of allowed apps + dock + Synapse settings + node status.
   App allowlist is PIN-managed in settings; kiosk (device owner) keeps strangers out.
7. **Keep HA Lovelace** as one tab ("Advanced / HA") — the existing WebView moves into a tab.

## Control feel
- Fire on touch-down/up with **optimistic UI** (tile flips instantly), then reconcile from the HA
  state_changed event. If HA rejects/doesn't change within ~2 s, revert the tile + short error buzz.

## Multi-user (Mason, 2026-10-07)
Profiles stored on the device; the **active user is chosen by the PIN entered** ("code set at setup").
Roles map to the Level 0/1/2 security model:
| Role | Max action level | UI | Powers |
|---|---|---|---|
| **Admin** (Mason) | 2 (locks/garage/alarm) | full | everything + manage profiles/settings/nodes |
| **User** (e.g. Riah) | 2 | full | all controls; can't edit Synapse settings/profiles |
| **Child** | 0 (lights/media/temp) | simplified, big tiles | no cameras/doors/security; limited apps; own orientation |
| **Guest** | 0, shared rooms | simplified | lights/media/temp only; minimal apps; the default/locked view |
- Each profile carries its own **orientation + layout** (home apps, visible rooms, accent, tile scale).
- Lock screen: enter PIN -> switch to that profile; idle/locked shows the default (guest) view.
- **Done:** Profiles/Role/Layout model (core-logic, 34 tests). PINs hashed (one device salt), dup PINs rejected.
- **Next:** a PIN lock/switch screen, a Settings pane (admin-only) to add/edit profiles, and applying each
  profile's orientation + allowed rooms/apps across the UI.

## Open input from Mason
- Which apps on the home screen? He floated: Camera, Settings, Jarvis chat, HA, browser, music.
- Room list + which entities per room (or we infer from HA areas once we read the registry).
- Logo: BRAIN will draw a vector neuron mark; Mason approves/tweaks.

## Does this need stock removed? No.
Launcher + native UI are ordinary app work (HOME intent category, already declared). Permanent install
(R-010) is unrelated and stays gated on its brick-risk recovery test.
