package com.nogal.formicary.entity;

import java.util.EnumSet;

import com.nogal.formicary.advancement.ModCriteriaTriggers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

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
 */
public class DepositToChestGoal extends Goal {
    /** How close the worker has to be to reach into the chest: 2 blocks, squared. */
    private static final double REACH_SQR = 4.0;

    /** Give up on an unreachable chest rather than shoving at a wall forever. */
    private static final int APPROACH_TIMEOUT_TICKS = 300;

    /** Ticks between path recalculations while walking to the chest. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    /** How long to wait before retrying after a failed trip (chest gone, chest full). */
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
            // and try again later rather than dumping it on the floor.
            this.cooldown = RETRY_COOLDOWN_TICKS;
            this.approachTicks = APPROACH_TIMEOUT_TICKS;
            this.ant.getNavigation().stop();
            return;
        }

        if (--this.repathTicks <= 0) {
            this.repathTicks = REPATH_INTERVAL_TICKS;
            this.moveToChest();
        }
    }

    @Override
    public void stop() {
        this.ant.getNavigation().stop();
        if (this.cooldown <= 0) {
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
}
