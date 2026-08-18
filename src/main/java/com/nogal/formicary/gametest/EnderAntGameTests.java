package com.nogal.formicary.gametest;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.colony.ColonyAnger;
import com.nogal.formicary.effect.ModMobEffects;
import com.nogal.formicary.entity.EnderAntBlinkGoal;
import com.nogal.formicary.entity.EnderAntEntity;
import com.nogal.formicary.entity.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the Ender Ant (Ep2, spec section 5): the teleport, the pearl drop
 * and the XP behind it, and the target rules.
 *
 * <p>As with every other test class here, none of this runs in the actual dimension --
 * {@code GameTestServer} bakes {@code WorldPresets.FLAT} into a deliberately empty
 * {@code LevelStem} registry, so {@code formicary:formicary} does not exist on the test
 * server (banked in {@code docs/gotchas/gametest.md}). Everything below is therefore an
 * overworld arena, and the two things that genuinely need the dimension -- runtime spawn
 * placement and the generation-time seeding -- are covered by the scripted {@code runServer}
 * probe recorded in the E4 commit message instead.
 */
@GameTestHolder(Formicary.MODID)
public class EnderAntGameTests {

    /**
     * The middle of {@code ender_arena} (37x37), and one block of standable air above the
     * floor: {@code absolutePos(0, 0, 0)} is the STRUCTURE BLOCK, so the template's own
     * {@code y=0} floor layer sits at relative {@code y=1} and air starts at {@code y=2}
     * (banked in {@code docs/gotchas/gametest.md}).
     */
    private static final BlockPos ARENA_CENTRE = new BlockPos(18, 2, 18);

    /** {@code long_platform} positions: 8 blocks apart, well inside the ant's FOLLOW_RANGE 12. */
    private static final BlockPos CHASE_ANT = new BlockPos(3, 2, 2);
    private static final BlockPos CHASE_QUARRY = new BlockPos(11, 2, 2);

    /** {@code platform} arena, where the kills happen. */
    private static final BlockPos KILL_SPOT = new BlockPos(2, 2, 2);

    /**
     * Comfortably more than the 1.0 of incidental damage a spawn can inflict, so
     * {@code LivingEntity.hurt}'s {@code invulnerableTime > 10 && amount <= lastHurt} early
     * return cannot swallow the swing. A 1.0F test hit is the classic silent no-op here
     * (banked in {@code docs/gotchas/entity-ai.md}); this test in particular would then
     * assert that a teleport did not happen for entirely the wrong reason.
     */
    private static final float TEST_HIT_DAMAGE = 6.0F;

    /**
     * Slack for the goal selector's alternating-tick cadence: {@code Mob.serverAiStep} runs
     * {@code targetSelector.tick()} only when {@code (tickCount + getId()) % 2 == 0}, so a
     * "next tick" assertion is really a "next two ticks" one.
     */
    private static final int GOAL_SETTLE_TICKS = 6;

    /** Long enough to survive the assertion window without expiring mid-test. */
    private static final int TEST_EFFECT_DURATION = 200;

    /**
     * How many ender ants the pearl test kills.
     *
     * <p>The drop is {@code UniformGenerator.between(0, 1)} rounded, i.e. a straight coin
     * flip per kill (see {@code ModEntityLootSubProvider#enderAntTable}) -- which is the
     * spec's "0-1 ender pearl" and is deliberately not a guaranteed drop, so a single kill
     * cannot be asserted on. 20 kills leaves a {@code 2^-20} chance -- about one run in a
     * million -- of a false failure, five orders of magnitude below the ~9% flake rate this
     * repo already rejected once over wheat seeds.
     */
    private static final int PEARL_SAMPLE_KILLS = 20;

    // ------------------------------------------------------------ teleport --

    /**
     * Spec section 5: "teleports 8-16 blocks ... when hurt".
     *
     * <p>Three details are forced rather than arbitrary:
     * <ul>
     *   <li>the arena is {@code ender_arena} (37x37) because a legitimate blink is up to
     *       {@link EnderAntEntity#TELEPORT_MAX_DISTANCE} blocks and anything smaller would
     *       put the ant outside the test's own bounds;</li>
     *   <li>the ant is moved back to the centre before the measured hit, because
     *       {@code helper.spawn} can leave it with a tick of incidental fall damage, and
     *       this mob answers <em>any</em> successful hurt with a blink -- so the origin has
     *       to be read after that has had a chance to happen, not before;</li>
     *   <li>the hit is {@link #TEST_HIT_DAMAGE} and its return value is asserted, because a
     *       hit swallowed by {@code invulnerableTime} would leave the ant standing still and
     *       this test would then be measuring nothing.</li>
     * </ul>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "ender_arena", timeoutTicks = 200)
    public static void a_hurt_ender_ant_blinks_at_least_eight_blocks(GameTestHelper helper) {
        EnderAntEntity ant = helper.spawn(ModEntities.ENDER_ANT.get(), ARENA_CENTRE);
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.runAfterDelay(4, () -> {
            Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(ARENA_CENTRE));
            ant.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);

            helper.assertTrue(
                    ant.hurt(helper.getLevel().damageSources().playerAttack(attacker), TEST_HIT_DAMAGE),
                    "setup: the swing has to actually land, or there is nothing to blink away from");

            // The blink is synchronous inside hurt() (EnderAntEntity.hurt -> teleportRandomly),
            // so measure NOW. A 10-tick-later measurement sampled the swing's residual
            // knockback on top of the blink: the drift ran +-1.8 blocks in that window and
            // occasionally dragged an 8.4-block blink under the 8.0 assertion (observed
            // 2026-08-18: moved 7.87 and 7.22 on otherwise-green runs).
            double moved = ant.position().distanceTo(origin);
            helper.assertTrue(moved >= EnderAntEntity.TELEPORT_MIN_DISTANCE,
                    "a hurt ender ant must blink at least " + EnderAntEntity.TELEPORT_MIN_DISTANCE
                            + " blocks; it moved " + String.format("%.2f", moved));
            helper.assertTrue(ant.isAlive(), "the blink must not have killed it");
            helper.succeed();
        });
    }

    /**
     * The other half of the same rule: a swing that {@code hurt} rejects must not buy a free
     * teleport.
     *
     * <p>This is the {@code invulnerableTime} early return used deliberately rather than
     * tripped over. Two hits in quick succession, the second no larger than the first:
     * {@code LivingEntity.hurt} returns {@code false} before recording anything, so
     * {@link EnderAntEntity#hurt} must not blink. Without the {@code super.hurt} gate the
     * mob would be teleport-locked by any attacker tapping it faster than the window.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "ender_arena", timeoutTicks = 200)
    public static void a_swing_swallowed_by_invulnerability_does_not_blink(GameTestHelper helper) {
        EnderAntEntity ant = helper.spawn(ModEntities.ENDER_ANT.get(), ARENA_CENTRE);
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.runAfterDelay(4, () -> {
            Vec3 landed = Vec3.atBottomCenterOf(helper.absolutePos(ARENA_CENTRE));
            ant.moveTo(landed.x, landed.y, landed.z, 0.0F, 0.0F);
            helper.assertTrue(
                    ant.hurt(helper.getLevel().damageSources().playerAttack(attacker), TEST_HIT_DAMAGE),
                    "setup: the first swing has to land");

            // Straight back to the centre, so the second swing is measured from a known spot.
            ant.moveTo(landed.x, landed.y, landed.z, 0.0F, 0.0F);
            helper.assertFalse(
                    ant.hurt(helper.getLevel().damageSources().playerAttack(attacker), TEST_HIT_DAMAGE),
                    "setup: a same-size follow-up inside invulnerableTime must be rejected by hurt");

            helper.runAfterDelay(10, () -> {
                double moved = ant.position().distanceTo(landed);
                helper.assertTrue(moved < EnderAntEntity.TELEPORT_MIN_DISTANCE,
                        "a swing hurt() rejected must not trigger a blink; it moved "
                                + String.format("%.2f", moved));
                helper.succeed();
            });
        });
    }

    // ---------------------------------------------------------------- loot --

    /**
     * Spec section 5: "0-1 ender pearl (+Looting)" -- the renewable half of the exit economy.
     *
     * <p>Killed the way {@code LootXpGameTests} kills things: one deliberately oversized hit
     * on the same tick the ant is spawned. Same tick matters twice over here -- nothing has
     * ticked yet so there is no incidental damage to blink away from, and a lethal hit leaves
     * {@code isAlive()} false so {@link EnderAntEntity#hurt}'s own guard declines to teleport
     * a corpse out of its own drops.
     *
     * <p>No Looting variant. Enchanting the mock player's weapon would turn the assertion
     * into "more pearls, usually" -- a statistical comparison rather than a test -- and the
     * Looting behaviour itself is vanilla's {@code EnchantedCountIncreaseFunction}, which is
     * not this mod's code. What is worth pinning is that the base drop exists at all, which
     * is what this does.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ender_ant_deaths_drop_ender_pearls(GameTestHelper helper) {
        Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < PEARL_SAMPLE_KILLS; i++) {
            EnderAntEntity ant = helper.spawn(ModEntities.ENDER_ANT.get(), KILL_SPOT);
            ant.hurt(helper.getLevel().damageSources().playerAttack(killer), 1000.0F);
        }

        helper.succeedWhen(() -> {
            helper.assertEntityNotPresent(ModEntities.ENDER_ANT.get());
            helper.assertItemEntityPresent(Items.ENDER_PEARL);
        });
    }

    /**
     * Spec section 5: "XP above a worker's". {@link EnderAntEntity#XP_REWARD} is 8, set as a
     * plain {@code Mob#xpReward} -- which is the correct pattern for a {@link
     * net.minecraft.world.entity.PathfinderMob}, where {@code Mob.getBaseExperienceReward()}
     * reads the field, and is exactly the pattern that silently does nothing on a
     * {@code TamableAnimal} (banked in {@code docs/gotchas/entity-ai.md}; asserted from the
     * other side by {@code LootXpGameTests}).
     *
     * <p>The assertion is on the SUM of the orbs, not on finding one orb worth 8, and that is
     * not a hedge: {@code ExperienceOrb.award} splits a reward through
     * {@code getExperienceValue}, whose tiers step 1, 3, 7, 17, ... -- so 8 always comes out
     * as an orb of 7 plus an orb of 1, and no orb worth 8 ever exists. (The two cannot merge
     * into each other either: {@code ExperienceOrb.canMerge} requires equal values.)
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void an_ender_ant_death_awards_its_full_xp_reward(GameTestHelper helper) {
        Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
        EnderAntEntity ant = helper.spawn(ModEntities.ENDER_ANT.get(), KILL_SPOT);

        ant.hurt(helper.getLevel().damageSources().playerAttack(killer), 1000.0F);

        helper.succeedWhen(() -> {
            helper.assertEntityNotPresent(ModEntities.ENDER_ANT.get());
            int total = helper.getEntities(EntityType.EXPERIENCE_ORB).stream()
                    .mapToInt(orb -> orb.getValue())
                    .sum();
            helper.assertValueEqual(total, EnderAntEntity.XP_REWARD,
                    "total experience from one ender ant's death");
        });
    }

    // --------------------------------------------------------------- aggro --

    /**
     * The target rules: an ender ant acquires a player, holds it, and drops it the instant
     * that player becomes disguised.
     *
     * <p><b>Why the grudge and not the on-sight goal.</b> The ant's
     * {@code NearestAttackableTargetGoal<Player>} resolves candidates through
     * {@code Level#getNearestPlayer}, which walks the level's player list --
     * {@code GameTestHelper.makeMockPlayer} returns a bare {@code Player} that is never added
     * to the level, so it is invisible to that search and to
     * {@code getEntitiesOfClass} alike (banked in {@code docs/gotchas/gametest.md}). The one
     * headless alternative, {@code makeMockServerPlayerInLevel}, is deprecated for removal
     * and puts a real player in the shared test level where every other test's target goals
     * can see it. So acquisition is driven through {@code HurtByTargetGoal} instead, exactly
     * as {@code DisguiseGameTests} drives the soldier's, and the on-sight radius is asserted
     * where it actually lives -- on the FOLLOW_RANGE attribute, which is what
     * {@code TargetGoal#getFollowDistance} reads for both the search box and the give-up
     * distance. The goal firing on sight in the real dimension is what the E4 {@code
     * runServer} evidence and play-testing cover.
     *
     * <p>Two forced details, both banked: the grudge is installed a tick after spawn, because
     * {@code HurtByTargetGoal.canUse} compares the mob's {@code lastHurtByMobTimestamp}
     * against its own stored {@code 0} and so cannot see damage dealt on the spawn tick; and
     * the delays are {@link #GOAL_SETTLE_TICKS} rather than one tick because the target
     * selector only ticks on alternating ticks. The whole window is ~14 ticks, comfortably
     * inside {@link EnderAntBlinkGoal#BLINK_DELAY_TICKS}, so no blink can move the ant
     * mid-assertion.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "long_platform", timeoutTicks = 200)
    public static void an_ender_ant_holds_a_player_target_and_drops_a_disguised_one(GameTestHelper helper) {
        EnderAntEntity ant = helper.spawn(ModEntities.ENDER_ANT.get(), CHASE_ANT);
        Player quarry = mockPlayerAt(helper, CHASE_QUARRY);

        helper.assertValueEqual(ant.getAttributeValue(Attributes.FOLLOW_RANGE),
                EnderAntEntity.FOLLOW_RANGE, "the ender ant's hunting range");
        helper.assertTrue(ColonyAnger.isValidTarget(quarry),
                "setup: an undisguised survival player must be a valid target for the on-sight goal");
        helper.assertTrue(ant.getTarget() == null, "the ant should start with no target");

        helper.runAfterDelay(2, () -> {
            ant.setLastHurtByMob(quarry);

            helper.runAfterDelay(GOAL_SETTLE_TICKS, () -> {
                helper.assertTrue(ant.getTarget() == quarry,
                        "the ant should be hunting the player it has a grudge against");

                quarry.addEffect(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, TEST_EFFECT_DURATION));
                helper.assertFalse(ColonyAnger.isValidTarget(quarry),
                        "a disguised player must stop being a valid target");

                helper.runAfterDelay(GOAL_SETTLE_TICKS, () -> {
                    helper.assertTrue(ant.getTarget() == null,
                            "the Pheromonal Disguise must break an ender ant's chase mid-run, not just "
                                    + "stop it starting one -- that is what the canAttack override is for");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * A mock player standing at {@code rel}. {@code makeMockPlayer} builds one at
     * {@code BlockPos.ZERO} in the shared test level, which is nowhere near the arena, and
     * every distance rule a target goal applies is measured in blocks. Mirrors
     * {@code DisguiseGameTests#mockPlayerAt}.
     */
    private static Player mockPlayerAt(GameTestHelper helper, BlockPos rel) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(rel));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }
}
