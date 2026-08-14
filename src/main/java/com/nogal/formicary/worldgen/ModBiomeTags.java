package com.nogal.formicary.worldgen;

import com.nogal.formicary.Formicary;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Biome tags this mod owns. Hand-written JSON under
 * {@code data/formicary/tags/worldgen/biome/} -- consistent with M4a's choice to
 * hand-write the four tier biome JSONs themselves rather than generate them, so there is
 * no matching datagen provider for this tag either.
 */
public final class ModBiomeTags {
    /**
     * The deep colony: Nurseries + Royal Depths, the tiers where soldiers are hostile on
     * sight per spec section 3 (see {@code ColonyAnger.isDeepTierHostileAt}).
     */
    public static final TagKey<Biome> DEEP_COLONY = create("deep_colony");

    private static TagKey<Biome> create(String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Formicary.MODID, path));
    }

    private ModBiomeTags() {
    }
}
