# Synapse Glass — design language (v1)
The premium, futuristic look for the native Synapse UI (R-107). Viewable prototype:
`synapse-glass.html` (open in a browser). Live version published to Mason as an artifact.

## Language
- **Ground:** deep radial night — #0b1430 → #070b1c → #03050c.
- **Glass:** frosted translucent panels (blur+saturate on-device via layered translucency, since the
  GE8320 can't do cheap live blur everywhere): fill `rgba(26,38,78,.38)`, 1px neon-tinted stroke,
  inner top highlight, soft outer shadow. One real blur element max on-device.
- **Neon:** blue #49b6ff · indigo #3a52ff · violet #9b6cff · mint #45f0c8 (status) · red #ff6b81 (power) · amber #ffc24d.
- **Type:** Rajdhani (display, wide tracking) + Manrope (body). Tabular numerals for clocks/values.
- **Motion:** a slow ambient neuron field (drifting nodes + faint links), a breathing ring on the hero
  neuron and the live mic. Respect prefers-reduced-motion. No heavy blur loops.

## Home screen anatomy (matches HANDS' mockup, leveled up)
1. Status strip: online dot · SYNAPSE·HOME · clock · **profile pill** (active user + role).
2. Glass hero: neuron logo, greeting, house summary, house-mode chips.
3. Room channels (CH 1/2/3 …) — from HA areas, **kitchen excluded**.
4. Scene pads (2×2) — physical-button feel, glow when on.
5. Thumb console: **Home · Jarvis mic orb · All off**.

## On-device notes (Views impl)
- Fake glass with layered translucent drawables + gradient strokes + elevation; use `RenderEffect`
  blur only on the single hero panel (API 31+), gated off if a frame-time check shows it's too slow.
- Haptic CLICK on every press (verified supported on the Stratus C8).
- Profile switch: enter PIN → that profile's accent, orientation, rooms, apps, tile scale.
