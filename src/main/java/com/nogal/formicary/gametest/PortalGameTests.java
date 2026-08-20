package com.nogal.formicary.gametest;

import java.util.List;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.portal.AnthillPortal;
import com.nogal.formicary.portal.TrailPath;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the M5 portal (spec section 2).
 *
 * <p>What can and cannot be tested here is decided by a limitation established in M4a:
 * {@code GameTestServer} bakes {@code WorldPresets.FLAT} into a deliberately empty
 * {@code LevelStem} registry, so {@code formicary:formicary} does not exist on the test
 * server and no test can cross into it. The teleports, the structure's placement in
 * savannas and the membrane patches in the ceiling are therefore verified on
 * {@code runServer} instead.
 *
 * <p>What is left is exactly the arithmetic, and it is the part that decides whether the
 * portal is safe: which impacts count as hitting an anthill, which block is an exit, where
 * an arriving player's feet may legally go, and how the breadcrumb buffer behaves. All of
 * that is pure enough to run on the overworld arenas.
 */
@GameTestHolder(Formicary.MODID)
public class PortalGameTests {

    /** Where the core goes in the long arena, far enough in that a 5-block miss still fits. */
    private static final BlockPos CORE = new BlockPos(5, 1, 2);

    /**
     * "The impact block is the core or any block within 2 blocks of a core" -- the radius
     * the anthill template is designed around, so this is the test that keeps the mound
     * fully hittable.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "long_platform")
    public static void anthill_core_is_found_within_two_blocks_and_not_beyond(GameTestHelper helper) {
        helper.setBlock(CORE, ModBlocks.ANTHILL_CORE.get());
        BlockPos core = helper.absolutePos(CORE);

        // core.equals(found), not assertValueEqual(found, core): the helper dereferences its
        // first argument, so a null result would surface as an NPE instead of the message.
        helper.assertTrue(core.equals(AnthillPortal.findCoreNear(helper.getLevel(), core)),
                "a pearl landing on the core itself must resolve to it");
        helper.assertTrue(
                core.equals(AnthillPortal.findCoreNear(helper.getLevel(), helper.absolutePos(new BlockPos(7, 1, 2)))),
                "a pearl landing 2 blocks along X must resolve to the core");
        helper.assertTrue(
                core.equals(AnthillPortal.findCoreNear(helper.getLevel(), helper.absolutePos(new BlockPos(7, 1, 4)))),
                "a pearl landing 2 blocks diagonally must resolve to the core");

        helper.assertTrue(
                AnthillPortal.findCoreNear(helper.getLevel(), helper.absolutePos(new BlockPos(8, 1, 2))) == null,
                "3 blocks along X is outside the radius and must not open a portal");
        helper.assertTrue(
                AnthillPortal.findCoreNear(helper.getLevel(), helper.absolutePos(new BlockPos(10, 1, 2))) == null,
                "5 blocks away must not open a portal");
        helper.succeed();
    }

    /** The exit side of the same hook: only an actual Daylight Membrane counts. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void only_a_daylight_membrane_counts_as_an_exit(GameTestHelper helper) {
        BlockPos membrane = new BlockPos(2, 1, 2);
        helper.setBlock(membrane, ModBlocks.DAYLIGHT_MEMBRANE.get());

        helper.assertTrue(AnthillPortal.isMembrane(helper.getLevel(), helper.absolutePos(membrane)),
                "a pearl landing on a Daylight Membrane must be an exit");
        helper.assertFalse(AnthillPortal.isMembrane(helper.getLevel(), helper.absolutePos(new BlockPos(2, 0, 2))),
                "the arena floor must not be an exit");
        helper.assertFalse(AnthillPortal.isMembrane(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2))),
                "the air beside it must not be an exit");
        helper.succeed();
    }

    /**
     * Play-test round 2, item 7: the membrane is bedrock-class now, so a survival-mode
     * player must never be able to mine one -- the pearl toll has to be the only way out.
     *
     * <p>Cannot be asserted through {@code GameTestHelper.destroyBlock} or
     * {@code level.destroyBlock(pos, true)} the way {@code LootXpGameTests} exercises real
     * breaks: per {@code docs/gotchas/gametest.md}, both of those are "force remove" paths
     * (the helper hardcodes {@code dropBlock=false}, and {@code Level#destroyBlock} is the
     * unconditional world-edit removal explosions and pistons use) that do not consult
     * hardness at all -- either one would "break" this block regardless of the property
     * change, giving a false pass. The real gate is
     * {@code BlockState#getDestroyProgress(Player, BlockGetter, BlockPos)}, which
     * {@code ServerPlayerGameMode.continueDestroyBlock} accumulates every tick to decide
     * when a block pops: {@code BlockBehaviour#getDestroyProgress} (verified in
     * {@code reference/}) returns {@code 0.0F} unconditionally whenever
     * {@code getDestroySpeed} is {@code -1.0F}, before it even looks at the player's tool,
     * which is exactly bedrock's own mechanism. {@code GameTestHelper.makeMockPlayer} is a
     * bare {@code Player} (never a real {@code ServerPlayer}), but that is all this call
     * needs -- no network connection or gamemode plumbing required.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void daylight_membrane_cannot_be_mined_in_survival(GameTestHelper helper) {
        BlockPos membrane = new BlockPos(2, 1, 2);
        helper.setBlock(membrane, ModBlocks.DAYLIGHT_MEMBRANE.get());
        BlockPos absPos = helper.absolutePos(membrane);
        BlockState state = helper.getLevel().getBlockState(absPos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.assertValueEqual(state.getDestroySpeed(helper.getLevel(), absPos), -1.0F,
                "Daylight Membrane's destroy speed (hardness)");
        helper.assertValueEqual(state.getDestroyProgress(player, helper.getLevel(), absPos), 0.0F,
                "Daylight Membrane's per-tick destroy progress for a survival player");
        helper.succeed();
    }

    /**
     * The arrival-safety rule, which is the whole of "never suffocate them, never drop them
     * into the void": a landing spot needs two blocks of air with something solid under it,
     * and of the legal spots the search must take the highest -- the player enters at the
     * top of the colony and descends from there.
     *
     * <p>Every block the search reads is written by the test first, so nothing here depends
     * on how the GameTest framework lines a template up against its structure block.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "tall_platform")
    public static void arrival_floor_search_requires_headroom_ground_and_takes_the_highest(GameTestHelper helper) {
        int base = helper.absolutePos(new BlockPos(1, 1, 1)).getY();
        int bandBottom = base + 1;
        int bandTop = base + 7;

        // Two stacked landing spots: one on the floor at rel y=1, one on a ledge at rel y=4.
        // The search must return the ledge.
        column(helper, 1, "S..S....");
        BlockPos twoLevels = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertValueEqual(
                AnthillPortal.findFloorInBand(helper.getLevel(), twoLevels.getX(), twoLevels.getZ(),
                        bandBottom, bandTop),
                base + 4, "the highest standable Y in a column with two landing spots");

        // Packed solid the whole way up: standing anywhere here would suffocate.
        column(helper, 2, "SSSSSSSS");
        BlockPos buried = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.assertValueEqual(
                AnthillPortal.findFloorInBand(helper.getLevel(), buried.getX(), buried.getZ(), bandBottom, bandTop),
                AnthillPortal.NO_FLOOR, "a column with no headroom must be rejected");

        // Nothing solid anywhere: arriving here would be a drop, not a landing.
        column(helper, 3, "........");
        BlockPos hole = helper.absolutePos(new BlockPos(3, 1, 1));
        helper.assertValueEqual(
                AnthillPortal.findFloorInBand(helper.getLevel(), hole.getX(), hole.getZ(), bandBottom, bandTop),
                AnthillPortal.NO_FLOOR, "a column with nothing to stand on must be rejected");
        helper.succeed();
    }

    /**
     * Writes rel y = 1..8 of the column at {@code (x, *, 1)} from a mask: {@code S} is
     * Packed Soil, {@code .} is air. Bottom character is rel y = 1.
     */
    private static void column(GameTestHelper helper, int x, String mask) {
        for (int i = 0; i < mask.length(); i++) {
            helper.setBlock(new BlockPos(x, 1 + i, 1),
                    mask.charAt(i) == 'S' ? ModBlocks.PACKED_SOIL.get() : Blocks.AIR);
        }
    }

    /**
     * The breadcrumb ring buffer: samples are spaced, the buffer wraps at its capacity, and
     * "newest first" really is the way the player came.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void trail_buffer_spaces_samples_and_caps_at_capacity(GameTestHelper helper) {
        TrailPath path = new TrailPath();

        helper.assertTrue(path.record(new BlockPos(0, 64, 0)), "the first sample is always recorded");
        helper.assertFalse(path.record(new BlockPos(1, 64, 0)),
                "a sample less than MIN_SAMPLE_DISTANCE from the last must be dropped");
        helper.assertValueEqual(path.size(), 1, "size after one accepted and one dropped sample");
        helper.assertTrue(path.record(new BlockPos(2, 64, 0)),
                "a sample exactly MIN_SAMPLE_DISTANCE away must be recorded");
        helper.assertValueEqual(path.size(), 2, "size after two accepted samples");

        // Walk far enough to overflow the buffer; every step clears the spacing gate.
        for (int i = 2; i <= TrailPath.CAPACITY + 50; i++) {
            path.record(new BlockPos(i * 2, 64, 0));
        }
        helper.assertValueEqual(path.size(), TrailPath.CAPACITY, "the buffer must cap, not grow");

        BlockPos newest = path.newest();
        helper.assertValueEqual(newest, new BlockPos((TrailPath.CAPACITY + 50) * 2, 64, 0),
                "the newest sample after wrapping");
        List<BlockPos> back = path.newestFirst(3);
        helper.assertValueEqual(back.size(), 3, "newestFirst honours its limit");
        helper.assertValueEqual(back.get(0), newest, "newestFirst starts at the newest sample");
        helper.assertValueEqual(back.get(1), new BlockPos((TrailPath.CAPACITY + 49) * 2, 64, 0),
                "newestFirst walks backwards along the route");
        helper.succeed();
    }

    /**
     * A Trail Pheromone must refuse (and so not be consumed) when there is nothing recorded
     * to retrace, and once lit must keep drawing for its full two minutes off a frozen
     * snapshot rather than the live buffer.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void trail_lights_only_from_a_recorded_route_and_then_freezes_it(GameTestHelper helper) {
        TrailPath path = new TrailPath();
        helper.assertFalse(path.light(), "lighting an empty path must fail so the item is not consumed");
        helper.assertFalse(path.isLit(), "a failed light must not start the countdown");

        path.record(new BlockPos(0, 64, 0));
        path.record(new BlockPos(0, 64, 4));
        helper.assertTrue(path.light(), "lighting a recorded path must succeed");
        helper.assertValueEqual(path.litTicksRemaining(), TrailPath.TRAIL_DURATION_TICKS, "trail duration");
        helper.assertValueEqual(path.litTrail().size(), 2, "the lit trail holds the recorded points");

        helper.assertTrue(path.tickLitTrail(), "the first tick of a lit trail draws");
        helper.assertFalse(path.tickLitTrail(), "the second tick does not -- refreshes are once a second");

        // Walking on after lighting must NOT eat the snapshot: this is the whole reason the
        // trail is frozen at use time rather than read live.
        path.record(new BlockPos(0, 64, 8));
        path.record(new BlockPos(0, 64, 12));
        helper.assertValueEqual(path.litTrail().get(0), new BlockPos(0, 64, 4),
                "the lit trail must still start where the player was when they used the item");
        helper.succeed();
    }

    /**
     * Play-test round 1 ("the trail only went about a quarter of the way before
     * stopping"): a lit trail must retrace <b>every</b> recorded sample, not just the
     * newest 30 -- the root cause pinned in the owner's report ({@code light()} used to
     * call {@code newestFirst(TRAIL_POINTS)} with {@code TRAIL_POINTS = 30}). 40 is
     * comfortably past that old cap and comfortably under {@link TrailPath#CAPACITY} (512),
     * so this fails against the pre-fix behaviour and passes against the fix.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void trail_light_retraces_every_recorded_sample_not_just_the_newest_thirty(GameTestHelper helper) {
        TrailPath path = new TrailPath();
        int samples = 40;
        for (int i = 0; i < samples; i++) {
            helper.assertTrue(path.record(new BlockPos(i * 2, 64, 0)),
                    "every step here is exactly MIN_SAMPLE_DISTANCE from the last, so it clears the spacing gate");
        }
        helper.assertValueEqual(path.size(), samples, "setup: every sample recorded, no wraparound yet");

        helper.assertTrue(path.light(), "lighting a recorded path must succeed");
        helper.assertValueEqual(path.litTrail().size(), samples,
                "a lit trail must retrace the whole recorded route, not cap at the old 30-point window");
        helper.assertValueEqual(path.litTrail().get(0), new BlockPos((samples - 1) * 2, 64, 0),
                "the lit trail still starts at the newest sample");
        helper.assertValueEqual(path.litTrail().get(samples - 1), new BlockPos(0, 64, 0),
                "and still reaches all the way back to the very first recorded sample -- the full walk, not a "
                        + "window of it");
        helper.succeed();
    }
}
