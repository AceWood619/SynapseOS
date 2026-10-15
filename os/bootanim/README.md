# Synapse Glass boot animation

`make_bootanim.py` renders the boot animation: the neuron logo (soma + circuit-trace dendrites ending in rings,
blue → violet) grows out, the **SynapseOS** wordmark fades in, then the logo breathes with sparks running
along the traces until Android finishes booting. Background: the night ground `#050916`.

| | |
|---|---|
| Output | `bootanimation.zip` (committed, ~2 MB, reproducible: same script → same SHA-256) |
| Frames | 560 × 700 box centered on the 720 × 1600 screen; the rest is filled with `#050916` by desc.txt |
| Timing | 20 fps · `part0` 30 frames once (1.5 s intro) · `part1` 40 frames looping (2 s) |
| Self-checks | STORED zip, `desc.txt` first, ≤ 30 fps, every frame the declared size, border pixels exactly the background colour (no visible box), ≤ 3 MB |

```bash
python3 os/bootanim/make_bootanim.py --preview preview.png   # needs Pillow
```

## Installing on the Stratus C8 (not done yet — needs the phone)

The stock file is `/product/media/bootanimation.zip` (read-only). Two options, least risky first:

1. **Test without touching the system image** — push and preview the animation while Android is running:
   `adb push os/bootanim/bootanimation.zip /data/local/tmp/` then, as root,
   `mount -o bind /data/local/tmp/bootanimation.zip /product/media/bootanimation.zip && setprop ctl.start bootanim`
   (`setprop ctl.stop bootanim` to stop). A bind mount disappears on reboot, so nothing is permanent.
2. **Every boot** — bind-mount from the existing boot hook (`/data/adb/synapse/boot.sh`).
   ❓ Unverified: whether the hook runs before `bootanimation` opens the zip on this device. If it runs later,
   the stock animation still plays that boot (harmless). The permanent-install image can bake the file in instead.

Never replace the file inside a flashed partition without Mason's go (see `coord/PROTOCOL.md`).
