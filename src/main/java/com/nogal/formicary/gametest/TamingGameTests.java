package com.nogal.formicary.gametest;

import java.util.ArrayList;
import java.util.List;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.entity.CollectDroppedItemsGoal;
import com.nogal.formicary.entity.CropHarvest;
import com.nogal.formicary.entity.CropScanner;
import com.nogal.formicary.entity.GuardPostTargetGoal;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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
     * The scan budget itself (spec section 4: "throttle its scanning -- don't scan the full
     * radius every tick").
     *
     * <p>Asserted on a deliberately tiny scanner so the arithmetic is checkable by hand: a
     * radius-2, one-column-per-call scanner over a 5x5 patch must take a specific number of
     * calls to reach a crop planted at a known column, which is only true if it advances
     * incrementally and remembers where it stopped. The real configuration's numbers are
     * then asserted arithmetically alongside.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform", skyAccess = true)
    public static void the_crop_scan_is_tick_budgeted_and_incremental(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(2, STAND_Y, 2);
        // Column order is (dx fastest, then dz), both running -radius..+radius, so the crop
        // at dx=+1, dz=0 is column index (0 + 2) * 5 + (1 + 2) = 13 -- the 14th read.
        BlockPos crop = anchor.offset(1, 0, 0);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));

        CropScanner scanner = new CropScanner(2, 1, 0);
        helper.assertValueEqual(scanner.columnCount(), 25, "columns in a radius-2 patrol area");
        helper.assertValueEqual(scanner.scansPerSweep(), 25, "calls per sweep at one column a call");

        BlockPos absoluteAnchor = helper.absolutePos(anchor);
        for (int call = 0; call < 13; call++) {
            helper.assertTrue(scanner.scan(helper.getLevel(), absoluteAnchor) == null,
                    "call " + call + " reads column " + call + ", which holds no crop");
        }
        helper.assertValueEqual(scanner.scan(helper.getLevel(), absoluteAnchor),
                helper.absolutePos(crop), "the 14th call reaches the planted column");

        // And the shipped configuration really is a slice, not a sweep.
        CropScanner shipped = new CropScanner(TamedWorkerAntEntity.PATROL_RADIUS,
                TamedWorkerAntEntity.SCAN_COLUMNS_PER_CALL, TamedWorkerAntEntity.PATROL_VERTICAL_REACH);
        helper.assertValueEqual(shipped.columnCount(), 1089, "columns in the shipped patrol area");
        helper.assertTrue(shipped.columnsPerScan() * 10 < shipped.columnCount(),
                "a single scan call must read well under a tenth of the patrol area");
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
