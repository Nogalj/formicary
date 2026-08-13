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
 * loop; this class only supplies the target predicate, a longer retry interval and the
 * linger timer. {@code MoveToBlockGoal.findNearestBlock} searches from {@code y - 1}
 * upward, so a carpet underfoot counts as a valid target.
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
