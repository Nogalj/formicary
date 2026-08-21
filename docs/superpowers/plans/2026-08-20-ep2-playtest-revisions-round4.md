# Ep2 play-test revisions -- round 4 (2026-08-20)

Logan's fourth play-test list, 7 items + 1 found-in-triage (the Anthill Core self-drop,
already fixed as `56e7d40` before this list arrived). No worldgen changes this round --
the current dev/play world stays valid.

Items, verbatim intent -> work package:

1. "Pincer sword and mandible pickaxe models are too small, compare them to vanilla" -> WP-C
2. "change the crafting recipe ... includes a queen's crest. i want them to be end goal
   items and also buff them to match that concept" -> WP-B
3. "chitin armor crafting recipe to be 'chitin plates' ... crafted from chitin and iron
   (similar to the netherite crafting recipe) ... also replace the chitin in the pincer
   sword and mandible pickaxe recipes" -> WP-B
4. "pheremone horn needs a texture fix" -> WP-C
5. "add ender ant spawn egg" -> WP-B
6. "tamed worker ants should be able to harvest pumpkins and melons, aswell as crops added
   ... by other mods potentially if you are able" -> WP-A
7. "tamed worker ants should idle closer to their chest instead of wandering off" -> WP-A
8. "after they harvest a crop they stand around or wander for a while before dropping it
   off at the chest" -> WP-A

## Root causes already diagnosed (main loop, pre-dispatch)

- **Item 8 (dawdle):** `DepositToChestGoal` arms `RETRY_COOLDOWN_TICKS = 100` on EVERY
  trip end -- `tick()` line ~112 sets it unconditionally on reaching the chest (success
  included) and `stop()` re-arms it as a backstop. 100 `canUse` calls ~= 200 real ticks
  (goal selector polls alternating ticks), so every single-crop cycle carries a ~10 s
  stand-around. The cooldown's stated purpose is "chest full / chest gone" backoff only.
- **Item 7 (idle drift):** `bindTo` sets `restrictTo(chest, PATROL_RADIUS + 4)` (20
  blocks) and priority-6 `WaterAvoidingRandomStrollGoal` wanders freely inside that
  leash; `MoveTowardsRestrictionGoal` (5) only fires once OUTSIDE it.
- **Item 6 (crops):** candidacy is already data (`ModBlockTags.HARVESTABLE_CROPS`) +
  generic ripeness code (`CropHarvest.isMature`: `CropBlock.isMaxAge` or integer `age`
  property at max). Pumpkin/melon have NO age property -> never mature. Replant/seed-take
  logic assumes an age crop.
- **Items 1/4 (art):** measured against extracted vanilla textures:
  pincer_sword extent 11x12 with 37 opaque px vs iron_sword 16x16 / 84 px (it is a 2px
  twig, no blade mass, no crossguard); mandible_pickaxe 12x13 / 69 px but the head is a
  stubby V, not a pickaxe's broad top arc + long diagonal handle; pheromone_horn is a
  flat unshaded amber wedge vs goat_horn's mouthpiece band + banded shading.

## Design decisions (main loop -- record in DECISIONS.md)

- **Chitin Plate** = shapeless **2 Chitin + 1 Iron Ingot -> 1 plate**. Netherite's
  *pattern* (monster part + metal, shapeless), not its 4+4 price: the armor set is 24
  units, and 4+4 would price it at 96 chitin + 96 iron. Set now costs 48 chitin + 24
  iron. Armor recipes keep their shapes with X = plate; armor material + new tool tier
  repair ingredient = plate.
- **ROYAL tier** (new, `ModToolTiers`): `INCORRECT_FOR_NETHERITE_TOOL`, durability 2300,
  speed 10.0F, attackDamageBonus 4.0F, enchantability 18, repair = Chitin Plate. A notch
  above netherite (2031/9.0) because the recipe costs a queen kill. The CHITIN tier
  becomes unused -> delete it (shrink the surface) unless something else still references
  it.
- **Pincer Sword**: base damage 5.0F (total 9, one above netherite's 8), speed -2.4F.
  **Mandible Pickaxe**: keeps 1.0F / -2.8F (standard), keeps the armor-gate bypass and
  the pickaxe+shovel union tag -- its buff is the tier (netherite mining level, speed 10,
  durability).
- **Recipes**: sword = column `plate / crest / stick` (crest as the guard); pickaxe =
  `plate crest plate` over `stick / stick` column. Both `unlockedBy` having a Queen's
  Crest, the same advertise-after-the-fight logic the Pheromone Horn recipe uses. One
  crest each = one queen kill per tool; queens are one per colony and colonies are
  infinite, so this is farmable end-game, not a one-shot choice.
- **Ageless-harvest rule** (`CropHarvest`): a block in `HARVESTABLE_CROPS` with no age
  property is ripe by existence -- harvested by break-and-bank with **no replant and no
  seed extraction** (the stem stays and regrows; taking a "seed" would steal a pumpkin
  from the drops). Stem blocks (`StemBlock` / `AttachedStemBlock`) are NEVER harvestable
  regardless of tags -- guard in code, since the conventional tag contents are outside
  our control.
- **Modded crops**: `HARVESTABLE_CROPS` datagen gains PUMPKIN + MELON and
  `addOptionalTag(c:crops)` (NeoForge convention tag -- verify the exact constant in
  reference/, likely `Tags.Blocks.CROPS`). Any modded crop tagged conventionally joins
  with zero Java; the existing generic age code already handles their ripeness.
- **Dawdle fix**: the retry cooldown arms only when the pack is still non-empty after
  the trip (chest full/gone/timeout). A successful emptying deposit arms nothing.
- **Idle-near-chest**: replace the priority-6 stroll with a bound-aware subclass: when
  bound, stroll targets are drawn within **IDLE_RADIUS = 6** of the chest (walk back
  toward the chest first when beyond it); when following, behavior unchanged.
- **Ender Ant Spawn Egg**: NeoForge `DeferredSpawnEggItem`, colors dark teal body /
  purple speckle (ender family), `item/template_spawn_egg` model parent, creative tab +
  lang.

## Work packages (sequential, one implementer at a time)

- **WP-A (opus): worker AI** -- dawdle, idle-near-chest, pumpkin/melon/modded crops.
  Tests: deposit-promptness (two-crop cycle wall-clock bound that the old 200-tick
  cooldown cannot pass), idle-position property test (draws from the goal's own
  getPosition stay within IDLE_RADIUS when bound -- deterministic seam, not a stroll
  observation), pumpkin harvested without replant + stem untouched, stem-tagged case
  never harvestable.
- **WP-B (sonnet): items/recipes/tier/egg** -- plate item+recipe+texture-name, armor
  recipe/repair swap, ROYAL tier + buffs, crest-gated tool recipes, spawn egg, lang,
  creative tab, datagen, DECISIONS.md entries. Retarget any suite assertion pinned to
  old tier stats (preserve the invariant, not the number).
- **WP-C (opus): art** -- pincer sword (full-canvas diagonal, real blade mass ~80+ px,
  crossguard, 1px margin kept for the border guard), mandible pickaxe (vanilla pickaxe
  silhouette: broad top arc head of two mandible picks + long diagonal handle, ~13x13),
  pheromone horn (mouthpiece band, curved taper, banded shading), chitin plate icon.
  All via assets-src/blocks.py (deterministic), guard must pass, regenerate.

## Verification (main loop, never delegated)

Build + full GameTest suite (87 baseline) + runData idempotency + blocks.py border guard
+ upscaled icon contact sheet vs vanilla sent to Logan. No probe run (no worldgen
touched). Commit per package, conventional messages, no tag, no push.
