package com.nogal.formicary.block;

import com.nogal.formicary.Formicary;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
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

    /**
     * D1: the larder's stockpile block -- carries the guaranteed exit pearls (see
     * {@code ModBlockLootSubProvider}). Hardness matches Brood Comb per the task brief;
     * texture is a recolor of the brood comb texture for now ({@code assets-src/blocks.py}).
     */
    public static final DeferredBlock<Block> PROVISION_COMB = BLOCKS.registerSimpleBlock(
            "provision_comb",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.HONEY_BLOCK)
                    .strength(0.6F)
                    .lightLevel(state -> 3));

    /** Egg Cluster's break-XP range (play-test round 1, spec item 3: "~3-7"). */
    private static final int EGG_CLUSTER_XP_MIN = 3;
    private static final int EGG_CLUSTER_XP_MAX = 7;

    /**
     * Play-test round 1 (spec item 3): "drops no items, pops XP". {@link DropExperienceBlock}
     * is vanilla's own class for exactly this (every ore uses it), and {@code
     * UniformInt.of(3, 7)} is the literal range diamond and emerald ore are constructed
     * with -- verified in the decompiled {@code Blocks.java}, not picked to match the spec
     * after the fact. Its XP is spawned by a NeoForge {@code BlockDropsEvent}
     * (see {@code CommonHooks.handleBlockDrops}), entirely independent of the block's own
     * loot table -- so the loot table below can be, and is, silk-touch-only. See {@code
     * ModBlockLootSubProvider} for the silk-touch decision.
     */
    public static final DeferredBlock<DropExperienceBlock> EGG_CLUSTER = BLOCKS.registerBlock(
            "egg_cluster",
            properties -> new DropExperienceBlock(UniformInt.of(EGG_CLUSTER_XP_MIN, EGG_CLUSTER_XP_MAX), properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .sound(SoundType.HONEY_BLOCK)
                    .strength(0.4F)
                    .lightLevel(state -> 0));

    /**
     * Play-test round 2, item 7: bedrock-class properties -- {@code strength(-1.0F,
     * 3_600_000.0F)} plus {@code noLootTable()}, the exact pair {@code Blocks.BEDROCK} uses
     * (verified in {@code reference/net/minecraft/world/level/block/Blocks.java}) -- so the
     * pearl toll at an anthill is the only way out of the colony, not a shovel.
     *
     * <p>Confirmed harmless to the pearl-exit mechanic by reading every place that touches
     * this block: {@code PortalEvents#onProjectileImpact} never breaks or replaces the
     * membrane at all -- it cancels the pearl, discards it, and calls
     * {@code AnthillPortal#exitColony}, which only ever teleports the player and never
     * touches a block. {@code AnthillPortal#openMembraneColumn} (the arrival-pocket punch)
     * writes the membrane with {@code ServerLevel#setBlock}, not a mining path -- and
     * {@code setBlock} does not consult hardness at all, only {@code getDestroyProgress}
     * (survival mining) and vanilla's own {@code Level#destroyBlock} (an unconditional
     * world-edit style removal used by pistons/explosions) do, neither of which either
     * class calls on this block. Hardness therefore gates a player's pickaxe and nothing
     * generation-time does.
     */
    public static final DeferredBlock<Block> DAYLIGHT_MEMBRANE = BLOCKS.registerSimpleBlock(
            "daylight_membrane",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .sound(SoundType.AMETHYST)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
                    .lightLevel(state -> 15));

    // --- Overworld ---

    public static final DeferredBlock<Block> ANTHILL_SOIL = BLOCKS.registerSimpleBlock(
            "anthill_soil",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PODZOL)
                    .sound(SoundType.ROOTED_DIRT)
                    .strength(0.5F));

    /**
     * The portal anchor: {@code PortalEvents} sends a pearl-thrower into the colony when
     * the pearl lands within {@code AnthillPortal.CORE_SEARCH_RADIUS} of one of these --
     * of ANY of these, wherever it sits, since {@code findCoreNear} matches the block
     * itself, not the generated structure. Play-test round 4: {@code noLootTable()},
     * because the block has no recipe and no creative-tab entry, so the old self-drop was
     * the one survival route to a core in hand -- i.e. to placing portable colony portals
     * anywhere. Still breakable (an anthill can be levelled); it just yields nothing.
     */
    public static final DeferredBlock<Block> ANTHILL_CORE = BLOCKS.registerSimpleBlock(
            "anthill_core",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BLACK)
                    .sound(SoundType.STONE)
                    .strength(1.5F, 6.0F)
                    .lightLevel(state -> 7)
                    .noLootTable());

    // --- Trophy ---

    public static final DeferredBlock<Block> QUEENS_CREST = BLOCKS.registerSimpleBlock(
            "queens_crest",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .sound(SoundType.AMETHYST)
                    .strength(3.0F, 6.0F));

    // --- Ep2 task H3: decorative families -- packed_soil_bricks (+ stairs/slab),
    // hardened_soil_tiles (+ stairs/slab), polished_resin (+ stairs/slab). Every shape in
    // a family reuses that family's parent block's exact BlockBehaviour.Properties
    // (mapColor/sound/strength), matching the task brief's "mineable tags matching
    // parents" -- and matching it literally, since none of Packed Soil / Hardened Soil /
    // Resin Block carry a mineable/* tag of their own (grep of src/generated confirms it),
    // so "matching" means carrying none here either, same as the parents.
    //
    // Deliberately NOT added to ModBlockTags.COLONY_FABRIC even though two parents
    // (Packed Soil, Hardened Soil) are: that tag gates the DIMENSION'S OWN raw terrain
    // behind the chitin set (spec section 5), and these are player-crafted building
    // blocks made FROM harvested fabric, not fabric themselves -- gating a build a player
    // made would punish building, not protect the dimension.
    //
    // Stairs/slab base states reference their OWN family's base block (not the raw
    // parent), the same way vanilla's oak_stairs point at oak_planks rather than at a log
    // -- and it is a safe forward reference: DeferredRegister iterates its entries in
    // registration order when the registry event fires, so by the time each shape's
    // supplier resolves, its family's base block (registered immediately above it, in the
    // same DeferredRegister) has already bound its DeferredBlock.
    //
    // Ep2 play-test revision (WP-1 item 4): packed_soil_brick_wall removed -- see
    // ModRecipeProvider/ModBlockStateProvider/ModItemModelProvider/ModBlockLootSubProvider
    // for the matching recipe/model/loot removals.

    private static final BlockBehaviour.Properties PACKED_SOIL_BRICK_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .sound(SoundType.ROOTED_DIRT)
            .strength(0.5F);

    public static final DeferredBlock<Block> PACKED_SOIL_BRICKS = BLOCKS.registerSimpleBlock(
            "packed_soil_bricks", PACKED_SOIL_BRICK_PROPERTIES);

    public static final DeferredBlock<StairBlock> PACKED_SOIL_BRICK_STAIRS = BLOCKS.registerBlock(
            "packed_soil_brick_stairs",
            properties -> new StairBlock(PACKED_SOIL_BRICKS.get().defaultBlockState(), properties),
            PACKED_SOIL_BRICK_PROPERTIES);

    public static final DeferredBlock<SlabBlock> PACKED_SOIL_BRICK_SLAB = BLOCKS.registerBlock(
            "packed_soil_brick_slab", SlabBlock::new, PACKED_SOIL_BRICK_PROPERTIES);

    private static final BlockBehaviour.Properties HARDENED_SOIL_TILE_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .sound(SoundType.STONE)
            .strength(2.0F, 6.0F);

    public static final DeferredBlock<Block> HARDENED_SOIL_TILES = BLOCKS.registerSimpleBlock(
            "hardened_soil_tiles", HARDENED_SOIL_TILE_PROPERTIES);

    public static final DeferredBlock<StairBlock> HARDENED_SOIL_TILE_STAIRS = BLOCKS.registerBlock(
            "hardened_soil_tile_stairs",
            properties -> new StairBlock(HARDENED_SOIL_TILES.get().defaultBlockState(), properties),
            HARDENED_SOIL_TILE_PROPERTIES);

    public static final DeferredBlock<SlabBlock> HARDENED_SOIL_TILE_SLAB = BLOCKS.registerBlock(
            "hardened_soil_tile_slab", SlabBlock::new, HARDENED_SOIL_TILE_PROPERTIES);

    private static final BlockBehaviour.Properties POLISHED_RESIN_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .sound(SoundType.HONEY_BLOCK)
            .strength(0.8F);

    public static final DeferredBlock<Block> POLISHED_RESIN = BLOCKS.registerSimpleBlock(
            "polished_resin", POLISHED_RESIN_PROPERTIES);

    public static final DeferredBlock<StairBlock> POLISHED_RESIN_STAIRS = BLOCKS.registerBlock(
            "polished_resin_stairs",
            properties -> new StairBlock(POLISHED_RESIN.get().defaultBlockState(), properties),
            POLISHED_RESIN_PROPERTIES);

    public static final DeferredBlock<SlabBlock> POLISHED_RESIN_SLAB = BLOCKS.registerBlock(
            "polished_resin_slab", SlabBlock::new, POLISHED_RESIN_PROPERTIES);

    private ModBlocks() {
    }
}
