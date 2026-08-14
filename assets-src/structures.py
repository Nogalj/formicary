"""
Structure-template generator for Formicary -- GameTest arenas and worldgen pieces.

There is no built-in empty template in 1.21: `@GameTest(template = "x")` needs a
real `.nbt` at `data/formicary/structure/x.nbt`. This writes them directly --
gzipped NBT, no Minecraft in the loop -- mirroring the tag layout of ModTest's
platform.nbt exactly (verified by reading that file back):

    root COMPOUND ""
      size        LIST<INT>       [x, y, z]
      entities    LIST<END>       (empty)
      blocks      LIST<COMPOUND>  {pos: LIST<INT>[3], state: INT}
      palette     LIST<COMPOUND>  {Name: STRING}
      DataVersion INT             3953   (1.21)

For the GameTest arenas only the floor layer is written; the GameTest framework
clears the volume to air before placing a template, so the empty space above
needs no entries.

The same omission is load-bearing for the M5 `anthill` worldgen piece, for a
different reason: `StructureTemplate.placeInWorld` only ever touches positions
that appear in `blocks`, so leaving air out means the mound is stamped onto the
savanna without carving an air box around itself. (A vanilla-saved structure DOES
list its air; `SinglePoolElement`'s processors -- `BlockIgnoreProcessor
.STRUCTURE_BLOCK` -- strip structure blocks but not air, which is why the legacy
element type with STRUCTURE_AND_AIR exists at all. Hand-writing the NBT sidesteps
the whole question.)

Run with: python assets-src\\structures.py
Requires: nothing outside the standard library.
"""

import gzip
import struct
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
STRUCTURE_DIR = REPO_ROOT / "src/main/resources/data/formicary/structure"

# 1.21's data version. Structure templates are run through the DataFixer on load,
# so a wrong value here would silently mangle the palette.
DATA_VERSION = 3953

TAG_END = 0
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10

FLOOR_BLOCK = "minecraft:polished_andesite"


def _string(value):
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _int(value):
    return struct.pack(">i", value)


def _int_list(values):
    return bytes([TAG_INT]) + _int(len(values)) + b"".join(_int(v) for v in values)


def _compound(entries):
    """entries: list of (tag_id, name, payload_bytes)."""
    out = b""
    for tag_id, name, payload in entries:
        out += bytes([tag_id]) + _string(name) + payload
    return out + bytes([TAG_END])


def _compound_list(payloads):
    if not payloads:
        return bytes([TAG_END]) + _int(0)
    return bytes([TAG_COMPOUND]) + _int(len(payloads)) + b"".join(payloads)


def _empty_list():
    return bytes([TAG_END]) + _int(0)


def build_platform(width, height, depth):
    """A flat one-block-thick floor of `FLOOR_BLOCK` filling y=0 of a
    width x height x depth volume."""
    blocks = []
    for x in range(width):
        for z in range(depth):
            blocks.append(_compound([
                (TAG_LIST, "pos", _int_list([x, 0, z])),
                (TAG_INT, "state", _int(0)),
            ]))

    palette = [_compound([(TAG_STRING, "Name", _string(FLOOR_BLOCK))])]

    root = _compound([
        (TAG_LIST, "size", _int_list([width, height, depth])),
        (TAG_LIST, "entities", _empty_list()),
        (TAG_LIST, "blocks", _compound_list(blocks)),
        (TAG_LIST, "palette", _compound_list(palette)),
        (TAG_INT, "DataVersion", _int(DATA_VERSION)),
    ])

    return bytes([TAG_COMPOUND]) + _string("") + root


def build_from_layers(layers, legend):
    """A template painted from a list of character grids, one per Y level.

    `layers[y][z][x]` is the legend key at (x, y, z); '.' means "no entry", i.e.
    leave whatever the world already has there. `legend` maps the remaining
    characters to block ids. Every layer must be the same rectangle.
    """
    depth = len(layers[0])
    width = len(layers[0][0])
    for layer in layers:
        if len(layer) != depth or any(len(row) != width for row in layer):
            raise ValueError("every layer must be the same width x depth rectangle")

    names = sorted({ch for layer in layers for row in layer for ch in row if ch != "."})
    missing = [ch for ch in names if ch not in legend]
    if missing:
        raise ValueError(f"legend is missing {missing}")
    palette_names = [legend[ch] for ch in names]
    state_of = {ch: i for i, ch in enumerate(names)}

    blocks = []
    for y, layer in enumerate(layers):
        for z, row in enumerate(layer):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                blocks.append(_compound([
                    (TAG_LIST, "pos", _int_list([x, y, z])),
                    (TAG_INT, "state", _int(state_of[ch])),
                ]))

    palette = [_compound([(TAG_STRING, "Name", _string(n))]) for n in palette_names]

    root = _compound([
        (TAG_LIST, "size", _int_list([width, len(layers), depth])),
        (TAG_LIST, "entities", _empty_list()),
        (TAG_LIST, "blocks", _compound_list(blocks)),
        (TAG_LIST, "palette", _compound_list(palette)),
        (TAG_INT, "DataVersion", _int(DATA_VERSION)),
    ])
    return bytes([TAG_COMPOUND]) + _string("") + root


# name -> (width, height, depth)
TEMPLATES = {
    # The workhorse arena, same dimensions as ModTest's.
    "platform": (5, 3, 5),
    # Long enough to put a soldier well outside ColonyAnger.ANGER_RADIUS (24) from
    # a trigger at the near end, without distorting the standard template.
    "long_platform": (48, 3, 5),
    # Tall enough to stack two candidate standing spots in one column, which is what
    # AnthillPortal.findFloorInBand's "pick the HIGHEST legal Y" rule needs to be
    # tested against. The standard 3-tall arena cannot hold two.
    "tall_platform": (5, 8, 5),
}


# ---------------------------------------------------------------------------
# M5: the savanna anthill
# ---------------------------------------------------------------------------
#
# Rows are Z, characters are X; layer index is Y. 7 x 5 x 7.
#
# Placement geometry, verified against JigsawPlacement.addPieces + Structure
# PoolElement.getGroundLevelDelta() (which is 1 and is NOT overridden by
# SinglePoolElement): with "project_start_to_heightmap": "WORLD_SURFACE_WG" the
# piece is moved so `boundingBox.minY() + 1 == firstFreeHeight`. firstFreeHeight
# is the first AIR y, so template y=0 lands on the topmost solid block (it
# replaces the grass) and y=1..4 are the four blocks of mound standing proud of
# the ground -- the "~7 wide, ~4 tall" the spec asks for, where the 7 comes from
# the flat excavated-soil ring at y=0 and the mound itself is 5 wide.
#
# The 5-wide mound is deliberate, not a compromise: entry detection is "the core
# or any block within 2 blocks of a core" (spec section 2), which
# AnthillPortal implements as a Chebyshev radius-2 cube. With the core at
# (3, 2, 3) that cube is x,z in [1,5] and y in [0,4] -- so EVERY block of the
# raised mound is a valid entry target, and only the flat surrounding ring is
# not. Widening the mound would create parts of the anthill that silently
# swallow a pearl.
ANTHILL_LEGEND = {
    "#": "formicary:anthill_soil",
    "C": "formicary:anthill_core",
}

ANTHILL_LAYERS = [
    # y=0 -- replaces the surface block: a ring of excavated soil around the mound.
    [
        ".#####.",
        "#######",
        "#######",
        "#######",
        "#######",
        "#######",
        ".#####.",
    ],
    # y=1 -- ground level. 5x5 with a bite out of one corner so it is not a square.
    [
        ".......",
        ".#####.",
        ".#####.",
        ".#####.",
        ".#####.",
        "..####.",
        ".......",
    ],
    # y=2 -- the heart: Anthill Core, fully enclosed by soil on all six sides.
    [
        ".......",
        "..###..",
        ".#####.",
        ".##C##.",
        ".#####.",
        "..###..",
        ".......",
    ],
    # y=3 -- shoulders, with one spur leaning south.
    [
        ".......",
        ".......",
        "..###..",
        "..###..",
        "..###..",
        "...#...",
        ".......",
    ],
    # y=4 -- the summit, two blocks rather than a symmetric cone point.
    [
        ".......",
        ".......",
        ".......",
        "...##..",
        "...#...",
        ".......",
        ".......",
    ],
]

LAYERED_TEMPLATES = {
    "anthill": (ANTHILL_LAYERS, ANTHILL_LEGEND),
}


def main():
    STRUCTURE_DIR.mkdir(parents=True, exist_ok=True)
    for name, (w, h, d) in TEMPLATES.items():
        out = STRUCTURE_DIR / f"{name}.nbt"
        out.write_bytes(gzip.compress(build_platform(w, h, d), mtime=0))
        print(f"wrote {out.relative_to(REPO_ROOT)}  ({w}x{h}x{d})")

    for name, (layers, legend) in LAYERED_TEMPLATES.items():
        out = STRUCTURE_DIR / f"{name}.nbt"
        out.write_bytes(gzip.compress(build_from_layers(layers, legend), mtime=0))
        placed = sum(row.count(ch) for layer in layers for row in layer
                     for ch in set(row) if ch != ".")
        w, h, d = len(layers[0][0]), len(layers), len(layers[0])
        print(f"wrote {out.relative_to(REPO_ROOT)}  ({w}x{h}x{d}, {placed} blocks)")


if __name__ == "__main__":
    main()
