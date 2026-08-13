"""
Programmer-art texture generator for Formicary's M1 block set.

One function per texture, each deterministic (seeded RNG keyed off the block
name so re-running this script produces byte-identical PNGs). Flat base color
+ simple noise/speckle/pattern per block, per the M1 art direction:

    palette/fabric blocks -> speckled dirt-like fill, distinct hue per tier
    resin family          -> amber tones, droplets / drip streaks
    fungal family          -> teal-green, pale glow dots
    hive family            -> hex-comb cell pattern, waxy/golden tones
    glow blocks             -> radial amber glow

Run with: python assets-src\\blocks.py
Requires: Pillow (PIL). Outputs 16x16 PNGs into
src/main/resources/assets/formicary/textures/block/ (and textures/item/
for the resin item sprite).
"""

import random
from pathlib import Path

from PIL import Image

SIZE = 16
REPO_ROOT = Path(__file__).resolve().parent.parent
BLOCK_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/block"
ITEM_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/item"


def rng(name):
    """Deterministic RNG seeded from the texture name -- same output every run."""
    return random.Random(f"formicary:{name}")


def blank(rgba=(0, 0, 0, 0)):
    return Image.new("RGBA", (SIZE, SIZE), rgba)


def speckle_fill(img, seed, base, dark, light, dark_chance=0.10, light_chance=0.12):
    """Flat base color with random darker/lighter single-pixel speckle noise."""
    r = rng(seed)
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            roll = r.random()
            if roll < dark_chance:
                px[x, y] = dark
            elif roll < dark_chance + light_chance:
                px[x, y] = light
            else:
                px[x, y] = base
    return img


def blotch_fill(img, seed, base, dark, light, blotch_chance=0.06, dark_chance=0.10):
    """Like speckle_fill but with small 2x1/1x2 blotches instead of single pixels
    (reads as coarser, blockier mottling -- used for the tougher/stony blocks)."""
    r = rng(seed)
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            px[x, y] = base
    for y in range(SIZE):
        for x in range(SIZE):
            roll = r.random()
            if roll < blotch_chance:
                color = dark if r.random() < 0.6 else light
                px[x, y] = color
                if x + 1 < SIZE and r.random() < 0.5:
                    px[x + 1, y] = color
                if y + 1 < SIZE and r.random() < 0.5:
                    px[x, y + 1] = color
    return img


def hex_comb(img, seed, base, line, cell, cell_w=4, cell_h=4):
    """Stylized honeycomb: a coarse hex-cell grid (offset alternating rows),
    dark grid lines, lighter cell interiors. Low-res approximation, reads as
    a comb pattern at 16x16."""
    px = img.load()
    for y in range(SIZE):
        row = y // cell_h
        x_off = (cell_w // 2) if (row % 2 == 1) else 0
        for x in range(SIZE):
            cx = (x + x_off) % cell_w
            cy = y % cell_h
            on_border = cx == 0 or cy == 0
            px[x, y] = line if on_border else cell
    # scatter a few base-color highlight pixels for texture
    r = rng(seed)
    for _ in range(SIZE):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        if px[x, y] == cell and r.random() < 0.5:
            px[x, y] = base
    return img


def radial_glow(img, seed, edge, mid, center, cx=7.5, cy=7.5):
    """Radial glow: bright center fading to a darker edge color, with a touch
    of per-pixel jitter so it doesn't look like a perfect vector gradient."""
    r = rng(seed)
    px = img.load()
    max_d = ((SIZE / 2) ** 2 + (SIZE / 2) ** 2) ** 0.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = (((x + 0.5) - cx) ** 2 + ((y + 0.5) - cy) ** 2) ** 0.5
            t = min(1.0, d / max_d)
            jitter = r.uniform(-0.06, 0.06)
            t = min(1.0, max(0.0, t + jitter))
            if t < 0.35:
                px[x, y] = center
            elif t < 0.7:
                px[x, y] = mid
            else:
                px[x, y] = edge
    return img


def lerp_color(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


# ---------------------------------------------------------------------------
# Palette / fabric soils (tier soils top -> bottom get visibly darker)
# ---------------------------------------------------------------------------

def packed_soil():
    return speckle_fill(blank(), "packed_soil",
                         base=(139, 107, 74, 255), dark=(110, 79, 52, 255), light=(161, 127, 88, 255))


def amber_earth():
    return speckle_fill(blank(), "amber_earth",
                         base=(168, 90, 46, 255), dark=(126, 63, 29, 255), light=(201, 122, 68, 255))


def deep_loam():
    return speckle_fill(blank(), "deep_loam",
                         base=(92, 46, 29, 255), dark=(62, 27, 16, 255), light=(122, 64, 40, 255))


def hardened_soil():
    return blotch_fill(blank(), "hardened_soil",
                        base=(117, 107, 96, 255), dark=(94, 86, 76, 255), light=(141, 131, 119, 255))


def anthill_soil():
    return speckle_fill(blank(), "anthill_soil",
                         base=(176, 141, 91, 255), dark=(140, 107, 62, 255), light=(199, 168, 118, 255))


# ---------------------------------------------------------------------------
# Resin family
# ---------------------------------------------------------------------------

def resin_weep():
    img = speckle_fill(blank(), "resin_weep",
                        base=(139, 107, 74, 255), dark=(110, 79, 52, 255), light=(161, 127, 88, 255),
                        dark_chance=0.08, light_chance=0.06)
    px = img.load()
    r = rng("resin_weep_droplets")
    droplets = [(3, 4), (9, 3), (12, 9), (5, 11), (8, 8), (2, 12)]
    for (dx, dy) in droplets:
        for (ox, oy) in [(0, 0), (1, 0), (0, 1), (1, 1)]:
            x, y = dx + ox, dy + oy
            if 0 <= x < SIZE and 0 <= y < SIZE:
                px[x, y] = (232, 162, 61, 255)
        hx, hy = dx, dy
        if 0 <= hx < SIZE and 0 <= hy < SIZE:
            px[hx, hy] = (255, 217, 138, 255)
    return img


def resin_block():
    img = blank((201, 122, 30, 255))
    px = img.load()
    r = rng("resin_block")
    for x in range(SIZE):
        if r.random() < 0.6:
            for y in range(SIZE):
                if r.random() < 0.5:
                    px[x, y] = (168, 90, 14, 255)
    for _ in range(10):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        px[x, y] = (255, 217, 138, 255)
    return img


def amber_glass():
    img = Image.new("RGBA", (SIZE, SIZE), (196, 130, 54, 150))
    px = img.load()
    r = rng("amber_glass")
    # translucent amber pane with a couple of lighter "glassy" streaks
    for y in range(SIZE):
        for x in range(SIZE):
            if x == y or x == y + 1:
                px[x, y] = (255, 214, 150, 190)
            elif r.random() < 0.05:
                px[x, y] = (150, 95, 35, 130)
    # a slightly more opaque frame edge, like a pane border
    for i in range(SIZE):
        px[i, 0] = (215, 150, 70, 200)
        px[i, SIZE - 1] = (215, 150, 70, 200)
        px[0, i] = (215, 150, 70, 200)
        px[SIZE - 1, i] = (215, 150, 70, 200)
    return img


# ---------------------------------------------------------------------------
# Fungal family (teal-green, pale glow spots)
# ---------------------------------------------------------------------------

def fungal_bloom():
    img = blank((0, 0, 0, 0))
    px = img.load()
    stem = (24, 74, 62, 255)
    cap = (46, 139, 114, 255)
    cap_light = (90, 199, 168, 255)
    glow = (191, 245, 224, 255)
    # stem
    for y in range(10, 16):
        px[7, y] = stem
        px[8, y] = stem
    # cap (a small rounded cluster)
    cap_pixels = [
        (5, 9), (6, 9), (9, 9), (10, 9),
        (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8),
        (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7),
        (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6),
        (6, 5), (7, 5), (8, 5), (9, 5),
        (7, 4), (8, 4),
    ]
    for (x, y) in cap_pixels:
        px[x, y] = cap
    for (x, y) in [(6, 7), (9, 7), (7, 6), (8, 8)]:
        px[x, y] = cap_light
    for (x, y) in [(7, 5), (9, 8), (5, 8)]:
        px[x, y] = glow
    return img


def fungal_carpet():
    img = speckle_fill(blank(), "fungal_carpet",
                        base=(46, 107, 84, 255), dark=(31, 74, 58, 255), light=(159, 230, 199, 255),
                        dark_chance=0.12, light_chance=0.08)
    return img


# ---------------------------------------------------------------------------
# Hive family (waxy / golden hex-comb)
# ---------------------------------------------------------------------------

def brood_comb():
    return hex_comb(blank(), "brood_comb",
                     base=(224, 193, 88, 255), line=(140, 110, 21, 255), cell=(201, 162, 39, 255))


def royal_comb():
    return hex_comb(blank(), "royal_comb",
                     base=(255, 221, 112, 255), line=(168, 121, 12, 255), cell=(232, 185, 35, 255))


def egg_cluster():
    img = blank((232, 220, 192, 255))
    px = img.load()
    r = rng("egg_cluster")
    eggs = [(3, 3, 3), (9, 4, 2), (5, 9, 3), (11, 10, 2), (2, 12, 2)]
    for (ex, ey, rad) in eggs:
        for dy in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                if dx * dx + dy * dy * 1.3 <= rad * rad:
                    x, y = ex + dx, ey + dy
                    if 0 <= x < SIZE and 0 <= y < SIZE:
                        shade = 245 if (dx <= 0 and dy <= 0) else 201
                        px[x, y] = (shade, shade - 5, shade - 30, 255)
    return img


# ---------------------------------------------------------------------------
# Glow blocks
# ---------------------------------------------------------------------------

def daylight_membrane():
    return radial_glow(blank(), "daylight_membrane",
                        edge=(255, 178, 56, 255), mid=(255, 214, 130, 255), center=(255, 243, 208, 255))


def anthill_core():
    return radial_glow(blank(), "anthill_core",
                        edge=(26, 21, 18, 255), mid=(90, 58, 24, 255), center=(255, 178, 56, 255))


def queens_crest():
    img = blank((184, 134, 11, 255))
    px = img.load()
    # ornate border
    for i in range(SIZE):
        px[i, 0] = (122, 90, 6, 255)
        px[i, SIZE - 1] = (122, 90, 6, 255)
        px[0, i] = (122, 90, 6, 255)
        px[SIZE - 1, i] = (122, 90, 6, 255)
    # center gem (diamond)
    cx, cy = 7.5, 7.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = abs(x + 0.5 - cx) + abs(y + 0.5 - cy)
            if d < 2.5:
                px[x, y] = (255, 234, 112, 255)
            elif d < 4.0:
                px[x, y] = (255, 215, 0, 255)
    return img


# ---------------------------------------------------------------------------
# Items
# ---------------------------------------------------------------------------

def resin_item():
    """16x16 amber blob item sprite on a transparent background."""
    img = blank((0, 0, 0, 0))
    px = img.load()
    cx, cy = 7.5, 8.5
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = (x + 0.5 - cx), (y + 0.5 - cy) * 1.15
            d = (dx * dx + dy * dy) ** 0.5
            if d < 5.6:
                t = d / 5.6
                color = lerp_color((255, 224, 150, 255), (196, 122, 24, 255), t)
                px[x, y] = color
    # highlight
    for (x, y) in [(5, 5), (6, 5), (5, 6)]:
        px[x, y] = (255, 244, 214, 255)
    return img


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
}


def main():
    BLOCK_TEX_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_TEX_DIR.mkdir(parents=True, exist_ok=True)

    for name, make in BLOCK_TEXTURES.items():
        img = make()
        out = BLOCK_TEX_DIR / f"{name}.png"
        img.save(out)
        print(f"wrote {out.relative_to(REPO_ROOT)}")

    for name, make in ITEM_TEXTURES.items():
        img = make()
        out = ITEM_TEX_DIR / f"{name}.png"
        img.save(out)
        print(f"wrote {out.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
