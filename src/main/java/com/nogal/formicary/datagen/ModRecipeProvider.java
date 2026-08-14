package com.nogal.formicary.datagen;

import java.util.concurrent.CompletableFuture;

import com.nogal.formicary.item.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;

/**
 * The mod's crafting recipes. M5 adds the first one; the rest of the recipe book is M8.
 *
 * <p>Verified in the decompiled 21.0.167 sources: the hook is
 * {@code protected void buildRecipes(RecipeOutput)} (the {@code (RecipeOutput,
 * HolderLookup.Provider)} overload just delegates to it), and
 * {@code RecipeProvider}'s constructor takes the {@code CompletableFuture} of registries
 * rather than a resolved provider. Output lands in {@code data/formicary/recipe/} --
 * singular, like {@code loot_table} -- because {@code PackOutput
 * .createRegistryElementsPathProvider} derives the folder from the registry key.
 */
public class ModRecipeProvider extends RecipeProvider {

    /** How many Trail Pheromones one Scent Gland + Fungal Bloom makes. Tunable per spec. */
    public static final int TRAIL_PHEROMONE_PER_CRAFT = 2;

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        // Spec section 2: "Scent Gland + Fungal Bloom -> 2, tunable". Shapeless, because
        // there is nothing about the arrangement that should matter.
        ShapelessRecipeBuilder
                .shapeless(RecipeCategory.MISC, ModItems.TRAIL_PHEROMONE.get(), TRAIL_PHEROMONE_PER_CRAFT)
                .requires(ModItems.SCENT_GLAND.get())
                .requires(ModItems.FUNGAL_BLOOM.get())
                .unlockedBy(getHasName(ModItems.SCENT_GLAND.get()), has(ModItems.SCENT_GLAND.get()))
                .save(recipeOutput);
    }
}
