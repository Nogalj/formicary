package com.nogal.formicary.gametest;

import java.util.ArrayList;
import java.util.List;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.entity.CollectDroppedItemsGoal;
import com.nogal.formicary.entity.CropHarvest;
import com.nogal.formicary.entity.CropScanner;
import com.nogal.formicary.entity.DepositToChestGoal;
import com.nogal.formicary.entity.GuardPostTargetGoal;
import com.nogal.formicary.entity.HarvestCropsGoal;
import com.nogal.formicary.entity.LarvaEntity;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.SoldierAntEntity;
import com.nogal.formicary.entity.TamedAntTargetGoal;
import com.nogal.formicary.entity.TamedSoldierAntEntity;
import com.nogal.formicary.entity.TamedWorkerAntEntity;
import com.nogal.formicary.entity.WorkerAntEntity;
import com.nogal.formicary.colony.ColonyAnger;
import com.nogal.formicary.effect.ModMobEffects;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the taming loop (spec section 4, M6).
 *
 * <p>Two limits of the harness shape everything below, both established in earlier
 * milestones and both banked in CLAUDE.md:
 * <ul>
 *   <li>{@code GameTestHelper.makeMockPlayer} returns a bare {@code Player} that is
 *       <em>never added to the level</em>. It can hold items and carry a damage source, but
 *       {@code level.getEntity(uuid)} will not find it -- so ownership is asserted through
 *       {@code getOwnerUUID()} and the wild-soldier rule is asserted through the
 *       player-taking half of the predicate rather than the UUID-resolving one.</li>
 *   <li>{@code absolutePos(0, 0, 0)} is the structure block, so the template's floor layer
 *       sits at <em>relative</em> y=1 and standable air starts at y=2. Every block these
 *       tests read is written by the test first.</li>
 * </ul>
 */
@GameTestHolder(Formicary.MODID)
public class TamingGameTests {
    /** Floor layer of a template: rel y=1 overwrites the arena's own floor block. */
    private static final int FLOOR_Y = 1;

    /** First standable layer above the floor. */
    private static final int STAND_Y = 2;

    private static final int TEST_EFFECT_DURATION = 200;

    /**
     * How far above its arena a radius-searching test works. See {@link #isolated}.
     *
     * <p>Comfortably more than {@code TamedWorkerAntEntity.DEPOSIT_SEARCH_RADIUS} so nothing
     * at arena level is reachable, and small enough to stay inside a flat world's build
     * height whichever Y the harness lays the grid out at.
     */
    private static final int ISOLATION_LIFT = 80;

    /**
     * Wheat harvests run by {@link #every_harvest_replants_even_when_the_roll_yields_no_seed}.
     * Sized so the ~9% zero-seed roll is near-certain to occur at least once.
     */
    private static final int ZERO_ROLL_ATTEMPTS = 64;

    // ------------------------------------------------------- the diet fork --

    /**
     * Spec section 4, step 2: "Feed it Royal Jelly (right-click) -&gt; it grows into a Tamed
     * Worker Ant owned by you."
     *
     * <p>Driven through {@code Entity.interact}, the same entry point a real right-click
     * takes, so {@code Mob.interact}'s own important-interaction pass runs first exactly as
     * it does in play.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void royal_jelly_grows_a_placed_larva_into_an_owned_worker(GameTestHelper helper) {
        LarvaEntity larva = placedLarva(helper, new BlockPos(2, STAND_Y, 2));
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        keeper.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROYAL_JELLY.get(), 2));

        larva.interact(keeper, InteractionHand.MAIN_HAND);

        helper.assertTrue(larva.isRemoved(), "the fed larva should be gone");
        TamedWorkerAntEntity grown = onlyEntity(helper, TamedWorkerAntEntity.class);
        helper.assertValueEqual(grown.getOwnerUUID(), keeper.getUUID(), "the grown worker's owner");
        helper.assertTrue(grown.isTame(), "the grown worker should be tame");
        helper.assertValueEqual(keeper.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1,
                "royal jelly left in hand after feeding one");
        helper.succeed();
    }

    /**
     * The other half of the fork: "feeding the placed larva raw meat instead of jelly grows
     * it into a Tamed Soldier Ant".
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void raw_meat_grows_a_placed_larva_into_an_owned_soldier(GameTestHelper helper) {
        LarvaEntity larva = placedLarva(helper, new BlockPos(2, STAND_Y, 2));
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        keeper.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF, 1));

        larva.interact(keeper, InteractionHand.MAIN_HAND);

        helper.assertTrue(larva.isRemoved(), "the fed larva should be gone");
        TamedSoldierAntEntity grown = onlyEntity(helper, TamedSoldierAntEntity.class);
        helper.assertValueEqual(grown.getOwnerUUID(), keeper.getUUID(), "the grown soldier's owner");
        helper.assertTrue(helper.getLevel()
                .getEntitiesOfClass(TamedWorkerAntEntity.class, helper.getBounds()).isEmpty(),
                "raw meat must not produce a worker");
        helper.succeed();
    }

    /**
     * The diet only applies to a larva you have taken and set down. A grub still in the
     * colony's nursery is the colony's brood, so feeding it does nothing at all -- which is
     * also what keeps a jelly-carrying player from converting a whole nursery by walking
     * through it.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void a_wild_larva_ignores_royal_jelly(GameTestHelper helper) {
        LarvaEntity larva = helper.spawn(ModEntities.LARVA.get(), new BlockPos(2, STAND_Y, 2));
        helper.assertFalse(larva.isPlaced(), "setup: a spawned larva is not a placed one");
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        keeper.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROYAL_JELLY.get(), 1));

        larva.interact(keeper, InteractionHand.MAIN_HAND);

        helper.assertFalse(larva.isRemoved(), "a wild larva must not be consumed by feeding");
        helper.assertTrue(helper.getLevel()
                .getEntitiesOfClass(TamedWorkerAntEntity.class, helper.getBounds()).isEmpty(),
                "a wild larva must not grow into a tamed ant");
        helper.assertValueEqual(keeper.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1,
                "the jelly must not be spent on a wild larva");
        helper.succeed();
    }

    // -------------------------------------------------------- chest binding --

    /**
     * Spec section 4, step 3: right-clicking a chest binds a following worker to it, and
     * sneak-right-clicking the worker puts it back into follow mode.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void chest_click_binds_a_following_worker_and_sneak_click_unbinds_it(GameTestHelper helper) {
        BlockPos chest = new BlockPos(1, STAND_Y, 1);
        helper.setBlock(chest, Blocks.CHEST);

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(3, STAND_Y, 3));
        worker.tame(keeper);
        // The mock player is not in the level, so put it where the ant is by hand: the
        // binding search measures from the player, exactly as the real click does.
        keeper.setPos(worker.getX(), worker.getY(), worker.getZ());

        helper.assertFalse(worker.isBound(), "a freshly grown worker follows, it is not bound");

        BlockPos absoluteChest = helper.absolutePos(chest);
        TamedWorkerAntEntity bound = TamedWorkerAntEntity.bindNearestFollower(
                helper.getLevel(), keeper, absoluteChest);
        helper.assertTrue(bound == worker, "the click should have bound this worker");
        helper.assertValueEqual(worker.getBoundChest(), absoluteChest, "the worker's bound chest");

        // A second click must not re-bind an already-working ant to a different chest.
        helper.assertTrue(
                TamedWorkerAntEntity.bindNearestFollower(helper.getLevel(), keeper,
                        helper.absolutePos(new BlockPos(3, STAND_Y, 1))) == null,
                "an already-bound worker must not be re-bound by another chest click");
        helper.assertValueEqual(worker.getBoundChest(), absoluteChest, "the bound chest after a second click");

        keeper.setShiftKeyDown(true);
        worker.interact(keeper, InteractionHand.MAIN_HAND);
        helper.assertFalse(worker.isBound(), "sneak-right-clicking the worker must unbind it");
        helper.succeed();
    }

    /**
     * Ep2: the sneak-click is a toggle. The half that already worked -- a bound worker going
     * back to follow mode -- is covered above; this is the half that used to play a sound and
     * do nothing. A following worker takes the nearest storage block within
     * {@code DEPOSIT_SEARCH_RADIUS} as its anchor.
     *
     * <p>Driven through the static seam rather than {@code worker.interact}, for the reason
     * the seam exists: {@code mobInteract}'s own guard is {@code isOwnedByUuid} plus
     * {@code isShiftKeyDown}, both of which a mock player satisfies, but the decision worth
     * asserting is the search itself.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void sneak_click_binds_a_following_worker_to_the_nearest_storage(GameTestHelper helper) {
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(2, STAND_Y, 2));
        worker.tame(keeper);
        lift(worker);

        BlockPos chest = worker.blockPosition().east(6);
        helper.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());

        helper.assertFalse(worker.isBound(), "a freshly grown worker follows, it is not bound");

        BlockPos bound = TamedWorkerAntEntity.bindNearestDeposit(helper.getLevel(), worker, keeper);

        helper.assertValueEqual(bound, chest, "the block the sneak-click bound the worker to");
        helper.assertValueEqual(worker.getBoundChest(), chest, "the worker's bound chest");
        helper.succeed();
    }

    /**
     * The other side of the toggle's new branch: with nothing bindable in range the worker is
     * left exactly as it was -- following, unbound -- rather than being anchored to wherever
     * it happens to be standing.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void a_sneak_click_with_no_storage_in_range_leaves_the_worker_following(GameTestHelper helper) {
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(2, STAND_Y, 2));
        worker.tame(keeper);
        lift(worker);

        helper.assertTrue(
                TamedWorkerAntEntity.findNearestDeposit(helper.getLevel(), worker.blockPosition()) == null,
                "setup: an isolated worker must have no deposit block within its search radius");

        helper.assertTrue(TamedWorkerAntEntity.bindNearestDeposit(helper.getLevel(), worker, keeper) == null,
                "a sneak-click with nothing in range must bind nothing");
        helper.assertFalse(worker.isBound(), "the worker must be left following");
        helper.succeed();
    }

    /** Binding and the pack survive a save/load round trip. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void worker_bind_and_pack_survive_a_save_round_trip(GameTestHelper helper) {
        BlockPos chest = helper.absolutePos(new BlockPos(1, STAND_Y, 1));
        TamedWorkerAntEntity saved = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(2, STAND_Y, 2));
        saved.bindTo(chest);
        saved.getPack().addItem(new ItemStack(Items.CARROT, 5));

        CompoundTag tag = new CompoundTag();
        saved.addAdditionalSaveData(tag);

        TamedWorkerAntEntity loaded = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(3, STAND_Y, 3));
        loaded.readAdditionalSaveData(tag);

        helper.assertValueEqual(loaded.getBoundChest(), chest, "the reloaded worker's bound chest");
        helper.assertValueEqual(loaded.getPack().countItem(Items.CARROT), 5,
                "carrots in the reloaded worker's pack");
        helper.succeed();
    }

    // ------------------------------------------------- the harvester loop --

    /**
     * <b>The test of this milestone</b> (spec section 4, step 4): a bound worker finds a
     * ripe crop inside its patrol radius, harvests it, replants from the harvested seed and
     * deposits the rest in its chest.
     *
     * <p>Carrots rather than wheat, and that choice is load-bearing rather than incidental.
     * Read out of {@code data/minecraft/loot_table/blocks/} in the client-extra jar:
     * carrots have an <em>unconditional</em> first pool, so a break always yields at least
     * one carrot -- and a carrot is both the crop's seed and its produce. Wheat's seed pool
     * is the fortune-binomial one only, which rolls zero seeds about 9% of the time; a test
     * asserting "it replanted" on wheat would therefore fail one run in eleven for reasons
     * that have nothing to do with this mod.
     *
     * <p>{@code skyAccess = true} matters too: without it the harness roofs the arena in
     * barrier blocks, the crop's {@code canSurvive} light check fails, and the replant would
     * be refused.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", skyAccess = true, timeoutTicks = 600)
    public static void bound_worker_harvests_replants_and_deposits(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, STAND_Y, 4);
        BlockPos crop = new BlockPos(6, STAND_Y, 4);

        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(4, STAND_Y, 4));
        worker.tame(keeper);
        worker.bindTo(helper.absolutePos(chest));

        helper.succeedWhen(() -> {
            helper.assertBlockState(crop,
                    state -> state.is(Blocks.CARROTS) && state.getValue(CropBlock.AGE) == 0,
                    () -> "expected a freshly replanted carrot at " + crop + ", found "
                            + helper.getBlockState(crop));
            Container container = HopperBlockEntity.getContainerAt(helper.getLevel(), helper.absolutePos(chest));
            helper.assertTrue(container != null, "the bound chest should still be a container");
            helper.assertTrue(container.countItem(Items.CARROT) > 0,
                    "the worker should have deposited its carrots in the bound chest");
        });
    }

    /**
     * Play-test round 1, spec item 2: a harvesting worker also collects loose item drops on
     * the ground, not just ripe crops, and delivers them home through the same chest trip.
     *
     * <p>Same harness shape as {@link #bound_worker_harvests_replants_and_deposits}: a bound
     * worker on the farm-sized platform, {@code succeedWhen} polling for the eventual
     * deposit rather than ticking a fixed number of times, so the test is not coupled to
     * exactly how many alternating-cadence {@code canUse} calls the pickup goal takes to
     * notice the drop (see CLAUDE.md: {@code Mob.serverAiStep} only calls a non-running
     * goal's {@code canUse} every other tick).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", timeoutTicks = 600)
    public static void bound_worker_collects_a_ground_item_and_deposits_it(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, STAND_Y, 4);
        BlockPos dropPos = new BlockPos(6, STAND_Y, 4);

        helper.setBlock(chest, Blocks.CHEST);

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(4, STAND_Y, 4));
        worker.tame(keeper);
        worker.bindTo(helper.absolutePos(chest));
        isolateFromForeignCrops(worker);

        BlockPos absoluteDrop = helper.absolutePos(dropPos);
        ItemEntity drop = new ItemEntity(helper.getLevel(), absoluteDrop.getX() + 0.5,
                absoluteDrop.getY(), absoluteDrop.getZ() + 0.5, new ItemStack(Items.STICK, 4));
        // No vanilla pickup-delay on this one -- it stands in for a drop that has been on
        // the ground a while, not something a player just tossed (see the class javadoc on
        // CollectDroppedItemsGoal for why a fresh drop is deliberately left alone).
        drop.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(drop);

        helper.succeedWhen(() -> {
            Container container = HopperBlockEntity.getContainerAt(helper.getLevel(), helper.absolutePos(chest));
            helper.assertTrue(container != null, "the bound chest should still be a container");
            helper.assertTrue(container.countItem(Items.STICK) > 0,
                    "the worker should have deposited the collected ground item in the bound chest");
            helper.assertTrue(
                    helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(),
                    "the picked-up item entity should be gone from the world once collected");
        });
    }

    /**
     * The regression guard for the 2026-08-18 pickup deadlock, and the deterministic half of
     * {@link #bound_worker_collects_a_ground_item_and_deposits_it}.
     *
     * <p>That test walks the whole loop and so only lands in the failing geometry by luck --
     * it failed about one run in sixteen, which is what kept the bug looking like noise for a
     * day. The bug itself is not stochastic at all: {@code PathNavigation.moveTo(Entity,
     * speed)} paths to the drop's <em>block</em> with accuracy 1 and retires the last node
     * once the ant is within {@code maxDistanceToWaypoint} of it, so "arrived" can leave the
     * ant a whole block short of a pickup range of 1.5 -- and {@code moveTo} opens with
     * {@code if (this.isDone()) return false;}, so once that happens no repath ever moves the
     * ant again. What was random was only whether a given drop settled inside the band, and
     * that is down to the random +-0.1 kick vanilla's
     * {@code ItemEntity(Level, x, y, z, ItemStack)} constructor gives every drop.
     *
     * <p>So this one removes the luck: the zero-velocity {@code ItemEntity} overload plus
     * hand-placed positions put the ant and the drop 1.70 apart in adjacent blocks every
     * single run. It fails on every run without the last-stretch nudge in
     * {@link CollectDroppedItemsGoal#tick()} and passes on every run with it.
     *
     * <p>The geometry above was never the fragile part. What was is where the arena lands on
     * the shared test grid -- see {@link #isolateFromForeignCrops}, which this test went red
     * for want of on 2026-08-20.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", timeoutTicks = 100)
    public static void a_drop_just_outside_pickup_range_is_still_collected(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, STAND_Y, 4);
        helper.setBlock(chest, Blocks.CHEST);

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(4, STAND_Y, 4));
        worker.tame(keeper);
        worker.bindTo(helper.absolutePos(chest));
        isolateFromForeignCrops(worker);

        // The exact geometry the flake trace captured: the ant near the west edge of its
        // block, the drop near the east edge of the block next door. 1.70 apart -- outside
        // PICKUP_RANGE (1.5), inside the block the navigation already calls "arrived", so
        // the path is satisfied by the ant's own block and moveTo never moves it again.
        BlockPos floor = helper.absolutePos(new BlockPos(5, STAND_Y, 4));
        double antX = floor.getX() + 0.15;
        double z = floor.getZ() + 0.5;
        worker.setPos(antX, floor.getY(), z);

        // Explicit zero velocity: the ordinary ItemEntity constructor kicks the drop a
        // random +-0.1, which is the randomness that made this read as a flake.
        ItemEntity drop = new ItemEntity(helper.getLevel(), antX + 1.7, floor.getY(), z,
                new ItemStack(Items.STICK, 4), 0.0, 0.0, 0.0);
        drop.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(drop);

        helper.succeedWhen(() -> helper.assertTrue(worker.getPack().countItem(Items.STICK) > 0,
                "the worker should have closed the last stretch and pocketed the drop"));
    }

    /**
     * The pickup-delay decision (spec item 2: "respect items a player just dropped"): a
     * drop still inside its vanilla pickup-delay window is left alone, exactly the filter
     * {@link RelocateItemGoal} already applies for the wild worker.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform")
    public static void a_freshly_dropped_item_under_pickup_delay_is_left_alone(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, STAND_Y, 4);
        BlockPos dropPos = new BlockPos(6, STAND_Y, 4);

        helper.setBlock(chest, Blocks.CHEST);

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(4, STAND_Y, 4));
        worker.tame(keeper);
        worker.bindTo(helper.absolutePos(chest));

        BlockPos absoluteDrop = helper.absolutePos(dropPos);
        ItemEntity fresh = new ItemEntity(helper.getLevel(), absoluteDrop.getX() + 0.5,
                absoluteDrop.getY(), absoluteDrop.getZ() + 0.5, new ItemStack(Items.STICK, 4));
        fresh.setDefaultPickUpDelay();
        helper.getLevel().addFreshEntity(fresh);

        CollectDroppedItemsGoal pickup = new CollectDroppedItemsGoal(worker, 1.0);
        helper.assertFalse(pickup.canUse(), "a drop still under its pickup delay must not be targeted");
        helper.succeed();
    }

    /**
     * <b>Retargeted in Ep2</b>, from {@code the_crop_scan_is_tick_budgeted_and_incremental}.
     *
     * <p>That version pinned the tick-budgeted cursor: thirteen empty calls over a radius-2
     * patch, then a hit on the fourteenth. The cursor is precisely what the one-crop shuttle
     * loop replaces -- a scan now happens about once per round trip rather than continuously,
     * so it sweeps the whole patrol area in one call and answers with the crop <em>nearest</em>
     * the anchor instead of the first one a wandering cursor landed on. The invariant under
     * both versions is unchanged and still asserted here: which crop the worker cuts next is a
     * deliberate, deterministic choice, and the shipped configuration covers the whole patrol
     * area rather than some corner of it.
     *
     * <p>The geometry is chosen so that "nearest" and "first found" disagree. Scan order runs
     * dz outer and dx inner, both ascending, so the far crop at {@code (dx=0, dz=-2)} is the
     * third column read while the near one at {@code (dx=+1, dz=0)} is the fourteenth -- the
     * very column the retired cursor test used. A scanner returning the first hit would answer
     * with the far crop every time.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform", skyAccess = true)
    public static void the_crop_scan_selects_the_nearest_mature_crop(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(2, STAND_Y, 2);
        BlockPos far = anchor.offset(0, 0, -2);
        BlockPos near = anchor.offset(1, 0, 0);
        plantRipeCarrot(helper, far);

        CropScanner scanner = new CropScanner(2, 0);
        helper.assertValueEqual(scanner.columnCount(), 25, "columns in a radius-2 patrol area");

        BlockPos absoluteAnchor = helper.absolutePos(anchor);
        helper.assertValueEqual(scanner.scan(helper.getLevel(), absoluteAnchor), helper.absolutePos(far),
                "a single call must sweep the whole patrol area, not a slice of it");
        helper.assertValueEqual(scanner.scan(helper.getLevel(), absoluteAnchor), helper.absolutePos(far),
                "the scanner carries no cursor, so a repeat call answers the same");

        plantRipeCarrot(helper, near);
        helper.assertValueEqual(scanner.scan(helper.getLevel(), absoluteAnchor), helper.absolutePos(near),
                "with two ripe crops the nearer is chosen, not the one read first");

        helper.setBlock(near, Blocks.AIR);
        helper.assertValueEqual(scanner.scan(helper.getLevel(), absoluteAnchor), helper.absolutePos(far),
                "with the near crop cut the far one is chosen again");

        // And the shipped configuration really does read the whole patrol area in that call.
        CropScanner shipped = new CropScanner(TamedWorkerAntEntity.PATROL_RADIUS,
                TamedWorkerAntEntity.PATROL_VERTICAL_REACH);
        helper.assertValueEqual(shipped.columnCount(), 1089, "columns in the shipped patrol area");
        helper.succeed();
    }

    /**
     * Ep2's one-crop shuttle: after a single harvest the next act is the trip home, not the
     * next crop. Two ripe crops are planted and only one is cut.
     *
     * <p>Asserted on the goals' own preconditions rather than on a live run, because "which
     * crop got cut first" in a running arena is a race and this is not. The rule is exactly
     * two facts -- the deposit goal's only precondition is now a non-empty pack, and it is
     * registered above the harvest goal so it preempts one -- and both are checked directly.
     * The end-to-end loop itself is still covered by
     * {@link #bound_worker_harvests_replants_and_deposits}, which passes unchanged.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", skyAccess = true)
    public static void one_harvest_sends_the_worker_home_before_the_next_crop(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, STAND_Y, 4);
        BlockPos near = new BlockPos(4, STAND_Y, 4);
        BlockPos far = new BlockPos(6, STAND_Y, 4);

        helper.setBlock(chest, Blocks.CHEST);
        plantRipeCarrot(helper, near);
        plantRipeCarrot(helper, far);

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(3, STAND_Y, 6));
        worker.tame(keeper);
        BlockPos absoluteChest = helper.absolutePos(chest);
        worker.bindTo(absoluteChest);

        DepositToChestGoal deposit = new DepositToChestGoal(worker, 1.0);
        HarvestCropsGoal harvest = new HarvestCropsGoal(worker, 1.0);

        helper.assertFalse(deposit.canUse(), "an empty pack has nothing to deliver");
        helper.assertTrue(harvest.canUse(), "a ripe crop inside the patrol radius is worth cutting");
        // The shipped configuration, not the hand-sized one the unit test above uses.
        helper.assertValueEqual(harvest.getScanner().scan(helper.getLevel(), absoluteChest),
                helper.absolutePos(near), "the crop the goal's own scanner picks out of the two");

        helper.assertTrue(worker.harvest(helper.getLevel(), helper.absolutePos(near)),
                "the nearer crop should have been harvested");

        helper.assertTrue(deposit.canUse(),
                "one crop in the pack is enough to start the trip -- it no longer has to be full");
        helper.assertTrue(depositOutranksHarvest(worker),
                "the deposit goal must be registered above the harvest goal, or the trip never preempts");
        // Play-test round 2, spec item 1: outranking the harvest goal is only half of the
        // rule, and on its own it is the half that let the field get swept anyway -- the
        // deposit goal spends most of its life on RETRY_COOLDOWN_TICKS, and a harvest goal
        // whose only pack condition was "not full" was free to cut all through that window.
        // The rule the worker actually has to obey is on this side: carrying anything at all
        // is a reason not to cut.
        helper.assertFalse(harvest.canUse(),
                "a worker already carrying produce must not start another harvest");
        helper.assertBlockState(far, CropHarvest::isHarvestable,
                () -> "the second crop must still be standing when the trip starts, found "
                        + helper.getBlockState(far));
        helper.succeed();
    }

    /**
     * <b>Play-test round 2, spec item 1</b>: the regression guard for the bug Logan reported
     * after round 1 shipped -- a bound worker still cut every ripe crop in the field before
     * walking one load home.
     *
     * <p>Round 1 put the whole one-at-a-time rule on the deposit side ({@code
     * DepositToChestGoal} needs nothing but a non-empty pack, and it outranks the harvest),
     * and {@link #one_harvest_sends_the_worker_home_before_the_next_crop} proves that much
     * holds. What it does not cover is the <em>window between trips</em>, which is where the
     * bug lived: after a delivery {@code DepositToChestGoal} puts itself on {@code
     * RETRY_COOLDOWN_TICKS} (100 {@code canUse} calls, and {@code Mob.serverAiStep} only
     * makes one every other tick -- so about 200 ticks), and for that whole window a harvest
     * goal gated on {@code isPackFull} was free to keep cutting. One crop went home, the next
     * eight piled up in the pack.
     *
     * <p>The obvious end-to-end assertion -- "the chest receives produce while crops are
     * still standing" -- does <em>not</em> reproduce it, which is worth recording: the very
     * first trip is correct even with the bug (an empty pack means no cooldown is running
     * yet), so that assertion passes either way. The defect is only visible from the second
     * trip on. What separates the two behaviours at every tick is what the ant is
     * <em>carrying</em>: one crop per trip means the pack never holds two crops' produce at
     * once.
     *
     * <p>Beetroot rather than carrot, and that is what makes the count exact rather than
     * statistical. Read out of {@code data/minecraft/loot_table/blocks/beetroots.json} in the
     * client-extra jar: an age-3 break yields exactly one {@code beetroot} from an
     * unconditional pool, and its seed is a <em>separate</em> item ({@code beetroot_seeds},
     * fortune-binomial) -- so {@code CropHarvest.takeSeed} spends a seed, never the produce.
     * {@code pack.countItem(BEETROOT)} is therefore precisely "crops cut and not yet
     * delivered". A carrot cannot do this job: carrot is both produce and seed, so the
     * replant eats one and a cut contributes 0-3 to the pack.
     *
     * <p>The high-water mark is monotonic on purpose. A violation is transient in the world
     * -- the worker does eventually deliver everything -- so an assertion that only looked at
     * the pack right now would let the run recover into a pass.
     *
     * <p><b>Lit from a block, not from the sky</b>, which every other crop test in this file
     * does the other way round. This is the one crop test that has to survive a long idle
     * stretch (the worker waits out {@code RETRY_COOLDOWN_TICKS} between trips), and ants
     * climb walls as of round 2: {@code skyAccess = true} leaves the arena open-topped, and a
     * worker that brushes a wall during that wait goes straight up and over it. Caught here
     * as a ~1-in-5 timeout whose diagnostic put the worker below its own floor and outside
     * the bounds -- once out, the one-block gap between the world surface and the arena floor
     * lets it wander under the arena, and nothing brings it back. So this arena keeps the
     * harness's default barrier roof and pays for the replant with a glowstone in the floor
     * instead: {@code CropBlock.canSurvive} asks {@code hasSufficientLight}, which is
     * {@code getRawBrightness(pos, 0) >= 8}, and that reads block light and sky light alike.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", timeoutTicks = 600)
    public static void a_bound_worker_never_carries_two_crops_at_once(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, STAND_Y, 4);
        helper.setBlock(chest, Blocks.CHEST);
        // Sunk into the floor layer in the middle of the patch: three blocks from the
        // furthest crop, so every one of them reads light 12 or better against the 8 the
        // replant needs, and nothing the worker walks into.
        helper.setBlock(new BlockPos(4, FLOOR_Y, 4), Blocks.GLOWSTONE);
        // Four ripe crops in a tight patch, all nearer this arena's chest than anything in a
        // neighbouring arena can be (see lift()'s javadoc on the grid): the scanner picks the
        // nearest ripe crop to the anchor, so the worker always has one of these to cut.
        for (BlockPos crop : List.of(new BlockPos(4, STAND_Y, 3), new BlockPos(4, STAND_Y, 5),
                new BlockPos(5, STAND_Y, 3), new BlockPos(5, STAND_Y, 5))) {
            plantRipeBeetroot(helper, crop);
        }

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(3, STAND_Y, 4));
        worker.tame(keeper);
        worker.bindTo(helper.absolutePos(chest));

        int[] mostCarriedAtOnce = {0};
        helper.succeedWhen(() -> {
            mostCarriedAtOnce[0] = Math.max(mostCarriedAtOnce[0],
                    worker.getPack().countItem(Items.BEETROOT));
            helper.assertTrue(mostCarriedAtOnce[0] <= 1,
                    "one crop per trip: the pack held " + mostCarriedAtOnce[0]
                            + " beetroots at once, so the worker cut another crop while still"
                            + " carrying the last one");
            Container container = HopperBlockEntity.getContainerAt(helper.getLevel(),
                    helper.absolutePos(chest));
            helper.assertTrue(container != null, "the bound chest should still be a container");
            // The counts and the position are in the message on purpose: a timeout here says
            // nothing on its own, and it was exactly these three numbers that identified the
            // climb-out (chest=1 pack=1, worker below its own floor, inBounds=false).
            helper.assertTrue(container.countItem(Items.BEETROOT) >= 2,
                    "the worker should have completed two separate one-crop trips; chest="
                            + container.countItem(Items.BEETROOT) + " pack="
                            + worker.getPack().countItem(Items.BEETROOT) + " worker at rel "
                            + worker.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))
                            + " inBounds=" + helper.getBounds().contains(worker.position()));
        });
    }

    /**
     * <b>The regression guard for the play-test-round-2 defect discovered while diagnosing
     * commit {@code 0e595bb}</b> (its message spells the trace out in full, under "Not fixed
     * here, and worth its own look"): a bound worker whose {@link CropScanner} picks a ripe
     * crop it cannot reach locks onto it permanently. {@code HarvestCropsGoal
     * .APPROACH_TIMEOUT_TICKS} stops the goal, but {@code canUse} re-scans immediately, the
     * scanner returns the identical nearest-but-unreachable crop, and the goal restarts at
     * it -- an infinite loop. Because harvest outranks {@link CollectDroppedItemsGoal} at
     * goal priority, the worker also never picks up anything in the meantime: it just stands
     * at the wall.
     *
     * <p>Two ripe beetroots are planted, one nearer the chest than the other, and only the
     * nearer one is walled off. {@link CropScanner#scan} always answers with the nearest ripe
     * crop regardless of whether anything can actually reach it, so pre-fix the goal locks
     * onto the walled one on every single re-scan and the chest never receives anything --
     * this test simply times out. The fix is a per-crop failure blacklist in
     * {@code HarvestCropsGoal}: an approach timeout records the position and the next scan
     * excludes it, so the goal picks the second-nearest crop -- this arena's reachable one --
     * immediately. Beetroot rather than carrot for the reason
     * {@link #a_bound_worker_never_carries_two_crops_at_once} already gives: separate produce
     * and seed items make {@code countItem(BEETROOT)} an exact signal.
     *
     * <p><b>The wall has to stop a climbing ant, not just a walking one -- and it has to beat
     * the goal's own 2-block reach, not just adjacency.</b> Round 2 gave every ant but the
     * queen and the larva {@code WallClimberNavigation} plus Spider's {@code
     * horizontalCollision} climb flag ({@code docs/gotchas/gametest.md}), so a 1-high fence is
     * not a wall -- the ant just climbs it. And {@code HarvestCropsGoal.REACH_SQR} is 4.0, i.e.
     * a 2-block radius: a ring of barrier on only the eight cells touching the crop still
     * leaves the four cells exactly 2 blocks out (one per cardinal direction) able to harvest
     * it without ever entering the ring at all -- caught by this test's own first draft, whose
     * chest happened to sit at exactly that distance and harvested straight through the wall.
     * {@link #encaseInBarrier} therefore blocks the full radius-2 square (every cell with
     * Chebyshev distance <= 2 from the crop, 24 cells, not just the 8 touching it), which
     * leaves no open cell anywhere inside Euclidean range 2.0 of the crop centre: the nearest
     * legal position outside the box is exactly 3.0 away. The box runs from {@link #STAND_Y}
     * through the topmost air layer this template has, rel y 5 -- four full blocks, matching
     * the height of the arena's own walls. {@code skyAccess} is left at its default
     * {@code false} on purpose so the harness's own barrier roof caps the box at rel y 6 for
     * free; climbing the outside of the box gets the ant nowhere it could not already reach by
     * climbing the arena's own perimeter.
     *
     * <p><b>The failed approach leaves the ant physically wedged, and the second crop's
     * approach has to route it clear of that instead of back into it.</b> A worker denied a
     * path climbs and stays pinned against the wall it cannot get past for the whole timeout
     * (observed directly: pos frozen, {@code onClimbable() == true}, for the entire 200-tick
     * approach) -- exactly the play-test trace in {@code 0e595bb}, which found the worker
     * "sat under the barrier roof... for the rest of the test". The reachable crop is
     * deliberately placed at rel (0, *, 1): due <em>west</em> of the chest, along z=1, which
     * never touches the box's z=2-6 footprint at any x. Retargeting the goal after the
     * blacklist fires therefore pulls the ant back the way it came and past the chest, not
     * past the box again -- confirmed empirically (an east-side placement at rel (7, *, 4),
     * this test's first draft, left the ant wedged against the box indefinitely even after
     * the blacklist correctly excluded it, because the straight line to that target still ran
     * through the box's footprint).
     *
     * <p><b>Isolated from foreign crops without stripping the goal under test.</b> Every other
     * radius-searching test in this file that plants no crop of its own calls
     * {@link #isolateFromForeignCrops} to remove {@code HarvestCropsGoal} entirely -- not an
     * option here, since that goal's behaviour is the whole point. Instead this test leans on
     * the fact {@link #a_bound_worker_never_carries_two_crops_at_once} already relies on:
     * {@code CropScanner} always answers with the crop nearest the anchor, so planting crops
     * close enough to this arena's own chest beats anything a neighbour could offer. The chest
     * sits at rel (4, *, 1), one block off this arena's own north wall (z=0); the closest any
     * block belonging to a neighbouring arena could be is that 1, plus the 6-block gap
     * {@code StructureGridSpawner.SPACE_BETWEEN_ROWS} leaves between rows of arenas, plus
     * however far into the neighbour's own footprint the block sits -- at least 7 (the west and
     * east neighbours, across the narrower 5-block {@code SPACE_BETWEEN_COLUMNS} gap, are
     * farther still: the chest sits 4 blocks in from either side wall, so at least 4+1+5=10).
     * Both crops planted here (3.0 and 4.0 blocks from the chest) beat that floor with room to
     * spare, so the scanner's choice is always one of this arena's own two crops, never a
     * neighbour's.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", timeoutTicks = 600)
    public static void a_bound_worker_gives_up_on_an_unreachable_crop_and_harvests_a_reachable_one(
            GameTestHelper helper) {
        BlockPos chest = new BlockPos(4, STAND_Y, 1);
        BlockPos walledCrop = new BlockPos(4, STAND_Y, 4);
        BlockPos reachableCrop = new BlockPos(0, STAND_Y, 1);

        helper.setBlock(chest, Blocks.CHEST);
        plantRipeBeetroot(helper, walledCrop);
        plantRipeBeetroot(helper, reachableCrop);
        encaseInBarrier(helper, walledCrop);

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(4, STAND_Y, 0));
        worker.tame(keeper);
        worker.bindTo(helper.absolutePos(chest));

        helper.succeedWhen(() -> {
            // A safety net as much as an assertion: if the box ever failed to hold, the
            // walled crop would stop being "still ripe" the moment it got cut, and this
            // would keep throwing forever instead of letting a false pass through.
            helper.assertBlockState(walledCrop, CropHarvest::isHarvestable,
                    () -> "the walled crop must never be reached, found "
                            + helper.getBlockState(walledCrop));
            Container container = HopperBlockEntity.getContainerAt(helper.getLevel(), helper.absolutePos(chest));
            helper.assertTrue(container != null, "the bound chest should still be a container");
            helper.assertTrue(container.countItem(Items.BEETROOT) > 0,
                    "the worker should have abandoned the unreachable crop and harvested the reachable one");
        });
    }

    /**
     * Ep2: the replant is guaranteed, not conditional on the loot roll.
     *
     * <p>Wheat on purpose, which is the mirror image of why
     * {@link #bound_worker_harvests_replants_and_deposits} insists on carrots. Wheat's seed
     * pool is the fortune-binomial one alone, so roughly 9% of breaks roll zero
     * {@code wheat_seeds} (read out of {@code data/minecraft/loot_table/blocks/wheat.json};
     * banked in {@code docs/gotchas/gametest.md}). The carrot test dodges that roll to stay
     * deterministic; this one walks into it deliberately. {@link #ZERO_ROLL_ATTEMPTS}
     * harvests make the zero-roll overwhelmingly likely to come up at least once
     * ({@code 1 - 0.91^64}, about 99.7%), and every single one of them has to leave an age-0
     * wheat behind -- which passes every run under the guarantee and would fail in roughly 24
     * runs out of 25 under the old "no seed, tile left bare" branch.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform", skyAccess = true)
    public static void every_harvest_replants_even_when_the_roll_yields_no_seed(GameTestHelper helper) {
        BlockPos crop = new BlockPos(2, STAND_Y, 2);
        helper.setBlock(crop.below(), Blocks.FARMLAND);

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(1, STAND_Y, 1));
        worker.tame(keeper);

        BlockPos absoluteCrop = helper.absolutePos(crop);
        for (int attempt = 0; attempt < ZERO_ROLL_ATTEMPTS; attempt++) {
            helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            helper.assertTrue(worker.harvest(helper.getLevel(), absoluteCrop),
                    "harvest " + attempt + " should have cut the ripe wheat");
            helper.assertBlockState(crop,
                    state -> state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 0,
                    () -> "every harvest must leave a replanted wheat behind, found "
                            + helper.getBlockState(crop));
            // Keep the pack from filling up and popping the overflow all over the arena.
            worker.getPack().clearContent();
        }
        helper.succeed();
    }

    /** The replant is paid for out of the harvest, and a crop with no seed in its drops is not. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void the_replant_seed_is_taken_from_the_harvested_drops(GameTestHelper helper) {
        List<ItemStack> wheatDrops = new ArrayList<>(
                List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS, 3)));
        ItemStack seed = CropHarvest.takeSeed(wheatDrops, Blocks.WHEAT);
        helper.assertValueEqual(seed.getItem(), Items.WHEAT_SEEDS, "the seed pulled from a wheat harvest");
        helper.assertValueEqual(seed.getCount(), 1, "exactly one seed is spent on the replant");
        helper.assertValueEqual(wheatDrops.get(1).getCount(), 2, "the rest of the seeds stay in the harvest");

        List<ItemStack> seedless = new ArrayList<>(List.of(new ItemStack(Items.WHEAT)));
        helper.assertTrue(CropHarvest.takeSeed(seedless, Blocks.WHEAT).isEmpty(),
                "a harvest with no seed in it cannot pay for a replant");

        // Nether wart proves the generic ripeness rule: it is not a CropBlock, so it
        // qualifies purely through its integer "age" property maxing out.
        BlockState ripeWart = Blocks.NETHER_WART.defaultBlockState()
                .setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 3);
        helper.assertTrue(CropHarvest.isMature(ripeWart), "age-3 nether wart is ripe");
        helper.assertFalse(CropHarvest.isMature(Blocks.NETHER_WART.defaultBlockState()),
                "age-0 nether wart is not ripe");
        helper.assertFalse(CropHarvest.isMature(Blocks.CARROTS.defaultBlockState()),
                "an age-0 carrot is not ripe");
        helper.succeed();
    }

    // --------------------------------------------------- colony neutrality --

    /**
     * Spec section 4: "tamed ants' fights never anger the colony or strip your disguise".
     *
     * <p>Both directions are checked, because they fail for different reasons: a tamed ant
     * hitting a wild one has no <em>player</em> offender to blame, and a tamed ant being hit
     * -- even by its own owner -- is not a colony ant to begin with.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void tamed_combat_never_provokes_the_colony(GameTestHelper helper) {
        SoldierAntEntity witness = helper.spawn(ModEntities.SOLDIER_ANT.get(), new BlockPos(1, STAND_Y, 1));
        WorkerAntEntity victim = helper.spawn(ModEntities.WORKER_ANT.get(), new BlockPos(2, STAND_Y, 2));
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        keeper.addEffect(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, TEST_EFFECT_DURATION));
        TamedSoldierAntEntity guard = helper.spawn(ModEntities.TAMED_SOLDIER_ANT.get(),
                new BlockPos(3, STAND_Y, 3));
        guard.tame(keeper);

        helper.assertFalse(ColonyAnger.isColonyAnt(guard), "a tamed ant is not one of the colony's");
        helper.assertTrue(ColonyAnger.isTamedAnt(guard), "a tamed ant is one of the player's");

        // (a) the tamed guard mauls a wild worker.
        victim.hurt(helper.getLevel().damageSources().mobAttack(guard), 2.0F);
        helper.assertFalse(witness.isAngry(), "a tamed ant's attack must not anger the colony");
        helper.assertFalse(victim.isFleeing(), "a tamed ant's attack must not panic the colony");
        helper.assertTrue(ColonyAnger.isDisguised(keeper), "the owner's disguise must survive its ant's fight");

        // (b) the owner clips their own guard.
        guard.hurt(helper.getLevel().damageSources().playerAttack(keeper), 1.0F);
        helper.assertFalse(witness.isAngry(), "hurting your own ant must not anger the colony");
        helper.assertTrue(ColonyAnger.isDisguised(keeper), "hurting your own ant must not strip the disguise");
        helper.succeed();
    }

    /**
     * Spec section 4: "wild soldiers treat them as they treat you -- safe while you're
     * disguised, targets when you're not."
     *
     * <p>Asserted on {@code isHostileToOwner}, the player-taking half of the rule, because
     * the UUID-resolving half cannot see a mock player (see the class javadoc).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void wild_soldiers_turn_on_a_tamed_ant_exactly_when_they_would_turn_on_its_owner(
            GameTestHelper helper) {
        SoldierAntEntity wild = helper.spawn(ModEntities.SOLDIER_ANT.get(), new BlockPos(1, STAND_Y, 1));
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.assertFalse(TamedAntTargetGoal.isHostileToOwner(wild, keeper),
                "a neutral colony has no quarrel with anyone's ants");

        wild.angerAt(keeper);
        helper.assertTrue(TamedAntTargetGoal.isHostileToOwner(wild, keeper),
                "once the colony is angry at you, your ants are targets too");

        keeper.addEffect(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, TEST_EFFECT_DURATION));
        helper.assertFalse(TamedAntTargetGoal.isHostileToOwner(wild, keeper),
                "a disguised owner's ants are read as colony members too");

        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertFalse(TamedAntTargetGoal.isHostileToOwner(wild, stranger),
                "one player's trespass must not make a bystander's ants targets");
        helper.succeed();
    }

    // ------------------------------------------------------- the guard post --

    /**
     * Guard-post mode: the toggle persists, and a stationed guard engages only what has
     * already taken aim at it or at its owner.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void a_guard_post_engages_only_threats_to_its_owner_or_itself(GameTestHelper helper) {
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedSoldierAntEntity guard = helper.spawn(ModEntities.TAMED_SOLDIER_ANT.get(),
                new BlockPos(2, STAND_Y, 2));
        guard.tame(keeper);
        SoldierAntEntity wild = helper.spawn(ModEntities.SOLDIER_ANT.get(), new BlockPos(3, STAND_Y, 3));
        TamedSoldierAntEntity nestmate = helper.spawn(ModEntities.TAMED_SOLDIER_ANT.get(),
                new BlockPos(1, STAND_Y, 1));
        nestmate.tame(keeper);

        helper.assertFalse(guard.isStationed(), "a fresh guard escorts, it does not hold a post");
        guard.toggleStationed();
        helper.assertTrue(guard.isStationed(), "sneak-clicking should plant the guard");
        helper.assertTrue(guard.getGuardPost() != null, "a stationed guard records where it stands");

        helper.assertFalse(GuardPostTargetGoal.threatens(guard, wild),
                "a wild soldier minding its own business is not a threat");
        wild.setTarget(guard);
        helper.assertTrue(GuardPostTargetGoal.threatens(guard, wild),
                "a wild soldier that has locked onto the guard is a threat");
        helper.assertFalse(GuardPostTargetGoal.threatens(guard, nestmate),
                "another of the owner's ants is never a threat");

        CompoundTag tag = new CompoundTag();
        guard.addAdditionalSaveData(tag);
        TamedSoldierAntEntity reloaded = helper.spawn(ModEntities.TAMED_SOLDIER_ANT.get(),
                new BlockPos(2, STAND_Y, 3));
        reloaded.readAdditionalSaveData(tag);
        helper.assertTrue(reloaded.isStationed(), "guard-post mode must survive a save round trip");
        helper.assertValueEqual(reloaded.getGuardPost(), guard.getGuardPost(), "the reloaded guard post");
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers --

    /**
     * Lifts {@code entity} {@link #ISOLATION_LIFT} blocks into open air above its arena.
     *
     * <p>Needed by the two binding tests and by nothing else in this file, because they are
     * the only ones that search by <em>radius</em> instead of reading a position the test
     * wrote. GameTest arenas are laid out in a grid five to six blocks apart
     * ({@code StructureGridSpawner.SPACE_BETWEEN_COLUMNS} / {@code _ROWS}, read in the
     * decompiled source) and a whole batch is live at once, so a 16-block block search
     * started at arena level reaches into the neighbours -- several of which stand a chest
     * of their own. Up here the only deposit block within range is the one the test placed.
     *
     * <p>The lifted entity is in free fall, which is harmless: every assertion that follows
     * runs in the same tick, before anything moves.
     */
    private static void lift(net.minecraft.world.entity.Entity entity) {
        entity.setPos(entity.getX(), entity.getY() + ISOLATION_LIFT, entity.getZ());
    }

    /** A ripe carrot on fresh farmland at {@code rel}. Both blocks are written by the test. */
    private static void plantRipeCarrot(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel.below(), Blocks.FARMLAND);
        helper.setBlock(rel, Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));
    }

    /**
     * A ripe beetroot on fresh farmland at {@code rel}, for the one test that needs a crop
     * whose produce and seed are different items.
     *
     * <p>{@code BeetrootBlock.AGE} rather than {@code CropBlock.AGE}: beetroot shortens the
     * range to 0-3 and carries {@code AGE_3}, so setting {@code CropBlock}'s {@code AGE_7} on
     * it throws {@code Cannot get property ... as it does not exist in BeetrootBlock}.
     */
    private static void plantRipeBeetroot(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel.below(), Blocks.FARMLAND);
        helper.setBlock(rel, Blocks.BEETROOTS.defaultBlockState()
                .setValue(BeetrootBlock.AGE, BeetrootBlock.MAX_AGE));
    }

    /**
     * Boxes in {@code cropRel} with barrier on every cell within Chebyshev distance 2 (a 5x5
     * square minus the centre, 24 columns), from {@link #STAND_Y} through the topmost air
     * layer this template has (rel y 5, four blocks tall) -- a real cage for a climbing ant,
     * not a 1-high fence it can climb over. See
     * {@link #a_bound_worker_gives_up_on_an_unreachable_crop_and_harvests_a_reachable_one}'s
     * javadoc for why radius 2 rather than the immediate ring: {@code HarvestCropsGoal}'s
     * 2-block reach lets it cut from a cell it never has to enter, so only blocking adjacency
     * still leaves a straight shot in from exactly 2 blocks out. Height 4 is enough because the
     * arena's own default barrier roof caps it at rel y 6 regardless.
     */
    private static void encaseInBarrier(GameTestHelper helper, BlockPos cropRel) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos column = cropRel.offset(dx, 0, dz);
                for (int y = STAND_Y; y <= STAND_Y + 3; y++) {
                    helper.setBlock(new BlockPos(column.getX(), y, column.getZ()), Blocks.BARRIER);
                }
            }
        }
    }

    /**
     * Takes {@link HarvestCropsGoal} off a worker whose test is about <em>ground pickup</em>,
     * so the run cannot be hijacked by a crop belonging to another test.
     *
     * <p>Not a convenience and not a way to make a slow test faster -- it repairs a genuine
     * hole in these two tests' isolation, found 2026-08-20. Every arena in a batch shares one
     * level on a 5-by-6 grid (already banked in {@code docs/gotchas/gametest.md}), and
     * {@code HarvestCropsGoal.canUse} runs {@link CropScanner} over
     * {@code PATROL_RADIUS = 16} blocks around the <em>bound chest</em> -- a 33x33x7 box that
     * covers this arena and several of its neighbours. Neither test plants a crop, so both
     * looked isolated; what they actually depended on was the luck of no neighbouring arena
     * holding a ripe crop at that offset. Adding two tests anywhere earlier in the session
     * re-packs the grid and changes which neighbour lands in the box.
     *
     * <p>When it does land there the run is not slow, it is over: harvest is priority 2 and
     * pickup priority 3, {@code GoalSelector} only lets a strictly lower-numbered goal preempt
     * a running one, so {@code CollectDroppedItemsGoal} never gets {@code MOVE}/{@code LOOK}
     * and never starts at all. The worker walks off toward the foreign crop, meets the arena
     * wall, and -- since round 2 gave every ant {@code WallClimberNavigation} plus Spider's
     * {@code horizontalCollision} climb flag -- climbs it and sits under the barrier roof for
     * the rest of the test. {@code HarvestCropsGoal}'s own {@code APPROACH_TIMEOUT_TICKS}
     * does not rescue it either: the goal stops, {@code canUse} re-scans, picks the same
     * unreachable crop and starts again, so no timeout budget can ever make this pass.
     *
     * <p>The alternative isolations were all worse. The pack cannot be pre-loaded to close
     * the {@code getPack().isEmpty()} gate, because a non-empty pack wakes
     * {@link DepositToChestGoal} at priority 1 and starves pickup just the same; and there is
     * no radius the anchor could sit at that clears a 16-block sweep on a 5-block grid. What
     * the harvest goal contributes to these two tests is nothing -- the harvest-over-pickup
     * ordering is asserted structurally by {@link #priorityOf}, not by racing the two goals.
     */
    private static void isolateFromForeignCrops(TamedWorkerAntEntity worker) {
        worker.goalSelector.removeAllGoals(goal -> goal instanceof HarvestCropsGoal);
    }

    /**
     * Whether the deposit goal is registered above the harvest goal on this worker -- the
     * half of the one-crop rule that {@code canUse} alone cannot show. {@code GoalSelector}
     * only lets a strictly <em>lower</em>-numbered goal preempt a running one.
     */
    private static boolean depositOutranksHarvest(TamedWorkerAntEntity worker) {
        int deposit = priorityOf(worker, DepositToChestGoal.class);
        int harvest = priorityOf(worker, HarvestCropsGoal.class);
        return deposit >= 0 && harvest >= 0 && deposit < harvest;
    }

    /** The registered priority of the first goal of {@code type}, or -1 if there is none. */
    private static int priorityOf(TamedWorkerAntEntity worker, Class<? extends Goal> type) {
        for (WrappedGoal wrapped : worker.goalSelector.getAvailableGoals()) {
            if (type.isInstance(wrapped.getGoal())) {
                return wrapped.getPriority();
            }
        }
        return -1;
    }

    /** Spawns a larva already flagged as placed, the way {@code LarvaItem} does. */
    private static LarvaEntity placedLarva(GameTestHelper helper, BlockPos pos) {
        LarvaEntity larva = helper.spawn(ModEntities.LARVA.get(), pos);
        larva.setPlaced(true);
        return larva;
    }

    /** The single entity of {@code type} in the arena; fails the test if there is not exactly one. */
    private static <T extends net.minecraft.world.entity.Entity> T onlyEntity(GameTestHelper helper,
            Class<T> type) {
        List<T> found = helper.getLevel().getEntitiesOfClass(type, helper.getBounds());
        helper.assertValueEqual(found.size(), 1, "number of " + type.getSimpleName() + " in the arena");
        return found.get(0);
    }
}
