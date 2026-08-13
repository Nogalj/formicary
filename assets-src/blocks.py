"""
Texture generator for Formicary's M1 block set -- vanilla-style programmer art.

One function per texture, each deterministic (seeded RNG keyed off the texture
name so re-running this script produces byte-identical PNGs). Technique notes,
aimed at reading like vanilla resources rather than flat speckle:

    * value-noise fills: a coarse 8x8 tone grid (5-tone palette) upscaled 2x
      with per-pixel +/-1 tone jitter -- noise arrives in connected clumps,
      the way vanilla dirt/stone does, and tiles seamlessly (grid cells are
      independent, clump stamps wrap mod 16).
    * directional bevels: combs and the trophy use light top-left / dark
      bottom-right edges like vanilla's chiseled blocks.
    * glow is dithered patches (glowstone-style), not smooth radial gradients.
    * anthill core uses magma-block-style bright veins over a dark base.

Run with: python assets-src\\blocks.py
Requires: Pillow (PIL). Outputs 16x16 PNGs into
src/main/resources/assets/formicary/textures/block/ (and textures/item/ for
the item sprites), the two 64x32 Chitin Armor overlays into
textures/models/armor/, plus labelled contact sheets at
assets-src/previews/blocks_sheet.png and previews/armor_layers_sheet.png for QA.
"""

import random
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 16
REPO_ROOT = Path(__file__).resolve().parent.parent
BLOCK_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/block"
ITEM_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/item"
ARMOR_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/models/armor"
PREVIEW_DIR = Path(__file__).resolve().parent / "previews"


def rng(name):
    """Deterministic RNG seeded from the texture name -- same output every run."""
    return random.Random(f"formicary:{name}")


def blank(rgba=(0, 0, 0, 0)):
    return Image.new("RGBA", (SIZE, SIZE), rgba)


def wrap(v):
    return v % SIZE


def value_noise_fill(name, palette, weights=None, jitter=0.22):
    """Clumped multi-tone fill: coarse 8x8 tone grid upscaled 2x, then per-pixel
    +/-1 tone jitter. Tones cluster in 2x2-ish patches like vanilla soil/stone."""
    r = rng(name)
    n = len(palette)
    if weights is None:
        # bell-shaped: mid tones dominate, extremes are accents
        weights = [1, 3, 6, 3, 1][:n] if n == 5 else [1] * n
    grid = [[r.choices(range(n), weights)[0] for _ in range(8)] for _ in range(8)]
    img = blank()
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            t = grid[y // 2][x // 2]
            if r.random() < jitter:
                t = max(0, min(n - 1, t + r.choice((-1, 1))))
            px[x, y] = palette[t]
    return img


def stamp_clumps(img, name, count, color, highlight=None, size_range=(2, 3)):
    """Small rectangular clumps (pebbles/inclusions) with an optional 1px
    top-left highlight. Wraps mod 16 so the texture still tiles."""
    r = rng(name + ":clumps")
    px = img.load()
    for _ in range(count):
        cx, cy = r.randrange(SIZE), r.randrange(SIZE)
        w, h = r.randint(*size_range), r.randint(*size_range)
        for dy in range(h):
            for dx in range(w):
                px[wrap(cx + dx), wrap(cy + dy)] = color
        if highlight:
            px[wrap(cx), wrap(cy)] = highlight
    return img


def comb(name, line, hollow, hollow_lit, cap, cap_rim, cap_shade, cell_w=4, cell_h=4):
    """Organic wax comb, bee-nest style: 4x4 cells on an offset grid. Each cell
    is randomly either OPEN (a dark hollow whose bottom inner edge catches the
    light) or CAPPED (a waxy dome, lit top-left with a shaded lower-right).
    The mix is what keeps it from reading as stamped metal tiles."""
    img = blank()
    px = img.load()
    r = rng(name)
    n_rows = SIZE // cell_h
    n_cols = SIZE // cell_w + 1  # +1: offset rows straddle the seam
    capped = {(cr, cc): r.random() < 0.5 for cr in range(n_rows) for cc in range(n_cols)}
    for y in range(SIZE):
        row = y // cell_h
        xo = (cell_w // 2) if row % 2 else 0
        for x in range(SIZE):
            col = (x + xo) // cell_w
            cx = (x + xo) % cell_w
            cy = y % cell_h
            if cx == 0 or cy == 0:
                px[x, y] = line
            elif capped[(row, col % n_cols)]:
                if cx == 1 and cy == 1:
                    px[x, y] = cap_rim
                elif cx == cell_w - 1 or cy == cell_h - 1:
                    px[x, y] = cap_shade
                else:
                    px[x, y] = cap
            else:
                if cy == cell_h - 1:
                    px[x, y] = hollow_lit
                elif cx == cell_w - 1:
                    px[x, y] = hollow
                elif cx == 1 and cy == 1:
                    px[x, y] = (max(hollow[0] - 20, 0), max(hollow[1] - 16, 0),
                                max(hollow[2] - 8, 0), 255)
                else:
                    px[x, y] = hollow
    return img


def lerp_color(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


# ---------------------------------------------------------------------------
# Palette / fabric soils (tier soils top -> bottom get visibly darker)
# ---------------------------------------------------------------------------

PACKED_SOIL_PAL = [(94, 70, 45, 255), (117, 88, 58, 255), (139, 107, 74, 255),
                   (160, 127, 89, 255), (182, 149, 106, 255)]


def packed_soil():
    img = value_noise_fill("packed_soil", PACKED_SOIL_PAL)
    # a couple of embedded grit pebbles, vanilla-dirt style
    stamp_clumps(img, "packed_soil", 3, (104, 79, 51, 255),
                 highlight=(171, 138, 97, 255), size_range=(2, 2))
    return img


def amber_earth():
    img = value_noise_fill("amber_earth",
                           [(110, 55, 24, 255), (136, 71, 33, 255), (163, 89, 43, 255),
                            (189, 111, 58, 255), (212, 134, 76, 255)])
    # occasional resin fleck bedded in the earth
    stamp_clumps(img, "amber_earth", 2, (224, 152, 56, 255),
                 highlight=(250, 205, 120, 255), size_range=(1, 2))
    return img


def deep_loam():
    img = value_noise_fill("deep_loam",
                           [(44, 22, 14, 255), (62, 32, 20, 255), (82, 44, 28, 255),
                            (103, 58, 37, 255), (124, 74, 48, 255)])
    stamp_clumps(img, "deep_loam", 3, (36, 18, 12, 255),
                 highlight=(110, 64, 40, 255), size_range=(2, 3))
    return img


def hardened_soil():
    img = value_noise_fill("hardened_soil",
                           [(74, 66, 58, 255), (92, 83, 73, 255), (110, 100, 89, 255),
                            (128, 118, 105, 255), (146, 136, 122, 255)])
    # embedded stones: darker blobs with a bright catch-light, coarse-dirt style
    stamp_clumps(img, "hardened_soil", 4, (66, 59, 52, 255),
                 highlight=(152, 142, 128, 255), size_range=(2, 3))
    return img


def anthill_soil():
    img = value_noise_fill("anthill_soil",
                           [(128, 99, 60, 255), (150, 119, 76, 255), (172, 139, 92, 255),
                            (192, 159, 110, 255), (211, 180, 130, 255)])
    # tiny tunnel mouths -- dark pinpricks the ants come and go by
    r = rng("anthill_soil:holes")
    px = img.load()
    for _ in range(4):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        px[x, y] = (96, 72, 42, 255)
        px[wrap(x + 1), y] = (112, 86, 52, 255)
    return img


# ---------------------------------------------------------------------------
# Resin family (ambers)
# ---------------------------------------------------------------------------

AMBER_DARK = (140, 77, 16, 255)
AMBER_BASE = (178, 104, 26, 255)
AMBER_MID = (208, 130, 38, 255)
AMBER_LIGHT = (232, 160, 64, 255)
AMBER_PALE = (250, 205, 120, 255)
AMBER_SPARK = (255, 238, 190, 255)


def resin_weep():
    """Packed-soil face with amber drips running down it -- the 'weep'."""
    img = value_noise_fill("resin_weep", PACKED_SOIL_PAL)
    px = img.load()
    r = rng("resin_weep:drips")
    for x0 in (2, 6, 10, 13):
        y0 = r.randint(0, 3)
        length = r.randint(5, 9)
        # dark seep hole at the source
        px[x0, y0] = (84, 60, 38, 255)
        for y in range(y0 + 1, min(SIZE - 1, y0 + length)):
            px[x0, y] = AMBER_MID
            if r.random() < 0.4:
                px[wrap(x0 + 1), y] = AMBER_BASE
        # glossy bead at the bottom of the drip
        yb = min(SIZE - 1, y0 + length)
        px[x0, yb] = AMBER_PALE
        px[wrap(x0 + 1), yb] = AMBER_LIGHT
    return img


def resin_block():
    """Honey-block-style: light outer frame, deep amber interior with a
    diagonal gloss band."""
    img = value_noise_fill("resin_block",
                           [AMBER_DARK, AMBER_BASE, (196, 118, 32, 255),
                            AMBER_MID, AMBER_LIGHT],
                           weights=[2, 5, 6, 4, 1])
    px = img.load()
    for i in range(SIZE):
        px[i, 0] = AMBER_LIGHT
        px[0, i] = AMBER_LIGHT
        px[i, SIZE - 1] = AMBER_DARK
        px[SIZE - 1, i] = AMBER_DARK
    # diagonal gloss running top-right to centre
    for (x, y) in [(11, 2), (10, 3), (9, 4), (8, 5), (12, 3), (11, 4)]:
        px[x, y] = AMBER_PALE
    px[11, 3] = AMBER_SPARK
    r = rng("resin_block:bubbles")
    for _ in range(4):
        x, y = r.randint(2, SIZE - 3), r.randint(2, SIZE - 3)
        px[x, y] = AMBER_DARK
    return img


def amber_glass():
    """Vanilla-glass-style: mostly-clear tinted interior, beveled frame,
    a short diagonal streak near the top-left corner."""
    img = Image.new("RGBA", (SIZE, SIZE), (216, 148, 60, 88))
    px = img.load()
    # beveled frame: light top/left, dark bottom/right
    for i in range(SIZE):
        px[i, 0] = (250, 205, 120, 240)
        px[0, i] = (250, 205, 120, 240)
        px[i, SIZE - 1] = (160, 95, 25, 240)
        px[SIZE - 1, i] = (160, 95, 25, 240)
    px[SIZE - 1, 0] = (216, 148, 60, 240)
    px[0, SIZE - 1] = (216, 148, 60, 240)
    # glass streak
    for (x, y) in [(4, 2), (3, 3), (2, 4), (6, 3), (5, 4)]:
        px[x, y] = (255, 238, 190, 170)
    r = rng("amber_glass:motes")
    for _ in range(3):
        x, y = r.randint(3, SIZE - 4), r.randint(6, SIZE - 3)
        px[x, y] = (240, 190, 110, 140)
    return img


# ---------------------------------------------------------------------------
# Fungal family (teal-green, pale glow)
# ---------------------------------------------------------------------------

FUNGAL_PAL = [(18, 54, 44, 255), (28, 77, 61, 255), (40, 101, 80, 255),
              (58, 128, 101, 255), (86, 160, 126, 255)]
FUNGAL_GLOW = (168, 232, 198, 255)
FUNGAL_BRIGHT = (210, 250, 228, 255)


def fungal_bloom():
    """Cross-plant sprite: broad luminous cap on a pale stem, transparent bg."""
    img = blank()
    px = img.load()
    stem_l = (94, 168, 138, 255)
    stem_d = (40, 101, 80, 255)
    cap_d = (28, 77, 61, 255)
    cap = (46, 121, 96, 255)
    cap_l = (86, 160, 126, 255)
    # stem: 2px, lit on the left
    for y in range(9, 16):
        px[7, y] = stem_l
        px[8, y] = stem_d
    # cap silhouette (rows top to bottom), gently domed
    rows = {
        3: range(6, 10),
        4: range(5, 11),
        5: range(4, 12),
        6: range(3, 13),
        7: range(3, 13),
        8: range(4, 12),
    }
    for y, xs in rows.items():
        for x in xs:
            px[x, y] = cap
    # shading: top-left lit, bottom-right shaded, dark under-rim (gills)
    for (x, y) in [(6, 3), (7, 3), (5, 4), (6, 4), (4, 5), (5, 5), (6, 5), (3, 6), (4, 6), (5, 6)]:
        px[x, y] = cap_l
    for (x, y) in [(11, 6), (12, 6), (10, 7), (11, 7), (12, 7), (10, 8), (11, 8)]:
        px[x, y] = cap_d
    for x in range(4, 12):
        px[x, 8] = cap_d
    # luminous spots + spore motes
    for (x, y) in [(8, 4), (5, 6), (10, 5)]:
        px[x, y] = FUNGAL_GLOW
    px[9, 4] = FUNGAL_BRIGHT
    for (x, y) in [(2, 9), (13, 4), (12, 11)]:
        px[x, y] = (168, 232, 198, 180)
    return img


def fungal_carpet():
    img = value_noise_fill("fungal_carpet", FUNGAL_PAL)
    px = img.load()
    r = rng("fungal_carpet:flecks")
    for _ in range(5):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        px[x, y] = FUNGAL_GLOW
    for _ in range(3):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        px[x, y] = (12, 40, 32, 255)
    return img


# ---------------------------------------------------------------------------
# Hive family (beveled wax comb)
# ---------------------------------------------------------------------------

def brood_comb():
    return comb("brood_comb",
                line=(134, 96, 26, 255),
                hollow=(92, 62, 14, 255), hollow_lit=(178, 132, 40, 255),
                cap=(206, 162, 60, 255), cap_rim=(236, 196, 98, 255),
                cap_shade=(166, 124, 34, 255))


def royal_comb():
    return comb("royal_comb",
                line=(158, 108, 12, 255),
                hollow=(112, 74, 8, 255), hollow_lit=(216, 158, 32, 255),
                cap=(240, 190, 56, 255), cap_rim=(255, 232, 140, 255),
                cap_shade=(198, 144, 24, 255))


def egg_cluster():
    """Pale ant eggs bedded on dark wax -- 3-tone shells, top-left lit."""
    img = value_noise_fill("egg_cluster",
                           [(104, 74, 20, 255), (122, 88, 26, 255), (136, 100, 32, 255),
                            (146, 106, 34, 255), (160, 118, 40, 255)])
    px = img.load()
    shell_hi = (243, 235, 210, 255)
    shell = (226, 214, 184, 255)
    shell_lo = (196, 180, 146, 255)
    shell_rim = (158, 138, 100, 255)
    eggs = [(3, 3, 3), (9, 2, 2), (13, 5, 2), (5, 9, 3), (11, 10, 3), (2, 13, 2), (8, 14, 2)]
    for (ex, ey, rad) in eggs:
        for dy in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                if dx * dx + dy * dy * 1.4 <= rad * rad:
                    x, y = ex + dx, ey + dy
                    if 0 <= x < SIZE and 0 <= y < SIZE:
                        if dx == rad or dy == rad - 1 and dx >= 0:
                            px[x, y] = shell_lo
                        elif dx <= -1 and dy <= -1:
                            px[x, y] = shell_hi
                        else:
                            px[x, y] = shell
        # partial dark outline anchoring the egg into the wax
        for (ox, oy) in [(rad, 0), (rad - 1, 1), (0, rad)]:
            x, y = ex + ox, ey + oy
            if 0 <= x < SIZE and 0 <= y < SIZE:
                px[x, y] = shell_rim
    return img


# ---------------------------------------------------------------------------
# Glow blocks
# ---------------------------------------------------------------------------

def daylight_membrane():
    """Glowstone-style dithered amber patches, brightest at the centre --
    unmistakably 'the way out'."""
    img = value_noise_fill("daylight_membrane",
                           [(232, 152, 44, 255), (247, 180, 66, 255), (252, 203, 106, 255),
                            (255, 222, 148, 255), (255, 238, 190, 255)],
                           weights=[2, 4, 6, 4, 2])
    px = img.load()
    # centre bias: bump pixels near the middle one tone brighter
    tones = [(232, 152, 44, 255), (247, 180, 66, 255), (252, 203, 106, 255),
             (255, 222, 148, 255), (255, 238, 190, 255)]
    for y in range(SIZE):
        for x in range(SIZE):
            if abs(x - 7.5) + abs(y - 7.5) < 5:
                t = tones.index(px[x, y]) if px[x, y] in tones else 2
                px[x, y] = tones[min(len(tones) - 1, t + 1)]
    for (x, y) in [(7, 7), (8, 8), (6, 9)]:
        px[x, y] = (255, 250, 230, 255)
    return img


def anthill_core():
    """Magma-block-style: near-black base with branching bright amber veins
    and a hot 2x2 heart."""
    img = value_noise_fill("anthill_core",
                           [(18, 14, 12, 255), (26, 20, 17, 255), (32, 25, 20, 255),
                            (40, 31, 25, 255), (48, 38, 30, 255)])
    px = img.load()
    r = rng("anthill_core:veins")
    halo = (150, 90, 30, 255)

    def paint_vein(x, y, steps):
        for _ in range(steps):
            px[wrap(x), wrap(y)] = (255, 178, 56, 255)
            for (hx, hy) in [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]:
                if px[wrap(hx), wrap(hy)][0] < 100:
                    px[wrap(hx), wrap(hy)] = halo
            if r.random() < 0.5:
                x += r.choice((-1, 1))
            else:
                y += r.choice((-1, 1))

    paint_vein(7, 7, 7)
    paint_vein(8, 8, 7)
    paint_vein(3, 12, 5)
    paint_vein(12, 3, 5)
    # hot heart
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        px[x, y] = (255, 220, 140, 255)
    return img


def queens_crest():
    """Gold trophy plaque: double-beveled frame, corner studs, faceted gem."""
    img = value_noise_fill("queens_crest",
                           [(168, 122, 10, 255), (184, 134, 11, 255), (196, 146, 20, 255),
                            (208, 158, 32, 255), (220, 170, 44, 255)],
                           weights=[2, 5, 6, 3, 1])
    px = img.load()
    # outer dark frame
    for i in range(SIZE):
        px[i, 0] = (122, 90, 6, 255)
        px[i, SIZE - 1] = (122, 90, 6, 255)
        px[0, i] = (122, 90, 6, 255)
        px[SIZE - 1, i] = (122, 90, 6, 255)
    # inner bevel: light top/left, dark bottom/right
    for i in range(1, SIZE - 1):
        px[i, 1] = (255, 232, 140, 255)
        px[1, i] = (255, 232, 140, 255)
        px[i, SIZE - 2] = (150, 108, 8, 255)
        px[SIZE - 2, i] = (150, 108, 8, 255)
    px[SIZE - 2, 1] = (208, 158, 32, 255)
    px[1, SIZE - 2] = (208, 158, 32, 255)
    # corner studs
    for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        px[x, y] = (255, 232, 140, 255)
        px[x + 1, y + 1] = (150, 108, 8, 255)
    # faceted centre gem (diamond): lit upper-left facet, shaded lower-right
    cx, cy = 7.5, 7.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = abs(x + 0.5 - cx) + abs(y + 0.5 - cy)
            if d < 3.5:
                if x + y < 14:
                    px[x, y] = (255, 244, 180, 255)
                elif x + y > 16:
                    px[x, y] = (216, 164, 20, 255)
                else:
                    px[x, y] = (244, 208, 72, 255)
            elif d < 4.5:
                px[x, y] = (122, 90, 6, 255)
    return img


# ---------------------------------------------------------------------------
# Items
# ---------------------------------------------------------------------------

def resin_item():
    """Amber glob item sprite: dark outline, warm core, specular highlight."""
    img = blank()
    px = img.load()
    cx, cy = 7.5, 8.5
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = (x + 0.5 - cx), (y + 0.5 - cy) * 1.15
            d = (dx * dx + dy * dy) ** 0.5
            if d < 5.6:
                t = d / 5.6
                px[x, y] = lerp_color((255, 224, 150, 255), (178, 104, 26, 255), t)
    # dark outline ring
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] == 0:
                continue
            dx, dy = (x + 0.5 - cx), (y + 0.5 - cy) * 1.15
            if (dx * dx + dy * dy) ** 0.5 > 4.7:
                px[x, y] = (124, 68, 14, 255)
    # drip tail up top + specular
    px[7, 2] = (208, 130, 38, 255)
    px[7, 3] = (232, 160, 64, 255)
    for (x, y) in [(5, 5), (6, 5), (5, 6)]:
        px[x, y] = AMBER_SPARK
    return img


def chitin_item():
    """Curved shell-plate item sprite: dark maroon-brown core with a lighter
    rim catching the light along one edge -- a stretched, rotated ellipse
    (an implicit SDF) rather than resin's round glob, so it reads as a plate."""
    img = blank()
    px = img.load()
    cx, cy = 8.0, 8.0

    def sdf(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        u = dx * 0.92 + dy * 0.40
        v = -dx * 0.40 + dy * 0.92
        return (u * u) / 34.0 + (v * v) / 14.0

    for y in range(SIZE):
        for x in range(SIZE):
            d = sdf(x, y)
            if d < 1.0:
                px[x, y] = lerp_color((156, 64, 38, 255), (58, 22, 14, 255), d)
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] != 0 and sdf(x, y) > 0.82:
                px[x, y] = (34, 12, 8, 255)
    for (x, y) in [(4, 5), (5, 4), (6, 4), (5, 6), (7, 3)]:
        if px[x, y][3] != 0:
            px[x, y] = (196, 96, 58, 255)
    px[5, 5] = (222, 130, 84, 255)
    return img


def larva_item():
    """Curled grub item sprite: pale cream body curling into a C, faint amber
    segment lines, two tiny dark eye dots at the head end -- matching the
    larva entity's palette (see LARVA in models.py)."""
    img = blank()
    px = img.load()
    base = (238, 224, 196, 255)
    light = (248, 238, 218, 255)
    shade = (214, 196, 164, 255)
    line = (222, 158, 70, 255)
    eye = (60, 40, 24, 255)

    # Curl path: centres for a C-shaped body: head end -> curls down -> tail.
    path = [(11, 4), (12, 5), (12, 7), (11, 8), (9, 9), (7, 10),
            (5, 10), (4, 9), (3, 7), (4, 5)]
    for i, (cx, cy) in enumerate(path):
        r = 1 if i in (0, len(path) - 1) else 2
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r + 1:
                    x, y = cx + dx, cy + dy
                    if 0 <= x < SIZE and 0 <= y < SIZE:
                        px[x, y] = light if (dx <= 0 and dy <= 0) else base
    for (x, y) in [(6, 11), (5, 10), (8, 10), (10, 8), (11, 6)]:
        if 0 <= x < SIZE and 0 <= y < SIZE and px[x, y][3] != 0:
            px[x, y] = shade
    for (x, y) in [(10, 7), (8, 9), (6, 9), (5, 8)]:
        if px[x, y][3] != 0:
            px[x, y] = line
    px[11, 3] = eye
    px[12, 4] = eye
    return img


# ---------------------------------------------------------------------------
# Chitin Armor (M3b) -- four item sprites + the two humanoid overlay layers
# ---------------------------------------------------------------------------

CHITIN_OUTLINE = (34, 12, 8, 255)
CHITIN_DARK = (58, 22, 14, 255)
CHITIN_BASE = (104, 40, 24, 255)
CHITIN_MID = (156, 64, 38, 255)
CHITIN_RIM = (196, 96, 58, 255)
CHITIN_SPARK = (222, 130, 84, 255)

# Legend shared by the four armor item masks below. Same five tones the chitin
# item sprite uses, so the set reads as made of that drop.
ARMOR_LEGEND = {
    ".": None,
    "o": CHITIN_OUTLINE,
    "d": CHITIN_DARK,
    "b": CHITIN_BASE,
    "m": CHITIN_MID,
    "r": CHITIN_RIM,
    "s": CHITIN_SPARK,
}

CHITIN_HELMET_MASK = [
    "................",
    "................",
    "....oooooooo....",
    "...orrrrrrrro...",
    "..ormmmmmmmmro..",
    "..ormmssmmmmro..",
    "..ormmmmmmmmro..",
    "..orbbbbbbbbro..",
    "..ordbbbbbbdro..",
    "..ordo....odro..",
    "..ordo....odro..",
    "..oddo....oddo..",
    "...oo......oo...",
    "................",
    "................",
    "................",
]

CHITIN_CHESTPLATE_MASK = [
    "................",
    "..oo........oo..",
    ".ormo......omro.",
    ".ormmoooooommro.",
    ".ormmrrrrrrmmro.",
    ".ormmmmmmmmmmro.",
    ".ormmmsmmmmmmro.",
    ".ormmmmmmmmmmro.",
    ".ordbbbbbbbbdro.",
    ".ordbbbbbbbbdro.",
    "..odbbbbbbbbdo..",
    "..odbbbbbbbbdo..",
    "..oddbbbbbbddo..",
    "...ooddddddoo...",
    ".....oooooo.....",
    "................",
]

CHITIN_LEGGINGS_MASK = [
    "................",
    "................",
    "..oooooooooooo..",
    ".ormmmmmmmmmmro.",
    ".ormmmsmmmmmmro.",
    ".ormmmmmmmmmmro.",
    ".orbbbbbbbbbbro.",
    ".orbbbbbbbbbbro.",
    ".ordbbboobbbdro.",
    ".ordbo....obdro.",
    ".ordbo....obdro.",
    ".oddbo....obddo.",
    "..oddo....oddo..",
    "..ooo......ooo..",
    "................",
    "................",
]

CHITIN_BOOTS_MASK = [
    "................",
    "................",
    "................",
    "................",
    "..oooo....oooo..",
    ".orsmro..orsmro.",
    ".ormmro..ormmro.",
    ".ormmro..ormmro.",
    ".orbbro..orbbro.",
    ".orbbbo..orbbbo.",
    ".oddddo..oddddo.",
    ".oooooo..oooooo.",
    "................",
    "................",
    "................",
    "................",
]


def from_mask(mask):
    """Paints a 16x16 sprite from a character mask via ARMOR_LEGEND."""
    if len(mask) != SIZE or any(len(row) != SIZE for row in mask):
        raise ValueError("armor mask must be 16 rows of 16 chars")
    img = blank()
    px = img.load()
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            colour = ARMOR_LEGEND[ch]
            if colour is not None:
                px[x, y] = colour
    return img


def chitin_helmet_item():
    return from_mask(CHITIN_HELMET_MASK)


def chitin_chestplate_item():
    return from_mask(CHITIN_CHESTPLATE_MASK)


def chitin_leggings_item():
    return from_mask(CHITIN_LEGGINGS_MASK)


def chitin_boots_item():
    return from_mask(CHITIN_BOOTS_MASK)


def chitin_panel(img, name, x0, y0, w, h, band=4):
    """Fills a UV rect with banded chitin plate: each band lit along its top row,
    dark seam along its bottom row, faint per-pixel tone jitter in between."""
    r = rng(f"{name}:{x0},{y0}")
    px = img.load()
    for yy in range(h):
        t = min(1.0, (yy % band) / max(1, band - 1) * 0.9 + 0.1)
        base = lerp_color(CHITIN_RIM, CHITIN_DARK, t)
        for xx in range(w):
            j = r.choice((-8, -4, 0, 0, 0, 4, 8))
            px[x0 + xx, y0 + yy] = tuple(
                max(0, min(255, base[i] + j)) for i in range(3)) + (255,)
    for yy in range(band - 1, h, band):
        for xx in range(w):
            px[x0 + xx, y0 + yy] = CHITIN_OUTLINE


# Box-UV rects for the vanilla humanoid armor model, read off HumanoidModel /
# HumanoidArmorModel in the decompiled 1.21 sources rather than from memory:
#   head  texOffs(0,0)   8x8x8   -> sides x0..31  y8..15,  caps x8..23  y0..7
#   body  texOffs(16,16) 8x12x4  -> sides x16..39 y20..31, caps x20..35 y16..19
#   arm   texOffs(40,16) 4x12x4  -> sides x40..55 y20..31, caps x44..51 y16..19
#   leg   texOffs(0,16)  4x12x4  -> sides x0..15  y20..31, caps x4..11  y16..19
# (mirrored left arm/leg reuse the same rects, so each is painted once.)
ARMOR_TEX_W, ARMOR_TEX_H = 64, 32


def chitin_layer_1():
    """Outer layer: helmet (HEAD), chestplate + sleeves (CHEST), boots (FEET).
    HumanoidArmorLayer.usesInnerModel() is true only for LEGS, so everything
    except the leggings reads this file."""
    img = Image.new("RGBA", (ARMOR_TEX_W, ARMOR_TEX_H), (0, 0, 0, 0))
    # helmet: head side faces + skull cap
    chitin_panel(img, "helm_sides", 0, 8, 32, 8)
    chitin_panel(img, "helm_top", 8, 0, 8, 8)
    chitin_panel(img, "helm_bottom", 16, 0, 8, 8)
    # chestplate: full body box
    chitin_panel(img, "chest_sides", 16, 20, 24, 12)
    chitin_panel(img, "chest_caps", 20, 16, 16, 4)
    # sleeves: full arm box
    chitin_panel(img, "arm_sides", 40, 20, 16, 12)
    chitin_panel(img, "arm_caps", 44, 16, 8, 4)
    # boots: only the bottom of the leg box, or they'd read as full greaves
    chitin_panel(img, "boot_sides", 0, 27, 16, 5, band=3)
    chitin_panel(img, "boot_sole", 8, 16, 4, 4, band=3)
    return img


def chitin_layer_2():
    """Inner layer: leggings only (LEGS is the sole slot using the inner model).
    Belt across the body box, greaves down the leg box, stopping above the ankle
    so the boots' own band is not doubled."""
    img = Image.new("RGBA", (ARMOR_TEX_W, ARMOR_TEX_H), (0, 0, 0, 0))
    chitin_panel(img, "belt_sides", 16, 20, 24, 5, band=3)
    chitin_panel(img, "belt_top", 20, 16, 8, 4, band=3)
    chitin_panel(img, "greave_sides", 0, 20, 16, 8)
    chitin_panel(img, "greave_top", 4, 16, 4, 4)
    return img


ARMOR_LAYER_TEXTURES = {
    "chitin_layer_1": chitin_layer_1,
    "chitin_layer_2": chitin_layer_2,
}


BLOCK_TEXTURES = {
    "packed_soil": packed_soil,
    "amber_earth": amber_earth,
    "deep_loam": deep_loam,
    "hardened_soil": hardened_soil,
    "anthill_soil": anthill_soil,
    "resin_weep": resin_weep,
    "resin_block": resin_block,
    "amber_glass": amber_glass,
    "fungal_bloom": fungal_bloom,
    "fungal_carpet": fungal_carpet,
    "brood_comb": brood_comb,
    "royal_comb": royal_comb,
    "egg_cluster": egg_cluster,
    "daylight_membrane": daylight_membrane,
    "anthill_core": anthill_core,
    "queens_crest": queens_crest,
}

ITEM_TEXTURES = {
    "resin": resin_item,
    "chitin": chitin_item,
    "larva": larva_item,
    "chitin_helmet": chitin_helmet_item,
    "chitin_chestplate": chitin_chestplate_item,
    "chitin_leggings": chitin_leggings_item,
    "chitin_boots": chitin_boots_item,
}


def contact_sheet(images, scale=8, cols=4):
    """Labelled QA sheet: every texture upscaled with a checkerboard backing
    so alpha reads correctly."""
    tile = SIZE * scale
    label_h = 14
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (tile + 8) + 8, rows * (tile + label_h + 8) + 8),
                      (34, 34, 34, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(images):
        gx = 8 + (i % cols) * (tile + 8)
        gy = 8 + (i // cols) * (tile + label_h + 8)
        # checkerboard backing
        for cy_ in range(0, tile, 16):
            for cx_ in range(0, tile, 16):
                shade = 72 if ((cx_ // 16 + cy_ // 16) % 2 == 0) else 56
                draw.rectangle([gx + cx_, gy + cy_, gx + cx_ + 15, gy + cy_ + 15],
                               fill=(shade, shade, shade, 255))
        big = img.resize((tile, tile), Image.NEAREST)
        sheet.alpha_composite(big, (gx, gy))
        draw.text((gx + 2, gy + tile + 2), name, fill=(220, 220, 220, 255))
    return sheet


def armor_layer_sheet(layers, scale=6):
    """QA sheet for the 64x32 humanoid overlays, with the UV rects the armor model
    actually samples outlined so a blank region is obvious at a glance."""
    rects = [
        ("head", 0, 8, 32, 8), ("head-cap", 8, 0, 16, 8),
        ("body", 16, 20, 24, 12), ("body-cap", 20, 16, 16, 4),
        ("arm", 40, 20, 16, 12), ("arm-cap", 44, 16, 8, 4),
        ("leg", 0, 20, 16, 12), ("leg-cap", 4, 16, 8, 4),
    ]
    tile_w, tile_h = ARMOR_TEX_W * scale, ARMOR_TEX_H * scale
    label_h = 14
    sheet = Image.new("RGBA", (tile_w + 16, len(layers) * (tile_h + label_h + 8) + 8),
                      (34, 34, 34, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(layers):
        gx, gy = 8, 8 + i * (tile_h + label_h + 8)
        for cy_ in range(0, tile_h, 12):
            for cx_ in range(0, tile_w, 12):
                shade = 72 if ((cx_ // 12 + cy_ // 12) % 2 == 0) else 56
                draw.rectangle([gx + cx_, gy + cy_, gx + cx_ + 11, gy + cy_ + 11],
                               fill=(shade, shade, shade, 255))
        sheet.alpha_composite(img.resize((tile_w, tile_h), Image.NEAREST), (gx, gy))
        for (_, rx, ry, rw, rh) in rects:
            draw.rectangle([gx + rx * scale, gy + ry * scale,
                            gx + (rx + rw) * scale - 1, gy + (ry + rh) * scale - 1],
                           outline=(90, 200, 255, 255))
        draw.text((gx + 2, gy + tile_h + 2), name, fill=(220, 220, 220, 255))
    return sheet


def main():
    BLOCK_TEX_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_TEX_DIR.mkdir(parents=True, exist_ok=True)
    ARMOR_TEX_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    generated = []
    for name, make in BLOCK_TEXTURES.items():
        img = make()
        out = BLOCK_TEX_DIR / f"{name}.png"
        img.save(out)
        generated.append((name, img))
        print(f"wrote {out.relative_to(REPO_ROOT)}")

    for name, make in ITEM_TEXTURES.items():
        img = make()
        out = ITEM_TEX_DIR / f"{name}.png"
        img.save(out)
        generated.append((name, img))
        print(f"wrote {out.relative_to(REPO_ROOT)}")

    layers = []
    for name, make in ARMOR_LAYER_TEXTURES.items():
        img = make()
        out = ARMOR_TEX_DIR / f"{name}.png"
        img.save(out)
        layers.append((name, img))
        print(f"wrote {out.relative_to(REPO_ROOT)}")

    sheet = contact_sheet(generated)
    sheet_path = PREVIEW_DIR / "blocks_sheet.png"
    sheet.save(sheet_path)
    print(f"wrote {sheet_path.relative_to(REPO_ROOT)}")

    armor_sheet = armor_layer_sheet(layers)
    armor_sheet_path = PREVIEW_DIR / "armor_layers_sheet.png"
    armor_sheet.save(armor_sheet_path)
    print(f"wrote {armor_sheet_path.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
