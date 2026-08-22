# Ep2 play-test revisions -- round 7 (2026-08-21)

Logan's seventh list: one question (answered, no work), one large armor rework, one food
pass. No worldgen this round.

## Item 0 -- "is the chitin armor recipe made with chitin plates?" ANSWERED, no work

Yes, since round 4. Verified against the GENERATED recipe JSON (ground truth, not the
builder source): all four of `chitin_{helmet,chestplate,leggings,boots}.json` key `X` to
`formicary:chitin_plate`. Nothing to change.

## Item 1 -- armor (WP-1)

Verbatim: "the actual armor texture on the player needs improvement. it looks way too
chunky and bulky and the texture is just a bunch of lines with eyes. i want the helmet to
have antennae and an opening for the players face. the chestplate should also only cover
the same amount of player space as a normal vanilla chestplate, so not the arms, and same
with the legs and boots. make them more ant like in design and texture."

**"Chunky and bulky" is measurable and confirmed.** Opaque-pixel counts, ours vs vanilla
iron, same 64x32 layout:

| layer | vanilla iron | ours | over |
|---|---|---|---|
| `layer_1` (helmet + chest + arms + boots) | 648 | **1056** | +63% |
| `layer_2` (leggings) | 280 | 296 | +6% |

Rendered side by side, the cause is plain: our painter FILLS each body-part UV rect with
solid horizontal bands, where vanilla paints a shaped silhouette and leaves the rest
transparent. Vanilla's chestplate stops partway down the arm; ours runs the arm's full
height. Vanilla's helmet has a transparent face notch; ours is a filled box with two
orange "eye" pixels -- literally Logan's "a bunch of lines with eyes".

So three separable defects: **coverage** (paint less), **design** (chitin plating and
segmentation, not bands), **features** (face opening + antennae).

**Antennae need geometry, not paint.** Armor layers are flat textures on the vanilla
`HumanoidModel`; a painted antenna would be a squiggle on the head cube's surface. Real
antennae require a custom armor model via NeoForge's
`IClientItemExtensions#getHumanoidArmorModel`. That class is NOT in `reference/` -- a
partial extraction, not a missing API (see `docs/gotchas/reference-extraction.md`): the
implementer re-extracts and verifies the signature before writing against it.

**Coverage rule (licensing-safe):** do NOT copy vanilla's alpha channel. MEASURE vanilla
iron's per-region coverage (which rows of each body-part rect it paints), then hand-code
equivalent box numbers in the painter. Shape parity, our own pixels.

## Item 2 -- food (WP-2)

Verbatim: "the fungus bowl should not be crafted with a bowl and should not drop a bowl
when finished so the food can stack and it also just doesnt make sense for it to be in a
bowl if its spawning in a ant nest. also compare the texture to the other food textures in
game and improve it along with the other food textures."

Three coupled changes to `FUNGAL_STEW` plus an art pass:
- recipe drops `Items.BOWL`; `FoodProperties` drops `usingConvertsTo(Items.BOWL)`; the
  item drops `stacksTo(1)` so it stacks to 64 like any non-bowl food.
- **Display name only** changes ("Fungal Stew" -> a bowl-free name); the registry id stays
  `fungal_stew`. Renaming the id would touch loot tables, advancements, recipes, models and
  a GameTest, and would void the item in any existing world -- not worth it for a label.
  Flag to Logan that the internal id keeps the old word.
- Art: measured against vanilla foods, all four of ours are flat low-contrast blobs.
  `honeyed_comb` 116 opaque px with THREE dots and no comb cells at all (vanilla honeycomb
  is 138 px of visible cells); `royal_jelly_treat` reads as butter on a plank;
  `royal_jelly` reads as a garlic clove; the stew is a bowl that is being removed anyway.
  Vanilla's foods carry a dark outline, 3-4 tone shading, a highlight, and one piece of
  internal structure (cookie chips, melon seeds, honeycomb cells). Ours carry none.

## Work packages (sequential -- both touch `assets-src/blocks.py`)

- **WP-1 (opus): armor.** Layer textures rebuilt to vanilla coverage + ant design + face
  opening; custom armor model carrying antennae; UV space for the antennae cubes allocated
  in the same 64x32 texture the painter owns. Verification is visual as well as numeric:
  posed render, and the opaque-count table above must land within ~10% of vanilla's.
- **WP-2 (sonnet): food.** De-bowl the stew (recipe + properties + stacking + display
  name), then repaint all four food icons to vanilla's standard.

## Verification (main loop, never delegated)

Build + full suite (99 baseline) + runData idempotency + the border guard + an armor render
and food contact sheet reviewed by eye before either is called done.
