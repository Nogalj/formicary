package com.nogal.formicary.effect;

import com.nogal.formicary.Formicary;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The potion that carries {@link ModMobEffects#PHEROMONAL_DISGUISE}, registered against
 * {@code Registries.POTION} the way vanilla's own {@code Potions} class registers into
 * {@code BuiltInRegistries.POTION} (verified against the decompiled {@code Potions.java}
 * -- e.g. {@code NIGHT_VISION = register("night_vision", new Potion(new
 * MobEffectInstance(MobEffects.NIGHT_VISION, 3600)))}).
 *
 * <p>Brewed from Awkward Potion + Scent Gland; see {@link ModBrewingRecipes}. The
 * generic container mixes (regular -> splash -> lingering -> tipped arrow), registered
 * once for every potion by vanilla's {@code PotionBrewing.addVanillaMixes}
 * ({@code addContainerRecipe(Items.POTION, Items.GUNPOWDER, Items.SPLASH_POTION)} etc.),
 * apply to this potion automatically -- no extra registration needed for those variants.
 */
public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Formicary.MODID);

    /** 3600 ticks = 3 minutes. Tunable. */
    public static final int DURATION_TICKS = 3600;

    public static final DeferredHolder<Potion, Potion> PHEROMONAL_DISGUISE = POTIONS.register(
            "pheromonal_disguise",
            () -> new Potion(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, DURATION_TICKS)));

    private ModPotions() {
    }
}
