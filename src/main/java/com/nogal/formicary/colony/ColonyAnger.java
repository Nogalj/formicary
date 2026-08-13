package com.nogal.formicary.colony;

import javax.annotation.Nullable;

import com.nogal.formicary.entity.LarvaEntity;
import com.nogal.formicary.entity.SoldierAntEntity;
import com.nogal.formicary.entity.WorkerAntEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The colony's shared aggro brain (spec section 3).
 *
 * <p>The colony is bee-style neutral: nobody attacks on sight. Two things provoke it --
 * harming any ant, and breaking a block in {@code #formicary:hive} -- and both funnel
 * through {@link #provoke}. Soldiers inside the radius turn hostile to the offender,
 * workers inside the radius panic, larvae never react.
 *
 * <p>Harvesting resource blocks (Resin Weep, Fungal Bloom / Carpet) is safe by
 * construction: those blocks simply are not in the hive tag, so nothing here fires.
 */
public final class ColonyAnger {
    // -------------------------------------------------------------- tunables --

    /** How far from the offence the colony notices, in blocks. Tunable. */
    public static final double ANGER_RADIUS = 24.0;

    /** How long a provoked soldier stays hostile: 600 ticks = 30 seconds. Tunable. */
    public static final int SOLDIER_ANGER_TICKS = 600;

    /** How long a provoked worker keeps running: 200 ticks = 10 seconds. Tunable. */
    public static final int WORKER_FLEE_TICKS = 200;

    // ------------------------------------------------------------- disguise --

    /**
     * Whether the colony reads this player as one of its own.
     *
     * <p>Every "is this player a valid colony target / does this anger apply" check in
     * the mod routes through here, so M4 has exactly one place to plug the effect in.
     *
     * @return always {@code false} for now -- nobody can be disguised yet.
     */
    public static boolean isDisguised(Player player) {
        // M4: Pheromonal Disguise effect check lands here.
        return false;
    }

    /** A player the colony is willing to turn on: alive, not a spectator, not disguised. */
    public static boolean isValidTarget(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isSpectator() && !isDisguised(player);
    }

    // -------------------------------------------------------------- triggers --

    /** Worker, soldier or larva -- the mobs whose injury the colony answers for. */
    public static boolean isColonyAnt(Entity entity) {
        return entity instanceof WorkerAntEntity
                || entity instanceof SoldierAntEntity
                || entity instanceof LarvaEntity;
    }

    /**
     * The player to blame for a damage source, whether they swung in melee (the source's
     * causing entity) or landed a projectile (the causing entity is still the shooter;
     * the direct entity is the arrow).
     */
    @Nullable
    public static Player offenderOf(@Nullable DamageSource source) {
        if (source == null) {
            return null;
        }
        if (source.getEntity() instanceof Player player) {
            return player;
        }
        if (source.getDirectEntity() instanceof Player player) {
            return player;
        }
        return null;
    }

    /**
     * Raises the alarm at {@code origin}: every soldier within {@link #ANGER_RADIUS} goes
     * hostile to {@code offender} for {@link #SOLDIER_ANGER_TICKS}, every worker within it
     * flees for {@link #WORKER_FLEE_TICKS}. Larvae are untouched -- they never fight.
     */
    public static void provoke(ServerLevel level, Vec3 origin, @Nullable Player offender) {
        if (offender == null || offender.isSpectator()) {
            return;
        }

        // M4: strip disguise here -- provoking the colony blows a disguised player's cover,
        // so the effect has to be removed before the radius sweep marks anyone hostile.

        AABB area = AABB.ofSize(origin, ANGER_RADIUS * 2.0, ANGER_RADIUS * 2.0, ANGER_RADIUS * 2.0);
        double radiusSqr = ANGER_RADIUS * ANGER_RADIUS;

        for (SoldierAntEntity soldier : level.getEntitiesOfClass(SoldierAntEntity.class, area,
                ant -> ant.isAlive() && ant.distanceToSqr(origin) <= radiusSqr)) {
            soldier.angerAt(offender);
        }

        for (WorkerAntEntity worker : level.getEntitiesOfClass(WorkerAntEntity.class, area,
                ant -> ant.isAlive() && ant.distanceToSqr(origin) <= radiusSqr)) {
            worker.startFleeingFrom(offender);
        }
    }

    private ColonyAnger() {
    }
}
