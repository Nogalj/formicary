"""
Texture generator for Formicary's M1 block set -- vanilla-style programmer art.

One function per texture, each deterministic (seeded RNG keyed off the texture
name so re-running this script produces byte-identical PNGs). Technique notes,
aimed at reading like vanilla resources rather than flat speckle:

    * value-noise fills: a coarse 8x8 tone grid (5-tone palette) upscaled 2x
      with per-pixel +/-1 tone jitter -- noise arrives in connected clumps,
      the way vanilla dirt/stone does, and tiles seamlessly (grid cells are
      independent, clump stamps wrap mod 16). Used by everything EXCEPT the
      soil family, which needs finer grain than a 2x2 flat cell can give.
    * soil fills (`soil_texture`): smoothstep-interpolated wrapping value noise
      at two lattices, gradient-cored elliptical blotches, and a fine speck
      layer, quantised to a 12-tone ramp only at the end -- gradients all the
      way down, so nothing reads as a flat chunk that the tile repeat can turn
      into a grid.
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
assets-src/previews/blocks_sheet.png, previews/armor_layers_sheet.png,
previews/effect_icons_sheet.png, previews/decorative_families_wall_sheet.png and
previews/soil_family_wall_sheet.png for QA. The two *_wall_sheet.png files tile
each texture into a sample wall -- the only way to see the repeat, which is what
a single 16x16 swatch hides.
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


def comb(name, line, hollow, hollow_lit, cap, cap_rim, cap_shade, cell_w=4, cell_h=4):
    """Organic wax comb, bee-nest style: 4x4 cells on an offset grid. Each cell
    is randomly either OPEN (a dark hollow whose far inner edge catches the
    light, near corner in shadow) or CAPPED (a waxy dome, lit top-left with a
    shaded lower-right). The mix is what keeps it from reading as stamped
    metal tiles.

    Ep2 task I2 re-diagnosis (2026-08-18): the capped cell's shading was
    already written as an x/y-symmetric L (`cx == cell_w - 1 or cy ==
    cell_h - 1`), but the hollow cell's was an if/elif PRIORITY chain --
    lit only along cy == cell_h - 1, plain hollow along cx == cell_w - 1 --
    which is not symmetric under swapping x and y. That asymmetry is what a
    UV rotation on the UP/DOWN faces can never fix: verified against
    `BlockElement.uvsByFace` in `reference/`, a full cube's default UV maps
    U to world X and V to world Y (flipped) on the north/south faces, so a
    north/south wall already needs ZERO rotation to agree with the floor's
    U=X, V=Z -- rotating those faces (the shipped 2026-08-13 fix) broke the
    one pairing that was already correct. The east/west faces map U to
    world Z instead, which relative to the floor's V=Z is a true axis swap
    (transpose) -- and `ModelBuilder.FaceRotation` only has the four pure
    rotations (verified in `reference/.../ModelBuilder.java`), none of
    which can express a transpose. No single rotation value reconciles
    both pairings; the fix has to make the texture itself transpose-
    invariant so neither pairing needs any rotation at all. Making the
    hollow cell's lit edge the same symmetric L shape the capped cell
    already uses does exactly that."""
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
                if cx == cell_w - 1 or cy == cell_h - 1:
                    px[x, y] = hollow_lit
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
#
# Detail rework (Logan's 2026-08-18 review, Ep2 task I1): the first pass built the
# soils out of `value_noise_fill` + `stamp_clumps`, and BOTH of those paint FLAT
# colour -- an 8x8 tone grid upscaled 2x is a field of 2x2 same-colour squares, and
# adjacent equal cells merge into 4x2 lumps, so the darkest cluster in the tile reads
# as one chunky same-colour blotch. Because every copy of the tile is identical, that
# one blotch is what the eye locks onto and the wall turns into a grid of it (see
# run/screenshots/i-pass/before/i_soil_packed.png -- a repeating dark "L").
#
# The replacement (`soil_texture` below) fixes the three things that caused it:
#   1. SMALLER BLOTCH SCALE -- the base is smoothly interpolated wrapping value noise
#      at 4px and 2px lattices, so the dominant feature is ~2-4px, not ~4-8px.
#   2. PER-BLOTCH INTERNAL GRADIENT -- both the base ramp (12 tones, not 5) and the
#      blotch stamps carry a falloff, so no feature is a flat slab of one colour.
#   3. A FINE SPECK LAYER on top -- dense single-pixel +/-1-tone grit that breaks the
#      remaining smooth areas without adding any large shape to lock onto.
# The block models then take vanilla `dirt`'s 4 random y-rotation variants
# (assets/minecraft/blockstates/dirt.json in the client-extra jar -- verified, not
# recalled) so floors and ceilings stop repeating one orientation as well.

PACKED_SOIL_PAL = [(103, 78, 51, 255), (119, 90, 60, 255), (139, 107, 74, 255),
                   (153, 119, 83, 255), (166, 131, 93, 255)]
AMBER_EARTH_PAL = [(124, 63, 28, 255), (139, 73, 34, 255), (163, 89, 43, 255),
                   (178, 101, 51, 255), (193, 114, 61, 255)]
DEEP_LOAM_PAL = [(54, 27, 17, 255), (64, 33, 21, 255), (82, 44, 28, 255),
                 (93, 51, 33, 255), (104, 59, 38, 255)]
HARDENED_SOIL_PAL = [(82, 74, 65, 255), (92, 83, 73, 255), (110, 100, 89, 255),
                     (119, 109, 97, 255), (129, 119, 106, 255)]
ANTHILL_SOIL_PAL = [(150, 117, 74, 255), (161, 128, 84, 255),
                    (172, 139, 92, 255), (183, 150, 103, 255)]


def tone_ramp(colors, steps):
    """Resample a short palette into a longer, evenly-spaced ramp.

    The old fills quantised to five tones, which is what made a blotch a flat slab:
    two neighbouring pixels either shared a tone exactly or jumped a visible step.
    A 12-step ramp over the same endpoints keeps the palette's value range identical
    while giving every soft feature somewhere to fade through."""
    out = []
    last = len(colors) - 1
    for i in range(steps):
        t = i * last / (steps - 1)
        lo = min(int(t), last - 1)
        out.append(lerp_color(colors[lo], colors[lo + 1], t - lo))
    return out


def _noise_field(name, cell):
    """Wrapping value noise on a `cell`-pixel lattice, smoothstep-interpolated.

    Wrapping is not decoration: the lattice indices are taken mod (SIZE // cell), so
    the right edge interpolates back into the left one and the tile stays seamless --
    the same property `value_noise_fill` got for free by making grid cells
    independent, kept here while the flat cells become gradients."""
    r = rng(name)
    n = SIZE // cell
    lattice = [[r.random() for _ in range(n)] for _ in range(n)]
    field = [[0.0] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        gy, fy = divmod(y, cell)
        ty = (fy / cell) * (fy / cell) * (3 - 2 * (fy / cell))
        for x in range(SIZE):
            gx, fx = divmod(x, cell)
            tx = (fx / cell) * (fx / cell) * (3 - 2 * (fx / cell))
            a = lattice[gy][gx]
            b = lattice[gy][(gx + 1) % n]
            c = lattice[(gy + 1) % n][gx]
            d = lattice[(gy + 1) % n][(gx + 1) % n]
            field[y][x] = (a + (b - a) * tx) * (1.0 - ty) + (c + (d - c) * tx) * ty
    return field


def soil_texture(name, colors, steps=12, octaves=((2, 1.0),), contrast=1.35,
                 blotches=((18, -0.7, (0.8, 1.4)), (15, 0.65, (0.8, 1.3))), speck_rate=0.5,
                 speck_amp=1.7):
    """The soil family's shared fill: smooth multi-octave base, gradient blotches,
    fine speck layer. Works in continuous tone-index space and only quantises to the
    ramp at the very end, so every layer composes as a gradient rather than as a
    stamp of flat colour.

    `blotches` is a list of (count, signed amplitude in ramp steps, radius range).
    A negative amplitude darkens. Each blotch is an ellipse with a 1 - d^2 falloff --
    that falloff IS the per-blotch internal gradient; the old `stamp_clumps` painted
    a solid rectangle plus one highlight pixel instead, which is the chunky-blotch
    complaint. Every layer wraps mod 16, so the tile is still seamless.

    The defaults are tuned against two numbers, not by eye alone: the spread of the
    tile's 4x4 BLOCK MEANS (call it coarse) and its per-pixel spread (fine). Coarse
    is what survives being repeated -- it is the motif the eye grids up -- so it has
    to come DOWN; fine is the grain, which has to stay UP or the soil goes plasticky.
    Hence the weighting toward the 2px octave, the >1 contrast (a mean of two uniform
    fields is over-concentrated around mid grey), and a speck layer big enough to
    matter."""
    tones = tone_ramp(colors, steps)
    fields = [(_noise_field(f"{name}:oct{cell}", cell), weight) for cell, weight in octaves]
    total = sum(weight for _, weight in octaves)

    level = [[0.0] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            v = sum(field[y][x] * weight for field, weight in fields) / total
            level[y][x] = (0.5 + (v - 0.5) * contrast) * (steps - 1)

    r = rng(name + ":blotches")
    for count, amplitude, radius_range in blotches:
        for _ in range(count):
            cx, cy = r.randrange(SIZE), r.randrange(SIZE)
            rad = r.uniform(*radius_range)
            # deliberately elliptical and jittered: a run of identical circles is a
            # stamp, and a stamp is exactly what reads as a motif at world scale
            ax, ay = rad * r.uniform(0.75, 1.3), rad * r.uniform(0.75, 1.3)
            reach = int(max(ax, ay)) + 1
            for dy in range(-reach, reach + 1):
                for dx in range(-reach, reach + 1):
                    d = (dx / ax) ** 2 + (dy / ay) ** 2
                    if d >= 1.0:
                        continue
                    level[wrap(cy + dy)][wrap(cx + dx)] += amplitude * (1.0 - d)

    r = rng(name + ":specks")
    for y in range(SIZE):
        for x in range(SIZE):
            if r.random() < speck_rate:
                level[y][x] += speck_amp * r.choice((-1.0, -0.55, 0.55, 1.0))

    # Ordered dither at the quantisation step. Without it the ramp turns every smooth
    # gradient into banded regions, and a band has an OUTLINE -- which is exactly the
    # recognisable shape that a repeated tile grids up. Half a step of 4x4 Bayer
    # noise scatters the band edge into grain instead.
    bayer = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
    img = blank()
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            v = level[y][x] + (bayer[y % 4][x % 4] / 16.0 - 0.47)
            px[x, y] = tones[max(0, min(steps - 1, int(round(v))))]
    return img


def packed_soil():
    return soil_texture("packed_soil", PACKED_SOIL_PAL)


def amber_earth():
    # one extra bright blotch family: the resin flecks this tier is named for, now
    # gradient-cored instead of a single flat pixel
    return soil_texture("amber_earth", AMBER_EARTH_PAL,
                        blotches=((13, -1.0, (0.8, 1.4)), (9, 0.9, (0.8, 1.3)),
                                  (4, 2.0, (0.6, 0.95))))


def deep_loam():
    # the darkest tier reads muddier: a touch more contrast, slightly bigger dark
    # blotches, and the same speck layer to keep the repeat from showing
    return soil_texture("deep_loam", DEEP_LOAM_PAL, contrast=1.7,
                        blotches=((14, -1.1, (0.8, 1.4)), (11, 1.0, (0.8, 1.3))),
                        speck_rate=0.52, speck_amp=1.9)


def hardened_soil():
    # embedded stones: bright-cored blotches over a stony ramp, low amplitude so
    # the repeat stays quiet
    return soil_texture("hardened_soil", HARDENED_SOIL_PAL,
                        blotches=((14, -1.1, (0.8, 1.4)), (12, 1.1, (0.8, 1.3))),
                        speck_rate=0.52, speck_amp=1.8)


def anthill_soil():
    """Excavated-pellet look: an anthill is thousands of carried soil granules.
    Dense 2x2 pellets (lit top-left, shaded bottom-right) packed over a sandy
    base, with a few dark pore openings the ants come and go by.

    The pellets survive the I1 rework unchanged in spirit -- a 2x2 that runs
    light/mid/mid/dark already IS a per-blotch internal gradient, which is the
    thing the other soils were missing -- but they now sit on the smooth
    `soil_texture` base instead of the flat 2x2 grid, and there are more of them
    at lower contrast so no single pellet cluster carries the tile."""
    img = soil_texture("anthill_soil", ANTHILL_SOIL_PAL, contrast=1.2,
                       blotches=((9, -0.9, (0.8, 1.3)),), speck_rate=0.34, speck_amp=1.3)
    px = img.load()
    r = rng("anthill_soil:pellets")
    for _ in range(34):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        mid = r.choice([(166, 133, 88, 255), (176, 143, 96, 255), (186, 152, 104, 255)])
        lite = (min(mid[0] + 20, 255), min(mid[1] + 18, 255), min(mid[2] + 15, 255), 255)
        dark = (max(mid[0] - 24, 0), max(mid[1] - 22, 0), max(mid[2] - 19, 0), 255)
        px[x, y] = lite
        px[wrap(x + 1), y] = mid
        px[x, wrap(y + 1)] = mid
        px[wrap(x + 1), wrap(y + 1)] = dark
    for _ in range(3):
        x, y = r.randrange(SIZE), r.randrange(SIZE)
        px[x, y] = (92, 68, 42, 255)
        px[wrap(x + 1), y] = (110, 83, 51, 255)
        px[x, wrap(y + 1)] = (110, 83, 51, 255)
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
    """WP-S2 art rework (2026-08-20, round-3 play-test): Logan could not tell
    the three combs apart in-game. Brood's old palette (line 134,96,26 /
    cap 206,162,60) sat close to royal_comb's saturated gold at a glance --
    same hue family, just a shade darker. This palette pushes brood toward
    pale, desaturated wax/tan (lower saturation, higher value) instead of
    darkening the same gold, which is what actually widens the visual gap:
    royal now reads as rich saturated amber, brood reads as washed-out
    beige-tan next to it, at small scale in a dim room."""
    return comb("brood_comb",
                line=(150, 132, 96, 255),
                hollow=(120, 104, 74, 255), hollow_lit=(196, 178, 140, 255),
                cap=(214, 198, 160, 255), cap_rim=(236, 224, 192, 255),
                cap_shade=(184, 166, 126, 255))


def royal_comb():
    return comb("royal_comb",
                line=(158, 108, 12, 255),
                hollow=(112, 74, 8, 255), hollow_lit=(216, 158, 32, 255),
                cap=(240, 190, 56, 255), cap_rim=(255, 232, 140, 255),
                cap_shade=(198, 144, 24, 255))


def provision_comb():
    """Provision Comb (WP-S2 art rework, 2026-08-20): superseded the Ep2 D1
    placeholder, which was a straight recolor of brood_comb's own `comb()`
    call -- same cell shape, same open/capped mix, different hue only. Logan
    could not tell the three combs apart in-game and undercounted provision
    combs partly for it: a hue-only difference does not survive small scale
    in a dim room.

    This is no longer a `comb()` call at all -- it paints its OWN silhouette,
    not a recolored variant. `comb()`'s look (both brood and royal) is a
    checkerboard of dark OPEN hollows next to light capped domes; that mix
    of dark and light is the texture the eye locks onto. Provision comb has
    no open cells at all -- every cell is capped, each stamped with a small
    dark seal dot at its centre (the wax plug), which brood/royal's capped
    cells never carry. The result reads as a near-uniform pale field of
    stoppered jars -- a stocked pantry -- which is a different silhouette at
    a glance, not just a different color, while the shared 4x4 offset hex
    lattice (the grid line, the lit corner) keeps the family resemblance."""
    img = blank()
    px = img.load()
    cell_w = cell_h = 4
    line = (110, 84, 50, 255)
    lid = (232, 210, 168, 255)
    lid_hi = (250, 236, 206, 255)
    lid_rim = (204, 178, 128, 255)
    seal = (150, 104, 54, 255)
    for y in range(SIZE):
        row = y // cell_h
        xo = (cell_w // 2) if row % 2 else 0
        for x in range(SIZE):
            cx = (x + xo) % cell_w
            cy = y % cell_h
            if cx == 0 or cy == 0:
                px[x, y] = line
            elif cx == 1 and cy == 1:
                px[x, y] = lid_hi
            elif cx == cell_w - 1 or cy == cell_h - 1:
                px[x, y] = lid_rim
            elif cx == 2 and cy == 2:
                px[x, y] = seal
            else:
                px[x, y] = lid
    return img


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


def packed_soil_bricks():
    """Packed Soil cut into bricks: same soil palette (so the material reads
    through), laid in a running-bond mortar grid -- two courses of two bricks,
    joints offset on alternate rows, the way vanilla's own bricks.png reads.

    Carried onto `soil_texture` by the I1 rework for the same reason it borrows the
    palette: "the material reads through" only holds while the brick face is made of
    the same stuff as the parent block. Low contrast and no blotches -- a cut brick
    is a dressed face, and the mortar grid is already all the structure it needs."""
    img = soil_texture("packed_soil_bricks", PACKED_SOIL_PAL, contrast=0.7,
                       blotches=((3, -1.1, (1.1, 1.7)),), speck_rate=0.2)
    px = img.load()
    mortar = (74, 56, 37, 255)
    for y in range(SIZE):
        if y % 4 == 0:
            for x in range(SIZE):
                px[x, y] = mortar
    for row in range(4):
        y0 = row * 4
        offset = 4 if row % 2 else 0
        for x in range(offset, SIZE, 8):
            for yy in range(y0, min(SIZE, y0 + 4)):
                px[wrap(x), yy] = mortar
    return img


def hardened_soil_tiles():
    """Hardened Soil cut into flat tiles: same stony palette, a straight
    (non-offset) 4x4 grout grid -- the straight grid is what tells it apart
    from the bricks' running bond at a glance. Same `soil_texture` base as its parent
    block for the same material-continuity reason as packed_soil_bricks."""
    img = soil_texture("hardened_soil_tiles", HARDENED_SOIL_PAL, contrast=0.7,
                       blotches=((3, -1.1, (1.1, 1.7)),), speck_rate=0.22)
    px = img.load()
    grout = (56, 50, 44, 255)
    for i in range(0, SIZE, 4):
        for x in range(SIZE):
            px[x, i] = grout
        for y in range(SIZE):
            px[i, y] = grout
    return img


def polished_resin():
    """Resin Block, smoothed: the same amber ramp as resin_block but with the
    noise jitter tightened way down (an even surface, not a lumpy one) and a
    single clean diagonal gloss line rather than resin_block's busier streak
    -- 'polished' reads as flatness plus one confident highlight."""
    img = value_noise_fill("polished_resin",
                           [AMBER_DARK, AMBER_BASE, (196, 118, 32, 255), AMBER_MID, AMBER_LIGHT],
                           weights=[1, 3, 8, 3, 1], jitter=0.08)
    px = img.load()
    for (x, y) in [(3, 12), (4, 11), (5, 10), (6, 9), (10, 5), (11, 4), (12, 3)]:
        px[x, y] = AMBER_PALE
    px[8, 7] = AMBER_SPARK
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
    # WP-S2 item 2 fallout (2026-08-20): the border guard also caught this
    # one -- the translucent rim's vertical reach (d<34, /0.82 scale) put its
    # bottom edge past row 15. cy nudged up; shape/technique unchanged.
    cx, cy = 8.0, 8.0
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
    """Royal Jelly (M6): a viscous glob, not a shiny bead -- a rounded crown
    tapering to a hanging drip at the bottom, a DARKER core (the raw
    ingredient reads denser than its finished ROYAL_JELLY_TREAT cousin), a
    skin that brightens toward a translucent edge, and a bright specular.
    Play-test round 7: the old shape was a round bulb with a spike on top --
    that silhouette reads as a garlic clove no matter how it is coloured, so
    this reshapes it into a liquid-drop silhouette (round top, drip-point
    bottom) instead of just recolouring the same clove."""
    img = blank()
    px = img.load()

    # Play-test round 7 reshape: rows round over fast at the crown (y3-6),
    # belly out at y6-7, then taper SLOWLY into a long drip tail (y8-13) --
    # that asymmetry (round top, long tail) is what reads as hanging liquid
    # rather than the lens/gem shape a symmetric taper gives. Kept at
    # row<=13 so outline()'s +1px ring stays off the canvas edge (border
    # guard).
    rows = {
        3: range(6, 10), 4: range(4, 12), 5: range(3, 13), 6: range(2, 14),
        7: range(2, 14), 8: range(3, 13), 9: range(4, 12), 10: range(5, 11),
        11: range(6, 10), 12: range(7, 9), 13: range(7, 8),
    }
    for y, xs in rows.items():
        for x in xs:
            px[x, y] = JELLY_MID

    cx, cy = 7.5, 6.8

    def sdf(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        return dx * dx + dy * dy

    # a dark viscous core, brightening out to a translucent edge skin --
    # the same three-band ramp as the treat, just centred lower/darker so
    # the raw ingredient reads denser than its finished cousin.
    for y, xs in rows.items():
        for x in xs:
            d = sdf(x, y)
            if d < 10:
                px[x, y] = lerp_color(JELLY_RIM, JELLY_DEEP, min(1.0, d / 10))
            elif d < 24:
                px[x, y] = lerp_color(JELLY_DEEP, JELLY_MID, min(1.0, (d - 10) / 14))
            else:
                px[x, y] = lerp_color(JELLY_MID, JELLY_CORE, min(1.0, (d - 24) / 12))

    outline(img, JELLY_RIM)

    # specular: a bright comma on the upper-left shoulder of the glob
    for (x, y) in [(5, 6), (6, 5), (5, 7)]:
        if px[x, y][3] != 0:
            px[x, y] = JELLY_CORE
    px[6, 5] = (255, 253, 240, 255)
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


HORN_OUTLINE = (86, 46, 12, 255)     # dark 1px rim
HORN_SHADE = (128, 74, 22, 255)      # the inner side of the curve
HORN_BODY = (196, 138, 58, 255)
HORN_LIT = (232, 178, 92, 255)
HORN_RIDGE = (252, 220, 158, 255)    # bright ridge along the outer curve
HORN_TIP = (255, 244, 210, 255)
HORN_BAND = (150, 102, 28, 255)      # the shading bands crossing the body
HORN_MOUTH = (214, 164, 62, 255)     # royal gold mouthpiece
HORN_MOUTH_LIT = (242, 208, 126, 255)
HORN_MOUTH_DARK = (166, 120, 36, 255)

# The horn's centre line: a quadratic Bezier from the wide mouthpiece end
# (low left) to the tip (upper right), bowing up-left so the body actually
# CURVES rather than running straight -- and a radius that tapers with it.
# Diagonal on purpose: every other item in this mod is a vertical blob or a
# vial, so the silhouette alone identifies it.
HORN_BASE = (4.5, 10.0)
HORN_CONTROL = (7.2, 5.2)
HORN_TIP_POINT = (13.1, 4.0)
HORN_R_BASE = 2.8
HORN_R_TIP = 0.8
# Raw fill bounds. outline() then spends one more pixel on every side, which
# lands the finished sprite on x 1..14 / y 2..13 -- a 14x12 extent with the
# 1px transparent margin assert_item_borders_transparent requires.
HORN_X_MIN, HORN_X_MAX = 2, 13
HORN_Y_MIN, HORN_Y_MAX = 3, 12
# Where along the curve (0 = mouthpiece, 1 = tip) the anatomy sits. Note the
# wide end's rounded cap ALL projects to u ~ 0, so the mouthpiece is a block
# rather than a stripe; what makes it read as banded is HORN_COLLAR_U, the
# dark ring separating it from the body.
HORN_MOUTH_U = 0.11
HORN_MOUTH_BAND_U = 0.05     # one dark band inside the mouthpiece block
HORN_COLLAR_U = 0.17
HORN_BAND_U = (0.34, 0.55, 0.74)
HORN_BAND_HALF_U = 0.038
HORN_TIP_U = 0.93
# How far past the first sample the wide end may bulge. Without this the
# round cap makes the mouthpiece a blob; vanilla's goat_horn is squared off
# at that end, and the flat cut is most of what reads as "mouthpiece".
HORN_BASE_FLAT = 1.1


def pheromone_horn_item():
    """Pheromone Horn (M7): reusable summon, crafted from the queen's gland.

    Round-4 item 4 ("needs a texture fix"): the old sprite was a hand-written
    mask -- a four-column amber band ruled along the diagonal with a gold
    patch at one end. 84 opaque px, but flat: constant width for most of its
    length, no ridge, no bands, no mouthpiece to speak of. Vanilla's goat_horn
    (99 px over a 14x12 extent, read out of the client-extra jar) gets its
    read from anatomy instead -- a banded mouthpiece block at the wide end, a
    body that tapers as it curves, and shading bands across it.

    So this is now swept geometry rather than a mask: a tapering radius
    carried along a bowed Bezier centre line, shaded by the signed distance
    to that line (bright ridge on the outer curve, HORN_SHADE on the inner),
    with the mouthpiece, the shading bands and the lightened tip all keyed to
    position ALONG the curve so every band follows the bend instead of
    cutting straight across it."""
    img = blank()
    px = img.load()
    samples = 128
    pts = [_bezier_point(HORN_BASE, HORN_CONTROL, HORN_TIP_POINT,
                         i / (samples - 1.0)) for i in range(samples)]

    for y in range(HORN_Y_MIN, HORN_Y_MAX + 1):
        for x in range(HORN_X_MIN, HORN_X_MAX + 1):
            cx, cy = x + 0.5, y + 0.5
            best_d2, best_i = None, 0
            for i, (sx, sy) in enumerate(pts):
                d2 = (cx - sx) ** 2 + (cy - sy) ** 2
                if best_d2 is None or d2 < best_d2:
                    best_d2, best_i = d2, i
            u = best_i / (samples - 1.0)
            r = HORN_R_BASE + (HORN_R_TIP - HORN_R_BASE) * u
            if best_d2 > r * r:
                continue

            i0, i1 = max(0, best_i - 1), min(samples - 1, best_i + 1)
            tx, ty = pts[i1][0] - pts[i0][0], pts[i1][1] - pts[i0][1]
            length = (tx * tx + ty * ty) ** 0.5 or 1.0
            nx, ny = -ty / length, tx / length
            sx, sy = pts[best_i]
            # negative = the outer (upper-left) side of the bend, which is the
            # side the light comes from
            perp = (cx - sx) * nx + (cy - sy) * ny
            if best_i == 0:
                along = (cx - sx) * (tx / length) + (cy - sy) * (ty / length)
                if along < -HORN_BASE_FLAT:
                    continue

            if u <= HORN_MOUTH_U:
                if abs(u - HORN_MOUTH_BAND_U) < HORN_BAND_HALF_U * 0.6:
                    colour = HORN_MOUTH_DARK
                elif perp <= -r * 0.5:
                    colour = HORN_MOUTH_LIT
                elif perp >= r * 0.45:
                    colour = HORN_MOUTH_DARK
                else:
                    colour = HORN_MOUTH
            elif u <= HORN_COLLAR_U:
                colour = HORN_MOUTH_DARK
            elif u >= HORN_TIP_U:
                colour = HORN_TIP
            elif any(abs(u - b) < HORN_BAND_HALF_U for b in HORN_BAND_U):
                colour = HORN_BAND
            elif perp <= -r * 0.55:
                colour = HORN_RIDGE
            elif perp <= -r * 0.1:
                colour = HORN_LIT
            elif perp >= r * 0.5:
                colour = HORN_SHADE
            else:
                colour = HORN_BODY
            px[x, y] = colour

    return outline(img, HORN_OUTLINE)


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
#
# Ep2 task I4 redesign (2026-08-18): unlike the worn layers' AHEAD_* family,
# this legend already spans 34-222 -- plenty of contrast -- so the icon fix
# is not a value-range problem. Each mask's single-pixel "s" spark widens to
# two pixels (matching the eye-glint fix in chitin_layer_1), and the boots
# gain a claw-tip spark at the toe to echo the worn boots' own AJAW_TIP
# accent, so the icon and the worn piece read as the same designed object.
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
    ".ormmmssmmmmmro.",
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
    ".ormmmssmmmmmro.",
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
    ".orbsbo..orbsbo.",
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


CHITIN_RIVET = (206, 206, 206, 255)

PLATE_LEGEND = {
    ".": None,
    "o": CHITIN_OUTLINE,
    "d": CHITIN_DARK,
    "b": CHITIN_BASE,
    "m": CHITIN_MID,
    "r": CHITIN_RIM,
    "R": CHITIN_RIVET,
}

# Chitin Plate (round-4 item 3): the forged intermediate the armor and both
# end-goal tools are now crafted from, so its icon has to read at a glance in
# a crafting grid the way iron_ingot does -- a squat slab with a lit top-left
# bevel, a shaded bottom-right one, and four iron rivets marking the corners
# where the metal is worked into the chitin. Deliberately the only sprite in
# the chitin family carrying a grey tone: the rivets are what say "iron went
# into this" without a second material filling the face.
PLATE_MASK = [
    "................",
    "................",
    "................",
    "...oooooooooo...",
    "..orrrrrrrrrro..",
    "..orRmmmmmmRdo..",
    "..ormmmmmmmmdo..",
    "..ormmmmmmmmdo..",
    "..orbbbbbbbbdo..",
    "..orbbbbbbbbdo..",
    "..orRbbbbbbRdo..",
    "..oddddddddddo..",
    "...oooooooooo...",
    "................",
    "................",
    "................",
]


def chitin_plate_item():
    """Chitin Plate: two chitin bound to an iron ingot, netherite-style."""
    return paint_mask(PLATE_MASK, PLATE_LEGEND)


# ---------------------------------------------------------------------------
# Chitin tool set -- diagonal tool icons, vanilla's own layout: tip at the
# top-right corner, stick handle running down to the bottom-left.
#
# Ep2 play-test revision (WP-1 item 2): the original five-tool Ep2 H1 set
# (sword/pickaxe/axe/shovel/hoe) is gone, replaced by two hybrid tools --
# Mandible Pickaxe and Pincer Sword.
#
# Round-4 play-test revision, item 1 ("models are too small, compare them to
# vanilla"): both icons were rebuilt against the ACTUAL vanilla sprites, read
# out of the client-extra jar rather than remembered --
#   iron_sword     16x16, 84 opaque px, corner to corner, a five-column blade
#                  band (dark edge / white / mid / white / black), a
#                  three-line crossguard and a short wrapped grip;
#   iron_pickaxe   13x13 extent, 68 opaque px, a broad head arc across the
#                  whole top plus a limb down the right, and a 3px stick
#                  handle running the full diagonal.
# Ours measured 11x12 / 37 px (a twig) and 12x13 / 69 px with a stubby V for
# a head. The rewrite below matches vanilla's mass and silhouette instead of
# its own previous shape, and both tools are now netherite-class end-goal
# items, so each carries a pale specular run along its lit edge.
#
# `_tool_icon` (one symmetric width profile traced along one straight axis)
# went with that rewrite: it cannot express a blade split into two prongs by
# a seam, nor a head whose two picks curve away from the shaft on their own,
# and both painters now shade per-region instead of by distance from a single
# centre line. The shared (s, t) frame it introduced survives as
# `_tool_frame`, which is the part both tools actually reuse.
# ---------------------------------------------------------------------------

TOOL_STICK_LIGHT = (178, 140, 92, 255)
TOOL_STICK_DARK = (140, 104, 62, 255)
TOOL_STICK_EDGE = (96, 70, 38, 255)

# End-goal tools want a hotter highlight than CHITIN_SPARK: a few pale pixels
# along the lit edge is what separates "netherite-class" from "another brown
# item" at inventory scale.
CHITIN_PALE = (246, 198, 160, 255)

# WP-S2 item 2 (2026-08-20): both tool icons used to paint corner-to-corner --
# the stick ran to the literal (0,15) corner and the head tapered out toward
# the opposite corner -- and outline() then rings the filled shape with one
# more pixel on top of that, so the visible sprite touched all four canvas
# edges. Logan's screenshots showed the blade/prong truncating at the icon
# edge; first-person rendering (which crops and magnifies the icon further)
# made it worse. TOOL_MARGIN keeps every RAW fill pixel at least this far
# from the edge so that after outline()'s +1px ring the sprite still has a
# fully transparent row/column on every side -- not just a smaller version of
# the same corner-touching shape, an actually-contained one.
#
# Round 4: it stays at 2, and that is NOT what was making the icons small.
# outline() spends the second pixel of margin on the sprite's own dark ring,
# so a RAW fill filling the 2..13 box renders as a 14x14 sprite with a 1px
# transparent border -- exactly the largest icon assert_item_borders_transparent
# allows. The old icons were small because their width profiles never filled
# the box, not because the box was small.
#
# Round 8b: that reasoning was right about the width profiles and wrong about
# the ceiling. Measured against the client jar, EVERY vanilla sword is a 16x16
# bounding box -- tip at (15,0), pommel at (0,15), 84 opaque px, corner to
# corner. A margin of 2 caps us at 14x14, which is 77% of vanilla's footprint,
# and no width profile can buy that back. So the sword now uses a margin of 1.
#
# That is still safe against the bug this constant was created for. outline()
# is 4-NEIGHBOUR (see its loop), so a raw fill pixel at (14,1) gets its ring at
# (15,1) and (14,0) -- both inside the canvas. Every raw pixel keeps its full
# dark ring, which is what "truncating at the icon edge" actually meant. What
# a margin of 1 gives up is only the fully-transparent border row, which is a
# convention of ours and not one vanilla observes. A margin of 0 would be the
# real hazard: raw fill on row 0 has nowhere to put its ring, and THAT reads as
# a cut-off shape.
TOOL_MARGIN = 2
TOOL_MIN = TOOL_MARGIN
TOOL_MAX = SIZE - 1 - TOOL_MARGIN  # 13: highest x/y a raw fill pixel may use

# The sword matches vanilla's corner-to-corner footprint; the pickaxe does not
# need to -- measured, ours is already 14x14 against vanilla's 13x13.
SWORD_MARGIN = 1


def _in_tool_bounds(x, y, margin=TOOL_MARGIN):
    return margin <= x <= SIZE - 1 - margin and margin <= y <= SIZE - 1 - margin


def _tool_frame(x, y):
    """The rotated frame both tools are drawn in: `s` is the perpendicular
    offset from the tool's diagonal axis (the antidiagonal x + y == SIZE - 1),
    positive toward the lower right; `t` is the position along that axis,
    negative toward the bottom-left butt and positive toward the top-right
    tip. Over the raw fill box (TOOL_MIN..TOOL_MAX) t runs -11..+11, with the
    two corners (TOOL_MIN, TOOL_MAX) and (TOOL_MAX, TOOL_MIN) sitting at
    s == 0. Note s and t always have opposite parity, so a fixed t line
    contains every OTHER s -- widths are therefore reasoned about per raster
    row, where s and t both advance by one per pixel."""
    return x + y - (SIZE - 1), x - y


def _bezier_point(p0, p1, p2, t):
    """A point on the quadratic Bezier curve p0 -> p1 (control) -> p2."""
    mt = 1.0 - t
    x = mt * mt * p0[0] + 2.0 * mt * t * p1[0] + t * t * p2[0]
    y = mt * mt * p0[1] + 2.0 * mt * t * p1[1] + t * t * p2[1]
    return x, y


def _paint_prong(px, base, control, tip, half_width_start, half_width_end,
                 samples=64, lit_side=1):
    """Rasterises one tapered, curved pick as a dense set of samples along a
    quadratic Bezier from `base` to `tip` (bowing toward `control`), shading
    each covered pixel by which side of the LOCAL tangent it falls on (RIM on
    the outer edge, DARK on the inner edge, MID in between) rather than by
    position in a single global frame -- which is what lets the stroke curve
    on its own instead of just widening along one straight axis.

    `lit_side` picks WHICH side of the tangent catches the light. The
    tangent's left normal depends on the direction the stroke is drawn in, so
    two picks leaving the same socket in opposite directions get opposite
    normals and, at lit_side=1 for both, opposite shading -- which is how the
    round-4 pickaxe first came out with a dark top arc and a lit underside.
    Pass -1 on a stroke drawn "backwards" to keep the light in one place."""
    pts = [_bezier_point(base, control, tip, i / (samples - 1)) for i in range(samples)]
    for y in range(SIZE):
        for x in range(SIZE):
            if not _in_tool_bounds(x, y):
                continue
            cx, cy = x + 0.5, y + 0.5
            best_d2, best_i = None, 0
            for i, (sx, sy) in enumerate(pts):
                d2 = (cx - sx) ** 2 + (cy - sy) ** 2
                if best_d2 is None or d2 < best_d2:
                    best_d2, best_i = d2, i
            t = best_i / (samples - 1)
            half = half_width_start + (half_width_end - half_width_start) * t
            if best_d2 > half * half:
                continue
            i0, i1 = max(0, best_i - 1), min(samples - 1, best_i + 1)
            tx, ty = pts[i1][0] - pts[i0][0], pts[i1][1] - pts[i0][1]
            length = (tx * tx + ty * ty) ** 0.5 or 1.0
            tx, ty = tx / length, ty / length
            nx, ny = -ty, tx
            sx, sy = pts[best_i]
            perp = ((cx - sx) * nx + (cy - sy) * ny) * lit_side
            if perp <= -half * 0.4:
                px[x, y] = CHITIN_RIM
            elif perp >= half * 0.4:
                px[x, y] = CHITIN_DARK
            else:
                px[x, y] = CHITIN_MID


def _paint_diagonal_shaft(px, t_min, t_max, s_values, colour_fn, margin=TOOL_MARGIN):
    """The stick handle both tools hang off: every raw pixel whose (s, t) has
    s in `s_values` and t in [t_min, t_max], coloured by colour_fn(s, t). One
    helper rather than two copies of the same rotated-frame loop, because the
    two tools differ only in how wide and long the shaft is and how it is
    shaded. outline() adds a dark column either side afterwards, so a
    three-value s_values renders as vanilla's five-column grip."""
    for y in range(SIZE):
        for x in range(SIZE):
            if not _in_tool_bounds(x, y, margin):
                continue
            s, t = _tool_frame(x, y)
            if s not in s_values or not (t_min <= t <= t_max):
                continue
            colour = colour_fn(s, t)
            if colour is not None:
                px[x, y] = colour


# --- Pincer Sword geometry, in the (s, t) frame ----------------------------
# Laid out against vanilla iron_sword's proportions: roughly 60% blade, 15%
# crossguard, 25% grip, measured along the diagonal. Ours has 22 diagonal
# steps of raw box (t in -11..11) where vanilla has 30 of full canvas.
#
# Round 8b re-derivation. Every vanilla sword was profiled in THIS (s, t)
# frame straight out of the client jar, and the layout below copies its
# proportions (numbers, never pixels). Vanilla, over its 31 outlined rungs:
#
#   blade   t = -2..+15   18 rungs, a constant five-column band (half 2.5)
#                         held all the way out, tapering only in the last two
#   guard   t = -6..-3     4 rungs, reaching s = +-7 at its widest
#   grip    t = -12..-7    6 rungs, one or two cells per rung
#   pommel  t = -15..-13   3 rungs, widening to 3 cells then closing to the
#                          corner
#
# Two things that re-derivation settled. Our blade band was ALREADY vanilla's
# thickness -- half 2.5 either side -- so the icon never read thin, it read
# SHORT: 25 occupied rungs against 31, and a 14x14 box against 16x16. And our
# crossguard reached s = +-4 where vanilla's reaches +-7, which is most of why
# ours read as a long knife rather than a weapon with heft.
SWORD_BLADE_T_MIN = -2      # perpendicular cut where blade meets crossguard
# Three lines thick: a dark separator against the blade, one line of lit
# body, and a shaded underside -- and GOLD, not chitin. The first round-4
# pass painted the guard in the blade's own reds and it disappeared into it
# as one club-shaped mass; a hue break is what makes a crossguard read at
# 16px, and royal gold is the material the recipe actually spends (a Queen's
# Crest sits in the guard slot of the crafting shape). Palette borrowed from
# the horn's mouthpiece golds.
SWORD_GUARD_T_MIN = -5
SWORD_GUARD_T_MAX = -3      # 3 PAINTED rungs. Vanilla's guard occupies 4
                            # rungs in the finished sprite, but the outermost
                            # is its outline -- painting 4 gave us 5 thick
                            # rungs once ringed (t = -7..-2 all 7-8 cells
                            # wide), a gold slab that put the icon 30% over
                            # vanilla's pixel weight and straight back toward
                            # the club silhouette round 4 fixed.
SWORD_GUARD_HALF_S = 6      # how far the guard reaches to either side. Was 4,
                            # which outlined to +-5 against vanilla's +-7; 6
                            # outlines to +-7 and lands on vanilla exactly.
SWORD_GRIP_T_MIN = -13      # the pommel end (the (1,14) raw corner)
SWORD_GRIP_T_MAX = -7       # everything above this belongs to the guard
SWORD_GRIP_S = (0, 1)       # two staggered columns -- the first pass used
                            # three (+outline = five visual), nearly as wide
                            # as the blade, which is half of why it read as a
                            # club rather than a sword
SWORD_POMMEL_T = -11        # at/below this the grip caps off as a pommel --
                            # 3 rungs of gold (t = -11..-13), vanilla's own
                            # pommel length
SWORD_TAPER_T = 11          # blade holds full width to here, then tapers.
                            # Vanilla holds full width to t == +13 of its 15
                            # and spends the last 3 rungs on the point; ours
                            # holds to 11 of 13 and spends 3, the same ratio.
                            # Holding the five-column band this far out is the
                            # "powerful weapon" half of the ask -- an early
                            # taper spends the blade on air.
SWORD_HALF_WIDE = 2.5       # -> a five-column band: two lit columns, the
                            #    seam, two more -- both prongs have to catch
                            #    light or the icon reads as one bright edge
                            #    with a shadow rather than as a pair
SWORD_BAND_CENTRE = 0.0
SWORD_TIP_T = 13            # t of the raw corner pixel, (14, 1)
                            # -- filled, so the blade ends in a real point.
                            # A forked "pincer mouth" tip was tried here
                            # (leave t == 11 empty, let the two t == 10
                            # pixels stand as prong points): it does not
                            # survive outline(), which floods the one-cell
                            # notch with the same dark it rings the whole
                            # sprite with, so the fork just reads as a blunt
                            # end. The pincer identity lives in the seam; the
                            # tip's job is to look sharp.
# Three pale specular pixels marching up the blade's lit edge (s == -2, so t
# must be odd for the pixel to exist at all -- see _tool_frame).
SWORD_SPECULAR_T = (-1, 1, 3)


# Hand-tuned, not linear, because of the parity trade the frame forces: a
# raster row holds only every OTHER s (see _tool_frame), so the achievable
# rung widths alternate -- an odd t can only be 3 cells wide or 1, an even t
# only 2 or 0. There is no 2 on an odd row to taper through, which is why
# this is a table and not an expression.
#
# Round 8 made the tip a 5-rung needle, which was right for a blade that
# stopped at t == 11 and wrong for one that now runs to the corner. Rendered
# at 14x it read as a DETACHED pixel: a needle skips every other rung (an odd
# t can hold s == 0, the even t either side cannot), outline() fills those
# skipped rungs with dark, and the last cell ends up touching the blade only
# diagonally, ringed in black on every other side.
#
# Vanilla does not skip. Profiled from the jar, its tip is t == +13, +14, +15
# holding 3, 2, 1 cells -- three CONSECUTIVE rungs, so the point is solid.
# That is the shape here now: full five-column band all the way to t == 11,
# then 3 -> 2 -> 1 into the corner.
#
# Worth stating because it constrains any future retune: a monotone taper with
# no gaps can be at most three rungs long, because after a 1-cell odd rung the
# next even rung must be 2 (widening) or 0 (a gap). Vanilla's tip is three
# rungs for exactly this reason, not by taste.
#
# Widths are exclusive (|s| < half), so each value only has to land between
# the cell it keeps and the next one out: 1.6 keeps s == +-1, 0.9 keeps only
# s == 0.
SWORD_TIP_HALVES = {12: 1.6, 13: 0.9}
SWORD_NEEDLE_HALF = 0.9     # fallback: anything past the table is one cell
                            # wide, never a pinch back to dark (see the
                            # s == 0 branch in pincer_sword_item)


def _sword_blade_half(t):
    """Half-width of the blade band at t: flat until SWORD_TAPER_T, then the
    hand-tuned SWORD_TIP_HALVES run down to a single-pixel point."""
    if t <= SWORD_TAPER_T:
        return SWORD_HALF_WIDE
    return SWORD_TIP_HALVES.get(t, SWORD_NEEDLE_HALF)


def pincer_sword_item():
    """Two chitin pincer prongs running the full diagonal as one blade,
    parted by a dark seam and meeting at the tip; a crossguard perpendicular
    to them; a wrapped grip and a bright pommel.

    Round-4 item 1: the previous icon was a single tapering claw about two
    pixels wide -- 37 opaque px against iron_sword's 84 -- which is why it
    read as a twig next to a vanilla weapon.

    Round 8b ("bigger, same footprint as a vanilla sword, feel like a
    powerful weapon"): round 4 matched vanilla on pixel COUNT and quietly
    missed on footprint. Measured against the client jar, every vanilla sword
    is a 16x16 bounding box; ours was 14x14, or 77% of the area, because
    TOOL_MARGIN reserved two pixels of border it did not need. The sword now
    uses SWORD_MARGIN = 1 and lands on 16x16, 15 occupied rungs, with a
    crossguard whose widest rungs (7, 8, 7 cells at t == -3, -4, -5) are
    vanilla's numbers exactly. It carries 108 px against vanilla's 84 -- the
    surplus is the pincer band, which needs five core columns where a vanilla
    blade needs three, and it suits a weapon that sits above netherite.

    This one fills the raw box corner to corner, five chitin columns across
    the middle of the blade,
    shaded SPARK / RIM / DARK-seam / RIM / MID from the lit upper-left edge
    down to the shaded lower-right one. The seam is the whole trick: two
    parallel prong masses either side of one dark column read as a pincer,
    where a single mass of the same width just reads as a wider blade -- and
    BOTH flanking columns have to be lit, or the dark column reads as shading
    on one blade instead of the gap between two. The band holds full width to
    t == 11 and then closes 3 -> 2 -> 1 into the canvas corner, vanilla's own
    tip shape (see SWORD_TIP_HALVES for why a solid taper can only be three
    rungs, and SWORD_TIP_T on the forked tip that was tried and rejected). The
    crossguard and pommel are royal gold (the crest the recipe spends), the
    one hue break on the icon, which is what stops guard, grip and blade
    merging into a single club-shaped chitin mass -- the failure of this
    icon's first round-4 pass."""
    img = blank()
    px = img.load()

    def grip_colour(s, t):
        if t <= SWORD_POMMEL_T:
            # gold cap, matching the guard -- the two gold masses bracketing
            # the dark wrap are what make the hilt read as one fitting
            return HORN_MOUTH
        # alternating wrap bands, one diagonal step apart
        return TOOL_STICK_LIGHT if t % 2 else TOOL_STICK_DARK

    _paint_diagonal_shaft(px, SWORD_GRIP_T_MIN, SWORD_GRIP_T_MAX,
                          SWORD_GRIP_S, grip_colour, margin=SWORD_MARGIN)

    for y in range(SIZE):
        for x in range(SIZE):
            if not _in_tool_bounds(x, y, SWORD_MARGIN):
                continue
            s, t = _tool_frame(x, y)

            if SWORD_GUARD_T_MIN <= t <= SWORD_GUARD_T_MAX:
                if abs(s) > SWORD_GUARD_HALF_S:
                    continue
                # Gold, in three lines: dark separator facing the blade, lit
                # body, shaded underside -- with only the outermost cell of
                # each arm darkened (darkening two per side shrank the lit
                # bar to a blob in the second round-4 pass). See the geometry
                # comment above on why the hue break is the whole point.
                if abs(s) >= SWORD_GUARD_HALF_S:
                    px[x, y] = HORN_MOUTH_DARK
                elif t == SWORD_GUARD_T_MAX:
                    px[x, y] = HORN_MOUTH_DARK
                elif t == SWORD_GUARD_T_MIN:
                    px[x, y] = HORN_MOUTH
                else:
                    px[x, y] = HORN_MOUTH_LIT
                continue

            if t < SWORD_BLADE_T_MIN:
                continue
            half = _sword_blade_half(t)
            if abs(s - SWORD_BAND_CENTRE) >= half:
                continue
            # The seam column only darkens where a lit prong flanks it on the
            # SAME raster row (parity puts s == +-2 on the wide odd-t rows);
            # on the narrow rows near the tip where s == 0 stands alone it
            # brightens to RIM instead, so the taper never pinches down to a
            # single dark pixel that reads as the blade snapping.
            if s <= -2:
                px[x, y] = CHITIN_PALE if t in SWORD_SPECULAR_T else CHITIN_SPARK
            elif s == -1:
                # the upper prong stays lit all the way out to its fork point
                px[x, y] = CHITIN_SPARK if t >= 7 else CHITIN_RIM
            elif s == 0:
                # CHITIN_BASE, not CHITIN_DARK: near-black seam pixels on
                # alternating rows read as rivet holes down a sausage; one
                # shade darker than the prongs reads as the groove between
                # them
                px[x, y] = CHITIN_BASE if half >= 2.0 else CHITIN_RIM
            elif s == 1:
                px[x, y] = CHITIN_RIM
            else:
                px[x, y] = CHITIN_MID

    return outline(img, CHITIN_OUTLINE)


# --- Mandible Pickaxe geometry ---------------------------------------------
PICK_SHAFT_T_MIN = -11      # the (2,13) raw corner
PICK_SHAFT_T_MAX = 9        # the shaft runs nearly the whole diagonal, and
                            # its last step or two pokes past the head at the
                            # top right exactly as vanilla's handle does
PICK_SHAFT_S = (0, 1)       # two columns -> vanilla's four-column stick once
                            # outline() has added an edge either side
PICK_SOCKET = (10.6, 4.6)   # where both picks meet the shaft, near the top
# Each pick: a quadratic Bezier leaving the socket, arcing away, and turning
# back DOWN at the tip -- the downward hook is what reads as an ant mandible
# rather than a spike. The long left pick sweeps the whole top of the canvas
# (control at y ~ 1.4) and the short right one drops down the right-hand side,
# which between them is vanilla iron_pickaxe's head: a broad top arc plus a
# descending right limb, with the handle socketed at the top right.
# (control, tip, lit_side) -- see _paint_prong on why the two picks need
# opposite lit_side values to be lit from the same direction.
PICK_LEFT = ((6.0, 1.4), (2.8, 4.6), -1)
PICK_RIGHT = ((13.4, 5.0), (12.6, 8.6), 1)
PICK_HALF_START = 1.8
PICK_HALF_END = 0.5
# Two pale specular pixels on the top arc's outer shoulder.
PICK_SPECULAR = ((5, 2), (8, 2))


def mandible_pickaxe_item():
    """Vanilla's pickaxe silhouette in chitin: a long diagonal stick handle
    from the bottom-left, and a broad head arc spanning the whole top, drawn
    as two curved mandible picks that meet at the handle socket and turn
    down at their tips.

    Round-4 item 1: the previous head was a stubby V planted mid-canvas --
    both picks left the shaft at (6.5, 8.5) and ran only to about y=3.4, so
    the head occupied the middle of the icon instead of arcing across its
    top, and nothing about it said 'pickaxe'. Vanilla iron_pickaxe's head
    spans x5..x14 across rows 2-4 and its handle runs the full diagonal;
    this now does the same, with the socket pushed up the shaft to
    PICK_SHAFT_T_MAX so both picks have room to arc rather than splay.

    The picks stay two independent tapered Bezier strokes (see _paint_prong),
    each shaded by which side of its own LOCAL tangent a pixel falls on --
    that is what lets them curve away from the shaft and hook back down
    instead of being one lobe that widens and narrows along a single axis."""
    img = blank()
    px = img.load()

    def shaft_colour(s, t):
        return TOOL_STICK_LIGHT if s == 0 else TOOL_STICK_EDGE

    _paint_diagonal_shaft(px, PICK_SHAFT_T_MIN, PICK_SHAFT_T_MAX,
                          PICK_SHAFT_S, shaft_colour)

    for control, tip, lit_side in (PICK_LEFT, PICK_RIGHT):
        _paint_prong(px, PICK_SOCKET, control, tip,
                     PICK_HALF_START, PICK_HALF_END, lit_side=lit_side)

    for (sx, sy) in PICK_SPECULAR:
        if px[sx, sy][3] != 0:
            px[sx, sy] = CHITIN_PALE

    return outline(img, CHITIN_OUTLINE)


# ---------------------------------------------------------------------------
# Provisions (Ep2 D1) -- the three colony foods (spec section 8)
# ---------------------------------------------------------------------------

def honeyed_comb_item():
    """Honeyed Comb: a broken-off, honey-glazed hunk of wax comb. Play-test
    round 7 art pass -- the old version was a flat SDF blob with three
    isolated notch pixels standing in for "comb", which measured at 116
    opaque px but carried no actual cell structure (vanilla's honeycomb item
    is a visible grid of cells at a comparable pixel count). This keeps the
    tilted-ellipse silhouette but fills it with `comb()`'s own offset
    cell-grid technique at a finer 3px pitch, so it reads as a chunk of
    comb rather than a biscuit -- while staying a bounded chunk silhouette,
    not a tiling wall texture, which is what keeps it distinct from the
    brood/royal/provision_comb BLOCK textures (each a full 16x16 tile)."""
    img = blank()
    px = img.load()
    line = (150, 96, 20, 255)
    cap = (240, 188, 66, 255)
    cap_hi = (255, 226, 140, 255)
    cap_shade = (196, 138, 34, 255)
    rim = (110, 68, 16, 255)
    drip = (222, 158, 40, 255)
    drip_hi = (255, 214, 100, 255)

    def sdf(x, y):
        dx, dy = x + 0.5 - 8.0, y + 0.5 - 7.0
        u = dx * 0.92 + dy * 0.30
        v = -dx * 0.30 + dy * 0.92
        return (u * u) / 34.0 + (v * v) / 22.0

    cell = 3
    for y in range(SIZE):
        row = y // cell
        xo = 1 if row % 2 else 0
        for x in range(SIZE):
            if sdf(x, y) >= 1.0:
                continue
            ccx = (x + xo) % cell
            ccy = y % cell
            if ccx == 0 or ccy == 0:
                px[x, y] = line
            elif ccx == 1 and ccy == 1:
                px[x, y] = cap_hi
            elif ccx == cell - 1 or ccy == cell - 1:
                px[x, y] = cap_shade
            else:
                px[x, y] = cap

    outline(img, rim)

    # a couple of honey drips off the lower edge -- kept at row<=13 so
    # outline()'s +1px ring stays off the canvas edge (border guard).
    px[6, 12] = drip
    px[6, 13] = drip_hi
    px[10, 11] = drip

    # glaze highlight, upper-left shoulder
    if px[5, 4][3] != 0:
        px[5, 4] = (255, 236, 170, 255)
    if px[6, 4][3] != 0:
        px[6, 4] = (255, 226, 140, 255)
    return img


# WP-2 play-test round 7: de-bowled. FUNGAL_STEW's display name is now
# "Fungal Cake" (ModLanguageProvider) -- the registry id and this function's
# name deliberately keep the old word (see ModItems' javadoc for why).
def fungal_stew_item():
    """Fungal Cake: a pressed patty of colony fungus, not a bowl of stew.
    Slab silhouette with a slightly domed top, painted in FUNGAL_PAL --
    the same teal-green already established by fungal_bloom/fungal_carpet,
    reused rather than inventing a new palette -- flecked with brighter
    FUNGAL_GLOW/FUNGAL_BRIGHT spore specks, a dark rim, and a lit top edge.
    It should look like something an ant colony pressed out of its own
    fungus, not something served in a dish."""
    img = blank()
    px = img.load()
    dark, mid1, mid2, light, pale = FUNGAL_PAL

    # slab body: mostly straight sides (a pressed patty, not a mushroom cap)
    # -- only the top row narrows, giving a slight dome instead of a peak.
    rows = {
        4: range(4, 12),
        5: range(3, 13),
        6: range(2, 14),
        7: range(2, 14),
        8: range(2, 14),
        9: range(2, 14),
        10: range(2, 14),
        11: range(3, 13),
    }
    for y, xs in rows.items():
        for x in xs:
            px[x, y] = mid2

    # lit top edge -- one row, the dome's crown catching the light
    for x in range(4, 12):
        px[x, 4] = light
    # a pressed seam line partway down -- reads as a compressed layer, not
    # a smooth round gem
    for x in range(2, 14):
        if px[x, 7][3] != 0:
            px[x, 7] = mid1
    # shaded underside -- the pressed slab's bottom face
    for x in range(3, 13):
        px[x, 11] = dark
    for x in range(2, 14):
        px[x, 10] = mid1

    outline(img, dark)

    # spore specks, deterministic -- brighter and more scattered than the
    # slab's own mid-tone so they read as flecks, not shading
    r = rng("fungal_stew:specks")
    spots = []
    for _ in range(6):
        x, y = r.randint(3, 12), r.randint(5, 10)
        spots.append((x, y))
    for (x, y) in spots[:4]:
        if px[x, y][3] != 0:
            px[x, y] = FUNGAL_GLOW
    for (x, y) in spots[4:]:
        if px[x, y][3] != 0:
            px[x, y] = FUNGAL_BRIGHT
    return img


def royal_jelly_treat_item():
    """Royal Jelly Treat: a glossy, SET jelly candy on a comb-wafer base --
    royal_jelly_item's own droplet family (JELLY_CORE/MID/RIM, reused, not
    reinvented) but the finished half of the pair: a light, glossy centre
    that brightens again right at the edge (a translucent-glass look, as if
    light is passing back out through the jelly's skin) instead of the raw
    ingredient's dark viscous core, and a stronger specular. Play-test round
    7: the old flat wafer + soft droplet read as butter on a plank, so the
    wafer now carries a hex-cell hint (a grout line, a lit top corner) to
    read as comb."""
    img = blank()
    px = img.load()
    cx, cy = 8.0, 7.5

    def sdf(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        return dx * dx + dy * dy

    # glossy droplet: a rich gold body (JELLY_MID, not the pale JELLY_CORE --
    # a whole droplet of the palest tone is what read as butter), then
    # translucent brightening right at the very edge instead of the raw
    # ingredient's dark-core ramp -- that edge band plus the specular below
    # are the only places the pale tone appears.
    for y in range(SIZE):
        for x in range(SIZE):
            d = sdf(x, y)
            if d < 8:
                px[x, y] = JELLY_MID
            elif d < 13:
                px[x, y] = lerp_color(JELLY_MID, JELLY_DEEP, (d - 8) / 5.0)
            elif d < 19:
                px[x, y] = lerp_color(JELLY_DEEP, JELLY_CORE, (d - 13) / 6.0)

    # comb wafer base beneath the droplet, with a hex-cell hint so it reads
    # as comb rather than a plain plank
    wafer = (150, 104, 40, 255)
    wafer_lit = (192, 140, 62, 255)
    wafer_line = (108, 72, 24, 255)
    for y in range(11, 14):
        for x in range(3, 13):
            if px[x, y][3] == 0:
                px[x, y] = wafer
    for x in range(3, 13):
        px[x, 11] = wafer_lit
    # a row of small grout marks -- a hex-cell hint, not full-height bars
    # (which read as drawer slats rather than comb)
    for (x, y) in [(5, 12), (8, 12), (10, 12), (6, 13), (9, 13)]:
        px[x, y] = wafer_line

    outline(img, JELLY_RIM)

    # strong specular -- bigger and brighter than the raw jelly's single dot
    for (x, y) in [(5, 5), (6, 4), (5, 6), (6, 5), (7, 4)]:
        if px[x, y][3] != 0:
            px[x, y] = JELLY_CORE
    px[6, 4] = (255, 253, 240, 255)
    px[6, 5] = (255, 253, 240, 255)
    return img


# --- Soldier-ant palette, mirrored from models.py S_* constants: the armor is
# --- literally plates of soldier chitin, so it reuses the exact tones.
SOLD_DARKEST = (26, 10, 8, 255)
SOLD_DARK = (58, 18, 14, 255)
SOLD_BASE = (92, 28, 20, 255)
SOLD_MID = (118, 40, 28, 255)
SOLD_LIGHT = (145, 55, 38, 255)
SOLD_PALE = (168, 70, 48, 255)
AHEAD_DARK = (30, 13, 11, 255)     # deepest head-plate tones -- hidden caps, joints
AHEAD_BASE = (46, 19, 15, 255)
# Ep2 task I4 (2026-08-18) banked this lesson: the helmet used to be painted out
# of an AHEAD_* ramp that topped out at 82, so next to the body's SOLD_LIGHT /
# SOLD_PALE (145/168) it had a much narrower value range -- which is exactly why
# it read as a flat dark blob on a posed armor stand while the torso's banding
# was still legible. Round 7 (2026-08-21) keeps that lesson by removing its
# cause: the helmet's visible faces are now painted from the SAME ramp the torso
# uses (SOLD_BASE 92 -> SOLD_PALE 168 -> CHITIN_RIM 196), so the head cannot
# drift dark relative to the body again. AHEAD_DARK/_BASE survive only for
# genuinely hidden geometry (boot sole, hip cap), where flatness is correct.
AJAW_TIP = (206, 122, 78, 255)     # pale mandible tip -- claw/clasp/face-frame accents
# The AEYE / EYE_GLOW painted "eye lenses" are GONE as of round 7: the helmet now
# has a real transparent face opening, which makes painted eyes both redundant and
# the exact thing Logan named ("just a bunch of lines with eyes").


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


def band_mask(y0, rows, width, first_row, last_row):
    """Coverage rows for a plain rectangular band: full width for absolute
    texture rows first_row..last_row (inclusive), transparent everywhere else.
    Returns `rows` strings of `width` chars, the first describing row y0."""
    return ["#" * width if first_row <= y0 + i <= last_row else "." * width
            for i in range(rows)]


def paint_strip(img, name, x0, y0, mask, ramp, roles, faces, bulge=14,
                seam_fill=None, rim_fill=None, corner=0.42):
    """Paint a UV strip from three parallel per-row tables plus a face list.

    Round 7 (2026-08-21) split what the old painter conflated:

      * `mask`  -- COVERAGE. One string per texture row starting at y0,
        '#' = plated, '.' = left transparent. The old
        `segment_plates`/`flat_plate` pair had no coverage table at all: it
        filled every rect it was handed, which is exactly why the set read as
        chunky and bulky.
      * `ramp`  -- the row's base COLOUR.
      * `roles` -- what the row IS: "rim" (a plate's lit top edge), "body", or
        "seam" (the dark gap where the next plate overlaps this one).

    `faces` is the list of face widths in strip order (right | front | left |
    back). The role table is applied PER FACE, not per row, and that is the
    point of this rewrite: painting a seam or a highlight straight across all
    64 columns is what produced Logan's "just a bunch of lines". Instead --

      * a seam only darkens the middle ~half of each face and reverts to
        `seam_fill` at the flanks, so plate boundaries read as a row of
        overlapping dashes rather than one continuous rule;
      * a rim highlight likewise fades to `rim_fill` at the flanks, so it
        reads as a specular on a curved plate rather than a stripe;
      * the outermost column of every face -- a real box corner, always in
        shadow -- is pulled toward SOLD_DARKEST, which lays a vertical grid
        over the whole piece and breaks any horizontal continuity that is
        left."""
    seam_fill = seam_fill or SOLD_BASE
    rim_fill = rim_fill or SOLD_MID
    px = img.load()
    for row, bits in enumerate(mask):
        base = ramp[row]
        if base is None:
            continue
        role = roles[row]
        col = 0
        for fw in faces:
            for i in range(fw):
                if bits[col + i] != "#":
                    continue
                frac = abs((i + 0.5) - fw / 2.0) / (fw / 2.0)   # 0 centre .. 1 edge
                if role == "seam" and frac > 0.5:
                    c = seam_fill
                elif role == "rim" and frac > 0.6:
                    c = rim_fill
                else:
                    c = base
                if role != "seam":
                    d = int((0.5 - frac) * bulge)
                    c = (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d // 2)),
                         max(0, min(255, c[2] + d // 2)), 255)
                if i == 0 or i == fw - 1:
                    c = lerp_color(c, SOLD_DARKEST, corner)
                px[x0 + col + i, y0 + row] = c
            col += fw
        if role != "seam":
            _jitter(px, name, x0, y0 + row, len(bits), 1)


def solid_cap(img, name, x0, y0, w, h, base, edge):
    """A cap face (top of head, sole of boot, hip joint): flat body with a
    darker border. Caps sit against the player and are barely seen edge-on, so
    they stay simple -- the shaping budget goes on the side strips."""
    px = img.load()
    for yy in range(h):
        for xx in range(w):
            border = yy in (0, h - 1) or xx in (0, w - 1)
            px[x0 + xx, y0 + yy] = edge if border else base
    _jitter(px, name, x0, y0, w, h)


# Box-UV rects for the vanilla humanoid armor model, read off HumanoidModel /
# HumanoidArmorModel in the decompiled 1.21 sources rather than from memory:
#   head  texOffs(0,0)   8x8x8   -> sides x0..31  y8..15,  caps x8..23  y0..7
#   body  texOffs(16,16) 8x12x4  -> sides x16..39 y20..31, caps x20..35 y16..19
#   arm   texOffs(40,16) 4x12x4  -> sides x40..55 y20..31, caps x44..51 y16..19
#   leg   texOffs(0,16)  4x12x4  -> sides x0..15  y20..31, caps x4..11  y16..19
#   hat   texOffs(32,0)  8x8x8   -> sides x32..63 y8..15,  caps x40..55 y0..7
# (mirrored left arm/leg reuse the same rects, so each is painted once. The hat
# box is part of the HEAD slot's model and is left fully transparent, exactly as
# vanilla's own armor layers leave it -- it is inflated a further 0.5, so
# anything painted there would float off the helmet.)
#
# Within a side strip the four faces run right | front | left | back; within a
# cap pair the FIRST slot is the UP face and the SECOND the DOWN face. Both were
# established by measurement, not recall -- see the coverage block below.
ARMOR_TEX_W, ARMOR_TEX_H = 64, 32

HEAD_SIDE_FACES = (8, 8, 8, 8)      # right | front | left | back
BODY_SIDE_FACES = (4, 8, 4, 8)
ARM_SIDE_FACES = (4, 4, 4, 4)
LEG_SIDE_FACES = (4, 4, 4, 4)

# ---------------------------------------------------------------------------
# Round-7 coverage (2026-08-21). Logan: "it looks way too chunky and bulky ...
# the chestplate should also only cover the same amount of player space as a
# normal vanilla chestplate, so not the arms, and same with the legs and boots."
#
# That is measurable, and it was measured. Vanilla iron_layer_1/_2 were pulled
# out of the 1.21 client jar into a scratch dir and their OPAQUE ROWS COUNTED
# per box-UV rect. Only the resulting NUMBERS live here -- no vanilla pixel,
# alpha channel or file was copied into this repo. What the count said:
#
#   region                    vanilla rows (of the rect)   vanilla px
#   helmet cap  (UP slot)     all 8x8                             64
#   helmet cap  (DOWN slot)   none -- never painted                 0
#   helmet sides              y8..y14, shaped, face notch open    154
#   chest caps  (both)        none -- never painted                 0
#   chest sides               y20..y30, full only y22..y28        222
#   sleeve cap  (UP slot)     all 4x4                              16
#   sleeve sides              y20..y24 ONLY (5 of 12 rows)         80
#   boot cap    (DOWN slot)   all 4x4                              16
#   boot sides                y26..y31 ONLY (6 of 12 rows)         96
#   ------------------------------------------------------- layer_1 648
#   belt (body) sides         y27..y31 ONLY -- the WAIST           120
#   greave sides              y20..y28 ONLY (9 of 12 rows)        144
#   greave cap  (UP slot)     all 4x4                              16
#   ------------------------------------------------------- layer_2 280
#
# Ours was 1056 / 296, because the old painter filled every rect it was handed:
# the sleeve ran the arm's full 12 rows instead of 5, both chest caps and the
# helmet's DOWN cap were solid, and the leggings' belt sat at y20..y24 -- the
# top of the torso box, i.e. across the CHEST -- instead of at the waist.
# ---------------------------------------------------------------------------

SLEEVE_ROWS = (20, 24)      # chestplate sleeve: stops partway down the upper arm
BOOT_ROWS = (26, 31)        # boots stay boot-height
GREAVE_ROWS = (20, 28)      # leggings stop above the ankle, leaving the boot room
BELT_ROWS = (27, 31)        # leggings' belt sits at the WAIST, not the chest

# Antenna UV allocation. This block and ChitinArmorModel.java are ONE source of
# truth in two files -- the Java mirrors these four numbers as named constants
# and cites this block. Both segments are cubes of w=1, h=LEN, d=1, so each
# occupies a 4-wide strip (east|north|west|south) under a 2px cap row:
#   scape    x56..59, y16..21      funicle  x56..59, y22..26
# x56..63 / y16..31 is the one region of the 64x32 armor layout that no
# humanoid box touches -- asserted below rather than asserted in a comment.
ANTENNA_SCAPE_UV = (56, 16)
ANTENNA_SCAPE_LEN = 5
ANTENNA_FUNICLE_UV = (56, 22)
ANTENNA_FUNICLE_LEN = 4

HUMANOID_UV_RECTS = (
    ("head sides", 0, 8, 32, 8), ("head caps", 8, 0, 16, 8),
    ("hat", 32, 0, 32, 16),
    ("body sides", 16, 20, 24, 12), ("body caps", 20, 16, 16, 4),
    ("arm sides", 40, 20, 16, 12), ("arm caps", 44, 16, 8, 4),
    ("leg sides", 0, 20, 16, 12), ("leg caps", 4, 16, 8, 4),
)


def assert_free_uv(x0, y0, w, h, what):
    """Guard the antenna UV allocation: fail loudly if it ever overlaps a
    humanoid box's rect. Without this, a future layout tweak would silently
    paint the antennae onto somebody's shoulder."""
    for (name, rx, ry, rw, rh) in HUMANOID_UV_RECTS:
        if x0 < rx + rw and rx < x0 + w and y0 < ry + rh and ry < y0 + h:
            raise AssertionError(
                "{} UV ({},{} {}x{}) overlaps the {} rect ({},{} {}x{})".format(
                    what, x0, y0, w, h, name, rx, ry, rw, rh))


def antenna_segment(img, name, uv, length, ramp):
    """Paint one antenna segment's box UV.

    The cube is w=1, h=length, d=1, so its side strip is 4 columns wide
    (east|north|west|south) starting one row below the cap row, and its two 1px
    caps sit on the cap row at u+1 (UP -- the segment's far end, which is seen)
    and u+2 (DOWN -- the joint, which is buried in the previous segment).
    `ramp` runs far end -> joint."""
    u, v = uv
    assert_free_uv(u, v, 4, length + 1, name)
    px = img.load()
    px[u + 1, v] = ramp[0]
    px[u + 2, v] = SOLD_DARKEST
    for i in range(length):
        for k in range(4):
            px[u + k, v + 1 + i] = ramp[i]
    _jitter(px, name, u, v + 1, 4, length)


# Helmet side strip (x0..31, y8..15), '#' plated / '.' left transparent.
# Faces: right x0..7 | FRONT x8..15 | left x16..23 | back x24..31, and within a
# side face the column adjacent to the front is x7 / x16 (established by
# measuring which half of each side face vanilla keeps as its helmet tapers).
# Rows y12..y15 open x9..x14 of the front face: that is the face opening Logan
# asked for -- the player's own face renders through it. x8 and x15 survive as
# 1px cheek posts framing the opening, tipped with AJAW_TIP mandible amber, and
# y11 stays closed as a brow ridge over the visor. Vanilla's own helmet is shut
# down to y11 and open below (with two eye slits), so this is the same amount of
# head, differently cut: one continuous opening instead of slits, which is what
# "an opening for the players face" asks for.
CHITIN_HELM_SIDE_MASK = [
    "################################",   # y8   dome plate, lit top edge
    "################################",   # y9   dome plate body
    "################################",   # y10  dome plate body
    "#######.########.###############",   # y11  brow ridge, still closed
    "#####...#......#...#############",   # y12  cheek plate; face opening starts
    "###.....#......#.....###########",   # y13  cheek plate body + mandible tips
    "..........................####..",   # y14  back neck flap
    "................................",   # y15
]

CHITIN_HELM_SIDE_RAMP = [
    CHITIN_RIM,      # y8  lit top edge of the dome plate
    SOLD_MID,        # y9
    SOLD_BASE,       # y10
    SOLD_DARKEST,    # y11 seam between the dome and cheek plates
    SOLD_PALE,       # y12 lit top edge of the cheek plate
    SOLD_BASE,       # y13
    SOLD_DARK,       # y14
    None,            # y15
]

CHITIN_HELM_SIDE_ROLES = [
    "rim", "body", "body", "seam", "rim", "body", "body", None,
]

# Chest side strip (x16..39, y20..31). Faces: right x16..19 | FRONT x20..27 |
# left x28..31 | back x32..39. The collar rows notch out around the neck and the
# skirt rows taper to a point front and back, so the plate reads as a gaster
# segment rather than a box -- and, per the measurement above, the piece stops
# at y30 instead of running the box's full 12 rows.
CHITIN_CHEST_SIDE_MASK = [
    "######....########....##",   # y20 collar, neck notch
    "#######..##########..###",   # y21
    "########################",   # y22 seam
    "########################",   # y23 mesonotum plate, lit top edge
    "########################",   # y24
    "########################",   # y25
    "########################",   # y26 seam
    "########################",   # y27 skirt plate, lit top edge
    "########################",   # y28
    ".....######......######.",   # y29 skirt taper
    "......####..............",   # y30 skirt point
    "........................",   # y31
]

CHITIN_CHEST_SIDE_RAMP = [
    CHITIN_RIM,      # y20 lit top edge of the collar plate
    SOLD_MID,        # y21
    SOLD_DARKEST,    # y22 seam
    SOLD_PALE,       # y23 lit top edge of the mesonotum plate
    SOLD_MID,        # y24
    SOLD_BASE,       # y25
    SOLD_DARKEST,    # y26 seam
    SOLD_PALE,       # y27 lit top edge of the skirt plate
    SOLD_MID,        # y28
    SOLD_BASE,       # y29
    SOLD_DARK,       # y30
    None,            # y31
]

CHITIN_CHEST_SIDE_ROLES = [
    "rim", "body", "seam",
    "rim", "body", "body", "seam",
    "rim", "body", "body", "body", None,
]


def chitin_layer_1():
    """Outer layer: helmet (HEAD), chestplate + sleeves (CHEST), boots (FEET).
    HumanoidArmorLayer.usesInnerModel() is true only for LEGS, so everything
    except the leggings reads this file.

    Round 7 (2026-08-21) rebuilt this from Logan's play-test note -- "way too
    chunky and bulky and the texture is just a bunch of lines with eyes ... i
    want the helmet to have antennae and an opening for the players face ...
    make them more ant like". Three separate defects, three separate fixes:

    * COVERAGE. The old painter filled every UV rect it was handed, which is
      why the set was +63% opaque against vanilla iron. Coverage is now an
      explicit mask table per region, with the row ranges taken from a direct
      measurement of vanilla's own layers (numbers only -- see the coverage
      block above). The sleeve stops after 5 of the arm box's 12 rows, the
      boot occupies 6, and the caps vanilla never paints are no longer painted.
    * DESIGN. Horizontal banding is gone. Each region is now a few BIG
      overlapping plates -- lit top edge, shaded body, near-black seam -- with
      a per-face bulge so a flat UV strip reads as a curved carapace. Beetle
      plating, not stripes.
    * FEATURES. The helmet's front face is cut open across x9..x14 so the
      player's face shows through, and the painted eye lenses are deleted: with
      a real opening they were redundant and they were the thing Logan named.
      The antennae are NOT painted here -- they are real geometry on
      ChitinArmorModel, and this file only owns their UV strip (x56..59).

    The Ep2 task I4 lesson (2026-08-18: the helmet read as a flat dark blob on
    a posed armor stand because it was painted from a ramp that topped out at
    82 while the torso ran to 168) is preserved by removing its cause rather
    than patching it. Every visible helmet face now draws from the SAME ramp as
    the torso -- SOLD_BASE 92 / SOLD_PALE 168 / CHITIN_RIM 196 -- so head and
    body cannot drift apart in value again, and the helmet's brightest pixels
    (the AJAW_TIP mandible posts framing the face opening) are brighter than
    anything on the chest."""
    img = Image.new("RGBA", (ARMOR_TEX_W, ARMOR_TEX_H), (0, 0, 0, 0))
    px = img.load()

    # -- helmet sides: two big plates, a hard seam, and an open face.
    paint_strip(img, "helm_sides", 0, 8, CHITIN_HELM_SIDE_MASK,
                CHITIN_HELM_SIDE_RAMP, CHITIN_HELM_SIDE_ROLES, HEAD_SIDE_FACES)
    # mandible-amber tips on the two cheek posts that frame the face opening --
    # the helmet's brightest pixels, and the same accent the boots' claws use.
    px[8, 13] = AJAW_TIP
    px[15, 13] = AJAW_TIP
    # crest ridge: two lit columns down the middle of the FRONT (x11..x12) and
    # BACK (x27..x28) faces, continuing the head shield's ridge over the dome.
    # A vertical feature is the cheapest thing that stops three stacked rows
    # from reading as three stripes -- which is what "just a bunch of lines"
    # meant -- and it is what an ant's head actually has.
    for yy in range(8, 11):
        for cx_ in (11, 12, 27, 28):
            px[cx_, yy] = lerp_color(px[cx_, yy], CHITIN_SPARK, 0.45)

    # -- helmet top: the head shield, split front/back by a seam with a raised
    # crest ridge running along the centre (x11..x12).
    for yy in range(8):
        for xx in range(8, 16):
            if yy in (0, 7) or xx in (8, 15):
                c = SOLD_DARK          # rounded margin
            elif yy == 4:
                c = SOLD_DARKEST       # seam: head shield | occiput
            elif xx in (11, 12):
                c = CHITIN_RIM         # crest ridge
            else:
                c = SOLD_MID
            px[xx, yy] = c
    for xx in range(9, 15):
        px[xx, 1] = CHITIN_SPARK if xx in (11, 12) else SOLD_PALE
        px[xx, 5] = CHITIN_SPARK if xx in (11, 12) else SOLD_PALE
    _jitter(px, "helm_top", 8, 0, 8, 8)
    # helmet DOWN cap (x16..23, y0..7) stays transparent -- vanilla never paints
    # it, and it is the underside of the head, permanently inside the player.

    # -- chestplate: three plates (collar / mesonotum / skirt), two seams.
    paint_strip(img, "chest_sides", 16, 20, CHITIN_CHEST_SIDE_MASK,
                CHITIN_CHEST_SIDE_RAMP, CHITIN_CHEST_SIDE_ROLES, BODY_SIDE_FACES)
    # Pronotum shield: a raised, ROUNDED centre plate on the chest's front
    # face, taller than it is wide and inset from the flanks -- deliberately
    # not a full-width bar, which would just be another line. Its highlight is
    # a 2px specular, not a row.
    for xx in range(22, 26):
        px[xx, 23] = SOLD_PALE
    for xx in range(21, 27):
        px[xx, 24] = SOLD_LIGHT
        px[xx, 25] = SOLD_MID
    px[23, 24] = CHITIN_SPARK
    px[24, 24] = CHITIN_SPARK
    for xx in range(22, 26):
        px[xx, 26] = SOLD_BASE   # the shield's tail breaks the seam below it
    # both chest caps (x20..35, y16..19) stay transparent, as vanilla's do.

    # -- sleeves: one pauldron plate that stops partway down the upper arm.
    paint_strip(img, "arm_sides", 40, 20,
                band_mask(20, 12, 16, SLEEVE_ROWS[0], SLEEVE_ROWS[1]),
                [CHITIN_RIM, SOLD_MID, SOLD_MID, SOLD_BASE, SOLD_DARKEST]
                + [None] * 7,
                ["rim", "body", "body", "body", "seam"] + [None] * 7,
                ARM_SIDE_FACES)
    solid_cap(img, "arm_cap", 44, 16, 4, 4, SOLD_LIGHT, SOLD_BASE)

    # -- boots: two short plates with pale claw tips at the toes (front face).
    paint_strip(img, "boot_sides", 0, 20,
                band_mask(20, 12, 16, BOOT_ROWS[0], BOOT_ROWS[1]),
                [None] * 6
                + [CHITIN_RIM, SOLD_MID, SOLD_DARKEST, SOLD_PALE, SOLD_BASE,
                   SOLD_DARK],
                [None] * 6 + ["rim", "body", "seam", "rim", "body", "body"],
                LEG_SIDE_FACES)
    for bx in (5, 6):
        px[bx, 31] = AJAW_TIP
        px[bx, 30] = lerp_color(AJAW_TIP, SOLD_BASE, 0.55)
    solid_cap(img, "boot_sole", 8, 16, 4, 4, AHEAD_BASE, AHEAD_DARK)

    # -- antennae: UV only. The cubes themselves live on ChitinArmorModel and
    # are parented to the head, so they inherit head rotation.
    antenna_segment(img, "antenna_scape", ANTENNA_SCAPE_UV, ANTENNA_SCAPE_LEN,
                    [SOLD_LIGHT, SOLD_MID, SOLD_MID, SOLD_BASE, SOLD_DARK])
    antenna_segment(img, "antenna_funicle", ANTENNA_FUNICLE_UV,
                    ANTENNA_FUNICLE_LEN,
                    [CHITIN_SPARK, CHITIN_RIM, SOLD_PALE, SOLD_MID])
    return img


def chitin_layer_2():
    """Inner layer: leggings only (HumanoidArmorLayer.usesInnerModel is true
    just for EquipmentSlot.LEGS).

    Round 7 moved the belt off the chest. It was painted at y20..y24 -- the TOP
    of the torso box, i.e. across the ribs -- where vanilla's leggings paint
    y27..y31, the bottom five rows, which is the waist. Greaves are three
    segments down y20..y28, stopping above the ankle so the boot's own plate is
    not doubled, and the clasp keeps its AJAW_TIP mandible accent."""
    img = Image.new("RGBA", (ARMOR_TEX_W, ARMOR_TEX_H), (0, 0, 0, 0))
    px = img.load()

    # -- belt: one plate around the waist.
    paint_strip(img, "belt_sides", 16, 20,
                band_mask(20, 12, 24, BELT_ROWS[0], BELT_ROWS[1]),
                [None] * 7
                + [CHITIN_RIM, SOLD_MID, SOLD_MID, SOLD_BASE, SOLD_DARKEST],
                [None] * 7 + ["rim", "body", "body", "body", "seam"],
                BODY_SIDE_FACES)
    for (cx_, cy_) in [(23, 28), (24, 28), (23, 29), (24, 29)]:
        px[cx_, cy_] = AJAW_TIP
    px[23, 28] = lerp_color(AJAW_TIP, SOLD_PALE, 0.4)

    # -- greaves: two leg segments, ant-leg style.
    paint_strip(img, "greave_sides", 0, 20,
                band_mask(20, 12, 16, GREAVE_ROWS[0], GREAVE_ROWS[1]),
                [CHITIN_RIM, SOLD_MID, SOLD_MID, SOLD_BASE, SOLD_DARKEST,
                 SOLD_PALE, SOLD_MID, SOLD_BASE, SOLD_DARKEST] + [None] * 3,
                ["rim", "body", "body", "body", "seam",
                 "rim", "body", "body", "seam"] + [None] * 3,
                LEG_SIDE_FACES)
    solid_cap(img, "greave_cap", 4, 16, 4, 4, AHEAD_BASE, AHEAD_DARK)
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
    "resin_block": resin_block,
    "amber_glass": amber_glass,
    "fungal_bloom": fungal_bloom,
    "fungal_carpet": fungal_carpet,
    "fungal_spore_crop_stage0": fungal_spore_crop_stage0,
    "fungal_spore_crop_stage1": fungal_spore_crop_stage1,
    "fungal_spore_crop_stage2": fungal_spore_crop_stage2,
    "brood_comb": brood_comb,
    "royal_comb": royal_comb,
    "provision_comb": provision_comb,
    "egg_cluster": egg_cluster,
    "daylight_membrane": daylight_membrane,
    "anthill_core": anthill_core,
    "queens_crest": queens_crest,
    "packed_soil_bricks": packed_soil_bricks,
    "hardened_soil_tiles": hardened_soil_tiles,
    "polished_resin": polished_resin,
}

ITEM_TEXTURES = {
    "resin": resin_item,
    "chitin": chitin_item,
    "chitin_plate": chitin_plate_item,
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
    "mandible_pickaxe": mandible_pickaxe_item,
    "pincer_sword": pincer_sword_item,
    "honeyed_comb": honeyed_comb_item,
    "fungal_stew": fungal_stew_item,
    "royal_jelly_treat": royal_jelly_treat_item,
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


def family_wall_sheet(families, cols=6, rows=4, scale=6):
    """Ep2 task H3 shot list: each decorative family's base texture tiled into
    a `cols` x `rows` swatch -- a stand-in for 'a sample wall of each family'
    in a pipeline with no running game client to screenshot an actual built
    wall. Every stairs/slab/wall shape in a family reuses this exact texture
    (see ModBlockStateProvider's single-texture stairsBlock/slabBlock/
    wallBlock calls), so tiling the flat texture is the same continuity check
    the file's other 'Tiling note' comments already care about -- does the
    pattern still read as a wall, or does the repeat show a seam/grid."""
    tile_px = SIZE * scale
    wall_w, wall_h = cols * tile_px, rows * tile_px
    label_h = 14
    sheet = Image.new("RGBA", (len(families) * (wall_w + 8) + 8, wall_h + label_h + 16),
                      (34, 34, 34, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(families):
        gx, gy = 8 + i * (wall_w + 8), 8
        big = img.resize((tile_px, tile_px), Image.NEAREST)
        for r in range(rows):
            for c in range(cols):
                sheet.alpha_composite(big, (gx + c * tile_px, gy + r * tile_px))
        draw.text((gx + 2, gy + wall_h + 2), name, fill=(220, 220, 220, 255))
    return sheet


# Icons allowed to reach the canvas edge, and the outline colour their border
# pixels must be. See assert_item_borders_transparent for why this is not just
# an exemption.
EDGE_TO_EDGE_ITEMS = {"pincer_sword": CHITIN_OUTLINE}


def assert_item_borders_transparent(items):
    """Permanent guard (WP-S2 item 2, 2026-08-20): the mandible pickaxe and
    pincer sword icons shipped with their blade/prong truncating at the
    canvas edge -- Logan's screenshots showed the cut mid-shape, and
    first-person rendering crops and magnifies the icon further, which is
    what made it visible in-game. Fixing those two by eye doesn't stop a
    future icon from doing the same thing, so this checks EVERY item
    texture written this run.

    The default rule is the strict one: the outermost row/column on all four
    sides must be fully transparent, i.e. >=1px of margin.

    Round 8b split that rule, because it was doing two jobs and only one of
    them was the bug. What actually reads as "truncated" is a FILL pixel with
    no room for its dark ring; a sprite merely touching the edge is not a
    defect -- measured against the client jar, every vanilla sword does it,
    tip at (15,0) and pommel at (0,15). Insisting on a transparent border
    therefore capped us at a 14x14 sprite against vanilla's 16x16, which is
    what made the Pincer Sword read as a knife.

    So an icon in EDGE_TO_EDGE_ITEMS may reach the edge, but its border
    pixels must be transparent OR exactly its outline colour. Since outline()
    only ever paints transparent cells adjacent to fill, a fill pixel on the
    border fails this -- which is precisely the original defect, still caught.
    """
    failures = []
    for name, img in items:
        px = img.load()
        w, h = img.size
        allowed = EDGE_TO_EDGE_ITEMS.get(name)

        def bad(x, y):
            colour = px[x, y]
            if colour[3] == 0:
                return False
            if allowed is not None and tuple(colour) == tuple(allowed):
                return False
            return True

        for x in range(w):
            if bad(x, 0):
                failures.append((name, x, 0, px[x, 0]))
            if bad(x, h - 1):
                failures.append((name, x, h - 1, px[x, h - 1]))
        for y in range(h):
            if bad(0, y):
                failures.append((name, 0, y, px[0, y]))
            if bad(w - 1, y):
                failures.append((name, w - 1, y, px[w - 1, y]))
    if failures:
        for (name, x, y, color) in failures:
            print(f"BORDER GUARD FAILED: {name} has a non-transparent pixel "
                  f"at ({x},{y}) = {color} -- item textures need >=1px of "
                  f"transparent margin on every side.")
        offenders = sorted({name for (name, *_rest) in failures})
        raise SystemExit(
            f"border guard: {len(failures)} violation(s) across "
            f"{len(offenders)} item texture(s): {', '.join(offenders)}")


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

    item_images = []
    for name, make in ITEM_TEXTURES.items():
        img = make()
        out = ITEM_TEX_DIR / f"{name}.png"
        img.save(out)
        generated.append((name, img))
        item_images.append((name, img))
        print(f"wrote {out.relative_to(REPO_ROOT)}")

    assert_item_borders_transparent(item_images)

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

    # Ep2 task H3: shot-list render of a sample wall of each decorative family.
    family_names = ["packed_soil_bricks", "hardened_soil_tiles", "polished_resin"]
    families = [(name, img) for (name, img) in generated if name in family_names]
    wall_sheet = family_wall_sheet(families)
    wall_sheet_path = PREVIEW_DIR / "decorative_families_wall_sheet.png"
    wall_sheet.save(wall_sheet_path)
    print(f"wrote {wall_sheet_path.relative_to(REPO_ROOT)}")

    # Ep2 task I1: the same tiled-wall check for the soil family, which is the
    # family the repeat actually bites on -- soils are what the whole dimension is
    # built out of, so "does the repeat show a grid" is their only real acceptance
    # test and it cannot be judged from a single 16x16 tile.
    soil_names = ["packed_soil", "amber_earth", "deep_loam", "hardened_soil",
                  "anthill_soil"]
    soils = [(name, img) for (name, img) in generated if name in soil_names]
    soil_sheet = family_wall_sheet(soils, cols=5, rows=5, scale=5)
    soil_sheet_path = PREVIEW_DIR / "soil_family_wall_sheet.png"
    soil_sheet.save(soil_sheet_path)
    print(f"wrote {soil_sheet_path.relative_to(REPO_ROOT)}")

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
