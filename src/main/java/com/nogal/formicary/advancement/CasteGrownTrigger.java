package com.nogal.formicary.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

/**
 * "Raise both castes" (spec section 8, M8): one trigger type, fired from
 * {@code LarvaEntity#growInto} with which caste was just raised, so the "raise both"
 * advancement can hang two criteria off the same trigger -- one filtered to
 * {@link Caste#WORKER}, one to {@link Caste#SOLDIER} -- and require both (spec:
 * "requirements = ALL").
 *
 * <p>One Java trigger class rather than two mirrors {@code TameAnimalTrigger}'s shape
 * (payload beyond the player predicate, matched generically): the caste is data, not a
 * reason for a second class.
 */
public class CasteGrownTrigger extends SimpleCriterionTrigger<CasteGrownTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Caste caste) {
        this.trigger(player, instance -> instance.matches(caste));
    }

    public enum Caste implements StringRepresentable {
        WORKER("worker"),
        SOLDIER("soldier");

        public static final Codec<Caste> CODEC = StringRepresentable.fromEnum(Caste::values);

        private final String id;

        Caste(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<Caste> caste)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                i -> i.group(
                                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                                Caste.CODEC.optionalFieldOf("caste").forGetter(TriggerInstance::caste))
                        .apply(i, TriggerInstance::new));

        public static Criterion<TriggerInstance> caste(Caste caste) {
            return ModCriteriaTriggers.CASTE_GROWN.get()
                    .createCriterion(new TriggerInstance(Optional.empty(), Optional.of(caste)));
        }

        /** {@code Optional.empty()} matches either caste -- unused by the shipped advancement. */
        public boolean matches(Caste actual) {
            return this.caste.isEmpty() || this.caste.get() == actual;
        }
    }
}
