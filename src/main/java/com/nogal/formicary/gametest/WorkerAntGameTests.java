package com.nogal.formicary.gametest;

import javax.annotation.Nullable;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.RelocateItemGoal;
import com.nogal.formicary.entity.WorkerAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the wild worker's ambient behaviour (spec section 3) -- currently
 * the item-relocation errand ({@link RelocateItemGoal}). Follows {@code TamingGameTests}'
 * established conventions; see its class javadoc for the two harness limits.
 */
@GameTestHolder(Formicary.MODID)
public class WorkerAntGameTests {
    /** First standable layer of a template: {@code absolutePos(0,0,0)} is the structure block. */
    private static final int STAND_Y = 2;

    /**
     * The wild-worker twin of
     * {@code TamingGameTests#a_drop_just_outside_pickup_range_is_still_collected} -- the same
     * 2026-08-18 pathing deadlock, the same deterministic geometry, a different goal.
     *
     * <p>{@link RelocateItemGoal} approaches with {@code moveTo(Entity, speed)} and demands a
     * 1.5-block pickup, the exact shape whose gap deadlocked {@code CollectDroppedItemsGoal}:
     * the navigation legitimately calls "arrived" up to ~2.2 blocks out, and once it is done
     * no repath ever moves the ant again. Here the deadlock was never filed as a bug because
     * its consequence is invisible in play -- the errand burns its 200-tick approach timeout,
     * the 30-60s cooldown swallows the retry, and the ant just looks like it changed its
     * mind about a stray item.
     *
     * <p>Geometry as in the twin: the zero-velocity {@code ItemEntity} overload plus
     * hand-placed positions put the ant and the drop 1.70 apart in adjacent blocks every
     * single run -- outside pickup range, inside the block the navigation is already
     * satisfied by. The randomly seeded errand cooldown is collapsed through
     * {@link RelocateItemGoal#skipCooldown()} so the errand starts before the stroll goal
     * can walk the ant off the hand-placed spot. Fails on every run without the
     * last-stretch nudge in {@link RelocateItemGoal#tick()}; passes on every run with it.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void a_drop_the_path_stops_short_of_is_still_relocated(GameTestHelper helper) {
        WorkerAntEntity worker = helper.spawn(ModEntities.WORKER_ANT.get(), new BlockPos(1, STAND_Y, 2));
        RelocateItemGoal errand = relocateGoalOf(worker);
        helper.assertTrue(errand != null, "the wild worker should register a RelocateItemGoal");
        errand.skipCooldown();

        // The exact geometry the deadlock trace captured: the ant near the west edge of its
        // block, the drop near the east edge of the block next door. 1.70 apart -- outside
        // PICKUP_RANGE (1.5), inside the block the navigation already calls "arrived", so
        // the path is satisfied by the ant's own block and moveTo never moves it again.
        BlockPos cell = helper.absolutePos(new BlockPos(2, STAND_Y, 2));
        double antX = cell.getX() + 0.15;
        double z = cell.getZ() + 0.5;
        worker.setPos(antX, cell.getY(), z);

        // Explicit zero velocity: the ordinary ItemEntity constructor kicks the drop a
        // random +-0.1, which would put the geometry back on a dice roll.
        ItemEntity drop = new ItemEntity(helper.getLevel(), antX + 1.7, cell.getY(), z,
                new ItemStack(Items.STICK, 4), 0.0, 0.0, 0.0);
        drop.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(drop);

        helper.succeedWhen(() -> helper.assertTrue(worker.getCarriedItem().is(Items.STICK),
                "the worker should have closed the last stretch and hoisted the drop"));
    }

    /** The worker's registered {@link RelocateItemGoal}, or null if there is none. */
    @Nullable
    private static RelocateItemGoal relocateGoalOf(WorkerAntEntity worker) {
        for (WrappedGoal wrapped : worker.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof RelocateItemGoal goal) {
                return goal;
            }
        }
        return null;
    }
}
