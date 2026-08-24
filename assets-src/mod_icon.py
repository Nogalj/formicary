"""Builds the mod's logo -- the icon NeoForge shows in the in-game mod list.

    uv run --with pillow python assets-src/mod_icon.py

Same contract as blocks.py, models.py and sounds.py: generated, never hand-drawn, and the
palette is IMPORTED from blocks.py rather than copied, so the icon can never drift out of
the chitin/amber range the rest of the mod is painted in.

Output goes to the ROOT of src/main/resources, not under assets/ -- neoforge.mods.toml's
`logoFile` is resolved relative to the root of the jar.

Design notes, because "make a logo" is otherwise unfalsifiable:

- A top-down ant, not the mob model. The mod list draws this small, and the mob's
  side-on silhouette collapses into a blob at that size; a top-down ant keeps three
  clearly separated body masses and a readable leg spread.
- Bold masses, thick strokes, no fine detail. Nothing here is thinner than 3 px at 128,
  so it survives being drawn at 32.
- The colony's own light behind it. The Anthill Core emits light level 7 and the
  dimension is lit by amber and fungal glow, so the ant sits on a warm pool rather than
  on flat black -- that reads as "underground, lit" instead of "dark".
"""

import importlib.util
import math
import pathlib

from PIL import Image, ImageDraw, ImageFilter

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = REPO_ROOT / "src" / "main" / "resources" / "formicary.png"

SIZE = 128
SS = 4                      # supersample factor; drawn at 512 then reduced
W = SIZE * SS


def _load_palette():
    """Import blocks.py by path so the icon shares its exact colours."""
    spec = importlib.util.spec_from_file_location(
        "formicary_blocks", REPO_ROOT / "assets-src" / "blocks.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


B = _load_palette()

LOAM = (23, 16, 11, 255)          # the dimension's deep-soil ground
GLOW = (233, 162, 59)             # amber; alpha applied per-ring below
COMB = (233, 162, 59, 26)         # honeycomb hairline


def hex_grid(draw):
    """A faint honeycomb lattice -- the Brood Comb blocks, at wallpaper strength."""
    r = 26 * SS
    dx = r * math.sqrt(3.0)
    dy = r * 1.5
    row = 0
    y = -r
    while y < W + r:
        offset = (dx / 2.0) if row % 2 else 0.0
        x = -dx
        while x < W + dx:
            pts = [(x + offset + r * math.cos(math.radians(60 * i + 30)),
                    y + r * math.sin(math.radians(60 * i + 30))) for i in range(6)]
            draw.polygon(pts, outline=COMB, width=max(1, SS // 2))
            x += dx
        y += dy
        row += 1


def radial_glow(size, centre, radius, colour, peak_alpha):
    """A soft pool of light, built as concentric discs. Cheaper and more controllable
    than blurring a hard circle, and it keeps the falloff smooth at every scale."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    steps = 48
    for i in range(steps, 0, -1):
        t = i / steps
        rr = radius * t
        alpha = int(peak_alpha * (1.0 - t) ** 2)
        if alpha <= 0:
            continue
        d.ellipse([centre[0] - rr, centre[1] - rr, centre[0] + rr, centre[1] + rr],
                  fill=colour + (alpha,))
    return layer


def limb(draw, start, joint, end, width, colour):
    """One leg or antenna: two thick segments with a rounded knee, so it reads as a
    jointed limb rather than a wire."""
    draw.line([start, joint], fill=colour, width=width)
    draw.line([joint, end], fill=colour, width=int(width * 0.8))
    r = width / 2.0
    draw.ellipse([joint[0] - r, joint[1] - r, joint[0] + r, joint[1] + r], fill=colour)


def body_mass(draw, centre, rx, ry, fill, rim, highlight):
    """A body segment: filled ellipse, dark rim, and a highlight offset up-left so every
    mass is lit from the same direction."""
    cx, cy = centre
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=rim,
                 width=max(2, int(3 * SS)))
    hx, hy = rx * 0.42, ry * 0.42
    ox, oy = cx - rx * 0.30, cy - ry * 0.34
    draw.ellipse([ox - hx, oy - hy, ox + hx, oy + hy], fill=highlight)


def build():
    img = Image.new("RGBA", (W, W), LOAM)
    draw = ImageDraw.Draw(img)

    hex_grid(draw)

    # The colony's light, behind everything.
    img.alpha_composite(radial_glow(W, (W * 0.5, W * 0.52), W * 0.58, GLOW, 170))

    cx = W * 0.5
    outline = B.CHITIN_OUTLINE
    # CHITIN_MID, not CHITIN_BASE: at 32px the darker tone sank into the loam and the
    # legs disappeared entirely, leaving a three-blob shape that read as nothing.
    leg_col = B.CHITIN_MID

    head_y = W * 0.235
    thorax_y = W * 0.455
    gaster_y = W * 0.745

    # Legs first, so they sit UNDER the body masses the way they do on a real ant.
    #
    # Two things here are what separate an ant from a spider at icon size, and the first
    # draft got both wrong. All six attach to the THORAX only -- a spider's radiate from
    # one fused mass -- and they SWEEP BACK: the front pair reaches forward, the middle
    # pair straight out, the rear pair well behind. Six evenly-radial legs read as a
    # spider no matter what the body does.
    leg_w = int(7 * SS)
    for side in (-1, 1):
        for dy0, spread, drop in ((-0.060, 0.32, -0.22),
                                  (0.000, 0.38, 0.00),
                                  (0.060, 0.33, 0.28)):
            start = (cx + side * W * 0.04, thorax_y + W * dy0)
            joint = (cx + side * W * spread * 0.60, thorax_y + W * (dy0 + drop * 0.25))
            end = (cx + side * W * spread, thorax_y + W * (dy0 + drop))
            limb(draw, start, joint, end, leg_w, leg_col)

    # Elbowed antennae -- the other ant tell. A sharp bend, out then forward, and thinner
    # than the legs so they never read as a fourth pair.
    for side in (-1, 1):
        limb(draw,
             (cx + side * W * 0.05, head_y - W * 0.02),
             (cx + side * W * 0.17, head_y - W * 0.14),
             (cx + side * W * 0.145, head_y - W * 0.275),
             int(5 * SS), leg_col)

    # Mandibles, open and forward.
    for side in (-1, 1):
        limb(draw,
             (cx + side * W * 0.06, head_y - W * 0.045),
             (cx + side * W * 0.115, head_y - W * 0.105),
             (cx + side * W * 0.055, head_y - W * 0.155),
             int(6 * SS), B.CHITIN_MID)

    # Waist. The petiole node between thorax and gaster is THE ant silhouette cue, so the
    # three masses are drawn well apart and bridged narrowly rather than overlapped.
    draw.line([(cx, thorax_y + W * 0.09), (cx, gaster_y - W * 0.17)],
              fill=B.CHITIN_BASE, width=int(11 * SS))
    draw.line([(cx, head_y + W * 0.075), (cx, thorax_y - W * 0.10)],
              fill=B.CHITIN_BASE, width=int(13 * SS))
    node = W * 0.036
    draw.ellipse([cx - node, gaster_y - W * 0.205 - node,
                  cx + node, gaster_y - W * 0.205 + node],
                 fill=B.CHITIN_MID, outline=outline, width=max(2, int(2.5 * SS)))

    # Gaster, thorax, head -- back to front. Head is WIDE, thorax is the narrowest of the
    # three, gaster is the largest: an ant's proportions, not a bug-shaped blob.
    body_mass(draw, (cx, gaster_y), W * 0.185, W * 0.215,
              B.CHITIN_MID, outline, B.CHITIN_RIM)
    body_mass(draw, (cx, thorax_y), W * 0.105, W * 0.135,
              B.CHITIN_RIM, outline, B.CHITIN_SPARK)
    body_mass(draw, (cx, head_y), W * 0.145, W * 0.115,
              B.CHITIN_RIM, outline, B.CHITIN_SPARK)

    # Eyes: the one place a pale accent earns its contrast.
    for side in (-1, 1):
        ex, ey, er = cx + side * W * 0.055, head_y - W * 0.012, W * 0.024
        draw.ellipse([ex - er, ey - er, ex + er, ey + er], fill=B.CHITIN_OUTLINE)
        draw.ellipse([ex - er * 0.5, ey - er * 0.5, ex + er * 0.15, ey + er * 0.15],
                     fill=B.CHITIN_PALE)

    # A touch of bloom on the amber, then down to final size.
    bloom = img.filter(ImageFilter.GaussianBlur(radius=6 * SS / 4))
    img = Image.blend(img, bloom, 0.16)
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def assert_shippable(img):
    """The logo has to be exactly what neoforge.mods.toml promises, and it has to still
    read once the mod list shrinks it."""
    if img.size != (SIZE, SIZE):
        raise AssertionError("logo must be %dx%d, got %s" % (SIZE, SIZE, img.size))
    if img.mode != "RGBA":
        raise AssertionError("logo must be RGBA, got %s" % img.mode)
    opaque = sum(1 for p in img.getdata() if p[3] > 0)
    if opaque != SIZE * SIZE:
        raise AssertionError("logo should be fully opaque; %d transparent px"
                             % (SIZE * SIZE - opaque))
    # Legibility proxy: at 32px the subject must still stand clear of its background.
    small = img.resize((32, 32), Image.LANCZOS).convert("RGB")
    px = small.load()
    subject = px[16, 22]                      # centre of the gaster
    corner = px[1, 1]                         # background
    contrast = sum(abs(a - b) for a, b in zip(subject, corner))
    if contrast < 90:
        raise AssertionError("subject does not separate from the background at 32px "
                             "(channel distance %d, want >= 90)" % contrast)
    return contrast


def main():
    img = build()
    contrast = assert_shippable(img)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote %s (%dx%d, %.1f KB)"
          % (OUT.relative_to(REPO_ROOT), img.size[0], img.size[1],
             OUT.stat().st_size / 1024.0))
    print("  subject/background separation at 32px: %d (min 90)" % contrast)


if __name__ == "__main__":
    main()
