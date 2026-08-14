package com.nogal.formicary;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModAdvancementTriggers {

    private ModAdvancementTriggers() {
    }

    private static DeferredHolder<CriterionTrigger<?>, SimpleTrigger> register(String name) {
        return TRIGGER_TYPES.register(name, SimpleTrigger::new);
    }

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGER_TYPES =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Formicary.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, SimpleTrigger> ENTER_FORMICARY_DIMENSION =
            register("enter_formicary_dimension");

    public static final DeferredHolder<CriterionTrigger<?>, SimpleTrigger> BREW_PHEROMONAL_DISGUISE =
            register("brew_pheromonal_disguise");

    public static final DeferredHolder<CriterionTrigger<?>, SimpleTrigger> CAPTURE_LARVA =
            register("capture_larva");

    public static final DeferredHolder<CriterionTrigger<?>, SimpleTrigger> DEFEAT_QUEEN_ANT =
            register("defeat_queen_ant");

    public static final DeferredHolder<CriterionTrigger<?>, SimpleTrigger> FIRST_TAMED_HARVEST =
            register("first_tamed_harvest");

    public static final DeferredHolder<CriterionTrigger<?>, SimpleTrigger> RAISE_BOTH_CASTES =
            register("raise_both_castes");

    public static final class SimpleTrigger extends SimpleCriterionTrigger<SimpleTrigger.Instance> {

        private static final SimpleTrigger.Instance DEFAULT_INSTANCE =
                new SimpleTrigger.Instance(Optional.empty());

        private SimpleTrigger() {
        }

        @Override
        public Codec<SimpleTrigger.Instance> codec() {
            return MapCodec.unit(DEFAULT_INSTANCE).codec();
        }

        public void trigger(ServerPlayer player) {
            super.trigger(player, instance -> true);
        }

        public record Instance(Optional<ContextAwarePredicate> player)
                implements SimpleCriterionTrigger.SimpleInstance {
        }
    }
}
