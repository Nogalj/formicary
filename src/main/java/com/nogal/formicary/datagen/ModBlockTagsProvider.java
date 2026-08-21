package com.nogal.formicary.datagen;

import java.util.concurrent.CompletableFuture;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlockTags;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Writes {@code data/formicary/tags/block/*.json} for the tags declared in
 * {@link ModBlockTags}.
 *
 * <p>The hive membership list is authoritative per spec section 5 -- Brood Comb, Royal
 * Comb, Egg Cluster, Daylight Membrane, Anthill Core. Nothing else belongs: Resin Weep
 * and the fungus blocks are harvestable without provoking the colony.
 */
public class ModBlockTagsProvider extends BlockTagsProvider {
    /**
     * The NeoForge convention tag for crops, {@code c:crops} -- the cross-mod agreement that
     * lets {@link ModBlockTags#HARVESTABLE_CROPS} pick up modded crops without naming them.
     */
    private static final ResourceLocation CONVENTION_CROPS =
            ResourceLocation.fromNamespaceAndPath("c", "crops");

    public ModBlockTagsProvider(PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Formicary.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModBlockTags.HIVE).add(
                ModBlocks.BROOD_COMB.get(),
                ModBlocks.ROYAL_COMB.get(),
                ModBlocks.EGG_CLUSTER.get(),
                ModBlocks.DAYLIGHT_MEMBRANE.get(),
                ModBlocks.ANTHILL_CORE.get());

        tag(ModBlockTags.COLONY_FABRIC).add(
                ModBlocks.PACKED_SOIL.get(),
                ModBlocks.AMBER_EARTH.get(),
                ModBlocks.DEEP_LOAM.get(),
                ModBlocks.HARDENED_SOIL.get());

        // M6: the vanilla crop set a tamed worker farms. M8 adds the mod's own crop --
        // the wild fungal_bloom BUSH block deliberately stays out (it has no age property
        // and is foraging, not farming; see ModBlockLootSubProvider), but the crop it
        // grows into is exactly the kind of thing a bound worker should tend.
        //
        // Ep2 play-test revision, round 4 (item 6): pumpkin and melon join as the first
        // AGELESS members -- they have no age property, so CropHarvest.isMature reads them
        // as ripe by existence and CropHarvest.isReplantable keeps the harvest to
        // break-and-bank (the stem regrows the fruit; see those javadocs). Their stems are
        // NOT listed and could not be harvested if they were: CropHarvest.isHarvestable
        // guards StemBlock/AttachedStemBlock in code, ahead of the tag.
        tag(ModBlockTags.HARVESTABLE_CROPS).add(
                Blocks.WHEAT,
                Blocks.CARROTS,
                Blocks.POTATOES,
                Blocks.BEETROOTS,
                Blocks.NETHER_WART,
                Blocks.PUMPKIN,
                Blocks.MELON,
                ModBlocks.FUNGAL_SPORE_CROP.get())
                // ...and the same round's "crops added by other mods" half: any modded crop
                // that already tags itself with the NeoForge convention tag is farmed with no
                // opt-in at all, since ripeness is generic code rather than a per-block list.
                //
                // Written as a literal ResourceLocation rather than a Tags constant on
                // purpose. NeoForge 21.0.167 has CROPS only under Tags.Items -- Tags.Blocks
                // carries no CROPS at all (verified by reading the whole Blocks inner class
                // in reference/net/neoforged/neoforge/common/Tags.java, not from memory) --
                // and addOptionalTag takes a ResourceLocation regardless, so borrowing the
                // item tag's location would be a longer way of writing the same string while
                // implying a block/item relationship that does not exist.
                //
                // addOptionalTag, not addTag: an OPTIONAL reference is skipped when nothing
                // defines the tag, where a hard reference makes loading fail. On a vanilla
                // install with no other mod present, nothing defines c:crops.
                .addOptionalTag(CONVENTION_CROPS);

        tag(ModBlockTags.WORKER_DEPOSITS).add(
                Blocks.CHEST,
                Blocks.TRAPPED_CHEST,
                Blocks.BARREL);

        // Ep2 play-test revision, round 2 (WP-R3 item 2): the Mandible Pickaxe's digging
        // tag is the union of vanilla's own pickaxe and shovel tags -- addTag composition,
        // not a hand-copied block list, so it tracks either vanilla tag if it changes.
        tag(ModBlockTags.MINEABLE_WITH_MANDIBLE_PICKAXE)
                .addTag(BlockTags.MINEABLE_WITH_PICKAXE)
                .addTag(BlockTags.MINEABLE_WITH_SHOVEL);
    }
}
