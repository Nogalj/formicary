package com.nogal.formicary.portal;

import java.util.List;

import javax.annotation.Nullable;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.worldgen.ModWorldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The game-bus hooks that make the portal and the breadcrumb trail work.
 *
 * <p>Event names verified against the decompiled 21.0.167 sources:
 * {@code net.neoforged.neoforge.event.entity.ProjectileImpactEvent} (cancellable, carries
 * the {@code HitResult}) and {@code net.neoforged.neoforge.event.tick.PlayerTickEvent.Post}
 * -- {@code PlayerTickEvent} is an abstract Pre/Post pair in 1.21, there is no bare
 * {@code PlayerTickEvent} to subscribe to.
 *
 * <p>The cancel-then-discard dance is not optional. {@code ThrowableProjectile#tick} reads
 * <pre>
 *   if (hitresult.getType() != MISS &amp;&amp; !EventHooks.onProjectileImpact(this, hitresult)) {
 *       this.hitTargetOrDeflectSelf(hitresult);
 *   }
 * </pre>
 * so cancelling suppresses {@code onHit} -- and with it the vanilla teleport, the fall
 * damage and the endermite roll -- but leaves the pearl entity alive and still moving.
 * Without an explicit {@code discard()} it would carry on through the anthill and fire the
 * event again on the next block it met.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PortalEvents {

    /** Particles puffed at the impact so the pearl visibly "goes into" the anthill. */
    private static final int IMPACT_PARTICLES = 24;

    /**
     * Particles sent per lit-trail point on each refresh.
     *
     * <p>Play-test round 1: dropped from 2 to 1 now that {@link TrailPath#light} retraces
     * the whole recorded buffer instead of the newest 30. At the old count of 2 that would
     * have meant up to 1024 particle sends in a single tick, every
     * {@link TrailPath#TRAIL_REFRESH_TICKS} ticks -- halving this alongside doubling the
     * refresh interval (20 -> 40 ticks) brought the worst case down sharply even though
     * the trail itself got far longer.
     */
    private static final int TRAIL_PARTICLES_PER_POINT = 1;

    /**
     * How close a lit-trail point must be to the player before its particle is sent.
     *
     * <p>Play-test round 8 raised {@link TrailPath#CAPACITY} 512 -> 2048. Without this
     * cull that would have been a 4x rise in packets per refresh, because the
     * {@code longDistance} flag {@link #drawTrail} passes to
     * {@code ServerLevel#sendParticles} sets vanilla's own cutoff to 512 blocks -- far
     * past anything the player can see, so effectively no cull at all (verified in the
     * decompiled {@code ServerLevel#sendParticles(ServerPlayer, boolean, double, double,
     * double, Packet)}: {@code closerToCenterThan(..., longDistance ? 512.0 : 32.0)}).
     *
     * <p>64 blocks is twice vanilla's non-longDistance cutoff and well past normal cave
     * sight lines, so nothing visible is lost. Keeping {@code longDistance = true} on top
     * of it is deliberate: that flag also sets {@code overrideLimiter} on the packet, so
     * the trail still draws for players on reduced particle settings -- which matters for
     * something whose whole job is telling you the way out.
     *
     * <p>Net effect: the trail is 4x longer and the steady-state packet count went down.
     */
    private static final int TRAIL_DRAW_RADIUS = 64;

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownEnderpearl pearl)) {
            return;
        }
        if (!(pearl.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(pearl.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos impact = impactBlock(event.getRayTraceResult());
        if (impact == null) {
            return;
        }

        // Membrane first: it is an exact block match, where the anthill test is a radius
        // search, so checking it first keeps a membrane placed next to a core unambiguous.
        if (AnthillPortal.isMembrane(level, impact)) {
            consume(event, pearl, level, impact);
            AnthillPortal.exitColony(player);
            return;
        }

        BlockPos core = AnthillPortal.findCoreNear(level, impact);
        if (core != null) {
            consume(event, pearl, level, impact);
            AnthillPortal.enterColony(player, core);
        }
    }

    /**
     * The block a pearl landed on. An entity hit has no block of its own, so it resolves to
     * the block containing the hit point -- an ant standing on the mound should count as
     * hitting the mound.
     */
    @Nullable
    private static BlockPos impactBlock(HitResult hit) {
        return switch (hit.getType()) {
            case BLOCK -> ((BlockHitResult) hit).getBlockPos();
            case ENTITY -> BlockPos.containing(hit.getLocation());
            case MISS -> null;
        };
    }

    private static void consume(ProjectileImpactEvent event, ThrownEnderpearl pearl, ServerLevel level,
            BlockPos impact) {
        event.setCanceled(true);
        pearl.discard();
        level.sendParticles(ParticleTypes.PORTAL, impact.getX() + 0.5, impact.getY() + 1.0, impact.getZ() + 0.5,
                IMPACT_PARTICLES, 0.5, 0.5, 0.5, 0.4);
    }

    // -------------------------------------------------------- breadcrumb trail --

    /**
     * Records where the player has been while they are in the colony, and draws any lit
     * trail.
     *
     * <p>Leaving the dimension wipes both. The trail is explicitly a session aid for the
     * descent you are on -- keeping a stale route across a trip out and back would light up
     * particles through terrain the player is nowhere near.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().dimension() != ModWorldgen.FORMICARY_LEVEL) {
            // getExistingData, not getData: the latter stores its default in the holder, so
            // asking it here would hang an empty ring buffer on every player on the server
            // who has never been anywhere near the colony.
            player.getExistingData(ModAttachments.TRAIL_PATH)
                    .filter(existing -> existing.size() > 0 || existing.isLit())
                    .ifPresent(TrailPath::clear);
            return;
        }

        TrailPath path = player.getData(ModAttachments.TRAIL_PATH);
        path.record(player.blockPosition());
        if (path.tickLitTrail()) {
            drawTrail(player, path.litTrail());
        }
    }

    /**
     * Puffs one particle per recorded point within {@link #TRAIL_DRAW_RADIUS}, sent to that
     * player alone -- the trail is their own memory of the route, not a beacon everyone in
     * the colony can read.
     *
     * <p>The whole frozen route stays lit; this only decides which of its points are worth
     * a packet right now. Walking the trail brings the next stretch into range, so it
     * reveals ahead of the player exactly as it did before the cull.
     */
    private static void drawTrail(ServerPlayer player, List<BlockPos> points) {
        ServerLevel level = player.serverLevel();
        double radiusSqr = (double) TRAIL_DRAW_RADIUS * TRAIL_DRAW_RADIUS;
        BlockPos here = player.blockPosition();
        for (BlockPos point : points) {
            if (here.distSqr(point) > radiusSqr) {
                continue;
            }
            level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true,
                    point.getX() + 0.5, point.getY() + 0.6, point.getZ() + 0.5,
                    TRAIL_PARTICLES_PER_POINT, 0.15, 0.25, 0.15, 0.0);
        }
    }

    private PortalEvents() {
    }
}
