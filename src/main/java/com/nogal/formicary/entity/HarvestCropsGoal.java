package com.nogal.formicary.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Work mode's cutting half (spec section 4): find the nearest ripe crop inside the patrol
 * radius, walk to it, harvest and replant it -- once. The load then goes home
 * ({@link DepositToChestGoal} outranks this goal and needs nothing but a non-empty pack),
 * and the next crop is chosen on the next trip -- this goal will not even look at the field
 * again until the pack is empty (see {@link #canUse()}).
 *
 * <p>{@link CropScanner} sweeps the whole patrol area on every call rather than a slice of
 * it. That is affordable because of where the call happens: the scan runs inside
 * {@link #canUse()}, behind the empty-pack gate, so a worker that is carrying anything at
 * all answers without scanning -- and once this goal or the higher-priority deposit goal is
 * running {@code GoalSelector} stops asking altogether. A full sweep is paid for about once
 * per round trip instead of continuously.
 */
public class HarvestCropsGoal extends Goal {
    /** How close the worker has to be before it can cut: 2 blocks, squared. */
    private static final double REACH_SQR = 4.0;

    /** Give up on an unreachable crop rather than standing on a wall forever. */
    private static final int APPROACH_TIMEOUT_TICKS = 200;

    /** Ticks between path recalculations while walking to the crop. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final TamedWorkerAntEntity ant;
    private final double speedModifier;
    private final CropScanner scanner;

    private int approachTicks;
    private int repathTicks;

    @Nullable
    private BlockPos target;

    public HarvestCropsGoal(TamedWorkerAntEntity ant, double speedModifier) {
        this.ant = ant;
        this.speedModifier = speedModifier;
        this.scanner = new CropScanner(TamedWorkerAntEntity.PATROL_RADIUS,
                TamedWorkerAntEntity.PATROL_VERTICAL_REACH);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** The scanner backing this goal -- exposed so its choice can be asserted in a test. */
    public CropScanner getScanner() {
        return this.scanner;
    }

    /**
     * A crop is only worth cutting with <b>nothing in the pack</b> -- that condition, not the
     * goal ordering, is what actually makes the shuttle one crop per trip.
     *
     * <p>Play-test round 2, spec item 1. Round 1 wrote the rule on the deposit side alone
     * (empty this before cutting more, and outrank the harvest so the trip preempts it) and
     * left this goal gated on {@code isPackFull} -- nine slots' worth of slack. In play the
     * worker still swept the whole field, and the window it did it in was the deposit goal's
     * own {@code RETRY_COOLDOWN_TICKS}: after every delivery {@code DepositToChestGoal} sits
     * out 100 {@code canUse} calls, which is about 200 ticks, because {@code
     * Mob.serverAiStep} only runs {@code goalSelector.tick()} on alternating ticks. Nothing
     * outranked the harvest during that window, so it cut, and cut, and cut. Reproduced by
     * {@code a_bound_worker_never_carries_two_crops_at_once}, which caught the pack holding
     * three crops at once.
     *
     * <p>Deliberately {@code isEmpty} rather than a relaxed "no produce" test: a load picked
     * up off the ground ({@link CollectDroppedItemsGoal}) is a load to deliver too, and the
     * one thing the worker must never do is stack errands. The pack-full check this replaces
     * is now redundant -- an empty pack is never a full one.
     */
    @Override
    public boolean canUse() {
        BlockPos anchor = this.ant.getBoundChest();
        if (anchor == null || !this.ant.getPack().isEmpty() || !(this.ant.level() instanceof ServerLevel)) {
            return false;
        }
        this.target = this.scanner.scan(this.ant.level(), anchor);
        return this.target != null;
    }

    /**
     * The same rule, held while the goal runs: the tick that cuts a crop fills the pack, and
     * that alone ends the run. {@link #tick()} also clears {@link #target} at that moment, so
     * either condition would stop it -- both are stated because the rule is "carrying
     * anything means stop", not "one crop happens to null the target".
     */
    @Override
    public boolean canContinueToUse() {
        return this.target != null
                && this.ant.isBound()
                && this.ant.getPack().isEmpty()
                && this.approachTicks < APPROACH_TIMEOUT_TICKS
                && CropHarvest.isHarvestable(this.ant.level().getBlockState(this.target));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.approachTicks = 0;
        this.repathTicks = 0;
        if (this.target != null) {
            this.ant.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(),
                    this.target.getZ() + 0.5, this.speedModifier);
        }
    }

    @Override
    public void tick() {
        if (this.target == null || !(this.ant.level() instanceof ServerLevel level)) {
            return;
        }
        this.approachTicks++;
        this.ant.getLookControl().setLookAt(this.target.getX() + 0.5, this.target.getY() + 0.5,
                this.target.getZ() + 0.5);

        if (this.ant.distanceToSqr(this.target.getX() + 0.5, this.target.getY() + 0.5,
                this.target.getZ() + 0.5) <= REACH_SQR) {
            this.ant.harvest(level, this.target);
            this.target = null;
            this.ant.getNavigation().stop();
            return;
        }

        if (--this.repathTicks <= 0) {
            this.repathTicks = REPATH_INTERVAL_TICKS;
            this.ant.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(),
                    this.target.getZ() + 0.5, this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.ant.getNavigation().stop();
    }
}
