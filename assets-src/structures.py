"""
GameTest structure-template generator for Formicary.

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

Only the floor layer is written; the GameTest framework clears the volume to air
before placing a template, so the empty space above needs no entries.

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


# name -> (width, height, depth)
TEMPLATES = {
    # The workhorse arena, same dimensions as ModTest's.
    "platform": (5, 3, 5),
    # Long enough to put a soldier well outside ColonyAnger.ANGER_RADIUS (24) from
    # a trigger at the near end, without distorting the standard template.
    "long_platform": (48, 3, 5),
}


def main():
    STRUCTURE_DIR.mkdir(parents=True, exist_ok=True)
    for name, (w, h, d) in TEMPLATES.items():
        out = STRUCTURE_DIR / f"{name}.nbt"
        out.write_bytes(gzip.compress(build_platform(w, h, d), mtime=0))
        print(f"wrote {out.relative_to(REPO_ROOT)}  ({w}x{h}x{d})")


if __name__ == "__main__":
    main()
