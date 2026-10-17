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
2. ~~**Every boot** via the boot hook~~: **doesn't work (verified 2026-10-16, BRAIN).** `phh-on-boot.sh` runs `/data/adb/synapse/boot.sh`
   about 10 s *after* `boot_completed`, and the boot animation has already ended by then. A bind mount from the hook would never be seen.
3. **Permanent:** bake `bootanimation.zip` into `/product/media/` in the permanent-install image (`os/PERMANENT_INSTALL.md`).
   That means flashing, so it needs Mason's explicit go.

✅ Preview verified on the C8 with option 1 (2026-10-16): full-screen and seamless. Afterwards it was unmounted and the stock file's sha256 was unchanged.

Never replace the file inside a flashed partition without Mason's go (see `coord/PROTOCOL.md`).
