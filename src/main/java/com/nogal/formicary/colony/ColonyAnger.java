package com.nogal.formicary.colony;

import javax.annotation.Nullable;

import com.nogal.formicary.effect.ModMobEffects;
import com.nogal.formicary.entity.LarvaEntity;
import com.nogal.formicary.entity.QueenAntEntity;
import com.nogal.formicary.entity.SoldierAntEntity;
import com.nogal.formicary.entity.TamedAnt;
import com.nogal.formicary.entity.WorkerAntEntity;
import com.nogal.formicary.worldgen.ModBiomeTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
     * @return {@code true} while {@link ModMobEffects#PHEROMONAL_DISGUISE} is active.
     */
    public static boolean isDisguised(Player player) {
        return player.hasEffect(ModMobEffects.PHEROMONAL_DISGUISE);
    }

    /** A player the colony is willing to turn on: alive, not a spectator, not disguised. */
    public static boolean isValidTarget(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isSpectator() && !isDisguised(player);
    }

    /**
     * Whether a <em>wild</em> colony ant may attack {@code target} <em>right now</em> --
     * the continuation-layer half of the disguise rule.
     *
     * <p>Every target goal in this mod already refuses to <em>acquire</em> a disguised
     * player, but acquisition is not where a chase lives. Vanilla's
     * {@code TargetGoal#canContinueToUse} never re-runs the goal's own
     * {@code TargetingConditions} predicate: it only re-checks {@code mob.canAttack(target)},
     * team, distance and line of sight. So a soldier already mid-chase kept attacking a
     * player who had just become disguised -- which is exactly what "killing the queen
     * didn't give me the effect that makes the ants not hostile" looked like from inside
     * the fight.
     *
     * <p>Wiring this into the ants' {@code canAttack} override makes the disguise
     * authoritative at both layers at once, because {@code canAttack} is read by
     * {@code TargetGoal#canContinueToUse} <em>and</em> by
     * {@code TargetingConditions#test}. It deliberately upgrades the brewed potion too: the
     * Pheromonal Disguise now breaks an existing aggro rather than only preventing a new
     * one. The abuse case that guards is unchanged -- {@link #provoke} strips the effect
     * before anything else happens, so a disguise buys a disengage, never a free hit.
     */
    public static boolean colonyMayAttack(LivingEntity target) {
        return !(target instanceof Player player) || !isDisguised(player);
    }

    // -------------------------------------------------------------- triggers --

    /**
     * Worker, soldier or larva -- the <em>wild</em> mobs whose injury the colony answers
     * for.
     *
     * <p>Tamed ants (M6) are outside this hierarchy entirely, which is what delivers the
     * spec's "tamed ants' fights never anger the colony or strip your disguise", by
     * construction rather than by a special case:
     * <ul>
     *   <li>A tamed ant taking damage -- including from its own owner -- is not a colony
     *       ant, so {@code ColonyAngerEvents.onColonyAntHurt} returns before
     *       {@link #provoke} is ever reached.</li>
     *   <li>A tamed ant <em>dealing</em> damage to a wild ant does reach that handler, but
     *       {@link #offenderOf} resolves only a {@code Player} and the attacker here is a
     *       mob, so there is no offender to raise the alarm against -- and with no
     *       {@code provoke} call there is no disguise strip either.</li>
     * </ul>
     * What wild soldiers do about tamed ants is the one part that is a deliberate rule
     * rather than a consequence: see {@code entity.TamedAntTargetGoal}.
     */
    public static boolean isColonyAnt(Entity entity) {
        if (entity instanceof SoldierAntEntity soldier) {
            // A soldier summoned by a Pheromone Horn (M7) is the player's, not the colony's.
            // Excluding it here is what stops the summoner's own stray swing at their ally
            // -- or a wild ant chewing on it -- from raising the alarm and stripping the
            // summoner's disguise.
            return !soldier.isAllied();
        }
        return entity instanceof WorkerAntEntity
                || entity instanceof LarvaEntity
                || entity instanceof QueenAntEntity;
    }

    /** Whether {@code entity} is one of the player's own ants rather than the colony's. */
    public static boolean isTamedAnt(Entity entity) {
        return entity instanceof TamedAnt;
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
        if (isDisguised(offender)) {
            offender.removeEffect(ModMobEffects.PHEROMONAL_DISGUISE);
        }

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

    // -------------------------------------------------------- deep-tier hostility --

    /**
     * The on-sight gate for the Nurseries and Royal Depths (spec section 3): "the deep
     * colony tolerates no strangers" -- a soldier standing in a
     * {@code #formicary:deep_colony} biome doesn't wait to be provoked, it targets the
     * nearest stranger the moment it sees one.
     *
     * <p>Split into {@link #isDeepColonyBiome} (world state) and
     * {@link #isValidDeepTierTarget} (player state) because a GameTest can only ever
     * exercise the player half directly: {@code GameTestServer} bakes
     * {@code WorldPresets.FLAT} into a deliberately empty {@code LevelStem} registry and
     * never loads this mod's dimension (see the banked {@code GameTestServer} rule in
     * CLAUDE.md), so no GameTest arena is ever actually in a deep-colony biome. The real
     * biome half is exercised by {@code runServer} instead.
     */
    public static boolean isDeepTierHostileAt(ServerLevel level, BlockPos pos, Player player) {
        return isDeepColonyBiome(level, pos) && isValidDeepTierTarget(player);
    }

    /** Whether {@code pos} sits in a deep-tier biome ({@code #formicary:deep_colony}). */
    public static boolean isDeepColonyBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(ModBiomeTags.DEEP_COLONY);
    }

    /**
     * A player the deep tiers are willing to turn on: everything {@link #isValidTarget}
     * requires, plus not creative. Unlike provoked colony anger, deep-tier hostility fires
     * with zero provocation, so a creative-mode player just standing there (building,
     * testing) is deliberately exempt -- provoked anger draws no such line.
     */
    public static boolean isValidDeepTierTarget(@Nullable Player player) {
        return isValidTarget(player) && !player.isCreative();
    }

    private ColonyAnger() {
    }
}
