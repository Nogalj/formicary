# Ep2 play-test revisions, round 3 — implementation plan (2026-08-19, night)

Source: Logan's third same-day pass (new world, post-round-2 geometry). Eight items.
Two are investigations (reproduce first): the arrival softlock and the missing provision
combs. Branch `ep2`, three packages, sequential. Suite 83 green, probe ALL PASS x3 at
start.

## WP-S1 — Worldgen: softlock, comb count, tiered patches, tighter colonies (opus)

1. **BUG (softlock, highest priority): spawning into the dimension can land you in a
   sealed box with no tunnel connection** (Logan, arriving in colony outskirts). The
   arrival pocket must connect to the traversable network BY CONSTRUCTION, everywhere —
   not probabilistically via a tunnel happening to pass. Reproduce first (probe-style:
   sample many arbitrary arrival XZ columns, BFS from the carved pocket, count how many
   reach a worm tunnel), diagnose why round-2 geometry exposed it (arrival tier changed;
   wilds have no ramps since round 1), then fix by carving a guaranteed connector from
   the arrival pocket to the nearest worm tunnel (or ramp if in-colony), and add the
   permanent probe invariant: every sampled arrival column reaches the network.
2. **BUG: larders present fewer provision combs than the 5-7 the pure function places**
   (Logan saw 3). Prime suspect: slot positions crossing chunk boundaries — the
   force-write pass runs per chunk; if a larder's slots extend into a neighboring chunk
   and that chunk's pass doesn't re-derive them, they are silently dropped. Verify in
   code (which chunks enumerate a larder, which blocks forceProvisionComb writes),
   reproduce (probe: compare pure-function slot count vs blocks that would actually be
   written per chunk pass), fix so every slot lands regardless of boundary, and assert
   it (world-level or write-simulation invariant, not just the pure function). Note the
   texture-confusion confound (WP-S2 makes provision comb distinct) but treat the write
   path as the real defect until measured otherwise.
3. **Soil patches re-spec (tier-conditional, LARGE)**: top tier (Fungal Gardens):
   sand + dirt; middle (Nurseries) and bottom (Royal Depths): gravel + dirt. Patch
   shape: broad and flat (roughly 6-14 wide, 2-3 tall, 20-60 blocks; dial) rather than
   the current small blobs; ~1-2 per chunk (dial). Same safety rule: fabric-only
   replacement, never chamber interiors/shells/corridors/ramps/landings/membrane;
   pocket invariant updated to the per-tier composition (assert no sand below the top
   tier, no gravel in the top tier).
4. **Colonies tighter still**: CORE 80 -> 64, OUTER 128 -> 100, SPACING 320 -> 288
   (dial). Known trade, accepted deliberately: the chamber census ceiling drops to
   ~2.5-3 of each kind per colony (the disc-area arithmetic from round 2); loosen the
   census floor to [2, 9] and record in DECISIONS.md that Logan chose compactness over
   the round-2 4-6 target when told they conflict; spacing 288 partially compensates
   via encounter rate (separation 288-96=192, still >> boss-bar needs; findability
   bound re-derives). All radius-coupled derivation comments re-checked.
5. Probe: all invariants recalibrated; three seeds ALL PASS; suite green; runData
   no-drift.

## WP-S2 — Art: comb family + tool icons (sonnet)

1. **Three distinct combs**: provision comb gets its OWN texture (visibly a stocked
   pantry — e.g. capped/plugged cells with wax lids, distinct silhouette at a glance);
   widen the hue gap between brood comb and royal comb (royal reads gold/amber, brood
   shifts toward pale wax/tan; dial within the palette). Update blocks.py, regenerate,
   judge the contact sheet + an in-world sanity check is WP-S3's photography's job.
2. **BUG: mandible pickaxe + pincer sword icons are cut off at the top edge of the
   16x16 canvas** (Logan's screenshots: the blade/prong truncates mid-shape). Rework
   both to fit entirely inside the canvas with at least 1px margin on all sides —
   shrink/reposition the shape, don't just crop differently. Verify by scanning the
   PNG's border rows/columns for non-transparent pixels (a scriptable check — add it as
   a permanent assertion in blocks.py's main() for ALL item textures so a future icon
   can never ship clipped), then judge the contact sheet visually.

## WP-S3 — Combat: queen tuning (sonnet)

1. **Queen melee damage down ~40%** (find the attack attribute, report before/after;
   dial).
2. **Acid spit blockable by shield**: verify the impact damage's DamageSource is
   shield-blockable (not tagged bypasses_shield; projectile sources should be), AND fix
   the likely real defect: the poison rider applies even when hurt() returns false
   (blocked), making a block feel useless. Poison (and any other rider) only on a
   successful hurt. GameTest if a blocking mock player is feasible; otherwise assert
   the rider-gating seam directly and say why.

## WP-S4 — Verification (main loop)

Build + suite + probe x3 (my runs), runData, fresh-region photography incl. a larder
comb-count visual check and the new patches, frames to Logan. No tag, no push.
