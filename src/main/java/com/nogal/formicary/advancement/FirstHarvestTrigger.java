package com.nogal.formicary.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * "First tamed harvest" (spec section 8, M8): fired from {@code DepositToChestGoal} the
 * first time a bound worker successfully empties any of its pack into its chest.
 *
 * <p>Fired from the real chest-deposit code rather than the chest-bind proxy the spec
 * offers as an acceptable fallback -- resolving the worker's owner as a
 * {@code ServerPlayer} (via {@code TamableAnimal#getOwner()}, exactly the check
 * {@code TamableAnimal} itself uses at line ~243 for its death message) was not the
 * rabbit hole the spec worried about, and binding a chest with nothing ever harvested
 * would otherwise satisfy an advancement named for the harvest. See
 * {@code docs/DECISIONS.md} for why this is not asserted in a GameTest: {@code
 * GameTestHelper.makeMockPlayer} returns a bare {@code Player}, not a {@code
 * ServerPlayer}, and has no {@code getAdvancements()} to listen on.
 *
 * <p>Modelled on vanilla's simplest triggers ({@code UsedEnderEyeTrigger}-shape): no
 * payload beyond the optional player predicate every {@link SimpleCriterionTrigger}
 * carries, because there is nothing else worth matching on.
 */
public class FirstHarvestTrigger extends SimpleCriterionTrigger<FirstHarvestTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                i -> i.group(
                                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player))
                        .apply(i, TriggerInstance::new));

        public static Criterion<TriggerInstance> firstHarvest() {
            return ModCriteriaTriggers.FIRST_HARVEST.get().createCriterion(new TriggerInstance(Optional.empty()));
        }
    }
}
