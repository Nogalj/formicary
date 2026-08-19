# Ep2 play-test revisions — implementation plan (2026-08-19)

Source: Logan's first fresh-world play-test of the ep2 branch (2026-08-19, two days ahead
of the planned Friday pass). Eleven revision items, all his explicit calls; this plan
grounds each in current code (three-agent recon, this date) and sets the numbers. The
tuning numbers marked (dial) are defaults chosen to match his described intent — cheap to
retune next round.

Branch: `ep2` (from `eb8829d`). Four work packages, executed sequentially (one implementer
in the checkout at a time), main-loop verification between packages. GameTest suite is 74
green at start; it must be green (with updated/added tests) after every package.

## Design notes / couplings

- **Pearl economy re-route.** Ep2's exit-affordability floor was "every larder guarantees
  1-2 pearls" (spec §2/§3). This round moves the floor to the ender ant: guaranteed 1
  pearl per kill + 4-6 seeded per colony (up from 2-3). Larder pearls drop to a rare
  bonus (5%). Peaceful residual: seeded ender ants survive Peaceful (PathfinderMob base)
  but are finite per colony; a Peaceful player who exhausts them and returns pearl-less
  walks to the next colony (<=340 blocks, worm tunnels ungated). Accepted; rejected
  alternative (one guaranteed pearl comb per larder) noted for later if play shows it's
  needed.
- **Shafts are currently a no-softlock guarantee** (DECISIONS.md Ep2: ramps ungated
  everywhere on the 48-grid). Colony-gating them removes vertical circulation from the
  wilds. Kept safe because: arrival pockets, worm tunnels, and the ceiling membrane stay
  ungated (explicitly untouched); exits only need cap access; descent in the wilds is
  inconvenience-by-design, not softlock. Larders anchor to ramp turns (walkable-from-ramp
  probe invariant), and larders already require f > eligibility, so every larder's anchor
  shaft remains realized under the same gate — the implementer must verify that
  consistency holds in code, not assume it.
- **Runtime respawn is dead in this dimension** (persistent-category cap starvation,
  worldgen.md). "More common" for any caste therefore means generation seeding, not biome
  weights. Biome JSON spawner lists are left as-is.

## WP-1 — Tools & blocks (sonnet)

1. **Remove the five chitin tools** (`chitin_sword/pickaxe/axe/shovel/hoe`):
   registrations `ModItems.java:144-169`, recipes `ModRecipeProvider.java:127-174` +
   `buildRecipes` call, creative tab `ModCreativeModeTabs.java:49-53`, lang
   `ModLanguageProvider.java:81-85`, item models `ModItemModelProvider.java:70-74`,
   texture functions + `ITEM_TEXTURES` entries in `assets-src/blocks.py:1435-1489`
   (delete the five PNGs under `src/main/resources/assets/formicary/textures/item/`).
2. **Add Mandible Pickaxe + Pincer Sword** on the existing `ModToolTiers.CHITIN`
   `SimpleTier` (durability 400, iron-class, chitin repair): vanilla `PickaxeItem` /
   `SwordItem`, iron-borrowed damage/speed constants like the removed set. Recipes mirror
   the removed shapes (pickaxe: 3 chitin + 2 sticks; sword: 2 chitin + 1 stick). Lang
   "Mandible Pickaxe" / "Pincer Sword". Handheld item models. New `_tool_icon`-based
   textures in blocks.py: pickaxe head reads as a pair of curved mandibles; sword blade
   reads as a single pincer claw. Creative tab entries where the old tools sat.
3. **Digging-gate bypass**: holding the Mandible Pickaxe (main hand) counts as gated-open
   regardless of armor —
   (a) `ChitinArmor.onFabricBreakSpeed` (`ChitinArmor.java:60-73`): return early (no /25
   slowdown) when the player's main-hand item is the Mandible Pickaxe;
   (b) `WearingFullChitinCondition` (`loot/WearingFullChitinCondition.java:49-52`): pass
   when full set OR the loot context TOOL is the Mandible Pickaxe.
   Update/extend the existing gate GameTests; add one test: no armor + mandible pickaxe
   -> full drops from a `colony_fabric` block.
4. **Remove `packed_soil_brick_wall`** (block+item): `ModBlocks.java:249-250`,
   `ModItems.java:206-207`, `ModBlockStateProvider.java:87`,
   `ModItemModelProvider.java:95,114-116` (drop the `wallItem` helper if now unused),
   `ModBlockLootSubProvider.java:129`, `ModRecipeProvider.java:308-314`,
   `ModLanguageProvider.java:46`, `assets-src/blocks.py:1956-1961` family_wall_sheet
   call. Creative tab is implicit (getEntries loop) — nothing to edit there.
5. **Remove Resin Weep entirely**: `ModBlocks.java:63-68`, `ModItems.java:175`,
   `ModBlockStateProvider.java:35`, `ModItemModelProvider.java:32`,
   `ModBlockLootSubProvider.java:104,177-188`, `ModLanguageProvider.java:28`, texture
   function `assets-src/blocks.py:388+` + PNG, worldgen placements
   `ColonyChunkGenerator.java:574-579,612-616,641-645` + constants
   `RESIN_WEEP_CHANCE_BY_TIER` / `THRONE_RESIN_WEEP_CHANCE` / `NURSERY_RESIN_WEEP_CHANCE`
   in `ColonyGeneratorTunables.java`, and the
   `ColonyAngerGameTests.resin_weep_break_does_not_anger` test (delete it).
   Resin stays obtainable via resin blocks (worldgen, self-dropping) + soldier drops.
6. `.\gradlew runData` — regenerated JSON must show only expected deletions/additions
   (no stray drift); run `python assets-src\blocks.py` after texture edits.

## WP-2 — Worldgen: chambers, fungus, shafts, larders (opus)

1. **Fungus only in gardens**: delete the ambient fungal section of
   `decorateFloorSpace` (`ColonyChunkGenerator.java:476-484`) and the
   `FUNGAL_CARPET/BLOOM_CHANCE_BY_TIER` arrays (`ColonyGeneratorTunables.java:195-196`).
2. **Gardens lusher** (dial): `GARDEN_SPORE_CROP_CHANCE 0.12 -> 0.16`,
   `GARDEN_FUNGAL_BLOOM_CHANCE 0.15 -> 0.24`, `GARDEN_FUNGAL_CARPET_CHANCE 0.50 -> 0.52`
   (`ColonyGeneratorTunables.java:777-779`; ~92% floor coverage).
3. **Garden ants**: new chamber-anchored seeding (pattern:
   `spawnLarvaeInNurseryChambers`, `ColonyChunkGenerator.java:850-890`): per garden,
   2-3 workers + 1 soldier (dial). Workers/soldiers are already unconditionally
   persistent via class-level `removeWhenFarAway` — no per-instance flag needed, but
   match the existing seeding conventions (`MobSpawnType.CHUNK_GENERATION`, ground snap).
4. **Nurseries**: `NURSERY_LARVAE_MIN 2 -> 3` (guaranteed 3-4); same new seeding adds
   2-3 workers + 1-2 soldiers per nursery (dial).
5. **Shafts colony-only**: realize a shaft cell only when
   `colonyField(shaft centre) >= CHAMBER_ELIGIBILITY_MIN_F` (0.2) — gate inside
   `shaftForCell`/`shaftsNear` (`ColonyNoise.java:172-291`) so `isAir`, the probe, and
   dev commands all see the same truth. Arrival pockets, worm tunnels, membrane stay
   ungated. Verify the larder->ramp anchoring stays consistent (see design notes).
6. **Landings rounded** (`ColonyNoise.java:279-288`): keep the floor disc flat (the
   ramp junction and traversal must stay clear), dome the ceiling (~+2 rise at centre),
   and taper the wall radius near roof/floor so the silhouette reads as a rounded
   chamber, not a flat cylinder. Any profile is fine if the probe's walkability BFS
   still passes and headroom along the ramp junction stays >= RAMP_AIR_HEIGHT.
7. **Larders at all tiers + slightly more common** (dial): per-cell tier pick (hash off
   the larder cell, uniform over tiers 0-3) instead of the hard
   `LARDER_FLOOR_MIN_Y=150` anchor — floors land inside the picked tier's chamber band,
   same next-ramp-turn anchoring per tier; larder-specific eligibility
   `LARDER_ELIGIBILITY_MIN_F = 0.15` (others stay 0.2).
8. **Provision comb count ~6/larder** (dial): keep `LARDER_GUARANTEED_PROVISION_COMB=2`,
   drop `LARDER_PROVISION_COMB_CHANCE 0.18 -> 0.035` (mean ~= 6 total).
9. **Provision comb loot rework** (`ModBlockLootSubProvider.provisionCombTable()`,
   `:152-163`): pearls: unconditional 1-2 -> `random_chance 0.05` for exactly 1.
   Food pool: weighted — Honeyed Comb 6, Fungal Stew 4, Royal Jelly Treat 1 (rolls 1-2).
   New ore pool (1 roll, weighted, ~70% yield): coal 20, raw copper 14, raw iron 12,
   raw gold 6, lapis 5, diamond 2, emerald 1, empty 25. Counts 1-3 for coal/copper/iron,
   1-2 gold/lapis, exactly 1 diamond/emerald.
10. **NoiseProbe updates** (`NoiseProbe.java`): shaft invariants sample only realized
    (in-colony) shafts + assert every realized shaft has f >= 0.2 + each measured colony
    contains >= 3 shafts; larder band check widens to per-tier bands; walkability BFS
    (ramps, landings, larder-from-ramp) must pass on seeds 1234567 / 42 / 987654321.
11. Update any GameTests/probe assertions bound to changed constants (WorldgenGameTests
    imports live constants — recheck which literals moved).

## WP-3a — Entity mechanics (sonnet)

1. **Ender ants more common**: `COLONY_ENDER_ANTS_MIN/MAX 2/3 -> 4/6`
   (`ColonyGeneratorTunables.java:415-416`).
2. **Guaranteed pearl**: `ModEntityLootSubProvider.enderAntTable()` (`:93-104`):
   base count `UniformGenerator.between(0,1)` -> `ConstantValue.exactly(1)`; keep the
   Looting bonus 0-1. Strengthen `ender_ant_deaths_drop_ender_pearls` to assert >= 1
   pearl on an unenchanted kill.
3. **Queen voice -> spider (temporary, custom sounds later)**:
   `getAmbientSound` `BEE_LOOP_AGGRESSIVE -> SPIDER_AMBIENT`
   (`QueenAntEntity.java:1104-1124`), and the phase-burst `playSound` at `:451`
   (same bee loop) -> `SPIDER_AMBIENT` at its existing volume/pitch. Keep warden roar
   (frenzy), llama spit (acid), rooted-dirt/explode (burrow/slam).
4. **Acid spit less lethal** (dial): `AcidSpitProjectile.IMPACT_DAMAGE 4.0F -> 2.0F`
   (`AcidSpitProjectile.java:42`); poison rider and 80t cooldown unchanged.

## WP-3b — Queen pincer art (opus)

Bigger, more menacing mandibles, base+tip idiom preserved (flat children of head,
absolute rest pose — see model javadoc):
- `assets-src/models.py:296-395`: base 4x3x4 -> ~5x3x5, tip 2x2x4 -> ~3x2x6,
  `QUEEN_MANDIBLE_TIP_ANGLE 0.3491 -> ~0.42`; artist freedom within the 128x64 atlas —
  repack the `mandible_base (94,27)` / `mandible_tip (94,34)` regions as needed and
  repaint (keep the gold-tipped plum palette).
- Mirror the exact numbers into `QueenAntModel.java` (`:83`, `:164-182`); `setupAnim`
  flex unchanged.
- `python assets-src\models.py` -> inspect `assets-src/previews/queen_ant_*.png`;
  previews are the QA gate before the client ever launches.

## WP-4 — Verification (main loop, never delegated)

- `.\gradlew build` clean; full GameTest suite green (`runGameTestServer *> log`,
  Select-String the summary — count will shift with removed/added tests; every failure
  triaged, zero tolerated).
- NoiseProbe, 3 seeds, ALL PASS with the updated invariants.
- `runData` idempotent (no drift on a second run).
- Fresh-world shot-list pass: garden (fungus + ants), nursery (3-4 larvae + ants),
  larder at a non-top tier (~6 combs), rounded landing, colony fringe (no wild shafts in
  frame), queen close-up (pincers). Frames go to Logan.
- No tag, no push, no version flip — Logan's next play-test gates the 1.0.0 freeze.
