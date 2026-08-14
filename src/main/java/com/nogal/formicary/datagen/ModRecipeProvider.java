package com.nogal.formicary.datagen;

import java.util.concurrent.CompletableFuture;

import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The mod's crafting recipes. M5 adds the first two; M8 adds the rest.
 */
public class ModRecipeProvider extends RecipeProvider {

    public static final int TRAIL_PHEROMONE_PER_CRAFT = 2;
    public static final int PHEROMONE_HORN_PER_CRAFT = 1;

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder
                .shapeless(RecipeCategory.MISC, ModItems.TRAIL_PHEROMONE.get(), TRAIL_PHEROMONE_PER_CRAFT)
                .requires(ModItems.SCENT_GLAND.get())
                .requires(ModItems.FUNGAL_BLOOM.get())
                .unlockedBy(getHasName(ModItems.SCENT_GLAND.get()), has(ModItems.SCENT_GLAND.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder
                .shapeless(RecipeCategory.TOOLS, ModItems.PHEROMONE_HORN.get(), PHEROMONE_HORN_PER_CRAFT)
                .requires(ModItems.ROYAL_PHEROMONE_GLAND.get())
                .requires(ModItems.RESIN.get())
                .requires(ModItems.CHITIN.get())
                .unlockedBy(getHasName(ModItems.ROYAL_PHEROMONE_GLAND.get()),
                        has(ModItems.ROYAL_PHEROMONE_GLAND.get()))
                .save(recipeOutput);

        // --- M8 recipes ---

        // Chitin Armor: Helmet (5 chitin + 1 core): CCC / CKC
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CHITIN_HELMET.get())
                .define('C', ModItems.CHITIN.get())
                .define('K', ModBlocks.ANTHILL_CORE.get())
                .pattern("CCC")
                .pattern("CKC")
                .unlockedBy(getHasName(ModItems.CHITIN.get()), has(ModItems.CHITIN.get()))
                .save(recipeOutput);

        // Chestplate (8 chitin + 1 core): C_C / CKC / CCC
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CHITIN_CHESTPLATE.get())
                .define('C', ModItems.CHITIN.get())
                .define('K', ModBlocks.ANTHILL_CORE.get())
                .pattern("C C")
                .pattern("CKC")
                .pattern("CCC")
                .unlockedBy(getHasName(ModItems.CHITIN.get()), has(ModItems.CHITIN.get()))
                .save(recipeOutput);

        // Leggings (7 chitin + 1 core): CCC / CKC / C_C
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CHITIN_LEGGINGS.get())
                .define('C', ModItems.CHITIN.get())
                .define('K', ModBlocks.ANTHILL_CORE.get())
                .pattern("CCC")
                .pattern("CKC")
                .pattern("C C")
                .unlockedBy(getHasName(ModItems.CHITIN.get()), has(ModItems.CHITIN.get()))
                .save(recipeOutput);

        // Boots (4 chitin + 1 core): C_C / CKC
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CHITIN_BOOTS.get())
                .define('C', ModItems.CHITIN.get())
                .define('K', ModBlocks.ANTHILL_CORE.get())
                .pattern("C C")
                .pattern("CKC")
                .unlockedBy(getHasName(ModItems.CHITIN.get()), has(ModItems.CHITIN.get()))
                .save(recipeOutput);

        // Resin Weep -> Resin Block: 9 weep -> 1 block
        ShapelessRecipeBuilder
                .shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.RESIN_BLOCK.get(), 1)
                .requires(ModItems.RESIN_WEEP.get())
                .requires(ModItems.RESIN_WEEP.get())
                .requires(ModItems.RESIN_WEEP.get())
                .requires(ModItems.RESIN_WEEP.get())
                .requires(ModItems.RESIN_WEEP.get())
                .requires(ModItems.RESIN_WEEP.get())
                .requires(ModItems.RESIN_WEEP.get())
                .requires(ModItems.RESIN_WEEP.get())
                .requires(ModItems.RESIN_WEEP.get())
                .unlockedBy(getHasName(ModItems.RESIN_WEEP.get()), has(ModItems.RESIN_WEEP.get()))
                .save(recipeOutput, "resin_block_from_resin_weep");

        // Resin Block -> Resin Weep: 1 block -> 9 weep
        ShapelessRecipeBuilder
                .shapeless(RecipeCategory.MISC, ModItems.RESIN_WEEP.get(), 9)
                .requires(ModItems.RESIN_BLOCK.get())
                .unlockedBy(getHasName(ModItems.RESIN_BLOCK.get()), has(ModItems.RESIN_BLOCK.get()))
                .save(recipeOutput, "resin_weep_from_resin_block");

        // Smelt Resin Block -> Amber Glass
        SimpleCookingRecipeBuilder
                .smelting(Ingredient.of(ModBlocks.RESIN_BLOCK.get()), RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.AMBER_GLASS.get(), 0.1F, 200)
                .unlockedBy(getHasName(ModItems.RESIN_BLOCK.get()), has(ModItems.RESIN_BLOCK.get()))
                .save(recipeOutput);

        // Brood Comb: 1 Honeycomb + 1 Chitin -> 1 Brood Comb
        ShapelessRecipeBuilder
                .shapeless(RecipeCategory.DECORATIONS, ModBlocks.BROOD_COMB.get(), 1)
                .requires(Items.HONEYCOMB)
                .requires(ModItems.CHITIN.get())
                .unlockedBy(getHasName(ModItems.CHITIN.get()), has(ModItems.CHITIN.get()))
                .save(recipeOutput);

        // Fungal Carpet: 3 Fungal Bloom in a row -> 3 Fungal Carpet
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.FUNGAL_CARPET.get(), 3)
                .define('F', ModBlocks.FUNGAL_BLOOM.get())
                .pattern("FFF")
                .unlockedBy(getHasName(ModItems.FUNGAL_BLOOM.get()), has(ModBlocks.FUNGAL_BLOOM.get()))
                .save(recipeOutput);
    }
}
