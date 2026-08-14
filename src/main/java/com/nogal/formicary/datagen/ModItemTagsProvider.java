package com.nogal.formicary.datagen;

import java.util.concurrent.CompletableFuture;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.item.ModItemTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Writes {@code data/formicary/tags/item/*.json}.
 *
 * <p>{@code ItemTagsProvider} is the one tag provider that needs a second future: it can
 * mirror a block tag into an item tag ({@code copy}), so it takes the block provider's
 * {@code contentsGetter()} as a dependency. Nothing here uses {@code copy} yet, but the
 * constructor demands the future either way -- {@code ModBlockTagsProvider} therefore has
 * to be built first in {@link DataGenerators} and handed over.
 */
public class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            CompletableFuture<TagsProvider.TagLookup<Block>> blockTags,
            ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, Formicary.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Spec section 4: "feeding the placed larva raw meat (use an item tag)".
        tag(ModItemTags.RAW_ANT_FOOD).add(
                Items.BEEF,
                Items.PORKCHOP,
                Items.CHICKEN,
                Items.MUTTON,
                Items.RABBIT,
                Items.COD,
                Items.SALMON);
    }
}
