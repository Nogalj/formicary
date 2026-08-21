package com.nogal.formicary.entity;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * How long a crop stays off-limits after an approach timeout, in ticks -- about a minute
     * at 20 tps. This is the trade the constant makes explicit: a genuinely-blocked crop (a
     * player's fence, an unreachable ledge) gets retried once a minute, which is cheap for a
     * scan that already runs about once per round trip; a crop that was only transiently
     * blocked (another mob standing in the doorway, a half-built wall) comes back on the very
     * next scan after this window instead of staying locked out for good. Discovered 2026-08-20
     * (commit 0e595bb): without an expiry, {@link #canUse()} re-scans the instant the timeout
     * stops this goal, {@link CropScanner} picks the identical unreachable crop again, and the
     * worker never harvests anything reachable -- nor collects any drops, since harvest
     * outranks {@link CollectDroppedItemsGoal} at goal priority.
     */
    private static final int BLACKLIST_DURATION_TICKS = 1200;

    /** Defensive cap on {@link #blacklistedUntil} so a worker that spends a long life failing
     * approaches (a field that slowly gets walled in around it) cannot grow the map without
     * bound; the oldest entry is dropped to make room. 32 is generous against a single ant's
     * realistic failure rate -- the field it patrols is bounded by {@link
     * TamedWorkerAntEntity#PATROL_RADIUS} and most crops in it are reachable. */
    private static final int BLACKLIST_MAX_SIZE = 32;

    private final TamedWorkerAntEntity ant;
    private final double speedModifier;
    private final CropScanner scanner;

    /**
     * Crop positions this goal has given up approaching, mapped to the game time they become
     * eligible again. Per-instance (per-ant) rather than shared: a crop unreachable to one
     * worker's patrol may sit right next to another's chest.
     *
     * <p>Deliberately not saved across a relog -- {@link #target} and {@link #approachTicks}
     * already are not, and a reload gives every goal a fresh start regardless (the {@code
     * restrictTo} gotcha in {@code docs/gotchas/entity-ai.md} is the same shape: some state
     * costs more to lose than this, and even that only gets saved because
     * {@link TamedWorkerAntEntity} chooses to re-apply it in {@code readAdditionalSaveData}).
     * A relog also resets the far cheaper thing this blacklist protects against -- a worker
     * that was mid-approach loses that progress too -- so a permanently-blocked crop is simply
     * rediscovered and re-blacklisted on the worker's next attempt after loading, at the same
     * one-minute cost paid the first time.
     */
    private final Map<BlockPos, Long> blacklistedUntil = new LinkedHashMap<>();

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
     * own {@code RETRY_COOLDOWN_TICKS}: after every delivery {@code DepositToChestGoal} sat
     * out 100 {@code canUse} calls, which is about 200 ticks, because {@code
     * Mob.serverAiStep} only runs {@code goalSelector.tick()} on alternating ticks. Nothing
     * outranked the harvest during that window, so it cut, and cut, and cut. Reproduced by
     * {@code a_bound_worker_never_carries_two_crops_at_once}, which caught the pack holding
     * three crops at once.
     *
     * <p><b>Round 4 (item 8) closed that window from the other end</b>, and the history above
     * is kept rather than rewritten because it is what this gate exists for. The cooldown is
     * now armed only when the pack is still non-empty after a trip -- failure backoff, as its
     * name always claimed -- so there is no longer a routine 200-tick stretch in which nothing
     * outranks the harvest. That does <em>not</em> make this gate redundant: the window can
     * still open on a genuinely failed delivery (chest full, chest broken, approach timed
     * out), and the one-crop-per-trip rule is a rule about what the worker carries, not about
     * how long the deposit goal happens to be asleep.
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
        this.target = this.scanner.scan(this.ant.level(), anchor, this::isBlacklisted);
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
            // Reaching it proves it reachable, whatever an earlier attempt on this same
            // position may have concluded -- clear it before anything else, not just on a
            // successful harvest() below.
            this.blacklistedUntil.remove(this.target);
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

    /**
     * The give-up path: {@link #canContinueToUse()} already stopped returning true once
     * {@link #approachTicks} hit {@link #APPROACH_TIMEOUT_TICKS}, and by the time the goal
     * selector calls this the reason is exactly that -- a still-non-null {@link #target} means
     * the goal did not end via the successful-harvest branch in {@link #tick()}, which already
     * nulled it out (and cleared any blacklist entry) before this could run. Blacklisting here
     * rather than at the timeout check itself keeps the bookkeeping in the one place already
     * responsible for "the goal is ending", instead of mutating state inside a boolean predicate.
     */
    @Override
    public void stop() {
        if (this.target != null && this.approachTicks >= APPROACH_TIMEOUT_TICKS) {
            this.blacklist(this.target);
        }
        this.target = null;
        this.ant.getNavigation().stop();
    }

    /** Whether {@code pos} is still inside its post-timeout cooldown window. */
    private boolean isBlacklisted(BlockPos pos) {
        Long expiry = this.blacklistedUntil.get(pos);
        if (expiry == null) {
            return false;
        }
        if (!(this.ant.level() instanceof ServerLevel level) || level.getGameTime() >= expiry) {
            // Lazily forget an expired entry rather than waiting for it to age out of the
            // size cap -- keeps the map's real size honest for that cap.
            this.blacklistedUntil.remove(pos);
            return false;
        }
        return true;
    }

    /** Records {@code pos} as unreachable for {@link #BLACKLIST_DURATION_TICKS}. */
    private void blacklist(BlockPos pos) {
        if (!(this.ant.level() instanceof ServerLevel level)) {
            return;
        }
        this.blacklistedUntil.put(pos, level.getGameTime() + BLACKLIST_DURATION_TICKS);
        if (this.blacklistedUntil.size() > BLACKLIST_MAX_SIZE) {
            // LinkedHashMap in insertion order: the first key the iterator yields is the
            // oldest entry still present.
            Iterator<BlockPos> oldest = this.blacklistedUntil.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }
}
