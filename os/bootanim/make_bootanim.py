#!/usr/bin/env python3
"""
Synapse Glass boot animation generator (R-124 bonus).

Renders a glowing neuron logo — a soma with circuit-trace dendrites ending in small rings, blue→violet,
"SynapseOS" wordmark — breathing on the deep-night ground, and packs it as an Android
bootanimation.zip.

    python3 os/bootanim/make_bootanim.py                # -> os/bootanim/bootanimation.zip
    python3 os/bootanim/make_bootanim.py --out x.zip --preview preview.png

Format notes (AOSP frameworks/base/cmds/bootanimation):
  * desc.txt line 1 = "WIDTH HEIGHT FPS". Frames are drawn centered; the rest of the screen is filled
    with the part's background colour, so the frames only need to cover the logo box. That keeps the
    zip tiny while the whole 720x1600 screen shows the night ground colour.
  * part line = "p COUNT PAUSE PATH #RRGGBB": part0 plays once (intro), part1 loops until boot ends.
  * The zip must be STORED (no compression) or the animation silently fails to load.

Pure Python + Pillow. Deterministic output: zip timestamps are fixed.
"""
import argparse
import io
import math
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

SCREEN_W, SCREEN_H = 720, 1600
BOX_W, BOX_H = 560, 700           # frame box (centered on screen)
FPS = 20
INTRO_FRAMES = 30                 # 1.5 s: traces grow out of the soma, wordmark fades in
LOOP_FRAMES = 40                  # 2.0 s breathing loop
SS = 2                            # supersampling for anti-aliasing
MAX_BYTES = 3 * 1024 * 1024
COLORS = 128                      # palette size per frame (keeps PNGs small)

NIGHT = (5, 9, 22)                # GROUND1-ish; also the desc.txt background
BLUE = (73, 182, 255)
INDIGO = (58, 82, 255)
VIOLET = (155, 108, 255)
MINT = (69, 240, 200)
INK = (234, 240, 255)

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/google-fonts/Poppins-Light.ttf",
    "/usr/share/fonts/truetype/google-fonts/Poppins-Regular.ttf",
    "C:/Windows/Fonts/segoeuil.ttf",
    "C:/Windows/Fonts/segoeui.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-ExtraLight.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(int(round(lerp(x, y, t))) for x, y in zip(c1, c2))


def ease_out(t):
    return 1 - (1 - t) ** 3


def dendrites():
    """Circuit-trace dendrites in logo units (soma at 0,0; radius ~1 = 100 px at 1x).
    Each is a polyline of orthogonal/45° segments ending at a ring."""
    s = 0.7071
    return [
        [(0, -0.42), (0, -1.15), (0.55, -1.7)],
        [(0.36, -0.22), (1.05, -0.65), (1.75, -0.65)],
        [(0.42, 0.12), (1.25, 0.12), (1.25 + s * 0.6, 0.12 + s * 0.6)],
        [(0.2, 0.37), (0.6, 1.15), (0.6, 1.75)],
        [(-0.22, 0.36), (-0.85, 1.0), (-1.55, 1.0)],
        [(-0.42, 0.0), (-1.2, 0.0), (-1.2 - s * 0.55, -0.55 * s)],
        [(-0.3, -0.3), (-0.8, -0.8), (-0.8, -1.45)],
    ]


def polyline_point(pts, t):
    """Point and partial polyline at fraction t (0..1) of total length."""
    segs = list(zip(pts, pts[1:]))
    lens = [math.dist(a, b) for a, b in segs]
    total = sum(lens)
    want = total * max(0.0, min(1.0, t))
    out = [pts[0]]
    for (a, b), L in zip(segs, lens):
        if want <= L:
            f = want / L if L else 0
            out.append((lerp(a[0], b[0], f), lerp(a[1], b[1], f)))
            return out
        out.append(b)
        want -= L
    return out


def load_font(size, path=None):
    for p in ([path] if path else []) + FONT_CANDIDATES:
        if p and os.path.isfile(p):
            try:
                return ImageFont.truetype(p, size)
            except OSError:
                pass
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def render_frame(grow, breathe, text_alpha, font):
    """grow 0..1 (trace length), breathe 0..1 (glow), text_alpha 0..1."""
    W, H = BOX_W * SS, BOX_H * SS
    cx, cy = W // 2, int(H * 0.40)
    unit = 92 * SS

    def P(x, y):
        return (cx + x * unit, cy + y * unit)

    base = Image.new("RGB", (W, H), NIGHT)
    # Soft night glow behind the mark (part of the "ground").
    halo = Image.new("L", (W, H), 0)
    hd = ImageDraw.Draw(halo)
    r = int(unit * (2.2 + 0.15 * breathe))
    hd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=int(70 + 40 * breathe))
    halo = halo.filter(ImageFilter.GaussianBlur(unit * 0.9))
    base.paste(Image.new("RGB", (W, H), (20, 34, 90)), (0, 0), halo)

    # Line art on its own layer so it can be blurred into a glow and laid back on top sharp.
    art = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)
    lw = max(2, int(0.075 * unit))
    for i, den in enumerate(dendrites()):
        col = mix(BLUE, VIOLET, i / 6)
        # Stagger: each dendrite starts a little later during the intro.
        g = max(0.0, min(1.0, (grow - i * 0.05) / 0.7))
        if g <= 0:
            continue
        pts = polyline_point(den, ease_out(g))
        d.line([P(*p) for p in pts], fill=col + (255,), width=lw, joint="curve")
        if g >= 1:
            ex, ey = P(*den[-1])
            rr = int(0.16 * unit)
            d.ellipse([ex - rr, ey - rr, ex + rr, ey + rr], outline=col + (255,), width=lw)
            # Synapse spark travelling along the trace while breathing.
            sp = (breathe + i / 7) % 1.0
            sx, sy = P(*polyline_point(den, sp)[-1])
            sr = int(0.07 * unit)
            d.ellipse([sx - sr, sy - sr, sx + sr, sy + sr], fill=MINT + (int(200 * breathe + 55),))

    # Soma: gradient disc (white core -> blue -> indigo).
    soma_r = int((0.48 + 0.04 * breathe) * unit)
    for k in range(soma_r, 0, -2):
        t = k / soma_r
        col = mix(INK, BLUE, t / 0.55) if t < 0.55 else mix(BLUE, INDIGO, (t - 0.55) / 0.45)
        d.ellipse([cx - k, cy - k, cx + k, cy + k], fill=col + (255,))

    glow = art.filter(ImageFilter.GaussianBlur(unit * 0.22))
    glow_alpha = glow.getchannel("A").point(lambda a: int(a * (0.55 + 0.45 * breathe)))
    glow.putalpha(glow_alpha)
    base = Image.alpha_composite(base.convert("RGBA"), glow)
    base = Image.alpha_composite(base, glow)          # second pass = stronger bloom
    base = Image.alpha_composite(base, art)

    if text_alpha > 0:
        txt = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        td = ImageDraw.Draw(txt)
        word = "SynapseOS"
        bbox = td.textbbox((0, 0), word, font=font)
        tw = bbox[2] - bbox[0]
        ty = int(H * 0.78)
        td.text(((W - tw) // 2 - bbox[0], ty), word, font=font, fill=INK + (int(235 * text_alpha),))
        base = Image.alpha_composite(base, txt)

    return base.convert("RGB").resize((BOX_W, BOX_H), Image.LANCZOS)


_EDGE_MASK = None


def feather_edges(img, px=60):
    """Blend the outer [px] of the frame into the exact background colour so there is no visible box
    where the frame meets the screen fill (desc.txt colour)."""
    global _EDGE_MASK
    if _EDGE_MASK is None or _EDGE_MASK.size != img.size:
        w, h = img.size
        px_ = px
        # Distance-to-edge ramp built from four linear gradients (cheap, done once).
        col = [max(0, 255 - int(255 * min(x, w - 1 - x) / px_)) for x in range(w)]
        row = [max(0, 255 - int(255 * min(y, h - 1 - y) / px_)) for y in range(h)]
        m = Image.new("L", (w, h))
        m.putdata([max(c, r) for r in row for c in col])
        _EDGE_MASK = m
    return Image.composite(Image.new("RGB", img.size, NIGHT), img, _EDGE_MASK)


def to_png(img):
    img = feather_edges(img)
    q = img.quantize(colors=COLORS, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    # Pin the palette entry used by the border to the exact background colour.
    edge_index = q.getpixel((0, 0))
    pal = q.getpalette()
    pal[edge_index * 3: edge_index * 3 + 3] = list(NIGHT)
    q.putpalette(pal)
    buf = io.BytesIO()
    q.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def build(out_path, preview=None, font_path=None):
    font = load_font(46 * SS, font_path)
    files = {}
    for i in range(INTRO_FRAMES):
        t = (i + 1) / INTRO_FRAMES
        files[f"part0/{i:03d}.png"] = to_png(render_frame(grow=t * 1.25, breathe=0.5 * t, text_alpha=max(0, (t - 0.55) / 0.45), font=font))
    last = None
    for i in range(LOOP_FRAMES):
        ph = i / LOOP_FRAMES
        breathe = 0.5 - 0.5 * math.cos(2 * math.pi * ph)   # 0 -> 1 -> 0, seamless loop
        img = render_frame(grow=2.0, breathe=breathe, text_alpha=1.0, font=font)
        files[f"part1/{i:03d}.png"] = to_png(img)
        if i == LOOP_FRAMES // 2:
            last = img
    bg = "#%02x%02x%02x" % NIGHT
    desc = f"{BOX_W} {BOX_H} {FPS}\np 1 0 part0 {bg}\np 0 0 part1 {bg}\n"

    with zipfile.ZipFile(out_path, "w", compression=zipfile.ZIP_STORED) as z:
        def add(name, data):
            zi = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_STORED
            z.writestr(zi, data)
        add("desc.txt", desc.encode())
        for name in sorted(files):
            add(name, files[name])

    size = os.path.getsize(out_path)
    if preview and last is not None:
        screen = Image.new("RGB", (SCREEN_W, SCREEN_H), NIGHT)
        screen.paste(last, ((SCREEN_W - BOX_W) // 2, (SCREEN_H - BOX_H) // 2))
        screen.save(preview)
    return size, desc


def verify(path):
    """Checks the things that make a bootanimation silently fail."""
    problems = []
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if names[0] != "desc.txt":
            problems.append("desc.txt is not the first entry")
        if any(i.compress_type != zipfile.ZIP_STORED for i in z.infolist()):
            problems.append("entries must be STORED (uncompressed)")
        w, h, fps = map(int, z.read("desc.txt").decode().splitlines()[0].split())
        if fps > 30:
            problems.append("fps > 30")
        if w > SCREEN_W or h > SCREEN_H:
            problems.append("frame box larger than the screen")
        for n in names:
            if n.endswith(".png"):
                with Image.open(io.BytesIO(z.read(n))) as im:
                    if im.size != (w, h):
                        problems.append(f"{n} is {im.size}, expected {(w, h)}")
                    rgb = im.convert("RGB")
                    corners = [rgb.getpixel(p) for p in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1))]
                    if any(c != NIGHT for c in corners):
                        problems.append(f"{n} border is not the exact background colour (visible box)")
    if os.path.getsize(path) > MAX_BYTES:
        problems.append("zip larger than 3 MB")
    return problems


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    ap.add_argument("--out", default=os.path.join(here, "bootanimation.zip"))
    ap.add_argument("--preview", help="also write a full-screen 720x1600 PNG preview")
    ap.add_argument("--font", help="TTF for the wordmark (default: Poppins Light / Segoe UI Light / DejaVu)")
    a = ap.parse_args()
    size, desc = build(a.out, a.preview, a.font)
    problems = verify(a.out)
    print(f"wrote {a.out}: {size / 1024:.0f} KiB, {INTRO_FRAMES}+{LOOP_FRAMES} frames @ {FPS} fps")
    print(desc.strip())
    if problems:
        print("PROBLEMS:\n  " + "\n  ".join(problems))
        sys.exit(1)
    print("checks: STORED, desc first, <=30 fps, frame size, seamless border, <=3 MB  OK")


if __name__ == "__main__":
    main()
