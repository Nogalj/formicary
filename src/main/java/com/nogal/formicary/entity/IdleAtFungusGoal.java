package com.nogal.formicary.entity;

import com.nogal.formicary.block.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;

/**
 * Ambient colony job: walk over to a nearby Fungal Bloom or Fungal Carpet and loiter
 * there for a few seconds before going back to wandering.
 *
 * <p>Extends vanilla's {@link MoveToBlockGoal}, which already owns the search + repath
 * loop; this class only supplies the target predicate, a longer retry interval, the
 * linger timer -- and the two overrides below. {@code MoveToBlockGoal.findNearestBlock}
 * searches from {@code y - 1} upward, so a carpet underfoot counts as a valid target.
 *
 * <h2>Play-test round 5, item 4: "ants jump when trying to path find through a fungal bloom"</h2>
 *
 * <p><b>Neither block was at fault.</b> Both were cleared experimentally before anything was
 * touched: a worker walking a solid row of Fungal Blooms on flat soil never leaves the ground
 * (rise 0.0000, 0 airborne ticks over a six-block walk) and {@code WalkNodeEvaluator}
 * derives {@code WALKABLE} over a bloom exactly as it does over bare air -- a {@code
 * BushBlock} with {@code noCollission} is {@code PathType.OPEN}, same as a vanilla flower.
 * The Fungal Carpet, the obvious suspect for a hop because it does have collision, turned out
 * to be a plain 1/16-block step (rise 0.0625, 0 airborne ticks); {@code Entity.collide}
 * absorbs it entirely in its step-up branch, so it does not even register as a horizontal
 * collision.
 *
 * <p><b>The jump was this goal's inherited target geometry.</b> {@link MoveToBlockGoal} is
 * written for targets a mob stands <em>on</em>: {@code moveMobToBlock} paths to
 * {@code blockPos.getY() + 1} and {@code getMoveToTarget} returns {@code blockPos.above()},
 * which for a solid block is the air above it -- correct. A fungus is not that. It occupies
 * the very air layer the ant walks through, so the inherited version aims a full block into
 * the sky above it, and then:
 * <ul>
 *   <li>{@code acceptedDistance()} is 1.0, measured to the centre of that airborne block.
 *       An ant standing squarely on the fungus is 1.5 away from it, so
 *       {@code isReachedTarget()} is <b>never</b> true and the goal keeps trying for its full
 *       1200-tick budget.</li>
 *   <li>With the ant already there the path is trivially finished, so
 *       {@code WallClimberNavigation.tick()} takes its no-path branch and pushes the move
 *       control at the raw target -- one block up.</li>
 *   <li>{@code MoveControl.tick()} then meets its own first jump condition exactly:
 *       {@code wantedY - getY()} is 1.0, over the ant's 0.6 step height, while the horizontal
 *       distance squared is 0.5, under its {@code max(1.0F, getBbWidth())} of 1.0. So it
 *       calls {@code jumpControl.jump()} -- every repath, forever.</li>
 * </ul>
 * Measured on a goal-stripped wild worker: a 1.25-block ballistic rise, 20 airborne ticks in
 * a 110-tick window, repeating on the goal's 40-tick repath cadence. Exactly what Logan saw.
 *
 * <p>The fix is to say what this goal actually means: <b>walk into the fungus, not onto it.</b>
 * Both halves of the inherited geometry are re-aimed at {@code blockPos} itself, which is a
 * walkable node, which the ant reaches, which satisfies {@code acceptedDistance} at 0.5 and
 * lets the linger timer start. No block property is touched and no navigation is
 * special-cased, so nothing else in the mod changes behaviour.
 */
public class IdleAtFungusGoal extends MoveToBlockGoal {
    private static final int LINGER_TICKS = 100;   // ~5 seconds
    private static final int RETRY_MIN_TICKS = 300;
    private static final int RETRY_SPREAD_TICKS = 300;

    private int lingerTicks;

    public IdleAtFungusGoal(PathfinderMob mob, double speedModifier, int searchRange) {
        super(mob, speedModifier, searchRange);
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.FUNGAL_BLOOM.get())
                || level.getBlockState(pos).is(ModBlocks.FUNGAL_CARPET.get());
    }

    @Override
    protected int nextStartTick(PathfinderMob creature) {
        return reducedTickDelay(RETRY_MIN_TICKS + creature.getRandom().nextInt(RETRY_SPREAD_TICKS));
    }

    /**
     * The fungus block itself, not the air above it. Vanilla's {@code blockPos.above()} is
     * right for a target you stand on and wrong for one you stand in -- see the class javadoc
     * for the hop it produces.
     */
    @Override
    protected BlockPos getMoveToTarget() {
        return this.blockPos;
    }

    /**
     * The other half of the same correction: {@code start()} routes through here rather than
     * through {@link #getMoveToTarget()}, and vanilla's version hardcodes the {@code + 1}.
     */
    @Override
    protected void moveMobToBlock() {
        this.mob.getNavigation().moveTo(this.blockPos.getX() + 0.5, this.blockPos.getY(),
                this.blockPos.getZ() + 0.5, this.speedModifier);
    }

    @Override
    public void start() {
        super.start();
        this.lingerTicks = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.lingerTicks < LINGER_TICKS && super.canContinueToUse();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isReachedTarget()) {
            this.lingerTicks++;
            this.mob.getNavigation().stop();
            this.mob.getLookControl().setLookAt(Vec3.atCenterOf(this.blockPos));
        }
    }
}
