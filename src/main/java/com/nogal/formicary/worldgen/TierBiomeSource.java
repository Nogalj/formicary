package com.nogal.formicary.worldgen;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * One biome per vertical tier band, chosen purely from Y.
 *
 * <p>1.21 biomes are 3D, so banding them vertically is a legitimate use of the biome
 * source rather than a hack -- it is how the Nether's cave biomes and the overworld's
 * deep-dark band work. Each band lines up exactly with the tier the generator carves and
 * paints, so the biome's fog/sky colours and its spawn list always agree with the rock the
 * player is standing in.
 *
 * <p><b>{@code getNoiseBiome} works in QUART coordinates</b> (one sample per 4x4x4 blocks),
 * not block coordinates -- verified against {@code BiomeSource#getBiomesWithin} and
 * {@code TheEndBiomeSource}, both of which convert with {@link QuartPos}. Forgetting that
 * would put the tier boundaries at y=12/24 instead of 48/96.
 *
 * <p>Play-test round 2 dropped the Upper Galleries, so this is three bands rather than four.
 * The conversion is unchanged and still load-bearing: {@link ColonyGeneratorTunables#tierIndex}
 * clamps to {@code TIER_COUNT - 1}, so a missed {@link QuartPos#toBlock} would not throw -- it
 * would silently paint the whole dimension in the Royal Depths' colours and spawn list.
 */
public class TierBiomeSource extends BiomeSource {

    public static final MapCodec<TierBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            Biome.CODEC.fieldOf("royal_depths").forGetter(source -> source.tiers[0]),
                            Biome.CODEC.fieldOf("nurseries").forGetter(source -> source.tiers[1]),
                            Biome.CODEC.fieldOf("fungal_gardens").forGetter(source -> source.tiers[2]))
                    .apply(instance, TierBiomeSource::new));

    /** Indexed bottom-up, matching {@link ColonyGeneratorTunables#tierIndex(int)}. */
    private final Holder<Biome>[] tiers;

    @SuppressWarnings("unchecked")
    public TierBiomeSource(Holder<Biome> royalDepths, Holder<Biome> nurseries,
            Holder<Biome> fungalGardens) {
        this.tiers = new Holder[] {royalDepths, nurseries, fungalGardens};
    }

    /** The biome for a tier index, for callers that already know the band (spawning). */
    public Holder<Biome> biomeForTier(int tier) {
        return this.tiers[tier];
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.tiers);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return this.tiers[ColonyGeneratorTunables.tierIndex(QuartPos.toBlock(quartY))];
    }
}
