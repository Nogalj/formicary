# Ep2 play-test revisions, round 2 — implementation plan (2026-08-19, evening)

Source: Logan's second review pass, same day round 1 shipped. Thirteen items. Numbers
marked (dial) are my defaults for his open quantities. Branch `ep2`, four packages,
sequential, main-loop verification between and after. Suite is 80 green at start; probe
ALL PASS on seeds 1234567/42/987654321.

## WP-R1 — Dimension geometry (opus): Y shrink, compact colonies, multi-chambers

The riskiest package; everything downstream re-derives from it. Do the three changes as
one coherent re-derivation, not three patches.

1. **Shrink the dimension: TIER_HEIGHT 48 -> 32, total height 192 -> 128** (dial; 4 tiers
   kept; 128 is a multiple of 16). Sweep EVERY Y-derived surface: ColonyGeneratorTunables
   (tier boundaries, CHAMBER_FLOOR_MIN_Y_BY_TIER, landing constants, ceiling/membrane cap
   band, arrival-pocket Y, ender-seed band if Y-coupled), the dimension_type JSON
   (height/logical_height 128), the four Y-band biome JSONs, dev-command tier naming, and
   every probe invariant with a Y in it. Chamber interior (~13 with shell) must still fit
   between a tier's floor-min and the next boundary — show the arithmetic in the
   constants' javadocs. Ramp period (~24.2/turn) is now ~1.3 turns per tier — confirm
   landings and chamber next-ramp-turn anchoring still resolve inside each band.
2. **Compact colonies: COLONY_CORE_RADIUS 100 -> 60, COLONY_OUTER_RADIUS 150 -> 90**
   (dial). COLONY_SPACING/JITTER unchanged (separation and boss-bar hysteresis
   guarantees keep their margins — re-check the derivation comments). Ender-seed ring
   solves from f in [0.3,0.8] and auto-adapts; verify, don't assume.
3. **All chambers more common + multiple per colony (throne stays exactly 1):** chamber
   grid spacing 96 -> 48 (each cell now anchors its own ramp cell — verify the
   anchor-resolution and the 120-degree slot layout still hold; slot separation bound
   re-derives at the same approach distance so it should be unchanged, prove it), plus a
   per-cell realization roll per kind, tuned so a colony carries roughly **4-6 realized
   chambers of each kind** (dial) under the new compact field. Larder keeps its looser
   0.15 eligibility and its all-tiers pick + throne distance gate.
4. **Probe**: recalibrate every count/band/walkability invariant; add "chambers of each
   kind per colony" measurement with a [3, 9] acceptance band; keep same-cell slot
   separation, throne clearance (block-scan), ramp-per-colony, dome profile, and
   top-to-bottom reachability. Three seeds ALL PASS.
5. GameTests bound to moved constants get retargeted (report each). runData should not
   drift (no datagen touched) — if it does, STOP.

## WP-R2 — Worldgen decoration & blocks (sonnet)

1. **Larder loot blocks: deterministic ~6** — round 1's 2 guaranteed + 0.035/wall-block
   still lands >10 in practice (walls have more eligible cells than estimated). Kill the
   per-wall-block chance entirely; place **5-7 provision combs per larder** (dial) via
   the same slot mechanism as the guaranteed 2 (angles jittered off the doorway bearing,
   the existing anti-doorway logic extended to N slots). Probe/GameTest asserting the
   count band.
2. **Daylight membrane unbreakable**: the ceiling membrane block gets bedrock-class
   properties (strength(-1.0F, 3_600_000.0F), no loot table) so the pearl toll is the
   only way out. Pearl passage (PortalEvents/AnthillPortal) must be untouched — it
   operates on the block in place, verify it does not break/replace-then-restore in a
   way that unbreakability disturbs. Update any GameTest that mined membrane.
3. **Scatter vanilla dirt, gravel, and sand through the fabric**: pocket/blob pass in
   the soil layers (ore-blob style, modest — flavor and shovel targets, not terrain
   takeover; ~2-4 pockets of 5-20 blocks per chunk in colony + wilds alike, dial), plus
   optional sand floors where it reads naturally. These blocks are NOT colony_fabric —
   free digging and normal drops is the point (pairs with the pickaxe-as-shovel item in
   WP-R3 and flint in the loot pool). Keep them out of chamber shells/floors so rooms
   stay intact (carve/decoration ordering — state where the pass runs).
4. **Anthill spawn rate in the overworld: ~2x** — find the placed-feature/biome-modifier
   rarity for the savanna anthill and double the frequency (halve rarity or double
   count; report before/after values).

## WP-R3 — Items & loot (sonnet)

1. **Provision comb loot pool expansion, small per-break yield**: keep the 5% pearl
   pool; food pool drops to exactly 1 roll (Honeyed Comb 6 / Fungal Stew 4 / Royal
   Jelly Treat 1); replace the ore pool with one merged **sundries pool, 1-2 rolls**,
   weighted (dial): stick 14 (1-2), flint 10 (1-2), rotten flesh 8 (1-2), bone 8 (1-2),
   string 8 (1-2), leather 6 (1), feather 6 (1-2), coal 16 (1-2), raw copper 12 (1-2),
   raw iron 10 (1-2), raw gold 5 (1), lapis 4 (1-2), diamond 1 (1), emerald 1 (1),
   leather helmet/chestplate/leggings/boots 1 each (1). Net: a break gives ~2-4 items.
   Update the distribution GameTest's expectations.
2. **Mandible Pickaxe digs like a shovel too**: new block tag
   `formicary:mineable_with_mandible_pickaxe` = union of `#minecraft:mineable/pickaxe`
   + `#minecraft:mineable/shovel` (datagen tag provider, addTag composition); register
   the item as a DiggerItem over that tag (verify the 1.21 DiggerItem ctor against
   reference/ — PickaxeItem is just DiggerItem + MINEABLE_WITH_PICKAXE). Gate bypass
   (ChitinArmor + loot condition) must keep matching the item. GameTest: sand or gravel
   digs at full speed + drops with the pickaxe.
3. **Pickaxe texture rework**: current icon reads as a stick. Give it a proper pickaxe
   silhouette — head with two picks flaring from the shaft top, each pick shaped/curved
   like an ant mandible (chitin palette, serration pixels welcome). Iterate on the
   contact sheet until it reads as a pickaxe at 16x16.
4. **Scent gland drop rates down**: worker 50% -> 25%, soldier 75% -> 40% (dial).
   Retarget any loot GameTests bound to the old chances.

## WP-R4 — Entity behavior (opus)

1. **BUG: tamed workers still sweep every grown crop before depositing.** Round 1 put
   the one-at-a-time intent on the DEPOSIT side (deposit requires non-empty pack) but
   left HarvestCropsGoal free to keep running while grown crops remain — so the ant
   fills its pack across the whole field, then deposits once. Reproduce FIRST with a
   GameTest (3+ grown crops, chest, assert a deposit happens after exactly one crop is
   collected), then fix on the HARVEST side: the harvest goal must not start (or
   continue to a second crop) while the pack holds produce. Watch the known traps:
   goals tick every other tick; moveTo arrival tolerance; the 9% wheat-seed replant
   flake (use a non-wheat crop or seed the RNG per gametest.md). Keep the ferry goal
   (isPackFull) and RETRY_COOLDOWN_TICKS pacing working; retarget round-1 shuttle tests
   that asserted the old sequencing (report each).
2. **Ants climb walls like spiders**: WallClimberNavigation + climbing-flag pattern from
   the decompiled Spider (createNavigation override; set the synced climbing flag from
   horizontalCollision in tick; onClimbable returns it). Apply to worker, soldier,
   ender, and the tamed variants; NOT the queen (boss arena design) and NOT larvae
   (they wriggle in place) — deliberate choice, note it in DECISIONS.md. Verify against
   reference/Spider.java, not memory. Run the full suite — pathing-sensitive tests
   (deadlock twins, shuttle, relocation) are the regression surface.
3. **Soldier status text: "guard post" state renders as "Staying"** — find the status
   string surface (announce/status display added in ep2) and rename that state's label;
   any lang entry included. Behavior unchanged.

## WP-R5 — Verification (main loop, never delegated)

Build + full suite (my run), probe 3 seeds (my run), runData idempotent, fresh-region
photography (NEW far offset — and note the dev save's old chunks are doubly stale now:
height change means Logan needs a NEW WORLD for his next test), frames to Logan. No
tag, no push, no version flip.
