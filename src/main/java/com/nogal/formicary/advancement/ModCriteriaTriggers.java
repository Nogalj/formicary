package com.nogal.formicary.advancement;

import com.nogal.formicary.Formicary;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * This mod's custom advancement criterion triggers (spec section 8, M8).
 *
 * <p>Registry key verified against {@code net.minecraft.core.registries.Registries}:
 * {@code TRIGGER_TYPE = createRegistryKey("trigger_type")}, and
 * {@code BuiltInRegistries.TRIGGER_TYPES = registerSimple(Registries.TRIGGER_TYPE,
 * CriteriaTriggers::bootstrap)} -- the same shape {@code ModLootConditions} already uses
 * for {@code Registries.LOOT_CONDITION_TYPE}. Vanilla registers each trigger *instance*
 * directly (e.g. {@code CriteriaTriggers.TAME_ANIMAL = register("tame_animal", new
 * TameAnimalTrigger())}); a {@link DeferredRegister} against the same key is the mod-side
 * equivalent, registering a constructor reference instead of a bare {@code new}.
 */
public final class ModCriteriaTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGER_TYPES =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Formicary.MODID);

    /** Fired from {@code DepositToChestGoal} on a bound worker's first successful deposit. */
    public static final DeferredHolder<CriterionTrigger<?>, FirstHarvestTrigger> FIRST_HARVEST =
            TRIGGER_TYPES.register("first_harvest", FirstHarvestTrigger::new);

    /** Fired from {@code LarvaEntity#growInto} for whichever caste was just raised. */
    public static final DeferredHolder<CriterionTrigger<?>, CasteGrownTrigger> CASTE_GROWN =
            TRIGGER_TYPES.register("caste_grown", CasteGrownTrigger::new);

    private ModCriteriaTriggers() {
    }
}
