package com.nogal.formicary.item;

import com.nogal.formicary.Formicary;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Item tags this mod owns. The JSON behind them is written by
 * {@code datagen/ModItemTagsProvider} -- these constants are just the lookup keys.
 */
public final class ModItemTags {
    /**
     * The diet that grows a placed larva into a <em>soldier</em> rather than a worker
     * (spec section 4): raw meat. Deliberately this mod's own tag rather than vanilla's
     * {@code #minecraft:meat}, which also contains every cooked cut and the rotten flesh /
     * spider eye family -- "raw meat" is the spec's wording and the diet fork should not
     * silently widen when Mojang retunes a food tag.
     */
    public static final TagKey<Item> RAW_ANT_FOOD = create("raw_ant_food");

    private static TagKey<Item> create(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(Formicary.MODID, path));
    }

    private ModItemTags() {
    }
}
