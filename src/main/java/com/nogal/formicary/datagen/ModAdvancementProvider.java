package com.nogal.formicary.datagen;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.advancement.CasteGrownTrigger;
import com.nogal.formicary.advancement.FirstHarvestTrigger;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.effect.ModPotions;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.worldgen.ModWorldgen;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.BrewedPotionTrigger;
import net.minecraft.advancements.critereon.ChangeDimensionTrigger;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntityTypePredicate;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.KilledTrigger;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * The {@code formicary} advancement tab (spec section 8, M8): a root plus six beats.
 *
 * <p>Datagen via NeoForge's {@link AdvancementProvider} rather than hand-written JSON --
 * the mod already datagens everything else (recipes, loot, tags, lang), and the
 * {@code IAdvancementBuilderExtension#save(Consumer, ResourceLocation, ExistingFileHelper)}
 * overload tracks each id with the {@code ExistingFileHelper} so a child can reference its
 * parent by {@link AdvancementHolder} instead of a bare string. {@code RecipeProvider}'s
 * own javadoc (see {@code ModRecipeProvider}) already noted the output folder for
 * {@code Registries.ADVANCEMENT} is {@code data/formicary/advancement/}.
 *
 * <p>Four of the six beats are pure vanilla triggers -- {@code changed_dimension},
 * {@code brewed_potion}, {@code inventory_changed}, {@code player_killed_entity} -- no mod
 * code beyond this JSON. The other two need a custom {@code CriterionTrigger}, registered
 * in {@code advancement.ModCriteriaTriggers} against {@code Registries.TRIGGER_TYPE} and
 * fired from the gameplay code that earns them: {@link FirstHarvestTrigger} from {@code
 * DepositToChestGoal}, {@link CasteGrownTrigger} from {@code LarvaEntity#growInto}.
 */
public class ModAdvancementProvider extends AdvancementProvider {
    public ModAdvancementProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
            ExistingFileHelper existingFileHelper) {
        super(output, registries, existingFileHelper, List.of(ModAdvancementProvider::generate));
    }

    private static void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver,
            ExistingFileHelper existingFileHelper) {
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(
                        ModBlocks.ANTHILL_CORE.get(),
                        Component.translatable("advancements.formicary.root.title"),
                        Component.translatable("advancements.formicary.root.description"),
                        ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png"),
                        AdvancementType.TASK, false, false, false)
                // Root advancements need >=1 criterion (Advancement.CRITERIA_CODEC rejects an
                // empty map); vanilla's own "story/root" solves this the same way -- a trivial
                // per-tick trigger that is effectively granted the moment the player exists.
                .addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
                .save(saver, id("root"), existingFileHelper);

        Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ModBlocks.ANTHILL_SOIL.get(),
                        Component.translatable("advancements.formicary.enter_dimension.title"),
                        Component.translatable("advancements.formicary.enter_dimension.description"),
                        null, AdvancementType.TASK, true, true, false)
                .addCriterion("entered_formicary",
                        ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(ModWorldgen.FORMICARY_LEVEL))
                .save(saver, id("enter_dimension"), existingFileHelper);

        Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ModItems.SCENT_GLAND.get(),
                        Component.translatable("advancements.formicary.brew_disguise.title"),
                        Component.translatable("advancements.formicary.brew_disguise.description"),
                        null, AdvancementType.TASK, true, true, false)
                .addCriterion("brewed_disguise", CriteriaTriggers.BREWED_POTION.createCriterion(
                        new BrewedPotionTrigger.TriggerInstance(Optional.empty(), Optional.of(ModPotions.PHEROMONAL_DISGUISE))))
                .save(saver, id("brew_disguise"), existingFileHelper);

        Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ModItems.LARVA.get(),
                        Component.translatable("advancements.formicary.capture_larva.title"),
                        Component.translatable("advancements.formicary.capture_larva.description"),
                        null, AdvancementType.TASK, true, true, false)
                .addCriterion("has_larva", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.LARVA.get()))
                .save(saver, id("capture_larva"), existingFileHelper);

        Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ModBlocks.QUEENS_CREST.get(),
                        Component.translatable("advancements.formicary.defeat_queen.title"),
                        Component.translatable("advancements.formicary.defeat_queen.description"),
                        null, AdvancementType.CHALLENGE, true, true, false)
                .addCriterion("killed_queen", KilledTrigger.TriggerInstance.playerKilledEntity(
                        EntityPredicate.Builder.entity().entityType(EntityTypePredicate.of(ModEntities.QUEEN_ANT.get()))))
                .save(saver, id("defeat_queen"), existingFileHelper);

        Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ModItems.CHITIN.get(),
                        Component.translatable("advancements.formicary.first_harvest.title"),
                        Component.translatable("advancements.formicary.first_harvest.description"),
                        null, AdvancementType.TASK, true, true, false)
                .addCriterion("first_harvest", FirstHarvestTrigger.TriggerInstance.firstHarvest())
                .save(saver, id("first_harvest"), existingFileHelper);

        Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ModItems.ROYAL_JELLY.get(),
                        Component.translatable("advancements.formicary.raise_both_castes.title"),
                        Component.translatable("advancements.formicary.raise_both_castes.description"),
                        null, AdvancementType.GOAL, true, true, false)
                .addCriterion("worker_grown", CasteGrownTrigger.TriggerInstance.caste(CasteGrownTrigger.Caste.WORKER))
                .addCriterion("soldier_grown", CasteGrownTrigger.TriggerInstance.caste(CasteGrownTrigger.Caste.SOLDIER))
                .requirements(AdvancementRequirements.Strategy.AND)
                .save(saver, id("raise_both_castes"), existingFileHelper);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Formicary.MODID, path);
    }
}
