package com.nogal.formicary.item;

import com.nogal.formicary.block.FungalSporeBlock;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/**
 * M8: The planting seed for {@link FungalSporeBlock}. A plain {@link BlockItem} wrapping
 * the crop block -- right-clicking farmland or dirt with this item plants the spore crop.
 *
 * <p>Unlike most BlockItems the crop block itself is the {@code FungalSporeBlock}; the item
 * display name is "Fungal Spore" while the placed-crop state is invisible (it renders like
 * a seedling). The harvest at max-age drops 1 Fungal Bloom + 1-2 spores; younger breaks
 * yield the spore item only.
 */
public class FungalSporeItem extends BlockItem {
    public FungalSporeItem(Item.Properties properties) {
        super(ModBlocks.FUNGAL_SPORE.get(), properties);
    }
}
