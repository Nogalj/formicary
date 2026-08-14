package com.nogal.formicary.block;

import com.nogal.formicary.Formicary;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * M1 block set -- see spec section 5 and {@code docs/DECISIONS.md}.
 *
 * <p>Everything is registered through {@link DeferredRegister.Blocks} per the project's
 * hard rule (see {@code CLAUDE.md}: "Register all content with DeferredRegister").
 * Colony-anger / chitin-gate / hive-tag behavior is explicitly out of scope for M1
 * (that's M3) -- every block here uses plain vanilla mining properties.
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Formicary.MODID);

    // --- Palette / fabric soils (tier soils top -> bottom get visibly tougher) ---

    public static final DeferredBlock<Block> PACKED_SOIL = BLOCKS.registerSimpleBlock(
            "packed_soil",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .sound(SoundType.ROOTED_DIRT)
                    .strength(0.5F));

    public static final DeferredBlock<Block> AMBER_EARTH = BLOCKS.registerSimpleBlock(
            "amber_earth",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.ROOTED_DIRT)
                    .strength(0.5F));

    public static final DeferredBlock<Block> DEEP_LOAM = BLOCKS.registerSimpleBlock(
            "deep_loam",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN)
                    .sound(SoundType.ROOTED_DIRT)
                    .strength(0.5F));

    public static final DeferredBlock<Block> HARDENED_SOIL = BLOCKS.registerSimpleBlock(
            "hardened_soil",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .sound(SoundType.STONE)
                    .strength(2.0F, 6.0F));

    // --- Resources ---

    public static final DeferredBlock<Block> RESIN_WEEP = BLOCKS.registerSimpleBlock(
            "resin_weep",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .sound(SoundType.ROOTED_DIRT)
                    .strength(0.5F));

    public static final DeferredBlock<Block> RESIN_BLOCK = BLOCKS.registerSimpleBlock(
            "resin_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.HONEY_BLOCK)
                    .strength(0.8F));

    public static final DeferredBlock<TransparentBlock> AMBER_GLASS = BLOCKS.registerBlock(
            "amber_glass",
            TransparentBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .sound(SoundType.GLASS)
                    .strength(0.3F)
                    .noOcclusion());

    // --- Flora / light ---

    public static final DeferredBlock<FungalBloomBlock> FUNGAL_BLOOM = BLOCKS.registerBlock(
            "fungal_bloom",
            FungalBloomBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .sound(SoundType.FUNGUS)
                    .noCollission()
                    .instabreak()
                    .lightLevel(state -> 10));

    public static final DeferredBlock<CarpetBlock> FUNGAL_CARPET = BLOCKS.registerBlock(
            "fungal_carpet",
            CarpetBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .sound(SoundType.MOSS_CARPET)
                    .instabreak()
                    .lightLevel(state -> 4));

    /**
     * M8: the overworld Fungal Spore crop -- see {@link FungalSporeCropBlock}. Properties
     * mirror vanilla wheat's ({@code Blocks.WHEAT}) exactly except for the self-lit
     * {@code lightLevel}, which scales with the block's own age property.
     */
    public static final DeferredBlock<FungalSporeCropBlock> FUNGAL_SPORE_CROP = BLOCKS.registerBlock(
            "fungal_spore_crop",
            FungalSporeCropBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .noCollission()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.FUNGUS)
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(FungalSporeCropBlock::lightForAge));

    // --- Hive ---

    public static final DeferredBlock<Block> BROOD_COMB = BLOCKS.registerSimpleBlock(
            "brood_comb",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .sound(SoundType.HONEY_BLOCK)
                    .strength(0.6F)
                    .lightLevel(state -> 3));

    public static final DeferredBlock<Block> ROYAL_COMB = BLOCKS.registerSimpleBlock(
            "royal_comb",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .sound(SoundType.HONEY_BLOCK)
                    .strength(0.6F)
                    .lightLevel(state -> 6));

    public static final DeferredBlock<Block> EGG_CLUSTER = BLOCKS.registerSimpleBlock(
            "egg_cluster",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .sound(SoundType.HONEY_BLOCK)
                    .strength(0.4F)
                    .lightLevel(state -> 0));

    public static final DeferredBlock<Block> DAYLIGHT_MEMBRANE = BLOCKS.registerSimpleBlock(
            "daylight_membrane",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .sound(SoundType.AMETHYST)
                    .strength(0.3F)
                    .lightLevel(state -> 15));

    // --- Overworld ---

    public static final DeferredBlock<Block> ANTHILL_SOIL = BLOCKS.registerSimpleBlock(
            "anthill_soil",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PODZOL)
                    .sound(SoundType.ROOTED_DIRT)
                    .strength(0.5F));

    public static final DeferredBlock<Block> ANTHILL_CORE = BLOCKS.registerSimpleBlock(
            "anthill_core",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BLACK)
                    .sound(SoundType.STONE)
                    .strength(1.5F, 6.0F)
                    .lightLevel(state -> 7));

    // --- Trophy ---

    public static final DeferredBlock<Block> QUEENS_CREST = BLOCKS.registerSimpleBlock(
            "queens_crest",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .sound(SoundType.AMETHYST)
                    .strength(3.0F, 6.0F));

    private ModBlocks() {
    }
}
