package com.nogal.formicary.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Play-test round 4, item 7: "tamed worker ants should idle closer to their chest instead of
 * wandering off."
 *
 * <p>The idle stroll is the lowest-value thing a bound worker does and it was the most
 * visible: {@link TamedWorkerAntEntity#bindTo} sets the vanilla home-point restriction to
 * {@code PATROL_RADIUS + 4} -- twenty blocks -- so that {@code MoveTowardsRestrictionGoal}
 * does not yank the worker back while it rounds the far edge of its field, and the plain
 * {@code WaterAvoidingRandomStrollGoal} this class replaces was free to use every one of
 * those twenty blocks between jobs. A worker with nothing to cut therefore spent its idle
 * time as far from its chest as the leash allowed, which reads as "it wandered off" even
 * though it is still, technically, at work.
 *
 * <p>So the leash is left exactly as it is -- it is what the <em>working</em> half of the
 * ant needs -- and only the idle draw is narrowed, to {@link #IDLE_RADIUS} around the bound
 * chest. Follow mode is untouched: with no bound chest this is vanilla
 * {@code WaterAvoidingRandomStrollGoal}, deferred to through {@code super}.
 *
 * <h2>Why the draw is anchored at the chest rather than at the ant</h2>
 *
 * <p>Vanilla's {@code LandRandomPos.getPos} draws a candidate around the <em>mob</em>, which
 * makes "stay near X" a two-case problem: inside the radius you can draw-and-reject, but
 * outside it every candidate is rejected and the mob is stuck. The usual answer,
 * {@code LandRandomPos.getPosTowards(mob, r, y, target)}, does not actually solve it either
 * -- it draws inside a {@code Math.PI / 2} half-arc of the direction to the target (read in
 * {@code RandomPos.generateRandomDirectionWithinRadians}), and a step taken perpendicular to
 * the target is inside that arc while leaving the mob <em>further</em> away than it started.
 *
 * <p>Drawing around the chest instead collapses both cases into one invariant that is true by
 * construction: <b>every position this goal ever returns while bound is within
 * {@link #IDLE_RADIUS} of the bound chest.</b> A worker already inside that ball strolls
 * around inside it; a worker outside it is by definition further from the chest than any
 * candidate can be, so the same draw walks it home. Nothing has to detect which case it is
 * in, and there is no arc geometry to get subtly wrong. It is also what makes the behaviour
 * testable without observing a stroll -- see
 * {@code TamingGameTests.a_bound_worker_only_ever_strolls_near_its_chest}.
 *
 * <p>The candidate filtering mirrors {@code LandRandomPos} itself rather than inventing its
 * own rules: push the candidate up out of solid ground, refuse water and non-zero pathfinding
 * malus ({@code movePosUpOutOfSolid} does both), refuse anything outside the world's build
 * limits or that the navigation calls an unstable destination, and score the survivors with
 * the mob's own {@code getWalkTargetValue} over ten attempts. The restriction check vanilla
 * also makes is left out on purpose and costs nothing: {@code IDLE_RADIUS} is 6 and the
 * restriction is the same chest at radius 20, so every candidate is inside it already.
 */
public class IdleNearChestGoal extends WaterAvoidingRandomStrollGoal {
    /**
     * How far from its bound chest an idle worker will wander, in blocks (play-test round 4,
     * item 7).
     *
     * <p>Six is chosen against the two numbers on either side of it rather than for its own
     * sake. Below it, {@code DepositToChestGoal.REACH_SQR} and {@code HarvestCropsGoal}'s
     * matching reach are both 2 blocks, so anything much tighter would make the idle ball
     * barely wider than "standing at the chest" and the ant would look frozen rather than
     * alive. Above it, the point of the change is that twenty blocks reads as wandering off;
     * six is close enough that the worker is visibly *at* its chest from anywhere a player
     * would be standing to look at it, while still being a real patch of ground to potter
     * around in.
     */
    public static final int IDLE_RADIUS = 6;

    /**
     * How far above or below the chest an idle candidate may be drawn. Matches
     * {@link TamedWorkerAntEntity#PATROL_VERTICAL_REACH}, for the same reason that constant
     * has its value: a terraced garden is a normal place to keep a worker, and an idle ball
     * flattened to the chest's own layer would keep walking it off the terrace it is on.
     */
    private static final int IDLE_VERTICAL_RANGE = 3;

    private final TamedWorkerAntEntity ant;

    public IdleNearChestGoal(TamedWorkerAntEntity ant, double speedModifier) {
        super(ant, speedModifier);
        this.ant = ant;
    }

    /**
     * The one thing this goal changes about vanilla's stroll: where the target comes from.
     * Everything else -- the random interval, the no-action-time gate, {@code start},
     * {@code canContinueToUse}, {@code stop} -- is inherited untouched.
     *
     * <p>Widened from {@code protected} to {@code public} so a GameTest can draw from it
     * directly. That matters more than it sounds: a stroll is chosen at a random interval and
     * walked by the navigation, so <em>observing</em> one in an arena samples a distribution
     * and races a climbing ant against the arena wall (see {@code docs/gotchas/gametest.md}).
     * The decision itself is a pure-enough function of the level and the ant's position, and
     * asserting on fifty draws of it pins the rule exactly.
     *
     * @return a stroll target, or {@code null} if there is nowhere suitable -- in which case
     *         the goal simply does not start this tick. Deliberately <em>not</em> a fallback
     *         to {@code super.getPosition()}: that draws around the ant with no reference to
     *         the chest at all, which is the behaviour being replaced.
     */
    @Nullable
    @Override
    public Vec3 getPosition() {
        BlockPos chest = this.ant.getBoundChest();
        if (chest == null) {
            // Follow mode: the ant belongs wherever its owner is, so vanilla's stroll is
            // exactly right and gets to run unmodified.
            return super.getPosition();
        }
        return drawNear(chest);
    }

    /**
     * A walkable position within {@link #IDLE_RADIUS} of {@code chest}, or {@code null} if ten
     * attempts found none. The radius is checked twice on purpose: once on the raw candidate,
     * and again after {@code movePosUpOutOfSolid} has pushed it up out of the ground, since
     * that push can move it out of the ball.
     */
    @Nullable
    private Vec3 drawNear(BlockPos chest) {
        return RandomPos.generateRandomPos(() -> {
            BlockPos offset =
                    RandomPos.generateRandomDirection(this.ant.getRandom(), IDLE_RADIUS, IDLE_VERTICAL_RANGE);
            BlockPos candidate = chest.offset(offset);
            if (!withinIdleRadius(candidate, chest)) {
                return null;
            }
            BlockPos stood = LandRandomPos.movePosUpOutOfSolid(this.ant, candidate);
            if (stood == null || !withinIdleRadius(stood, chest)) {
                return null;
            }
            return GoalUtils.isOutsideLimits(stood, this.ant)
                    || GoalUtils.isNotStable(this.ant.getNavigation(), stood)
                            ? null
                            : stood;
        }, this.ant::getWalkTargetValue);
    }

    /** The invariant this whole class exists to hold. */
    private static boolean withinIdleRadius(BlockPos pos, BlockPos chest) {
        return pos.distSqr(chest) <= (double) IDLE_RADIUS * IDLE_RADIUS;
    }
}
