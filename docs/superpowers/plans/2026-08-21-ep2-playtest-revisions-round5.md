# Ep2 play-test revisions -- round 5 (2026-08-21)

Logan's fifth play-test list, 5 items, with a screenshot: a loaded tamed worker wedged
high on an overworld tree trunk under the leaf canopy -- the exact wedge geometry banked
in `docs/gotchas/gametest.md` (climbing ants + `skyAccess` arenas), now reproduced in
live play. Items 2-3 are worldgen: **Logan needs a NEW WORLD for the sixth test.**

Items, verbatim intent -> work package:

1. "ant gets stuck after climbing and is not able to deliver its harvest back at the
   chest" -> WP-alpha
2. "much larger patches of sand, dirt, and gravel" -> WP-beta
3. "add packed soil randomly generating through the top layer, small 1~3 block sized
   patches very frequent. and use amber earth in the second layer in the same small
   1~3 block sized patches very frequent" -> WP-beta
4. "ants jump when trying to path find through a fungal bloom" -> WP-alpha
5. "player should be able to plant inside the formicary dimension. in farmland and the
   fungal spore" -> WP-gamma

## Diagnoses / design decisions (main loop -- record in DECISIONS.md)

- **Item 1 root cause (known, banked):** every ant except queen/larva sets its climbing
  flag off bare `horizontalCollision` (`AntClimbing`), so a worker walking its farm brushes
  a tree trunk, rises 0.2/tick, and wedges under the canopy; the wedge is stable even
  after goals retarget (the round-3 cage test proved it). Two-layer fix, both for TAMED
  ants only (wild ants keep unconditional climbing -- colony walls are their habitat):
  1. **Climb gate**: a tamed worker/soldier only sets the climbing flag when its current
     navigation actually wants to go UP (target/next node meaningfully above foot level)
     -- flat farm errands stop producing tree ascents. If reading the path cleanly proves
     infeasible, fall back to "climb only in the formicary dimension" and STOP to report
     which gate landed.
  2. **Delivery recall**: when `DepositToChestGoal` exhausts `APPROACH_TIMEOUT_TICKS`
     with a loaded pack and the chest still out of reach, the worker TELEPORTS beside the
     chest (wolf-style safe-spot search, the `FollowOwnerGoal` precedent) and deposits.
     Delivery becomes guaranteed no matter what terrain did to the walk. Same-dimension
     only (bound chests always are).
- **Item 4:** diagnose before fixing (superpowers systematic-debugging). Hypotheses, in
  order: (a) path-type/malus mapping for `FungalBloomBlock` (a `BushBlock` subclass --
  check what `WalkNodeEvaluator` derives for it, and whether the self-`lightLevel` or
  anything else knocks it out of OPEN); (b) an interaction with the climbing flag /
  `WallClimberNavigation`'s no-path push fallback; (c) Logan saying "bloom" but the
  1/16-collision `FUNGAL_CARPET` being the actual trigger. Fix per diagnosis; prefer a
  block-side path hint over navigation special-cases.
- **Items 2-3:** seams scale to ~3-4x their round-3 patch AREA ("much larger", second
  time asked). New micro-patch pass: 1-3 block clusters, several per chunk per tier --
  packed soil speckling the TOP tier's fabric, amber earth speckling the SECOND tier's.
  Distinct RNG salts per patch type; never key variety off a y-only-differing positional
  stream's first draw (the banked Xoroshiro first-draw correlation). NoiseProbe gains
  composition invariants for both (per-tier micro-patch frequency band + seam coverage
  band) and must stay ALL PASS on the three standard seeds (1234567 / 42 / 987654321).
- **Item 5:** three changes.
  1. **Tilling**: hoe turns the three soft tier soils (Packed Soil, Amber Earth, Deep
     Loam) into vanilla farmland -- via NeoForge's tool-modification hook (event or
     block override; verify the 21.0.167 API shape in reference/), no new block classes.
  2. **Fungal Spore in the dark**: the crop keeps farmland placement but drops the
     light-level survival requirement (a cave fungus; applies in the overworld too --
     one rule, thematically right).
  3. Vanilla crops then already work down there: farmland + a Fungal Bloom's light 10
     clears wheat's >= 8 requirement. No code -- record the interaction.

## Work packages (sequential, one implementer at a time)

- **WP-alpha (opus): climbing + pathing** -- climb gate, delivery recall, bloom-jump
  diagnosis + fix. Tests: caged-worker recall delivers (deterministic barrier cage),
  climb gate property (flag stays false pathing on the flat; true when target is above),
  plus whatever seam the bloom fix exposes. Mind every climbing-ant gametest gotcha.
- **WP-beta (opus): worldgen** -- seam upscale + micro-patches + probe invariants.
  Probe ALL PASS x3 seeds is the gate; no chamber/shaft/arrival regressions.
- **WP-gamma (sonnet): planting** -- tilling hook, spore light rule, DECISIONS entries
  for all of round 5. Tests: till seam produces farmland from all three soils and not
  from Hardened Soil; spore placed at light 0 on farmland survives and grows.

## Verification (main loop, never delegated)

Build + full suite (93 baseline) + NoiseProbe 3 seeds + runData idempotency. Fresh-region
two-pass photography of the new seams/micro-patches (docs/dev-tools.md recipe) so Logan
sees the worldgen before his sixth test. Commit per package; no tag, no push.
