package com.nogal.formicary.gametest;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.colony.ColonyAnger;
import com.nogal.formicary.effect.ModMobEffects;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.SoldierAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the Pheromonal Disguise system (spec section 3, M4b).
 *
 * <p>None of these can run in the actual dimension: the gametest dimension registry has
 * no {@code formicary:formicary} entry (M4a verified this -- {@code GameTestServer} bakes
 * {@code WorldPresets.FLAT} into a deliberately empty {@code LevelStem} registry), so
 * every scenario here is exercised on the overworld {@code platform} arena instead, the
 * same way {@link ColonyAngerGameTests} does.
 *
 * <p>A consequence of that: the deep-tier gating test below can only exercise the
 * player-side half of {@code ColonyAnger.isDeepTierHostileAt} -- the arena's biome is
 * never tagged {@code #formicary:deep_colony}, so the biome half is proven only by
 * {@code runServer}, not here. See that test's javadoc for the detail.
 */
@GameTestHolder(Formicary.MODID)
public class DisguiseGameTests {
    private static final BlockPos NEAR_BLOCK = new BlockPos(1, 1, 2);
    private static final BlockPos NEAR_ANT = new BlockPos(3, 1, 2);

    /** long_platform is 48 long on X: 19 blocks apart, inside FOLLOW_RANGE but out of reach. */
    private static final BlockPos CHASE_ANT = new BlockPos(3, 1, 2);
    private static final BlockPos CHASE_QUARRY = new BlockPos(22, 1, 2);

    /** Long enough to survive the test tick without expiring mid-assertion. */
    private static final int TEST_EFFECT_DURATION = 200;

    /**
     * Slack for the goal selector's alternating-tick cadence: {@code Mob.serverAiStep} runs
     * {@code targetSelector.tick()} only when {@code (tickCount + getId()) % 2 == 0}, so a
     * "next tick" assertion is really a "next two ticks" one.
     */
    private static final int GOAL_SETTLE_TICKS = 6;

    /**
     * Comfortably more than the 1.0 of fall damage {@code helper.spawn} inflicts. A soldier
     * lands hurt, so it is still inside {@code invulnerableTime} two ticks later, and
     * {@code LivingEntity.hurt} returns early -- before it records {@code lastHurtByMob} --
     * for any follow-up hit that is not larger than that first one. A 1.0F test swing
     * therefore looks like it landed (the {@code LivingIncomingDamageEvent} that angers the
     * colony fires higher up in {@code hurt}, above that early return) while recording no
     * grudge at all.
     */
    private static final float CHASE_HIT_DAMAGE = 6.0F;

    /**
     * "Top-tier anger doesn't target the disguised player": once a soldier is genuinely
     * angry (a real, undisguised offender broke a hive block), a disguised bystander must
     * still fail {@code ColonyAnger.isValidTarget} -- the exact predicate
     * {@code ColonyAngerTargetGoal}'s fallback search uses to pick a substitute target
     * when the recorded offender is unreachable. That predicate, not
     * {@code soldier.isAngry()} (which only reflects whether *some* offender is being
     * chased), is what "gets targeted by the anger sweep" actually means for a bystander
     * who never provoked anything themselves.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void disguised_bystander_is_not_a_valid_anger_target(GameTestHelper helper) {
        SoldierAntEntity soldier = helper.spawn(ModEntities.SOLDIER_ANT.get(), NEAR_ANT);
        Player offender = helper.makeMockPlayer(GameType.SURVIVAL);
        Player disguisedBystander = helper.makeMockPlayer(GameType.SURVIVAL);
        disguisedBystander.addEffect(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, TEST_EFFECT_DURATION));

        breakAsPlayer(helper, NEAR_BLOCK, ModBlocks.BROOD_COMB.get(), offender);

        helper.assertTrue(soldier.isAngry(), "the real offender should have angered the soldier");
        helper.assertFalse(ColonyAnger.isValidTarget(disguisedBystander),
                "a disguised bystander must never be a valid anger-sweep target");
        helper.succeed();
    }

    /**
     * "Attacking any wild ant or breaking any hive-tagged block instantly strips the
     * effect and angers the colony as normal": a disguised player who breaks a hive
     * block is stripped by the {@code // M4: strip disguise here} seam in
     * {@code ColonyAnger.provoke} BEFORE the radius sweep runs, so the sweep then marks
     * them hostile exactly like any other offender.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void provoke_by_disguised_player_strips_effect_and_angers(GameTestHelper helper) {
        SoldierAntEntity soldier = helper.spawn(ModEntities.SOLDIER_ANT.get(), NEAR_ANT);
        Player offender = helper.makeMockPlayer(GameType.SURVIVAL);
        offender.addEffect(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, TEST_EFFECT_DURATION));

        helper.assertTrue(ColonyAnger.isDisguised(offender), "setup: offender should start disguised");

        breakAsPlayer(helper, NEAR_BLOCK, ModBlocks.BROOD_COMB.get(), offender);

        helper.assertFalse(ColonyAnger.isDisguised(offender), "provoking should strip the disguise");
        helper.assertTrue(soldier.isAngry(), "the colony should still anger normally after the strip");
        helper.assertValueEqual(soldier.getPersistentAngerTarget(), offender.getUUID(), "soldier's anger target");
        helper.succeed();
    }

    /**
     * The deep-tier gating helper's player-side half: {@code isValidDeepTierTarget}
     * rejects a disguised player and accepts a plain survival one.
     *
     * <p>The full {@code isDeepTierHostileAt(level, pos, player)} additionally requires
     * the position to sit in a {@code #formicary:deep_colony} biome, which this test
     * arena never is (see the class javadoc) -- so, per the M4b brief, this test covers
     * only the player-side gate directly, and separately confirms (trivially, since the
     * arena's biome is not deep-colony) that the composed helper still comes out false
     * rather than silently ignoring the biome half.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void deep_tier_gate_rejects_disguised_and_accepts_undisguised(GameTestHelper helper) {
        Player disguised = helper.makeMockPlayer(GameType.SURVIVAL);
        disguised.addEffect(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, TEST_EFFECT_DURATION));
        Player plain = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.assertFalse(ColonyAnger.isValidDeepTierTarget(disguised),
                "a disguised player must never be a valid deep-tier target");
        helper.assertTrue(ColonyAnger.isValidDeepTierTarget(plain),
                "an undisguised, non-creative survival player must be a valid deep-tier target");

        helper.assertFalse(
                ColonyAnger.isDeepTierHostileAt(helper.getLevel(), helper.absolutePos(NEAR_BLOCK), plain),
                "the biome half of the gate must still hold: this arena is not #formicary:deep_colony");

        helper.succeed();
    }

    /**
     * Play-test round 1, the regression that matters: the disguise now breaks an aggro that
     * is <em>already running</em>, not only one that has yet to be picked.
     *
     * <p>Everything up to M8 gated the disguise in the goals' {@code TargetingConditions}
     * predicates, which vanilla consults only while <em>acquiring</em> a target.
     * {@code TargetGoal#canContinueToUse} re-checks {@code canAttack}, team, distance and
     * line of sight, and nothing else -- so a soldier mid-chase held on regardless. That is
     * what "killing the queen didn't give me the effect" looked like from inside a fight:
     * the grace landed, and the soldiers already chasing kept coming.
     *
     * <p>Driven through {@code HurtByTargetGoal}, which is the goal that slipped through:
     * personal retaliation consults neither the anger flag nor the target predicate once it
     * has started. Two details of the arrangement are forced rather than arbitrary --
     * <ul>
     *   <li>the hit waits two ticks, because {@code HurtByTargetGoal.canUse} compares the
     *       mob's {@code lastHurtByMobTimestamp} against its own stored {@code 0}, so a hit
     *       landed on the spawn tick is invisible to it;</li>
     *   <li>the arena is {@code long_platform} with the player 19 blocks out -- inside the
     *       soldier's {@code FOLLOW_RANGE} (24, pinned to {@code ANGER_RADIUS}) but far
     *       enough that the chase never reaches melee, so nothing in the assertion window
     *       depends on how a mock player takes damage.</li>
     * </ul>
     * The delays are {@link #GOAL_SETTLE_TICKS} rather than one tick because
     * {@code Mob.serverAiStep} only runs {@code targetSelector.tick()} -- and therefore
     * {@code canContinueToUse} -- on alternating ticks.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "long_platform", timeoutTicks = 200)
    public static void a_soldier_mid_chase_drops_a_player_who_becomes_disguised(GameTestHelper helper) {
        SoldierAntEntity soldier = helper.spawn(ModEntities.SOLDIER_ANT.get(), CHASE_ANT);
        Player quarry = mockPlayerAt(helper, CHASE_QUARRY);

        helper.assertTrue(soldier.getTarget() == null, "the soldier should start with no target");

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    soldier.hurt(helper.getLevel().damageSources().playerAttack(quarry), CHASE_HIT_DAMAGE),
                    "setup: the swing has to actually register, or nothing records a grudge");

            helper.runAfterDelay(GOAL_SETTLE_TICKS, () -> {
                helper.assertTrue(soldier.getTarget() == quarry,
                        "setup: the soldier should be chasing the player who hit it");
                quarry.addEffect(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, TEST_EFFECT_DURATION));

                helper.runAfterDelay(GOAL_SETTLE_TICKS, () -> {
                    helper.assertTrue(soldier.getTarget() == null,
                            "a disguised player must be dropped mid-chase, not just never picked");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * A mock player standing at {@code rel}. {@code makeMockPlayer} builds one at
     * {@code BlockPos.ZERO} in the shared test level, which is nowhere near the arena, and
     * every distance rule a target goal applies is measured in blocks. Mirrors
     * {@code QueenGameTests#mockPlayerAt}.
     */
    private static Player mockPlayerAt(GameTestHelper helper, BlockPos rel) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(rel));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    /**
     * Posts {@code BlockEvent.BreakEvent} for {@code block} at {@code relPos} on behalf of
     * {@code player}, the way {@code ServerPlayerGameMode.destroyBlock} does, then clears
     * the block. Mirrors {@code ColonyAngerGameTests#breakAsPlayer}.
     */
    private static void breakAsPlayer(GameTestHelper helper, BlockPos relPos, Block block, Player player) {
        helper.setBlock(relPos, block);
        BlockPos absPos = helper.absolutePos(relPos);
        BlockState state = helper.getLevel().getBlockState(absPos);
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), absPos, state, player));
        helper.setBlock(relPos, Blocks.AIR);
    }
}
