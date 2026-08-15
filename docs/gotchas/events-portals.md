# Formicary gotchas -- NeoForge events, projectiles, cross-dimension teleport

Moved verbatim from CLAUDE.md "Banked rules" by the 2026-08-14 /tidy-claude-md
restructure; each entry keeps its original `verified:` date. Routed by the symptom
index in CLAUDE.md.

- **NeoForge event names for 1.21 that training memory gets wrong.** All three verified in
  the extracted 21.0.167 sources: damage is
  `net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent` (fires in
  `LivingEntity#hurt` before mitigation -- the old `LivingHurtEvent` is GONE, and
  `LivingDamageEvent` is now an abstract `Pre`/`Post` pair fired later in the sequence);
  block breaking is `net.neoforged.neoforge.event.level.BlockEvent.BreakEvent`; mining
  speed is `net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed`
  (`getOriginalSpeed()` / `getNewSpeed()` / `setNewSpeed(float)`). The game bus enum is
  `EventBusSubscriber.Bus.GAME` -- and `EventBusSubscriber` ships in the FML *loader* jar
  (`net.neoforged.fancymodloader:loader`), NOT the neoforge sources jar, so `javap` on
  that jar is the only way to check it. (`verified: 2026-08-13`)
- **`ProjectileImpactEvent` cancel does not stop the projectile.** `ThrowableProjectile#tick`
  reads `if (hit != MISS && !EventHooks.onProjectileImpact(this, hit)) hitTargetOrDeflectSelf(hit);`
  -- cancelling suppresses `onHit` (and with it the pearl's teleport, fall damage and
  endermite roll) but leaves the entity alive and still moving, so it must be `discard()`ed
  explicitly or it fires the event again on the next block. Event class is
  `net.neoforged.neoforge.event.entity.ProjectileImpactEvent`. Also: there is no bare
  `PlayerTickEvent` to subscribe to in 1.21 -- it is an abstract `Pre`/`Post` pair under
  `net.neoforged.neoforge.event.tick`. (`verified: 2026-08-13`)
- **Cross-dimension teleport in 1.21 is `DimensionTransition`, not `PortalInfo`.** The
  command-equivalent entry point is
  `ServerPlayer#teleportTo(ServerLevel, x, y, z, Set<RelativeMovement>, yRot, xRot)` -- it
  adds the `TicketType.POST_TELEPORT` chunk ticket, then delegates to
  `teleportTo(ServerLevel, x, y, z, yaw, pitch)`, which routes a cross-dimension move through
  `changeDimension(new DimensionTransition(...))`. The plain `teleportTo(double, double,
  double)` cannot change dimension at all. Generate the destination chunk yourself
  (`level.getChunk(cx, cz)`) before reading blocks there: an ungenerated chunk reads as air
  all the way down, which looks exactly like a safe landing spot. (`verified: 2026-08-13`)
