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
