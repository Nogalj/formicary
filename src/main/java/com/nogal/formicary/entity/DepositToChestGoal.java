package com.nogal.formicary.entity;

import java.util.EnumSet;

import com.nogal.formicary.advancement.ModCriteriaTriggers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/**
 * Work mode's delivery half (spec section 4): carry the pack back to the bound chest and
 * empty it.
 *
 * <p>Runs at a higher priority than {@link HarvestCropsGoal}, and Ep2 makes that ordering
 * the whole shape of work mode: the only precondition is <b>anything in the pack at all</b>,
 * so the act after a harvest is always the trip home. One crop out, one trip back. The
 * three-trigger arrangement it replaces (pack full, or a field-gone-quiet counter, or
 * elapsed time) let a worker clear a whole field before it ever walked anywhere, which made
 * one ant out-produce a real farm -- the nerf is the point, not a side effect.
 *
 * <p>A missing or blocked chest is not a reason to drop anything. The worker keeps the load
 * and idles by the anchor, and this goal simply cannot start again until a container is
 * back -- which is what makes "the chest was broken while I was out" recoverable rather
 * than expensive.
 *
 * <h2>Play-test round 5, item 1: the approach timeout is a recall, not a give-up</h2>
 *
 * <p>Logan's fifth play-test produced a screenshot of a loaded worker wedged high on a tree
 * trunk with its harvest still in the pack. {@link AntClimbing}'s round-5 gate stops that
 * particular wedge from forming, but the class of failure it belongs to does not have a
 * finite list of causes: any terrain a walk cannot cross -- a fence line, a ravine, a chest
 * moved behind a wall, a player-built roof -- ends the same way, with a worker holding
 * produce it can never hand over. Delivery is the one step of the shuttle a player is
 * actually waiting on, so it stops depending on the walk at all.
 *
 * <p>When {@link #APPROACH_TIMEOUT_TICKS} runs out with a loaded pack and the chest still out
 * of reach, the worker <b>teleports to a safe stand beside the chest</b> and deposits on that
 * same tick. The search mirrors vanilla's pet teleport (read in {@code TamableAnimal}'s
 * {@code maybeTeleportTo} / {@code canTeleportTo} cluster in {@code reference/}): a candidate
 * is only taken when {@code WalkNodeEvaluator.getPathTypeStatic} calls it {@code WALKABLE}
 * and the ant's own bounding box fits there without collision. Nothing is teleported into.
 *
 * <p>It fires <b>only</b> at the timeout boundary, never while a path is still making
 * progress, and the round-4 cooldown semantics are untouched: a recall that ends in a
 * successful deposit empties the pack and therefore arms nothing, exactly like a walked
 * delivery, while a recall that finds nowhere to stand leaves the load in the pack and takes
 * the ordinary failure backoff.
 */
public class DepositToChestGoal extends Goal {
    /** How close the worker has to be to reach into the chest: 2 blocks, squared. */
    private static final double REACH_SQR = 4.0;

    /**
     * How long the worker walks at a chest before the trip stops being a walk. Round 5 turned
     * this from a give-up into the trigger for {@link #recallBeside}: the load gets delivered
     * either way, and this only decides whether the ant's legs or the recall did it.
     */
    private static final int APPROACH_TIMEOUT_TICKS = 300;

    /**
     * Where a recall looks for somewhere to stand, in order: the four faces of the chest
     * first, then its four diagonals. Nearest-first, and deterministic rather than the random
     * ten-attempt scatter vanilla's pet teleport uses -- there are only eight candidates per
     * level, so sampling them adds nothing but the chance of missing one.
     */
    private static final int[][] RECALL_OFFSETS = {
        { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
    };

    /**
     * The heights those offsets are tried at, relative to the chest: level with it, then one
     * up, then one down. Same spirit as vanilla's {@code nextIntBetweenInclusive(-1, 1)} on
     * the vertical -- a chest on a terrace still has a stand beside it -- but ordered so the
     * nearest is taken first.
     */
    private static final int[] RECALL_LEVELS = { 0, 1, -1 };

    /** Ticks between path recalculations while walking to the chest. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    /**
     * How long to wait before retrying after a <b>failed</b> trip (chest gone, chest full,
     * approach timed out), counted in {@code canUse} calls rather than ticks -- and
     * {@code Mob.serverAiStep} only polls a non-running goal on alternating ticks, so 100 of
     * these is about 200 real ticks.
     *
     * <p>Failure backoff only, and play-test round 4 (item 8) is what made that stop being a
     * documentation detail: arming this on a <em>successful</em> delivery too parked the
     * worker for ~10 s after every single crop, which read as "they stand around before
     * dropping it off".
     */
    private static final int RETRY_COOLDOWN_TICKS = 100;

    private final TamedWorkerAntEntity ant;
    private final double speedModifier;

    private int approachTicks;
    private int repathTicks;
    private int cooldown;

    public DepositToChestGoal(TamedWorkerAntEntity ant, double speedModifier) {
        this.ant = ant;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        BlockPos chest = this.ant.getBoundChest();
        if (chest == null || this.ant.getPack().isEmpty()) {
            return false;
        }
        return this.ant.getBoundContainer(this.ant.level()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.ant.isBound()
                && !this.ant.getPack().isEmpty()
                && this.approachTicks < APPROACH_TIMEOUT_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.approachTicks = 0;
        this.repathTicks = 0;
        this.moveToChest();
    }

    @Override
    public void tick() {
        BlockPos chest = this.ant.getBoundChest();
        if (chest == null) {
            return;
        }
        this.approachTicks++;
        this.ant.getLookControl().setLookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);

        // Round 5: the moment the walk is out of time, stop relying on it. Guarded on all
        // three conditions rather than on the clock alone -- a trip that has already emptied
        // the pack or is already in reach has nothing to recall, and this must never fire
        // while a path is still making progress.
        if (this.approachTicks >= APPROACH_TIMEOUT_TICKS
                && !this.ant.getPack().isEmpty()
                && this.ant.distanceToSqr(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5) > REACH_SQR) {
            this.recallBeside(chest);
        }

        if (this.ant.distanceToSqr(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5) <= REACH_SQR) {
            Container container = this.ant.getBoundContainer(this.ant.level());
            if (container != null && this.ant.depositInto(container)) {
                this.ant.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.2F);
                // M8: "first tamed harvest" advancement. getOwner() resolves through
                // level().getPlayerByUUID -- null for a GameTest mock player (see
                // FirstHarvestTrigger's javadoc), but a real ServerPlayer in play, exactly
                // the check TamableAnimal itself makes before sending its death message.
                LivingEntity owner = this.ant.getOwner();
                if (owner instanceof ServerPlayer serverPlayer) {
                    ModCriteriaTriggers.FIRST_HARVEST.get().trigger(serverPlayer);
                }
            }
            // Whatever is left over (full chest, chest gone) stays in the pack: back off
            // and try again later rather than dumping it on the floor. Play-test round 4,
            // item 8 -- the backoff is armed ONLY when something is actually left over.
            // Arming it on every arrival, success included, is what made the worker stand
            // around for ~10 s with an empty pack after each delivery: 100 canUse calls is
            // about 200 real ticks, because Mob.serverAiStep polls a non-running goal on
            // alternating ticks. An emptied pack is a finished trip, not a failed one.
            this.cooldown = this.ant.getPack().isEmpty() ? 0 : RETRY_COOLDOWN_TICKS;
            this.approachTicks = APPROACH_TIMEOUT_TICKS;
            this.ant.getNavigation().stop();
            return;
        }

        if (--this.repathTicks <= 0) {
            this.repathTicks = REPATH_INTERVAL_TICKS;
            this.moveToChest();
        }
    }

    /**
     * The backstop for every way this goal can end that {@link #tick()}'s arrival branch does
     * not cover -- the chest being broken mid-trip, the ant being unbound, or a recall that
     * found nowhere to stand. All of those leave the load undelivered, which is exactly when
     * the retry cooldown is the right answer.
     *
     * <p>Play-test round 4, item 8: an <em>empty</em> pack arms nothing. A trip that ended
     * because there is nothing left to carry has already succeeded, and the next harvest
     * should be free to start on the very next tick.
     */
    @Override
    public void stop() {
        this.ant.getNavigation().stop();
        if (this.cooldown <= 0 && !this.ant.getPack().isEmpty()) {
            this.cooldown = RETRY_COOLDOWN_TICKS;
        }
    }

    private void moveToChest() {
        BlockPos chest = this.ant.getBoundChest();
        if (chest != null) {
            this.ant.getNavigation().moveTo(chest.getX() + 0.5, chest.getY() + 1.0, chest.getZ() + 0.5,
                    this.speedModifier);
        }
    }

    /**
     * Teleports the worker to the first safe stand beside {@code chest} that is also inside
     * {@link #REACH_SQR} of it, so {@link #tick()}'s arrival branch fires on this same tick.
     *
     * <p>The reach check is not redundant with the offsets: measured from the ant's feet to
     * the chest's centre, a diagonal one level down is 4.25 away and would leave the worker
     * standing next to a chest it still cannot open -- a slower version of the bug this
     * whole mechanism exists to remove. Every candidate is therefore checked against the
     * same expression the arrival branch uses rather than trusted for being adjacent.
     *
     * @return whether a stand was found and taken
     */
    private boolean recallBeside(BlockPos chest) {
        for (int level : RECALL_LEVELS) {
            for (int[] offset : RECALL_OFFSETS) {
                BlockPos stand = chest.offset(offset[0], level, offset[1]);
                if (!this.isSafeStand(stand)) {
                    continue;
                }
                double x = stand.getX() + 0.5;
                double y = stand.getY();
                double z = stand.getZ() + 0.5;
                double dx = x - (chest.getX() + 0.5);
                double dy = y - (chest.getY() + 0.5);
                double dz = z - (chest.getZ() + 0.5);
                if (dx * dx + dy * dy + dz * dz > REACH_SQR) {
                    continue;
                }
                this.ant.moveTo(x, y, z, this.ant.getYRot(), this.ant.getXRot());
                // A wedged ant is usually mid-shove or mid-climb; Entity.moveTo resets the
                // position but not the velocity (banked in docs/gotchas/gametest.md), so
                // without this the worker arrives still travelling in whatever direction had
                // it pinned. Fall distance goes with it: the recall is a teleport, not a drop.
                this.ant.setDeltaMovement(Vec3.ZERO);
                this.ant.resetFallDistance();
                this.ant.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the worker could stand at {@code pos} -- vanilla's pet-teleport test, which is
     * {@code WalkNodeEvaluator.getPathTypeStatic} answering {@code WALKABLE} (open block,
     * something solid under it) plus the mob's own bounding box fitting there.
     */
    private boolean isSafeStand(BlockPos pos) {
        if (WalkNodeEvaluator.getPathTypeStatic(this.ant, pos) != PathType.WALKABLE) {
            return false;
        }
        BlockPos shift = pos.subtract(this.ant.blockPosition());
        return this.ant.level().noCollision(this.ant, this.ant.getBoundingBox().move(shift));
    }
}
