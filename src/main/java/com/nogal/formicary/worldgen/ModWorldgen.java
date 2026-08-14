package com.nogal.formicary.worldgen;

import com.mojang.serialization.MapCodec;
import com.nogal.formicary.Formicary;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The registry bridge that lets the data-driven dimension JSON name our Java worldgen.
 *
 * <p>{@code ChunkGenerator.CODEC} and {@code BiomeSource.CODEC} are dispatch codecs keyed on
 * the {@code "type"} field, resolved through
 * {@code BuiltInRegistries.CHUNK_GENERATOR} / {@code BuiltInRegistries.BIOME_SOURCE}. Those
 * registries hold {@code MapCodec} values, not generator instances -- so what gets
 * registered here is {@link ColonyChunkGenerator#CODEC} and {@link TierBiomeSource#CODEC}
 * themselves. Miss this and {@code data/formicary/dimension/formicary.json} fails to parse
 * at server start with an unknown-type error, taking the whole world load with it.
 *
 * <p>Registry key paths verified in {@code net.minecraft.core.registries.Registries}:
 * {@code worldgen/chunk_generator} and {@code worldgen/biome_source}.
 */
public final class ModWorldgen {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Formicary.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Formicary.MODID);

    static {
        CHUNK_GENERATORS.register("colony", () -> ColonyChunkGenerator.CODEC);
        BIOME_SOURCES.register("tier", () -> TierBiomeSource.CODEC);
    }

    /** {@code formicary:formicary} -- the id every worldgen file for the dimension uses. */
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, Formicary.MODID);

    /**
     * The level key M5 will teleport players to. Note the registry naming mismatch:
     * {@code Registries.LEVEL_STEM} is the key {@code minecraft:dimension} and loads from
     * {@code data/<ns>/dimension/}, while {@code Registries.DIMENSION} is the runtime level
     * registry that shares the same id.
     */
    public static final ResourceKey<Level> FORMICARY_LEVEL = ResourceKey.create(Registries.DIMENSION, ID);

    /** {@code data/formicary/dimension/formicary.json}. */
    public static final ResourceKey<LevelStem> FORMICARY_STEM = ResourceKey.create(Registries.LEVEL_STEM, ID);

    private ModWorldgen() {
    }
}
