# Formicary gotchas -- mob AI, taming, boss

Moved verbatim from CLAUDE.md "Banked rules" by the 2026-08-14 /tidy-claude-md
restructure, re-flowed 2026-08-15 to take in the play-test round 1 rules; each entry
keeps its original `verified:` date. Routed by the symptom index in CLAUDE.md.

- **`TamableAnimal` traps.** (a) `mobInteract` is **public** by the time it reaches
  `Animal`, so an override in any `TamableAnimal` subclass must be `public` too --
  `protected` (what `PathfinderMob` subclasses in this repo use) is a compile error, not a
  warning. (b) `OwnerHurtByTargetGoal` and `OwnerHurtTargetGoal` both open with
  `if (isTame() && !isOrderedToSit())`, so vanilla's sit/stay cannot back a "stays put but
  still fights" mode -- that needs its own flag. (c) `OwnableEntity#getOwner` resolves
  through `level().getPlayerByUUID`, so it is always null for a `GameTestHelper` mock
  player; assert on `getOwnerUUID()` instead. (`verified: 2026-08-13`)
- **`Mob.serverAiStep` runs `goalSelector.tick()` -- and therefore every non-running goal's
  `canUse()` -- only on alternating ticks** (`(tickCount + getId()) % 2`); the other tick
  gets `tickRunningGoals(false)`. Anything budgeting work per `canUse` call is really
  budgeting per *two* ticks. (`verified: 2026-08-13`)
- **1.21 boss-bar and mob-signature gotchas.** `ServerBossEvent(Component, BossBarColor,
  BossBarOverlay)` lives in `net.minecraft.server.level`, its enums in
  `net.minecraft.world.BossEvent` (which is NOT in the seeded `reference/` -- extract
  `net/minecraft/world/BossEvent.java`), and the plumbing is `Entity#startSeenByPlayer` /
  `#stopSeenByPlayer` overridden to `addPlayer`/`removePlayer` (copy `WitherBoss`). Two
  signatures that training memory gets wrong and the compiler catches:
  `Mob#canBeLeashed()` takes **no** `Player` in 1.21, and `LivingEntity#getVoicePitch()` is
  **public**, so a `protected` override is a "weaker access privileges" error. `Mob` also does
  **not** persist its `restrictTo` restriction -- save the centre yourself and re-apply it in
  `readAdditionalSaveData` or a relog frees the mob. (`verified: 2026-08-13`)
- **Vanilla's owner-aware goals are `TamableAnimal`-only.** `OwnerHurtByTargetGoal`,
  `OwnerHurtTargetGoal` and `FollowOwnerGoal` all take a `TamableAnimal` in their constructors,
  so a "temporarily allied" mob that is not one has to reimplement them. The vanilla shape
  worth copying is the timestamp guard: read `getLastHurtByMob()` / `getLastHurtMob()` together
  with `getLastHurtByMobTimestamp()` / `getLastHurtMobTimestamp()` and refuse to re-adopt a
  grudge whose timestamp you have already seen. (`verified: 2026-08-13`)
- **Clearing a mob's target does NOT make it stay cleared -- a running `TargetGoal` puts
  it straight back.** `TargetGoal#canContinueToUse` opens with `livingentity =
  mob.getTarget(); if (livingentity == null) livingentity = this.targetMob;` and ends with
  `mob.setTarget(livingentity)`, so the goal re-installs its own cached target on the next
  goal-cleanup tick. It also never re-runs the goal's `TargetingConditions` predicate --
  only `mob.canAttack(target)`, team, distance and line of sight. **`canAttack(LivingEntity)`
  is therefore the only seam that binds a chase both while it is being picked
  (`TargetingConditions#test` calls it) and while it is running.** Any "this mob must stop
  attacking X" rule belongs there, not in a target goal's predicate. Corollary: vanilla's
  `NeutralMob#stopBeingAngry` already clears `lastHurtByMob`, the persistent anger target
  AND `setTarget(null)` -- so "the anger was cleared but the target wasn't" is almost never
  the real diagnosis; "it was cleared and re-installed two ticks later" usually is.
  (`verified: 2026-08-15`)
- **`LivingEntity.hurt` returns early -- before it records `lastHurtByMob` -- when
  `invulnerableTime > 10 && amount <= lastHurt`, but fires `LivingIncomingDamageEvent`
  *above* that return.** So a follow-up hit no bigger than the previous one still provokes
  everything hooked to the damage event while recording no grudge, no knockback and no
  actual damage. This bites GameTests specifically: `helper.spawn` drops a mob far enough
  to deal 1.0 fall damage, so a 1.0F test swing a few ticks later is a silent no-op that
  *looks* like it landed. Hit for meaningfully more (6.0F works) whenever the test depends
  on `hurt` returning true. (`verified: 2026-08-15`)
- **`HurtByTargetGoal.canUse` compares `mob.getLastHurtByMobTimestamp()` against its own
  `timestamp` field, which starts at `0`** -- so damage dealt on the mob's spawn tick
  (`tickCount == 0`) is invisible to the goal permanently, since the two match. A test that
  needs a mob to acquire a target through personal retaliation must let a tick or two pass
  before landing the hit. (`verified: 2026-08-15`)
- **`TamableAnimal`'s ancestor `Animal` overrides `getBaseExperienceReward()` to a flat
  `1 + random.nextInt(3)`, ignoring `Mob#xpReward` completely.** Setting `this.xpReward` in
  a `TamableAnimal` subclass's constructor -- the pattern that works for every
  `PathfinderMob`-direct entity, where `Mob.getBaseExperienceReward()` reads the field --
  compiles clean and silently does nothing. A tamed caste that needs a specific XP reward
  must override `getBaseExperienceReward()` itself. Verified in the decompiled `Animal.java`
  and caught by a GameTest asserting an orb value outside `Animal`'s 1-3 fallback range.
  (`verified: 2026-08-15`)
- **A goal that walks to a target with `PathNavigation.moveTo` but completes on its own,
  tighter range deadlocks permanently -- the mob stands next to the thing it wants, forever.**
  `moveTo(Entity, speed)` is `createPath(entity.blockPosition(), accuracy = 1)`, and
  `followThePath` retires the last node as soon as the mob is within `maxDistanceToWaypoint`
  of it (`bbWidth / 2` for a mob wider than 0.75, so 0.45 for a 0.9-wide ant), so "arrived"
  legitimately leaves the mob up to about 2.2 blocks from the real target. If the goal's own
  completion range is tighter than that, the mob lands in the gap and **nothing ever moves it
  again**: `moveTo(Path, speed)` opens with `if (this.isDone()) return false;` without
  touching the mob, so a repath loop is a no-op and an approach-timeout-then-restart loop only
  replays the same stalemate. Caught 2026-08-18 in `CollectDroppedItemsGoal`, whose pickup
  range was 1.5 against that 2.2 arrival: a trace showed the worker frozen 1.537 blocks from
  its drop, `navDone=true`, for 575 consecutive ticks. The fix is to close the last stretch
  with `getMoveControl().setWantedPosition(...)` -- the same primitive `PathNavigation.tick()`
  itself uses to push a mob at its next waypoint -- bounded to a few blocks so it stays a final
  step rather than becoming a pathfinding substitute that could walk the mob off a ledge. Any
  goal pairing `moveTo` with a hand-written reach wants checking against this. `RelocateItemGoal`
  had the identical 1.5-block pickup on the same `moveTo(Entity)` approach and got the same fix
  plus a deterministic twin test (`a_drop_the_path_stops_short_of_is_still_relocated`) on
  2026-08-18 -- its deadlock had gone unfiled because the failure is invisible in play: the
  errand burns its approach timeout, the cooldown swallows the retry, and the ant just looks
  like it changed its mind. `HarvestCropsGoal` and `DepositToChestGoal` (reach 2.0, aimed at
  block centres) were assessed safe and left alone: `PathFinder.findPath` accepts an end node
  by MANHATTAN distance (`node.distanceManhattan(target) <= accuracy`, read in the decompiled
  source), so an accuracy-1 path ends on the target block or one of its six face neighbours,
  and against a block-CENTRE aim the worst retirement position -- 1.0 of node offset plus the
  0.45 per-axis waypoint slack plus the half-block up to the centre, ant on standable ground --
  lands about 1.6 out, inside 2.0 with ~0.4 to spare. The item goals were the exposed pair
  because an item entity's measured position floats up to a further ~0.7 from the centre of
  the block actually pathed to (`blockPosition()`), which is what stretched "arrived" to ~2.2
  against their 1.5. (`verified: 2026-08-18`)
- **`xpReward = N` never produces one orb worth N.** `ExperienceOrb.award` splits the total
  through `getExperienceValue`'s tier ladder (1, 3, 7, 17, ...), so a reward of 8 always
  arrives as 7+1. A GameTest using `anyMatch(orb.getValue() == XP_REWARD)` works for 7 by
  luck of a tier boundary and silently fails for 8 -- assert the SUM of orb values instead
  (`an_ender_ant_death_awards_its_full_xp_reward`). (`verified: 2026-08-18`)
- **A goal's own give-up timeout does not stop it re-picking the identical target the instant
  it restarts.** `HarvestCropsGoal.APPROACH_TIMEOUT_TICKS` was documented as "give up on an
  unreachable crop rather than standing on a wall forever" and did stop the goal -- but
  `canUse()` re-scans immediately, `CropScanner` has no memory of the failed attempt, and it
  answers with the same nearest-but-unreachable crop every time, so the goal restarts at it: a
  permanent lock-on, discovered as a known gap in commit `0e595bb`'s play-test trace and fixed
  2026-08-20. The fix is a per-position failure blacklist owned by the goal (not the scanner --
  `CropScanner.scan` gained a `Predicate<BlockPos> exclude` overload instead, keeping it a pure
  stateless sweep), keyed to an expiry (`level.getGameTime() + duration`, ~1200 ticks) rather
  than a boolean, so a transiently-blocked position clears itself without a second code path,
  and cleared explicitly the moment the goal actually reaches a position (proof it was
  reachable after all) rather than only on a successful action there. Any "give up and try
  again" goal that re-derives its own target from scratch on every `canUse()` call has this
  exposure unless the derivation itself excludes recent failures. (`verified: 2026-08-20`)
