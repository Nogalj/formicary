package com.nogal.formicary.gametest;

import java.util.List;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.FungalSporeCropBlock;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.block.SoilTilling;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for play-test round 5, item 5: planting inside the colony. Follows
 * {@code PolishGameTests}' and {@code TamingGameTests}' established conventions -- see
 * {@code docs/gotchas/gametest.md} (open before touching this file) for the harness
 * limits this works around.
 *
 * <p>The tilling test drives {@link SoilTilling#tilledState} directly rather than through
 * {@code GameTestHelper.makeMockPlayer} and a simulated hoe {@code UseOnContext} --
 * {@code docs/gotchas/gametest.md}'s mock-player limits (no advancements, invisible to
 * {@code getNearestPlayer}) make that route unreliable for asserting a block-state
 * transform, and {@code AntClimbing#tamedClimbFlag} is this project's own precedent for
 * testing a pure decision function straight, the same way this file does.
 */
@GameTestHolder(Formicary.MODID)
public class PlantingGameTests {
    private static final int STAND_Y = 2;

    /** Generous bound on {@code randomTick} attempts -- see the method javadoc below. */
    private static final int GROWTH_ATTEMPTS = 2000;

    /**
     * Ticks to wait before trusting {@code getRawBrightness} after placing blocks.
     * {@code setBlockAndUpdate} only *enqueues* a light-engine update -- the engine
     * drains that queue over the following world ticks, not synchronously inside the
     * call -- so reading light in the same tick the arena is assembled is unreliable.
     */
    private static final int LIGHT_SETTLE_TICKS = 4;

    /**
     * Vanilla wheat's own survival/growth threshold ({@code CropBlock.hasSufficientLight}
     * and the {@code randomTick} brightness gate both read as {@code getRawBrightness(pos,
     * 0) >= 8/9} -- see {@code docs/gotchas/gametest.md}), used here as the bar the fungal
     * spore crop has to clear <em>under</em>, not the literal value 0. Two things stop the
     * darkest reachable cell from ever measuring exactly 0: the crop is self-lit ({@code
     * FungalSporeCropBlock#lightForAge}, 2 at age 0) so its own occupied cell always carries
     * at least that much block light, and {@code BarrierBlock#propagatesSkylightDown}
     * unconditionally returns {@code true} -- confirmed by reading the decompiled source
     * after this test first measured light 15 under the harness's supposedly-roofing
     * default barrier encasement -- so neither the default roof nor the default side walls
     * block anything at all; a genuinely light-tight cell has to be built by hand.
     */
    private static final int WHEAT_LIGHT_THRESHOLD = 8;

    /**
     * The three soft tier soils till into farmland when the space above is air; Hardened
     * Soil (the dimension's stone-equivalent, spec section 5) does not till at all; and the
     * air-above gate itself refuses even a tillable soil when the space above is not air --
     * vanilla's own tilling rule (see {@code SoilTilling}'s javadoc), mirrored rather than
     * invented.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void hoe_tilling_turns_the_three_soft_soils_into_farmland_but_not_hardened_soil(
            GameTestHelper helper) {
        for (Block soil : List.of(ModBlocks.PACKED_SOIL.get(), ModBlocks.AMBER_EARTH.get(),
                ModBlocks.DEEP_LOAM.get())) {
            BlockState tilled = SoilTilling.tilledState(soil.defaultBlockState(), true);
            helper.assertTrue(tilled != null && tilled.is(Blocks.FARMLAND),
                    soil + " should till into farmland when the space above is air, got " + tilled);
        }

        BlockState hardenedResult = SoilTilling.tilledState(ModBlocks.HARDENED_SOIL.get().defaultBlockState(), true);
        helper.assertTrue(hardenedResult == null,
                "hardened soil must not be tillable even with air above, got " + hardenedResult);

        BlockState blockedByCeiling = SoilTilling.tilledState(ModBlocks.PACKED_SOIL.get().defaultBlockState(), false);
        helper.assertTrue(blockedByCeiling == null,
                "packed soil must not till when the space above is not air, got " + blockedByCeiling);

        helper.succeed();
    }

    /**
     * A Fungal Spore crop planted on farmland inside a hand-built, genuinely light-tight
     * cell (spec item 5, part 2: "the spore's light-free survival/growth").
     *
     * <p>{@code @GameTest(skyAccess = ...)}'s default barrier encasement does
     * <b>not</b> supply that darkness -- {@code docs/gotchas/gametest.md}'s existing entry
     * says the default roof blocks a light-dependent assertion, but reading the decompiled
     * {@code BarrierBlock} after this test first measured light 15 there shows {@code
     * propagatesSkylightDown} unconditionally returns {@code true} (and the block is
     * {@code noOcclusion()} besides), so neither the roof nor the side walls attenuate
     * light at all; open sky several arenas away tunnels straight through. This test
     * therefore seals the crop's single air cell itself: farmland below (already opaque
     * for light purposes -- {@code FarmBlock#useShapeForLightOcclusion} returns {@code
     * true}) and a real solid block ({@code Blocks.STONE}) on all five other faces, so no
     * external light source can reach it by any path. See {@link #WHEAT_LIGHT_THRESHOLD}'s
     * javadoc for why the measured light still isn't literally 0. The light-dependent
     * assertions run after {@link #LIGHT_SETTLE_TICKS} (see its javadoc) rather than in
     * the same tick the cell is built.
     *
     * <p>{@code canSurvive} is asserted directly (the seam {@link FungalSporeCropBlock}
     * itself overrides), then growth is forced with {@code GameTestHelper#randomTick}
     * (real per-call {@code RandomSource}, not a fixed seed) looped up to
     * {@link #GROWTH_ATTEMPTS} times -- the block's own 50% coin flip plus {@code
     * CropBlock.getGrowthSpeed}'s soil-fertility roll give roughly a 1-in-13 chance per
     * call with farmland directly below, so 2000 attempts leaves the odds of a false
     * failure astronomically small while still calling the real, unmocked growth path
     * (not {@code performBonemeal}, which would skip {@link FungalSporeCropBlock
     * #randomTick} entirely).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void fungal_spore_crop_survives_and_grows_at_zero_light(GameTestHelper helper) {
        BlockPos cropRel = new BlockPos(2, STAND_Y, 2);
        helper.setBlock(cropRel.below(), Blocks.FARMLAND);
        // Seal every other face of the crop's cell in real opaque blocks -- see the method
        // javadoc for why the harness's own barrier encasement cannot be trusted to do this.
        helper.setBlock(cropRel.above(), Blocks.STONE);
        helper.setBlock(cropRel.north(), Blocks.STONE);
        helper.setBlock(cropRel.south(), Blocks.STONE);
        helper.setBlock(cropRel.east(), Blocks.STONE);
        helper.setBlock(cropRel.west(), Blocks.STONE);
        helper.setBlock(cropRel, ModBlocks.FUNGAL_SPORE_CROP.get().defaultBlockState());

        BlockPos cropAbs = helper.absolutePos(cropRel);

        // See LIGHT_SETTLE_TICKS' javadoc -- the enclosure's light-engine update is only
        // enqueued by setBlockAndUpdate, not applied synchronously.
        helper.runAfterDelay(LIGHT_SETTLE_TICKS, () -> {
            int light = helper.getLevel().getRawBrightness(cropAbs, 0);
            helper.assertTrue(light < WHEAT_LIGHT_THRESHOLD,
                    "measured light at the sealed crop cell was " + light + ", expected well under "
                            + "the vanilla wheat threshold of " + WHEAT_LIGHT_THRESHOLD
                            + " (farmland floor + solid stone on every other face)");

            BlockState placed = helper.getLevel().getBlockState(cropAbs);
            boolean survives = placed.canSurvive(helper.getLevel(), cropAbs);
            helper.assertTrue(survives,
                    "fungal spore crop should canSurvive with no light requirement -- measured light " + light);

            int ageBefore = placed.getValue(FungalSporeCropBlock.AGE);
            int attempts = 0;
            while (helper.getLevel().getBlockState(cropAbs).getValue(FungalSporeCropBlock.AGE) == ageBefore
                    && attempts < GROWTH_ATTEMPTS) {
                helper.randomTick(cropRel);
                attempts++;
            }
            int ageAfter = helper.getLevel().getBlockState(cropAbs).getValue(FungalSporeCropBlock.AGE);
            helper.assertTrue(ageAfter > ageBefore,
                    "fungal spore crop should advance age via randomTick at light " + light + " within "
                            + GROWTH_ATTEMPTS + " attempts -- stayed at age " + ageAfter);

            helper.succeed();
        });
    }
}
