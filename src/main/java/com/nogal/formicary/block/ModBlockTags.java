package com.nogal.formicary.block;

import com.nogal.formicary.Formicary;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags this mod owns. The JSON behind them is written by
 * {@code datagen/ModBlockTagsProvider} -- these constants are just the lookup keys.
 *
 * <p>Both tags are behavioural contracts, not cosmetic groupings, so later milestones
 * extend the colony's reactions purely by adding blocks to the tag JSON rather than by
 * editing Java.
 */
public final class ModBlockTags {
    /**
     * Structure of the colony itself. Breaking any of these angers every soldier inside
     * {@code ColonyAnger.ANGER_RADIUS} (spec section 3). Resource blocks -- Resin Weep,
     * Fungal Bloom / Carpet -- are deliberately NOT in here: harvesting is foraging, not
     * theft-of-structure, and the colony tolerates it.
     */
    public static final TagKey<Block> HIVE = create("hive");

    /**
     * The dimension's fabric: the three tier soils plus Hardened Soil. Without a full set
     * of Chitin Armor these mine 25x slower and drop nothing (spec section 5). M4's
     * generator adds its blocks here rather than touching the gate code.
     */
    public static final TagKey<Block> COLONY_FABRIC = create("colony_fabric");

    /**
     * What a tamed worker in work mode will harvest and replant (spec section 4, M6). The
     * tag decides <em>candidacy</em> only -- whether a candidate is ripe is worked out in
     * code by {@code entity.CropHarvest#isMature}, which understands vanilla's
     * {@code CropBlock} plus any block carrying an integer {@code age} property (which is
     * how Nether Wart joins without a special case).
     *
     * <p>A modded crop opts in by adding itself to this tag: as long as it has an
     * {@code age} property whose maximum means "ripe", drops a {@code BlockItem} of itself
     * as the seed, and can be re-placed at its default state, the harvester needs no code
     * change at all.
     */
    public static final TagKey<Block> HARVESTABLE_CROPS = create("harvestable_crops");

    /**
     * What a tamed worker can be bound to and deposit into (spec section 4, M6). Chest,
     * Trapped Chest and Barrel by default; a modded storage block opts in by joining the
     * tag, provided {@code HopperBlockEntity.getContainerAt} can see a {@code Container}
     * there (which is the same test a hopper makes).
     */
    public static final TagKey<Block> WORKER_DEPOSITS = create("worker_deposits");

    private static TagKey<Block> create(String path) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(Formicary.MODID, path));
    }

    private ModBlockTags() {
    }
}
