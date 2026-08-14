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
textures/models/armor/, the 18x18 mob-effect icons (M4b) into
textures/mob_effect/, plus labelled contact sheets at
assets-src/previews/blocks_sheet.png, previews/armor_layers_sheet.png and
previews/effect_icons_sheet.png for QA.
"""

import random
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 16
REPO_ROOT = Path(__file__).resolve().parent.parent
BLOCK_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/block"
ITEM_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/item"
ARMOR_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/models/armor"
EFFECT_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/mob_effect"
PREVIEW_DIR = Path(__file__).resolve().parent / "previews"

# Vanilla's own status-effect icons (checked by extracting
# assets/minecraft/textures/mob_effect/night_vision.png from the client resources jar)
# are 18x18, not 16x16 -- a different constant from the block/item SIZE above.
EFFECT_ICON_SIZE = 18


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

# Tiling note (Logan's 2026-08-13 review): adjacent copies of a 16x16 are IDENTICAL,
# so any high-contrast one-off feature reads as a grid at world scale, and cube_all
# rotates the same texture differently on top/bottom faces. The soils therefore keep
# their value range TIGHT (mid three tones dominate, extremes are subtle) and their
# features small and isotropic -- nothing directional, nothing eye-catching.

PACKED_SOIL_PAL = [(103, 78, 51, 255), (119, 90, 60, 255), (139, 107, 74, 255),
                   (153, 119, 83, 255), (166, 131, 93, 255)]


def packed_soil():
    img = value_noise_fill("packed_soil", PACKED_SOIL_PAL)
    # a single grit clump, kept close to the base tones so repeats stay quiet
    stamp_clumps(img, "packed_soil", 1, (110, 83, 55, 255),
                 highlight=(158, 124, 87, 255), size_range=(2, 2))
    return img


def amber_earth():
    img = value_noise_fill("amber_earth",
                           [(124, 63, 28, 255), (139, 73, 34, 255), (163, 89, 43, 255),
                            (178, 101, 51, 255), (193, 114, 61, 255)])
    # a single subdued resin fleck per tile
    stamp_clumps(img, "amber_earth", 1, (212, 138, 62, 255),
                 highlight=(228, 164, 90, 255), size_range=(1, 1))
    return img


def deep_loam():
    img = value_noise_fill("deep_loam",
                           [(54, 27, 17, 255), (64, 33, 21, 255), (82, 44, 28, 255),
                            (93, 51, 33, 255), (104, 59, 38, 255)])
    stamp_clumps(img, "deep_loam", 2, (46, 23, 15, 255),
                 highlight=(96, 54, 34, 255), size_range=(2, 2))
    return img


def hardened_soil():
    img = value_noise_fill("hardened_soil",
                           [(82, 74, 65, 255), (92, 83, 73, 255), (110, 100, 89, 255),
                            (119, 109, 97, 255), (129, 119, 106, 255)])
    # embedded stones, low contrast so the repeat stays quiet
    stamp_clumps(img, "hardened_soil", 3, (73, 66, 58, 255),
                 highlight=(133, 123, 110, 255), size_range=(2, 2))
    return img


def anthill_soil():
    """Excavated-pellet look: an anthill is thousands of carried soil granules.
    Dense 2x2 pellets (lit top-left, shaded bottom-right) packed over a sandy
    base, with a few dark pore openings the ants come and go by."""
    img = value_noise_fill("anthill_soil",
                           [(150, 117, 74, 255), (161, 128, 84, 255),
                            (172, 139, 92, 255), (183, 150, 103, 255)],
                           weights=[2, 5, 5, 2], jitter=0.15)
    px = img.load()
    r = rng("anthill_soil:pellets")
    for _ in range(26):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        mid = r.choice([(166, 133, 88, 255), (176, 143, 96, 255), (186, 152, 104, 255)])
        lite = (min(mid[0] + 26, 255), min(mid[1] + 24, 255), min(mid[2] + 20, 255), 255)
        dark = (max(mid[0] - 30, 0), max(mid[1] - 28, 0), max(mid[2] - 24, 0), 255)
        px[x, y] = lite
        px[wrap(x + 1), y] = mid
        px[x, wrap(y + 1)] = mid
        px[wrap(x + 1), wrap(y + 1)] = dark
    for _ in range(3):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        px[x, y] = (82, 60, 36, 255)
        px[wrap(x + 1), y] = (102, 76, 46, 255)
        px[x, wrap(y + 1)] = (102, 76, 46, 255)
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


# --- M8: the Fungal Spore crop -- 5 ages (0-4) sharing 3 painted stages, the same
# --- fewer-textures-than-ages economy vanilla's nether wart uses (4 ages, 3 textures).
# --- See ModBlockStateProvider for the age->stage mapping.

FUNGAL_CROP_STEM_L = (94, 168, 138, 255)
FUNGAL_CROP_STEM_D = (40, 101, 80, 255)


def fungal_spore_crop_stage0():
    """Barely-there sprout: three thin bare stems poking out of the soil."""
    img = blank()
    px = img.load()
    for y in range(11, 16):
        px[7, y] = FUNGAL_CROP_STEM_L
        px[8, y] = FUNGAL_CROP_STEM_D
    for y in range(12, 16):
        px[4, y] = FUNGAL_CROP_STEM_D
    for y in range(13, 16):
        px[11, y] = FUNGAL_CROP_STEM_D
    px[7, 10] = (58, 128, 101, 255)
    px[4, 11] = FUNGAL_CROP_STEM_D
    px[11, 12] = FUNGAL_CROP_STEM_D
    return img


def fungal_spore_crop_stage1():
    """Small spore cluster: three short stems, each topped with a tiny glowing cap."""
    img = blank()
    px = img.load()
    for (x, bottom, top) in [(4, 15, 9), (8, 16, 6), (12, 15, 8)]:
        for y in range(top + 2, bottom):
            px[x, y] = FUNGAL_CROP_STEM_D
        for (dx, dy) in [(-1, 0), (0, 0), (1, 0), (0, -1)]:
            xx, yy = x + dx, top + dy
            if 0 <= xx < SIZE and 0 <= yy < SIZE:
                px[xx, yy] = (58, 128, 101, 255)
        px[x, top] = FUNGAL_GLOW
    return img


def fungal_spore_crop_stage2():
    """Mature-looking small bloom: a compact glowing cap on a stem -- a scaled-down
    fungal_bloom, so the crop visibly telegraphs 'ready to harvest' before it maxes out."""
    img = blank()
    px = img.load()
    for y in range(10, 16):
        px[7, y] = FUNGAL_CROP_STEM_L
        px[8, y] = FUNGAL_CROP_STEM_D
    rows = {5: range(6, 10), 6: range(5, 11), 7: range(5, 11), 8: range(6, 10)}
    for y, xs in rows.items():
        for x in xs:
            px[x, y] = (46, 121, 96, 255)
    for (x, y) in [(6, 5), (7, 5), (5, 6), (6, 6)]:
        px[x, y] = (86, 160, 126, 255)
    for (x, y) in [(9, 7), (8, 8)]:
        px[x, y] = (28, 77, 61, 255)
    px[7, 6] = FUNGAL_GLOW
    px[8, 6] = FUNGAL_BRIGHT
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


def scent_gland_item():
    """Scent Gland (M4b): a small pale-amber sac, plucked from a worker or soldier --
    a solid core (same lerp technique as resin_item's glob) ringed by a lower-alpha
    band so the outer edge reads as slightly translucent, plus a tiny dark duct nub at
    the top where it was torn free."""
    img = blank()
    px = img.load()
    cx, cy = 8.0, 9.0
    core = (250, 224, 176, 255)
    mid = (224, 168, 88, 255)
    rim = (188, 118, 40, 255)

    def sdf(x, y):
        dx, dy = x + 0.5 - cx, (y + 0.5 - cy) * 0.82
        return dx * dx + dy * dy

    for y in range(SIZE):
        for x in range(SIZE):
            d = sdf(x, y)
            if d < 24:
                px[x, y] = lerp_color(core, mid, min(1.0, d / 24))
    # translucent rim: a thin lower-alpha ring just outside the solid core
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] != 0:
                continue
            d = sdf(x, y)
            if d < 34:
                c = lerp_color(mid, rim, (d - 24) / 10)
                px[x, y] = (c[0], c[1], c[2], 150)
    # duct nub -- where it was torn off the ant
    px[8, 3] = (128, 82, 34, 255)
    px[8, 4] = (158, 104, 46, 255)
    # specular highlight
    px[6, 6] = (255, 246, 222, 255)
    px[6, 7] = AMBER_SPARK
    return img


def fungal_spores_item():
    """Fungal Spores (M8): a small teal spore cluster -- three round motes fanned
    around a stem nub, sharing fungal_bloom's palette so the seed reads as the same
    family in the hotbar."""
    img = blank()
    px = img.load()
    for y in range(10, 13):
        px[7, y] = FUNGAL_CROP_STEM_D
        px[8, y] = FUNGAL_CROP_STEM_D
    for (mx, my, rad) in [(6, 7, 1), (9, 6, 1), (8, 9, 1)]:
        for dy in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                if dx * dx + dy * dy <= rad * rad + 1:
                    x, y = mx + dx, my + dy
                    if 0 <= x < SIZE and 0 <= y < SIZE:
                        px[x, y] = FUNGAL_GLOW if (dx <= 0 and dy <= 0) else (58, 128, 101, 255)
    px[9, 5] = FUNGAL_BRIGHT
    return outline(img, (18, 54, 44, 255))


def outline(img, colour):
    """Paints a 1px border of `colour` in the transparent pixels touching the
    shape. Applied after a sprite is filled, so the silhouette reads at
    inventory scale the way vanilla's outlined items do."""
    px = img.load()
    edge = []
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and px[nx, ny][3] != 0:
                    edge.append((x, y))
                    break
    for (x, y) in edge:
        px[x, y] = colour
    return img


# Royal Jelly's own ramp: paler and greener-gold than the resin/scent-gland amber,
# so the two goo items are not the same sprite at a glance in the hotbar.
JELLY_CORE = (253, 243, 202, 255)
JELLY_MID = (243, 216, 124, 255)
JELLY_DEEP = (206, 164, 56, 255)
JELLY_RIM = (140, 100, 24, 255)


def royal_jelly_item():
    """Royal Jelly (M6): a fat, glossy pale-gold droplet -- round bulb with a
    drawn-out point on top, lit from the upper left, shaded along the lower
    right, and outlined so the silhouette holds at 16px."""
    img = blank()
    px = img.load()
    cx, cy = 8.0, 10.5

    def sdf(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        return dx * dx + dy * dy

    # the bulb
    for y in range(SIZE):
        for x in range(SIZE):
            d = sdf(x, y)
            if d < 20:
                px[x, y] = lerp_color(JELLY_CORE, JELLY_MID, min(1.0, d / 20))

    # the point: a spine widening from one pixel as it drops into the bulb
    for y in range(2, 11):
        half = (y - 2) // 3
        for x in range(8 - half, 9 + half):
            if px[x, y][3] == 0:
                px[x, y] = lerp_color(JELLY_MID, JELLY_CORE, (y - 2) / 9.0)

    # shade the LOWER-RIGHT skin only -- shading the whole perimeter would ring the
    # droplet in gold and read as a coin rather than as a lit blob.
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] == 0:
                continue
            on_edge = (x + 1 >= SIZE or px[x + 1, y][3] == 0) or (y + 1 >= SIZE or px[x, y + 1][3] == 0)
            if on_edge and (x + 0.5 - cx) + (y + 0.5 - cy) > -1.0:
                px[x, y] = JELLY_DEEP

    outline(img, JELLY_RIM)

    # specular: a bright comma on the upper-left shoulder of the bulb
    for (x, y) in [(5, 9), (6, 8), (5, 10)]:
        if px[x, y][3] != 0:
            px[x, y] = JELLY_CORE
    px[6, 9] = (255, 253, 240, 255)
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


TRAIL_PHEROMONE_LEGEND = {
    ".": None,
    "o": (96, 52, 12, 255),          # dark glass rim
    "g": (250, 224, 176, 110),       # empty glass, mostly see-through
    "G": (255, 246, 222, 190),       # lit glass edge
    "s": AMBER_SPARK,                # specular on the shoulder
    "F": AMBER_LIGHT,                # fluid surface
    "f": AMBER_MID,                  # fluid body
    "d": AMBER_BASE,                 # fluid shaded edge
    "c": (124, 92, 54, 255),         # cork
    "C": (156, 120, 76, 255),        # cork, lit
    "m": (250, 205, 120, 170),       # escaping scent mote
}

# A stoppered vial, three quarters full. Shares the resin/scent-gland amber ramp so it
# reads as the same family of colony-made goo, and keeps the silhouette narrow and
# vertical so it is not mistaken for the Scent Gland's round sac at inventory scale.
TRAIL_PHEROMONE_MASK = [
    "................",
    ".........m......",
    "......m.........",
    "......cCCc......",
    "......cCCc......",
    "......oGGo......",
    ".....ogGGgo.....",
    "....osgGGggo....",
    "....ogFFFFgo....",
    "....odffffdo....",
    "....odffffdo....",
    "....odffffdo....",
    "....odffffdo....",
    "....oddffddo....",
    ".....oooooo.....",
    "................",
]


def trail_pheromone_item():
    """Trail Pheromone (M5): a corked vial of amber pheromone with a mote or two
    escaping the stopper."""
    img = paint_mask(TRAIL_PHEROMONE_MASK, TRAIL_PHEROMONE_LEGEND)
    px = img.load()
    # specular running down the left of the fluid, and one bright mote on the surface
    px[5, 9] = AMBER_LIGHT
    px[5, 10] = AMBER_MID
    px[6, 8] = AMBER_PALE
    return img


# --- M7: the queen's drop and what it makes --------------------------------
# Both share one gold-and-amber ramp that nothing else in the mod uses, so the
# two royal items read as a set and neither is mistaken for the Scent Gland (a
# plain pale sac) at inventory scale.

ROYAL_LEGEND = {
    ".": None,
    "o": (104, 58, 12, 255),         # dark rim
    "d": (186, 120, 40, 255),        # deep amber, shaded edge
    "a": (222, 166, 84, 255),        # amber body
    "A": (246, 212, 148, 255),       # amber, lit
    "s": (255, 250, 232, 255),       # specular
    "v": (255, 234, 172, 255),       # the glowing core, seen through the shell
    "c": (146, 102, 22, 255),        # gold collar, shaded
    "C": (232, 184, 66, 255),        # gold collar, lit
}

# A fat royal gland: visibly bigger than the Scent Gland's sac, lit from within,
# and wearing a small gold collar where it was cut free of the queen.
ROYAL_GLAND_MASK = [
    "................",
    ".......cc.......",
    "......cCCc......",
    "......cCCc......",
    ".....odaado.....",
    "....odAAsado....",
    "...odAsvAaddo...",
    "..odAAvvAaaddo..",
    "..oaAAvvAaaddo..",
    "..oaaAAvAaaddo..",
    "..odaaAAaaaddo..",
    "...odaaaaaddo...",
    "....oddaaddo....",
    ".....oddddo.....",
    "......oooo......",
    "................",
]


def royal_pheromone_gland_item():
    """Royal Pheromone Gland (M7): the queen's guaranteed drop, and the only
    source of the Pheromone Horn."""
    return paint_mask(ROYAL_GLAND_MASK, ROYAL_LEGEND)


HORN_LEGEND = {
    ".": None,
    "o": (86, 46, 12, 255),          # dark rim
    "b": (152, 92, 30, 255),         # horn, shaded
    "H": (226, 168, 82, 255),        # horn body
    "A": (252, 226, 168, 255),       # the lit inside of the bell
    "G": (214, 164, 62, 255),        # gold band
    "g": (166, 120, 36, 255),        # gold band, shaded
}

# A curved horn: flared bell low-left, tapering up-right to the mouthpiece, with
# a gold band around the bell. Diagonal on purpose -- every other item in this
# mod is a vertical blob or a vial, so the silhouette alone identifies it.
HORN_MASK = [
    "...........ooo..",
    "..........obHHo.",
    ".........obHHbo.",
    "........obHHbo..",
    ".......obHHbo...",
    "......obHHbo....",
    ".....obHHbo.....",
    "....obHHbo......",
    "...obHHbo.......",
    "..obHAHbo.......",
    "..obHAAHbo......",
    ".obGHAAHbo......",
    ".obGGHAHbo......",
    ".obgGGHHbo......",
    "..obgggbo.......",
    "...ooooo........",
]


def pheromone_horn_item():
    """Pheromone Horn (M7): reusable summon, crafted from the queen's gland."""
    img = paint_mask(HORN_MASK, HORN_LEGEND)
    px = img.load()
    # a specular run along the horn's upper edge, so the curve catches light
    for (x, y) in [(11, 1), (10, 2), (9, 3), (8, 4), (7, 5)]:
        if px[x, y][3] != 0:
            px[x, y] = (250, 220, 158, 255)
    px[4, 11] = (255, 246, 214, 255)
    return img


# ---------------------------------------------------------------------------
# Mob effect icons (M4b) -- 18x18, vanilla's status-icon size (not SIZE=16)
# ---------------------------------------------------------------------------

def pheromonal_disguise_icon():
    """Pheromonal Disguise status icon: an amber ant-mask glyph -- a rounded
    chitin mask with dark eye slits and two curved antennae sweeping up and
    outward -- readable at the small HUD/inventory scale vanilla uses for
    every other effect icon."""
    img = Image.new("RGBA", (EFFECT_ICON_SIZE, EFFECT_ICON_SIZE), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 8.5, 10.5

    def sdf(x, y):
        dx, dy = x + 0.5 - cx, (y + 0.5 - cy) * 1.08
        return (dx * dx + dy * dy) ** 0.5

    for y in range(EFFECT_ICON_SIZE):
        for x in range(EFFECT_ICON_SIZE):
            d = sdf(x, y)
            if d < 5.6:
                px[x, y] = lerp_color(AMBER_PALE, AMBER_BASE, min(1.0, d / 5.6))
    for y in range(EFFECT_ICON_SIZE):
        for x in range(EFFECT_ICON_SIZE):
            if px[x, y][3] != 0 and sdf(x, y) > 4.8:
                px[x, y] = AMBER_DARK
    # eye slits -- a dark almond mark either side of centre, reading as an insect mask
    for (x, y) in [(6, 10), (7, 10), (10, 10), (11, 10)]:
        px[x, y] = AMBER_DARK
    # specular highlight
    px[6, 8] = AMBER_SPARK
    px[7, 7] = AMBER_LIGHT
    # antennae curving up and outward from the top of the mask
    for (x, y) in [(6, 6), (5, 5), (4, 4), (3, 3)]:
        px[x, y] = AMBER_MID
    for (x, y) in [(11, 6), (12, 5), (13, 4), (14, 3)]:
        px[x, y] = AMBER_MID
    px[3, 2] = AMBER_LIGHT
    px[14, 2] = AMBER_LIGHT
    return img


EFFECT_ICONS = {
    "pheromonal_disguise": pheromonal_disguise_icon,
}


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


def paint_mask(mask, legend):
    """Paints a 16x16 sprite from a character mask and a legend of colours."""
    if len(mask) != SIZE or any(len(row) != SIZE for row in mask):
        raise ValueError("mask must be 16 rows of 16 chars")
    img = blank()
    px = img.load()
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            colour = legend[ch]
            if colour is not None:
                px[x, y] = colour
    return img


def from_mask(mask):
    """Paints a 16x16 sprite from a character mask via ARMOR_LEGEND."""
    return paint_mask(mask, ARMOR_LEGEND)


def chitin_helmet_item():
    return from_mask(CHITIN_HELMET_MASK)


def chitin_chestplate_item():
    return from_mask(CHITIN_CHESTPLATE_MASK)


def chitin_leggings_item():
    return from_mask(CHITIN_LEGGINGS_MASK)


def chitin_boots_item():
    return from_mask(CHITIN_BOOTS_MASK)


# --- Soldier-ant palette, mirrored from models.py S_* constants: the armor is
# --- literally plates of soldier chitin, so it reuses the exact tones.
SOLD_DARKEST = (26, 10, 8, 255)
SOLD_DARK = (58, 18, 14, 255)
SOLD_BASE = (92, 28, 20, 255)
SOLD_MID = (118, 40, 28, 255)
SOLD_LIGHT = (145, 55, 38, 255)
SOLD_PALE = (168, 70, 48, 255)
AHEAD_DARK = (30, 13, 11, 255)     # soldier head-plate tones (helmet + pauldrons)
AHEAD_BASE = (46, 19, 15, 255)
AHEAD_MID = (64, 27, 21, 255)
AHEAD_LIGHT = (82, 36, 27, 255)
AJAW_TIP = (206, 122, 78, 255)     # pale mandible tip -- claw/clasp accents
AEYE = (196, 98, 52, 255)          # antenna-tip amber -- eye lenses


def _jitter(px, name, x0, y0, w, h, amount=6):
    """Faint per-pixel tone jitter over an already-painted rect."""
    r = rng(f"{name}:jit:{x0},{y0}")
    for yy in range(y0, y0 + h):
        for xx in range(x0, x0 + w):
            c = px[xx, yy]
            if c[3] == 0:
                continue
            j = r.choice((-amount, 0, 0, 0, amount))
            px[xx, yy] = (max(0, min(255, c[0] + j)), max(0, min(255, c[1] + j)),
                          max(0, min(255, c[2] + j)), 255)


def segment_plates(img, name, x0, y0, w, h, band=4, rim=None, base=None, seam=None,
                   face_w=None):
    """Overlapping chitin segment bands, gaster-style: each band has a lit top
    rim, a shaded body that bulges (lighter) toward each face's centre columns,
    and a near-black seam row. face_w splits the strip into faces so the bulge
    curves per face rather than across the whole UV strip."""
    rim = rim or SOLD_LIGHT
    base = base or SOLD_BASE
    seam = seam or SOLD_DARKEST
    px = img.load()
    fw = face_w or w
    for yy in range(h):
        phase = yy % band
        for xx in range(w):
            fx = xx % fw
            centre = abs((fx + 0.5) - fw / 2.0) / (fw / 2.0)   # 0 centre .. 1 edge
            bulge = int((0.5 - centre) * 22)                   # +11 centre, -11 edge
            if phase == 0:
                c = rim
            elif phase == band - 1:
                c = seam
            else:
                c = base
            if c is not seam:
                c = (max(0, min(255, c[0] + bulge)), max(0, min(255, c[1] + bulge // 2)),
                     max(0, min(255, c[2] + bulge // 2)), 255)
            px[x0 + xx, y0 + yy] = c
    _jitter(px, name, x0, y0, w, h)


def flat_plate(img, name, x0, y0, w, h, base, rim, seam):
    """A single smooth plate: lit top row, shaded bottom row, jittered body."""
    px = img.load()
    for yy in range(h):
        for xx in range(w):
            if yy == 0:
                c = rim
            elif yy == h - 1:
                c = seam
            else:
                c = base
            px[x0 + xx, y0 + yy] = c
    _jitter(px, name, x0, y0, w, h)


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
    except the leggings reads this file.

    Design pulls straight from the soldier ant model: near-black head plates
    with a crest ridge and amber eye lenses on the helmet, gaster-style segment
    bands on the torso with a dark pronotum shield, pauldron plates on the
    shoulders, mandible-pale claw tips on the boots."""
    img = Image.new("RGBA", (ARMOR_TEX_W, ARMOR_TEX_H), (0, 0, 0, 0))
    px = img.load()

    # -- helmet: head-plate tones. Side strip is 4 faces of 8: right/front/left/back.
    flat_plate(img, "helm_sides", 0, 8, 32, 8, AHEAD_BASE, AHEAD_MID, SOLD_DARKEST)
    # brow ridge -- one lighter row above the eyes, all the way round
    for xx in range(32):
        px[xx, 10] = AHEAD_LIGHT
    # amber eye lenses: two on the front face, one on each side face
    for (ex, ey) in [(10, 12), (13, 12), (3, 12), (20, 12)]:
        px[ex, ey] = AEYE
        px[ex, ey - 1] = lerp_color(AEYE, AHEAD_LIGHT, 0.5)
    # skull cap with a crest ridge running front-to-back
    flat_plate(img, "helm_top", 8, 0, 8, 8, AHEAD_BASE, AHEAD_MID, AHEAD_DARK)
    for yy in range(0, 8):
        px[11, yy] = AHEAD_MID
        px[12, yy] = AHEAD_LIGHT
    px[12, 1] = SOLD_LIGHT
    flat_plate(img, "helm_bottom", 16, 0, 8, 8, AHEAD_DARK, AHEAD_DARK, AHEAD_DARK)

    # -- chestplate: segment bands (3 bands over 12 rows), bulging per face.
    # Body side strip is faces of width 4/8/4/8 -- fake it with face_w=4 (the 8-wide
    # faces just get two gentle bulges, which reads as paired plates).
    segment_plates(img, "chest_sides", 16, 20, 24, 12, band=4, face_w=4)
    # pronotum shield: a dark rounded plate on the chest front face (x20..27)
    flat_plate(img, "chest_shield", 21, 21, 6, 5, AHEAD_BASE, SOLD_PALE, SOLD_DARKEST)
    px[21, 21] = SOLD_BASE   # knock the corners off so it reads rounded
    px[26, 21] = SOLD_BASE
    px[21, 25] = SOLD_DARK
    px[26, 25] = SOLD_DARK
    # shoulder caps
    flat_plate(img, "chest_caps", 20, 16, 16, 4, AHEAD_MID, SOLD_LIGHT, AHEAD_DARK)

    # -- sleeves: pauldron plate over the top, segments below.
    flat_plate(img, "arm_pauldron", 40, 20, 16, 4, AHEAD_BASE, SOLD_PALE, SOLD_DARKEST)
    segment_plates(img, "arm_sides", 40, 24, 16, 8, band=4, face_w=4)
    flat_plate(img, "arm_caps", 44, 16, 8, 4, AHEAD_MID, SOLD_LIGHT, AHEAD_DARK)

    # -- boots: dark chitin with pale claw tips at the toes (front face x4..7).
    flat_plate(img, "boot_sides", 0, 27, 16, 5, SOLD_DARK, SOLD_MID, SOLD_DARKEST)
    for (bx, by) in [(5, 31), (6, 31)]:
        px[bx, by] = AJAW_TIP
    px[5, 30] = lerp_color(AJAW_TIP, SOLD_DARK, 0.5)
    flat_plate(img, "boot_sole", 8, 16, 4, 4, SOLD_DARKEST, SOLD_DARKEST, SOLD_DARKEST)
    return img


def chitin_layer_2():
    """Inner layer: leggings only. Belt with a mandible-pale clasp across the
    body box, tight segment greaves down the leg box, stopping above the ankle
    so the boots' own band is not doubled."""
    img = Image.new("RGBA", (ARMOR_TEX_W, ARMOR_TEX_H), (0, 0, 0, 0))
    px = img.load()
    flat_plate(img, "belt_sides", 16, 20, 24, 5, AHEAD_BASE, SOLD_MID, SOLD_DARKEST)
    # clasp on the front face
    for (cx_, cy_) in [(23, 21), (24, 21), (23, 22), (24, 22)]:
        px[cx_, cy_] = AJAW_TIP
    px[23, 21] = lerp_color(AJAW_TIP, SOLD_PALE, 0.4)
    flat_plate(img, "belt_top", 20, 16, 8, 4, AHEAD_DARK, AHEAD_DARK, AHEAD_DARK)
    segment_plates(img, "greave_sides", 0, 20, 16, 8, band=3, face_w=4,
                   rim=SOLD_MID, base=SOLD_DARK, seam=SOLD_DARKEST)
    flat_plate(img, "greave_top", 4, 16, 4, 4, AHEAD_DARK, AHEAD_DARK, AHEAD_DARK)
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
    "fungal_spore_crop_stage0": fungal_spore_crop_stage0,
    "fungal_spore_crop_stage1": fungal_spore_crop_stage1,
    "fungal_spore_crop_stage2": fungal_spore_crop_stage2,
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
    "fungal_spores": fungal_spores_item,
    "royal_jelly": royal_jelly_item,
    "scent_gland": scent_gland_item,
    "trail_pheromone": trail_pheromone_item,
    "royal_pheromone_gland": royal_pheromone_gland_item,
    "pheromone_horn": pheromone_horn_item,
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
    EFFECT_TEX_DIR.mkdir(parents=True, exist_ok=True)
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

    effect_icons = []
    for name, make in EFFECT_ICONS.items():
        img = make()
        out = EFFECT_TEX_DIR / f"{name}.png"
        img.save(out)
        effect_icons.append((name, img))
        print(f"wrote {out.relative_to(REPO_ROOT)}")

    if effect_icons:
        effect_sheet = contact_sheet(effect_icons, cols=1)
        effect_sheet_path = PREVIEW_DIR / "effect_icons_sheet.png"
        effect_sheet.save(effect_sheet_path)
        print(f"wrote {effect_sheet_path.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
