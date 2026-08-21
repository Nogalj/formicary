package com.nogal.formicary.gametest;

import javax.annotation.Nullable;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.entity.IdleAtFungusGoal;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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

    /** How long the fungus-approach tests watch. Comfortably longer than a three-block walk. */
    private static final int FUNGUS_WATCH_TICKS = 140;

    /** Search radius handed to the {@link IdleAtFungusGoal} under test -- the wild worker's own. */
    private static final int FUNGUS_SEARCH_RANGE = 8;

    /**
     * How close the worker has to get for the run to count as having exercised the approach.
     * Looser than the goal's own {@code acceptedDistance} of 1.0 on purpose: the point is to
     * refuse a vacuous pass, not to re-assert arrival.
     */
    private static final double FUNGUS_APPROACH_PROOF = 1.5;

    /**
     * How far above the floor the worker may legitimately be: a Fungal Carpet is a 1/16-block
     * step up, and stepping onto one is not a jump. A jump clears a full block.
     */
    private static final double FUNGUS_STEP_SLACK = 0.2;

    /**
     * Ticks of settle allowed before the airborne count starts. {@code GameTestHelper.spawn}
     * drops an entity in slightly above its floor -- the same drop that deals the 1.0 fall
     * damage banked in {@code docs/gotchas/entity-ai.md} -- so the first couple of ticks read
     * {@code onGround() == false} for every mob in every arena regardless of behaviour. Two
     * ticks were measured here; five is that with room and still nowhere near the ~46 the
     * defect produced. The last airborne tick of the <em>whole</em> run is reported in the
     * failure message, so a run that passes has also shown the airborne ticks were confined
     * to this window rather than merely uncounted.
     */
    private static final int FUNGUS_SETTLE_TICKS = 5;

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

    /**
     * <b>Play-test round 5, item 4</b>: "ants jump when trying to path find through a fungal
     * bloom." A wild worker walks to a Fungal Bloom three blocks away and must arrive
     * <em>without ever leaving the ground</em>.
     *
     * <p>Neither block is at fault -- both were cleared experimentally before anything was
     * changed. A worker pathing along a solid row of blooms on flat soil never leaves the
     * ground (rise 0.0000, 0 airborne ticks) and {@code WalkNodeEvaluator.getPathTypeStatic}
     * answers {@code WALKABLE} over a bloom exactly as it does over bare air; the carpet, the
     * standing suspect for a hop, is a plain 1/16-block step (rise 0.0625, 0 airborne ticks).
     * The jump comes from {@link IdleAtFungusGoal}'s <em>inherited target geometry</em> -- see
     * that class's javadoc for the mechanism.
     *
     * <p>Every other goal is stripped, the same isolation
     * {@code TamingGameTests#isolateFromForeignCrops} performs and for the same reason: the
     * subject is one goal's arithmetic, and a stroll goal walking the ant off its mark would
     * only add noise. The fungus is placed three blocks away so the goal has a real approach
     * to run, and near enough that the radius-8 search can never prefer a neighbouring
     * arena's.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", timeoutTicks = FUNGUS_WATCH_TICKS + 40)
    public static void a_worker_walks_to_a_bloom_without_jumping(GameTestHelper helper) {
        assertNoHopToFungus(helper, ModBlocks.FUNGAL_BLOOM.get().defaultBlockState(), "bloom");
    }

    /**
     * The carpet half of {@link #a_worker_walks_to_a_bloom_without_jumping}. Logan reported
     * the bloom, but {@link IdleAtFungusGoal} accepts either and the defect was in the goal,
     * so both are guarded -- the carpet arguably harder, since an ant standing on one is
     * already 1/16 of a block off the floor.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", timeoutTicks = FUNGUS_WATCH_TICKS + 40)
    public static void a_worker_walks_to_a_carpet_without_jumping(GameTestHelper helper) {
        assertNoHopToFungus(helper, ModBlocks.FUNGAL_CARPET.get().defaultBlockState(), "carpet");
    }

    /**
     * Walks a goal-stripped wild worker to {@code fungus} and fails if it ever leaves the
     * ground, or if it never got near enough for the run to have tested anything.
     */
    private static void assertNoHopToFungus(GameTestHelper helper, BlockState fungus, String what) {
        BlockPos start = new BlockPos(2, STAND_Y, 4);
        BlockPos fungusPos = new BlockPos(5, STAND_Y, 4);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, STAND_Y - 1, z), ModBlocks.PACKED_SOIL.get());
            }
        }
        helper.setBlock(fungusPos, fungus);

        WorkerAntEntity worker = helper.spawn(ModEntities.WORKER_ANT.get(), start);
        worker.goalSelector.removeAllGoals(goal -> true);
        worker.targetSelector.removeAllGoals(goal -> true);
        worker.goalSelector.addGoal(1, new IdleAtFungusGoal(worker, 1.0, FUNGUS_SEARCH_RANGE));

        double floorY = helper.absolutePos(start).getY();
        Vec3 fungusCentre = Vec3.atCenterOf(helper.absolutePos(fungusPos));
        int[] airborneTicks = { 0 };
        int[] lastAirborneTick = { -1 };
        double[] highWater = { floorY };
        double[] closest = { Double.MAX_VALUE };
        helper.onEachTick(() -> {
            if (!worker.onGround()) {
                lastAirborneTick[0] = (int) helper.getTick();
                if (helper.getTick() >= FUNGUS_SETTLE_TICKS) {
                    airborneTicks[0]++;
                }
            }
            highWater[0] = Math.max(highWater[0], worker.getY());
            closest[0] = Math.min(closest[0], worker.position().distanceTo(fungusCentre));
        });
        helper.runAtTickTime(FUNGUS_WATCH_TICKS, () -> {
            helper.assertTrue(closest[0] <= FUNGUS_APPROACH_PROOF,
                    "setup: the worker never approached the " + what + " (closest "
                            + String.format("%.3f", closest[0]) + "), so this run proved nothing");
            helper.assertValueEqual(airborneTicks[0], 0,
                    "ticks the worker spent airborne walking to a " + what + " after tick "
                            + FUNGUS_SETTLE_TICKS + " (last airborne tick overall "
                            + lastAirborneTick[0] + ", high-water y "
                            + String.format("%.4f", highWater[0]) + " against a floor at " + floorY + ")");
            helper.assertTrue(highWater[0] <= floorY + FUNGUS_STEP_SLACK,
                    "a worker walking to a " + what + " must stay on the floor; high-water y was "
                            + String.format("%.4f", highWater[0]) + " against a floor at " + floorY);
            helper.succeed();
        });
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
