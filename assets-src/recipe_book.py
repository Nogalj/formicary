"""Builds docs/recipe-book.html -- the player-facing crafting reference.

Same contract as blocks.py and sounds.py: the page is GENERATED, never
hand-edited.

    uv run python assets-src/recipe_book.py

Everything mechanical is read straight out of the datagen output, so the page
cannot disagree with the game:

  recipe shape, ingredients, counts   src/generated/.../data/formicary/recipe/
  what unlocks each recipe            .../advancement/recipes/<category>/
  display names                       .../assets/formicary/lang/en_us.json
  item icons                          src/main/resources/.../textures/

The first version of this page had those 26 recipes typed into the HTML by
hand. Five of them changed in the same session it was published, which is the
whole argument for this script: after any recipe edit the page is one command
behind, not a transcription job.

Only two things stay curated here, because neither is derivable: which group
a recipe is displayed under, and whether it is flagged as new or reworked in
Episode 2. GROUPS below is checked against the datapack on every run, so a
recipe added later fails the build instead of quietly vanishing from the page.
"""

import base64
import io
import json
import pathlib
import re

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
GENERATED = REPO_ROOT / "src" / "generated" / "resources"
RECIPE_DIR = GENERATED / "data" / "formicary" / "recipe"
ADVANCEMENT_DIR = GENERATED / "data" / "formicary" / "advancement" / "recipes"
LANG_FILE = GENERATED / "assets" / "formicary" / "lang" / "en_us.json"
TEXTURE_DIRS = [
    REPO_ROOT / "src" / "main" / "resources" / "assets" / "formicary" / "textures" / "item",
    REPO_ROOT / "src" / "main" / "resources" / "assets" / "formicary" / "textures" / "block",
]
TEMPLATE = REPO_ROOT / "assets-src" / "recipe-book.template.html"
OUTPUT = REPO_ROOT / "docs" / "recipe-book.html"

# The one curated table: display order, group, and the Ep2 flag.
# "new" = the recipe did not exist before Episode 2, "rework" = it existed but
# its inputs changed, "" = untouched since it first shipped.
CURATED = [
    ("pincer_sword", "tools", "rework"),
    ("mandible_pickaxe", "tools", "rework"),
    ("chitin_helmet", "tools", "rework"),
    ("chitin_chestplate", "tools", "rework"),
    ("chitin_leggings", "tools", "rework"),
    ("chitin_boots", "tools", "rework"),
    ("pheromone_horn", "tools", "rework"),

    ("chitin_plate", "mats", "new"),
    ("resin_block", "mats", ""),
    ("resin", "mats", ""),
    ("trail_pheromone", "mats", "rework"),

    ("honeyed_comb", "food", "new"),
    ("fungal_stew", "food", "new"),
    ("royal_jelly_treat", "food", "new"),

    ("amber_glass", "build", ""),
    ("packed_soil_bricks", "build", "new"),
    ("packed_soil_brick_stairs", "build", "new"),
    ("packed_soil_brick_slab", "build", "new"),
    ("hardened_soil_tiles", "build", "new"),
    ("hardened_soil_tile_stairs", "build", "new"),
    ("hardened_soil_tile_slab", "build", "new"),
    ("polished_resin", "build", "new"),
    ("polished_resin_stairs", "build", "new"),
    ("polished_resin_slab", "build", "new"),

    ("brood_comb", "deco", "rework"),
    ("fungal_carpet", "deco", ""),
]

# Stairs and slabs have no icon of their own here -- they render their parent
# block's texture, clipped to the derived silhouette by the page's CSS.
SHAPE_PARENTS = {
    "packed_soil_brick_stairs": ("packed_soil_bricks", "stairs"),
    "packed_soil_brick_slab": ("packed_soil_bricks", "slab"),
    "hardened_soil_tile_stairs": ("hardened_soil_tiles", "stairs"),
    "hardened_soil_tile_slab": ("hardened_soil_tiles", "slab"),
    "polished_resin_stairs": ("polished_resin", "stairs"),
    "polished_resin_slab": ("polished_resin", "slab"),
}

# Drawn by the page as original stand-ins; we do not ship Mojang's pixels.
VANILLA_NAMES = {
    "minecraft:iron_ingot": "Iron Ingot",
    "minecraft:stick": "Stick",
    "minecraft:sugar": "Sugar",
    "minecraft:goat_horn": "Goat Horn",
}


def read_json(path):
    return json.load(io.open(str(path), encoding="utf-8"))


def load_recipes():
    """Every recipe, keyed by its output id's path, in datapack terms only."""
    out = {}
    for path in sorted(RECIPE_DIR.glob("*.json")):
        d = read_json(path)
        kind = d["type"].split(":")[1]
        r = {"id": d["result"]["id"], "c": d["result"]["count"]}
        if kind == "crafting_shaped":
            r["t"] = "shaped"
            r["p"] = d["pattern"]
            r["k"] = {k: v["item"] for k, v in d["key"].items()}
        elif kind == "crafting_shapeless":
            r["t"] = "shapeless"
            r["ing"] = [i["item"] for i in d["ingredients"]]
        elif kind == "smelting":
            r["t"] = "smelt"
            r["inp"] = d["ingredient"]["item"]
            r["time"] = d["cookingtime"]
            r["xp"] = d["experience"]
        else:
            raise AssertionError("%s: unhandled recipe type %r -- the page has no "
                                 "layout for it" % (path.name, d["type"]))
        out[path.stem] = r
    return out


def load_gates():
    """What each recipe's advancement watches for -- i.e. what unlocks it.

    Every recipe advancement carries a `has_the_recipe` criterion plus one
    `has_<thing>` criterion holding the real trigger item; that second one is
    the gate.
    """
    gates = {}
    for path in sorted(ADVANCEMENT_DIR.glob("*/*.json")):
        criteria = read_json(path).get("criteria", {})
        for name, body in criteria.items():
            if name == "has_the_recipe":
                continue
            items = body.get("conditions", {}).get("items", [])
            if items:
                gates[path.stem] = items[0]["items"]
                break
    return gates


def load_names():
    lang = read_json(LANG_FILE)
    names = dict(VANILLA_NAMES)
    for key, value in lang.items():
        if key.startswith("item.formicary.") or key.startswith("block.formicary."):
            path = key.split(".", 2)[2]
            if "." in path:          # e.g. item.formicary.trail_pheromone.no_trail
                continue
            names["formicary:" + path] = value
    return names


def texture_path(name):
    for directory in TEXTURE_DIRS:
        candidate = directory / (name + ".png")
        if candidate.exists():
            return candidate
    raise AssertionError("no texture found for %r in item/ or block/" % name)


def referenced_ids(recipes):
    """Every item id the page will draw an icon for."""
    ids = set()
    for r in recipes:
        ids.add(r["id"])
        ids.add(r["gate"])
        if r["t"] == "shaped":
            ids.update(r["k"].values())
        elif r["t"] == "smelt":
            ids.add(r["inp"])
        else:
            ids.update(r["ing"])
    return ids


def build_textures(ids):
    """Base64 the mod's own 16x16 art for every id the page references."""
    wanted = set()
    for item_id in sorted(ids):
        if not item_id.startswith("formicary:"):
            continue
        path = item_id.split(":", 1)[1]
        wanted.add(SHAPE_PARENTS.get(path, (path, None))[0])

    textures = {}
    for name in sorted(wanted):
        raw = texture_path(name).read_bytes()
        textures[name] = "data:image/png;base64," + base64.b64encode(raw).decode("ascii")
    return textures


def template_vanilla_icons(html):
    """The vanilla ids the template has hand-drawn stand-in art for.

    We deliberately do not ship Minecraft's own textures, so every vanilla
    ingredient needs an original icon in the template's VAN table. Without this
    check a newly-introduced vanilla ingredient renders as an EMPTY slot that
    still has a working tooltip -- which is invisible to a broken-image scan,
    and is exactly what happened when the Goat Horn replaced chitin in the
    Pheromone Horn recipe.
    """
    match = re.search(r'var VAN = \{(.*?)\n\};', html, re.S)
    if not match:
        raise AssertionError("template has no VAN table")
    return set(re.findall(r'"(minecraft:[a-z_]+)"\s*:', match.group(1)))


def main():
    datapack = load_recipes()
    gates = load_gates()
    names = load_names()

    curated_keys = [key for key, _, _ in CURATED]
    missing = sorted(set(datapack) - set(curated_keys))
    extra = sorted(set(curated_keys) - set(datapack))
    if missing:
        raise AssertionError(
            "these recipes exist in the datapack but are not in CURATED, so they "
            "would silently miss the page -- add them: %s" % ", ".join(missing))
    if extra:
        raise AssertionError(
            "CURATED lists recipes that no longer exist in the datapack: %s"
            % ", ".join(extra))
    if len(curated_keys) != len(set(curated_keys)):
        raise AssertionError("CURATED has a duplicate entry")

    recipes = []
    for key, group, era in CURATED:
        r = dict(datapack[key])
        r["g"] = group
        if era:
            r["era"] = era
        if key not in gates:
            raise AssertionError("no unlock advancement found for %r" % key)
        r["gate"] = gates[key]
        recipes.append(r)

    unnamed = sorted(i for i in referenced_ids(recipes) if i not in names)
    if unnamed:
        raise AssertionError("no display name for: %s" % ", ".join(unnamed))

    textures = build_textures(referenced_ids(recipes))

    html = io.open(str(TEMPLATE), encoding="utf-8").read()

    drawn = template_vanilla_icons(html)
    undrawn = sorted(i for i in referenced_ids(recipes)
                     if not i.startswith("formicary:") and i not in drawn)
    if undrawn:
        raise AssertionError(
            "no stand-in icon in the template's VAN table for: %s -- these would "
            "render as empty slots. Draw one (16x16 viewBox, original art, not "
            "Minecraft's pixels)." % ", ".join(undrawn))
    for marker, payload in (
            ("/*__ICONS__*/", "const TEX = " + json.dumps(textures, indent=0, sort_keys=True) + ";"),
            ("/*__NAMES__*/", "var NAME = " + json.dumps(names, indent=0, sort_keys=True) + ";"),
            ("/*__RECIPES__*/", "var RECIPES = " + json.dumps(recipes, indent=0) + ";")):
        if marker not in html:
            raise AssertionError("template is missing %s" % marker)
        html = html.replace(marker, payload)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    io.open(str(OUTPUT), "w", encoding="utf-8", newline="\n").write(html)

    shaped = sum(1 for r in recipes if r["t"] == "shaped")
    shapeless = sum(1 for r in recipes if r["t"] == "shapeless")
    smelt = sum(1 for r in recipes if r["t"] == "smelt")
    print("wrote %s" % OUTPUT.relative_to(REPO_ROOT))
    print("  %d recipes (%d shaped, %d shapeless, %d smelting), %d icons, %.1f KB"
          % (len(recipes), shaped, shapeless, smelt, len(textures),
             len(html.encode("utf-8")) / 1024.0))


if __name__ == "__main__":
    main()
