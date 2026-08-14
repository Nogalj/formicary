package com.nogal.formicary.gametest;

import java.util.List;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.effect.ModMobEffects;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.QueenAntEntity;
import com.nogal.formicary.entity.SoldierAntEntity;
import com.nogal.formicary.entity.WorkerAntEntity;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.item.PheromoneHornItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for M7: the queen's phase bursts and death grace, the Pheromone Horn,
 * allied-soldier neutrality, and the Royal Comb loot swap.
 *
 * <p>The dimension itself is out of reach here -- {@code GameTestServer} bakes
 * {@code WorldPresets.FLAT} into an empty {@code LevelStem} registry and never loads this
 * mod's dimension (banked in CLAUDE.md), so the throne chamber and the queen's placement in
 * it are verified by {@code NoiseProbe -PprobeWhat=throne} and by {@code runServer}, not
 * from here. What these tests own is everything that happens once she exists.
 *
 * <p>The mock-player limit shapes two of them. {@code makeMockPlayer} returns a bare
 * {@code Player} that is never added to the level, so
 * {@code QueenAntEntity.playersInRange}'s entity lookup can never see one. Both the burst
 * and the death grace therefore also take the player they already have a handle on -- the
 * queen's target, and her killer -- and both tests drive the <em>real</em> trigger
 * ({@code hurt} with a genuine player-attack damage source) rather than calling the effect
 * half directly.
 */
@GameTestHolder(Formicary.MODID)
public class QueenGameTests {
    /** Centre of the 25x7x25 arena. Floor is rel y=1, so standable air starts at y=2. */
    private static final BlockPos ARENA_CENTRE = new BlockPos(12, 2, 12);

    /** Somewhere in the arena a mock player can stand without being on top of the queen. */
    private static final BlockPos ARENA_SIDE = new BlockPos(9, 2, 12);

    private static final BlockPos SMALL_ARENA_A = new BlockPos(1, 2, 1);
    private static final BlockPos SMALL_ARENA_B = new BlockPos(3, 2, 3);
    private static final BlockPos SMALL_ARENA_C = new BlockPos(1, 2, 3);

    // ------------------------------------------------------------ the fight --

    /**
     * Spec section 3: "at health thresholds emits a pheromone burst that summons soldier
     * waves and briefly slows nearby players."
     *
     * <p>Driven the way a real fight drives it: a genuine player-attack {@code hurt} takes
     * her past 75% and her own {@code customServerAiStep} notices on the next tick. The
     * damage is sized to cross exactly one threshold, so "fired once" is a real assertion
     * rather than an artefact of a single big hit.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "arena_platform", timeoutTicks = 200)
    public static void a_phase_threshold_summons_a_wave_and_slows_the_player(GameTestHelper helper) {
        QueenAntEntity queen = helper.spawn(ModEntities.QUEEN_ANT.get(), ARENA_CENTRE);
        Player intruder = mockPlayerAt(helper, ARENA_SIDE);
        queen.setTarget(intruder);

        helper.assertValueEqual(queen.getFiredPhaseCount(), 0, "phases fired before any damage");
        helper.assertValueEqual(soldiersNear(helper, queen).size(), 0, "soldiers before any damage");

        // 200 max health: 60 damage lands her at 70%, past 0.75 and nowhere near 0.50.
        queen.hurt(helper.getLevel().damageSources().playerAttack(intruder), 60.0F);

        helper.runAfterDelay(4, () -> {
            helper.assertValueEqual(queen.getFiredPhaseCount(), 1, "phases fired after crossing 75%");
            helper.assertValueEqual(soldiersNear(helper, queen).size(), QueenAntEntity.BURST_SOLDIERS,
                    "soldiers summoned by the burst");
            helper.assertTrue(intruder.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "the burst should have slowed the player she is fighting");
            helper.assertValueEqual(intruder.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier(),
                    QueenAntEntity.BURST_SLOWNESS_AMPLIFIER, "slowness amplifier");
            helper.succeed();
        });
    }

    /**
     * The same threshold must not fire twice, however many hits cross it -- otherwise a
     * fast weapon would summon a wave per swing.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "arena_platform", timeoutTicks = 200)
    public static void a_threshold_already_crossed_never_fires_again(GameTestHelper helper) {
        QueenAntEntity queen = helper.spawn(ModEntities.QUEEN_ANT.get(), ARENA_CENTRE);
        Player intruder = mockPlayerAt(helper, ARENA_SIDE);
        queen.setTarget(intruder);

        queen.hurt(helper.getLevel().damageSources().playerAttack(intruder), 60.0F);

        helper.runAfterDelay(4, () -> {
            helper.assertValueEqual(queen.getFiredPhaseCount(), 1, "phases fired after the first hit");
            // Heal her back over the threshold and take her under it again: the bitmask,
            // not the health, is what decides.
            queen.setHealth(queen.getMaxHealth());
            queen.setHealth(queen.getMaxHealth() * 0.70F);
            helper.runAfterDelay(4, () -> {
                helper.assertValueEqual(queen.getFiredPhaseCount(), 1, "phases fired after re-crossing 75%");
                helper.assertValueEqual(soldiersNear(helper, queen).size(), QueenAntEntity.BURST_SOLDIERS,
                        "soldiers after re-crossing 75%");
                helper.succeed();
            });
        });
    }

    /**
     * Spec section 3: "on her death, every player within ~24 blocks gains Pheromonal
     * Disguise for ~3 min and existing colony anger toward them is cleared -- a safe walk
     * out."
     *
     * <p>The killing blow itself provokes the colony first ({@code LivingIncomingDamageEvent}
     * fires before the damage is applied, so {@code ColonyAnger.provoke} runs and angers
     * every soldier in range), and only then does {@code die} run. That ordering is the
     * point of the test: the grace has to out-rank the anger its own trigger caused.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "arena_platform", timeoutTicks = 200)
    public static void killing_the_queen_disguises_the_slayer_and_calms_the_colony(GameTestHelper helper) {
        QueenAntEntity queen = helper.spawn(ModEntities.QUEEN_ANT.get(), ARENA_CENTRE);
        SoldierAntEntity guard = helper.spawn(ModEntities.SOLDIER_ANT.get(), ARENA_SIDE);
        WorkerAntEntity worker = helper.spawn(ModEntities.WORKER_ANT.get(), ARENA_SIDE);
        Player slayer = mockPlayerAt(helper, ARENA_SIDE);

        guard.angerAt(slayer);
        worker.startFleeingFrom(slayer);
        helper.assertTrue(guard.isAngry(), "the guard should start angry");
        helper.assertTrue(worker.isFleeing(), "the worker should start fleeing");
        helper.assertFalse(slayer.hasEffect(ModMobEffects.PHEROMONAL_DISGUISE),
                "the slayer should not start disguised");

        queen.hurt(helper.getLevel().damageSources().playerAttack(slayer), 1000.0F);

        helper.assertTrue(queen.isDeadOrDying(), "the queen should be dead");
        helper.assertTrue(slayer.hasEffect(ModMobEffects.PHEROMONAL_DISGUISE),
                "her death should have disguised the slayer");
        helper.assertValueEqual(slayer.getEffect(ModMobEffects.PHEROMONAL_DISGUISE).getDuration(),
                QueenAntEntity.GRACE_DISGUISE_TICKS, "the grace's disguise duration");
        helper.assertFalse(guard.isAngry(), "her death should have called the guard off");
        helper.assertFalse(worker.isFleeing(), "her death should have stopped the worker fleeing");
        helper.succeed();
    }

    /**
     * The grace is not a blanket amnesty: it calls the colony off the players it reached,
     * and leaves a grudge against anyone else standing. Without this the test above would
     * pass on an implementation that simply calmed every soldier in range.
     *
     * <p>Driven through the {@code grantDeathGrace} seam rather than by killing her, and
     * that is forced rather than convenient: {@code ColonyAnger.ANGER_RADIUS} and
     * {@link QueenAntEntity#GRACE_RADIUS} are both 24, so the killing blow's own
     * {@code provoke} re-points <em>every</em> soldier the grace can reach at the killer
     * before {@code die} runs. There is therefore no end-to-end arrangement in which a
     * soldier inside the grace is angry at somebody else. The end-to-end path is covered by
     * the test above; this one owns the scoping rule.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "arena_platform", timeoutTicks = 200)
    public static void the_death_grace_only_forgives_the_players_it_reached(GameTestHelper helper) {
        SoldierAntEntity calmed = helper.spawn(ModEntities.SOLDIER_ANT.get(), ARENA_SIDE);
        SoldierAntEntity stillAngry = helper.spawn(ModEntities.SOLDIER_ANT.get(), ARENA_SIDE);
        Player slayer = mockPlayerAt(helper, ARENA_SIDE);
        Player bystander = mockPlayerAt(helper, ARENA_SIDE);

        calmed.angerAt(slayer);
        stillAngry.angerAt(bystander);

        QueenAntEntity.grantDeathGrace(helper.getLevel(),
                Vec3.atBottomCenterOf(helper.absolutePos(ARENA_CENTRE)), List.of(slayer));

        helper.assertTrue(slayer.hasEffect(ModMobEffects.PHEROMONAL_DISGUISE),
                "the player the grace reached should be disguised");
        helper.assertFalse(bystander.hasEffect(ModMobEffects.PHEROMONAL_DISGUISE),
                "a player the grace did not reach should not be disguised");
        helper.assertFalse(calmed.isAngry(), "anger at the reached player should be cleared");
        helper.assertTrue(stillAngry.isAngry(), "anger at a different player should survive");
        helper.assertValueEqual(stillAngry.getPersistentAngerTarget(), bystander.getUUID(),
                "the untouched guard's anger target");
        helper.succeed();
    }

    // -------------------------------------------------------- pheromone horn --

    /**
     * Spec section 3: "on use summons 2 allied soldier ants ... the item is not consumed and
     * goes on a ~120s cooldown".
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "arena_platform", timeoutTicks = 200)
    public static void the_horn_summons_two_allies_without_being_consumed(GameTestHelper helper) {
        Player summoner = mockPlayerAt(helper, ARENA_CENTRE);
        ItemStack horn = new ItemStack(ModItems.PHEROMONE_HORN.get());
        summoner.setItemInHand(InteractionHand.MAIN_HAND, horn);

        helper.assertFalse(summoner.getCooldowns().isOnCooldown(ModItems.PHEROMONE_HORN.get()),
                "the horn should not start on cooldown");

        InteractionResultHolder<ItemStack> result = ModItems.PHEROMONE_HORN.get()
                .use(helper.getLevel(), summoner, InteractionHand.MAIN_HAND);

        helper.assertValueEqual(result.getResult(), InteractionResult.SUCCESS, "the horn's use result");
        helper.assertValueEqual(horn.getCount(), 1, "horn stack size after use (it is reusable)");
        helper.assertTrue(summoner.getCooldowns().isOnCooldown(ModItems.PHEROMONE_HORN.get()),
                "the horn should be on cooldown after use");

        List<SoldierAntEntity> allies = alliesOf(helper, summoner);
        helper.assertValueEqual(allies.size(), PheromoneHornItem.SUMMON_COUNT, "allies summoned");
        for (SoldierAntEntity ally : allies) {
            helper.assertTrue(ally.isAllied(), "a summoned soldier should be flagged allied");
            helper.assertTrue(ally.getSummonExpiry() > helper.getLevel().getGameTime(),
                    "a summoned soldier should have a future expiry");
        }
        helper.succeed();
    }

    /** An ally disperses when its timer runs out rather than joining the colony forever. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void an_ally_disperses_when_its_summon_expires(GameTestHelper helper) {
        Player summoner = mockPlayerAt(helper, SMALL_ARENA_A);
        SoldierAntEntity ally = helper.spawn(ModEntities.SOLDIER_ANT.get(), SMALL_ARENA_B);
        // A two-tick life, so the expiry branch in customServerAiStep is what removes it.
        ally.summonFor(summoner, 2);

        helper.assertTrue(ally.isAlive(), "the ally should start alive");
        helper.runAfterDelay(20, () -> {
            helper.assertFalse(ally.isAlive(), "the ally should have dispersed once its summon expired");
            helper.succeed();
        });
    }

    /**
     * Spec section 3: allied soldiers "never anger the colony, and don't count as the player
     * attacking an ant if they fight wild soldiers".
     *
     * <p>Both directions are checked, because they fail for different reasons. An ally
     * <em>dealing</em> damage was already safe before M7 -- {@code ColonyAnger.offenderOf}
     * only ever resolves a {@code Player}, and the attacker here is a mob. An ally being
     * <em>hurt</em> is the half M7 had to fix: it is a {@code SoldierAntEntity}, so
     * {@code isColonyAnt} used to answer true for it and the summoner's own stray swing
     * would have raised the alarm against them. The wild-soldier control at the end is what
     * stops this passing on an implementation where nothing provokes at all.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void an_allied_soldiers_fights_never_reach_the_colony(GameTestHelper helper) {
        Player summoner = mockPlayerAt(helper, SMALL_ARENA_A);
        SoldierAntEntity ally = helper.spawn(ModEntities.SOLDIER_ANT.get(), SMALL_ARENA_B);
        ally.summonFor(summoner, 1200);
        WorkerAntEntity victim = helper.spawn(ModEntities.WORKER_ANT.get(), SMALL_ARENA_B);
        SoldierAntEntity witness = helper.spawn(ModEntities.SOLDIER_ANT.get(), SMALL_ARENA_C);

        // (a) the ally mauls a wild worker.
        victim.hurt(helper.getLevel().damageSources().mobAttack(ally), 1.0F);
        helper.assertFalse(witness.isAngry(), "an ally hurting a wild ant must not anger the colony");

        // (b) the summoner clips their own ally.
        ally.hurt(helper.getLevel().damageSources().playerAttack(summoner), 1.0F);
        helper.assertFalse(witness.isAngry(), "hurting your own ally must not anger the colony");
        helper.assertFalse(summoner.hasEffect(ModMobEffects.PHEROMONAL_DISGUISE)
                        && witness.isAngry(), "the disguise strip must not have fired either");

        // (c) control: the same swing at a WILD soldier does anger the colony.
        SoldierAntEntity wild = helper.spawn(ModEntities.SOLDIER_ANT.get(), SMALL_ARENA_B);
        wild.hurt(helper.getLevel().damageSources().playerAttack(summoner), 1.0F);
        helper.assertTrue(witness.isAngry(), "hurting a WILD soldier still angers the colony");
        helper.assertValueEqual(witness.getPersistentAngerTarget(), summoner.getUUID(),
                "the witness's anger target");
        helper.succeed();
    }

    // ------------------------------------------------------------ royal comb --

    /**
     * Spec section 5: "Royal Comb: breaking drops 1 Royal Jelly via loot table (replacing
     * the M1 placeholder self-drop)."
     *
     * <p>Read straight out of the loot table with {@code Block.getDrops}, which is the same
     * call the tamed worker's harvester makes, so this exercises the generated JSON rather
     * than a Java branch.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void breaking_royal_comb_yields_royal_jelly_not_the_block(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.ROYAL_COMB.get());
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(rel);
        BlockState state = level.getBlockState(pos);

        List<ItemStack> bare = Block.getDrops(state, level, pos, null);

        helper.assertValueEqual(countOf(bare, ModItems.ROYAL_JELLY.get().asItem()), 1,
                "royal jelly from a bare-handed break");
        helper.assertValueEqual(countOf(bare, ModBlocks.ROYAL_COMB.get().asItem()), 0,
                "royal comb from a bare-handed break (the M1 placeholder is gone)");

        // Silk Touch still lifts the comb itself -- it is a build material as well as a
        // jelly source, and this is the vanilla convention for "take the container".
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        Holder<Enchantment> silkTouch = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH);
        silk.enchant(silkTouch, 1);
        List<ItemStack> silked = Block.getDrops(state, level, pos, null, null, silk);

        helper.assertValueEqual(countOf(silked, ModBlocks.ROYAL_COMB.get().asItem()), 1,
                "royal comb from a Silk Touch break");
        helper.assertValueEqual(countOf(silked, ModItems.ROYAL_JELLY.get().asItem()), 0,
                "royal jelly from a Silk Touch break");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers --

    /**
     * A mock player standing at {@code rel} in the arena.
     *
     * <p>The position matters: {@code makeMockPlayer} builds one at {@code BlockPos.ZERO} in
     * the shared test level, which is nowhere near the arena, and every radius rule in M7 is
     * measured in blocks.
     */
    private static Player mockPlayerAt(GameTestHelper helper, BlockPos rel) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(rel));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    /** Every soldier in the level within the burst's own radius of the queen. */
    private static List<SoldierAntEntity> soldiersNear(GameTestHelper helper, QueenAntEntity queen) {
        double reach = QueenAntEntity.BURST_RADIUS * 2.0;
        return helper.getLevel().getEntitiesOfClass(SoldierAntEntity.class,
                AABB.ofSize(queen.position(), reach, reach, reach), SoldierAntEntity::isAlive);
    }

    /** Every live soldier in the level flagged as this player's summon. */
    private static List<SoldierAntEntity> alliesOf(GameTestHelper helper, Player summoner) {
        return helper.getLevel().getEntitiesOfClass(SoldierAntEntity.class,
                AABB.ofSize(summoner.position(), 16.0, 16.0, 16.0),
                soldier -> soldier.isAlive() && soldier.isSummonedBy(summoner));
    }

    private static int countOf(List<ItemStack> drops, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : drops) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
