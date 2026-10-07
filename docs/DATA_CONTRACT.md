# SynapseOS live-data contract — the nervous-system bus

> Origin: raised by the architecture/review AI (SPINE), 2026-10-07 — *"treat the live-data
> wire as the Synapse nervous-system bus, not merely a Glass UI API. Glass should be one
> consumer of the stream, not the reason the stream exists."* BRAIN agrees and writes it down
> here so it stays true as the code grows.

## The principle
```
Sensors / Android hardware
      ↓
Synapse Core capability layer        (NodeService, sensors, presence fusion, BLE)
      ↓
normalized live state + events       ← THE BUS (this contract)
      ↓
Home Assistant · Synapse Mesh · Jarvis
      ↓
Glass + other UIs                    (HomeView, RoomPadView, …) — replaceable consumers
```
**Glass must be able to disappear without taking the underlying intelligence down.** Any UI is a
subscriber to the bus; nothing upstream may import a UI class or depend on a view being alive.

## Where the bus lives today (✅ current, verified in code)
- **In-process bus:** `HaRepository` (app) — a process-wide singleton. `NodeService` owns the
  connection and drives it; UIs only call `onChange {}` and read `cache`/`rooms`/`connected`.
  The UI already does **not** bind to the service. This is the decoupling SPINE asked for, in miniature.
- **Live source:** `HaWsClient` (OkHttp WebSocket to HA) → `EntityCache` (thread-safe). Plus the HA
  area/entity/device registries, normalized by `HaRegistry` into `Area`/`Room`.
- **Node → HA direction:** `NodePublisher` + the control API publish this node's own sensors to HA.
- **Derived signals (pure, tested):** `PresenceFusion` (presence + **confidence**, BLE+activity),
  `ResilientLight` (per-entity **source** selection local⇄cloud), `RoomControl`, `MediaRemote`.

## The event/state record — fields
A unit on the bus is one entity's state or one event. Marked by what exists now vs. what SPINE's
contract adds. **Adding a field is fine; removing or renaming one is a breaking change** that must be
noted in `FINDINGS.md` and reviewed.

| Field | Status | Meaning |
|---|---|---|
| `entityId` | ✅ now (`Entity.entityId`) | `domain.object_id`, the stable key |
| `domain` | ✅ now (`Entity.domain`) | light / media_player / switch / sensor / … |
| `state` | ✅ now (`Entity.state`) | HA state string |
| `attributes` | ✅ now (`Entity.attributes`) | raw HA attributes map |
| `friendlyName` | ✅ now (derived) | display name |
| `roomId` / `areaId` | ✅ now (via `HaRegistry`/`Room`, resolved separately) | room context — **proposed: carry it on the record itself**, not only in the room model |
| `ts` (event time, ms) | ❗ proposed | when the state was observed/changed; needed for staleness + ordering |
| `source` | ⚠️ partial (`ResilientLight` picks local/cloud at read) | where it came from: `ha` \| `local` \| `cloud` \| `node:<id>` \| `sensor:<kind>` — **proposed: first-class on every record** |
| `confidence` 0..1 | ⚠️ partial (`PresenceFusion` only) | for fused/derived signals; raw HA states are 1.0 |
| `capability` | ❗ proposed | what can be *done* (toggle, dim, transport, dpad, lock…) — decouples UIs from domain-sniffing |
| `nodeId` | ✅ in config | which Synapse node observed/owns this |

### Proposed normalized shape (target, not yet emitted)
```
SynapseSignal {
  entityId: String, domain: String, state: String,
  attributes: Map, friendlyName: String,
  roomId: String?, nodeId: String,
  ts: Long,                      // epoch ms, observed
  source: String,               // "ha" | "local" | "cloud" | "node:<id>" | "sensor:<kind>"
  confidence: Double = 1.0,     // 1.0 for direct reads
  capabilities: Set<String>     // "toggle","dim","transport","dpad","lock",...
}
```
Migration is additive: wrap `Entity` with these fields in the cache layer; UIs keep working off the
existing getters while new consumers (Mesh, Jarvis, logging) read the richer record.

## Rules for anyone touching the bus
1. **No upstream → UI dependency.** `core-logic` and the service layer never import a view.
2. **Schema changes are append-only** unless reviewed; record any break in `FINDINGS.md`.
3. **Every derived signal carries its `source` and `confidence`.** Don't launder a guess as a fact.
4. **Timestamp everything** once `ts` lands, so staleness and offline-first behavior are decidable.
5. **Mark capability, not domain, for UIs** so a new device type doesn't require UI edits.

## Open (for SPINE's review when back)
- Should the bus expose a typed `SynapseSignal` now, or stay `Entity`+side-tables until the Mesh needs it?
- `Synapse Mesh`: in-process today; cross-node bus (which transport? HA as broker vs. direct) is undesigned.
- Acoustic/inaudible sensing as an experimental capability source — flagged experimental, confidence-gated.
