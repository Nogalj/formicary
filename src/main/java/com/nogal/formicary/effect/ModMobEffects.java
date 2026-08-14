package com.nogal.formicary.effect;

import com.nogal.formicary.Formicary;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Pheromonal Disguise effect (spec section 3): while active, the colony reads the
 * player as one of its own -- {@link com.nogal.formicary.colony.ColonyAnger#isDisguised}
 * is the one place that checks for it.
 *
 * <p>Registered against {@code Registries.MOB_EFFECT} the same way
 * {@code ModArmorMaterials} registers into {@code Registries.ARMOR_MATERIAL}. 1.21's
 * {@code MobEffect(MobEffectCategory, int)} constructor is {@code protected} --
 * verified against the decompiled {@code MobEffect.java}, whose only direct callers
 * ({@code MobEffects.java}) live in the same package -- so a mod-side registration has to
 * go through an (anonymous) subclass instead of calling the constructor directly; the
 * empty {@code {}} body below is exactly that subclass.
 *
 * <p>No custom tick behaviour: the effect is a pure marker the colony's AI reads, so a
 * bare {@code MobEffect} (no {@code applyEffectTick} override) is correct.
 */
public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Formicary.MODID);

    /** Amber, matching the resin/chitin item family's palette. Tunable. */
    private static final int DISGUISE_COLOR = 0xD08226;

    public static final DeferredHolder<MobEffect, MobEffect> PHEROMONAL_DISGUISE = MOB_EFFECTS.register(
            "pheromonal_disguise",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, DISGUISE_COLOR) {
            });

    private ModMobEffects() {
    }
}
