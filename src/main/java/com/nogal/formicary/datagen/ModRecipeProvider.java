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

    /** How many Pheromone Horns one Royal Pheromone Gland makes. Tunable. */
    public static final int PHEROMONE_HORN_PER_CRAFT = 1;

    /** Storage-block convention (spec section 8, M8): 9 Resin <-> 1 Resin Block. */
    public static final int RESIN_PER_BLOCK = 9;

    /** Amber is fossilized resin (spec section 8): smelt a Resin Block into Amber Glass. */
    public static final float AMBER_GLASS_SMELT_XP = 0.35F;
    public static final int AMBER_GLASS_SMELT_TIME = 200;

    /** Vanilla's own wool->carpet economy (2 in, 3 out) -- see buildRecipes for why. */
    public static final int FUNGAL_CARPET_PER_CRAFT = 3;

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

        // Spec section 3: "gland + resin + chitin, tunable". Shapeless for the same reason
        // -- nothing about the arrangement should matter -- and unlocked by the gland, which
        // only the queen drops, so the recipe book cannot advertise it before the fight.
        ShapelessRecipeBuilder
                .shapeless(RecipeCategory.TOOLS, ModItems.PHEROMONE_HORN.get(), PHEROMONE_HORN_PER_CRAFT)
                .requires(ModItems.ROYAL_PHEROMONE_GLAND.get())
                .requires(ModItems.RESIN.get())
                .requires(ModItems.CHITIN.get())
                .unlockedBy(getHasName(ModItems.ROYAL_PHEROMONE_GLAND.get()),
                        has(ModItems.ROYAL_PHEROMONE_GLAND.get()))
                .save(recipeOutput);

        // M8 (spec section 8): recipes, kept modest per section 5's standing rule --
        // "decorative crafting for resin/amber/glass". Deliberately NOT reciped: royal
        // jelly, scent gland, larva, anthill core/soil, membrane -- those stay
        // drops/harvest/worldgen-only by design. Queen's Crest still has no recipe of its
        // own (still drop-only -- see ModBlockLootSubProvider) even though round 4 makes
        // it an INGREDIENT the two end-goal tool recipes consume (chitinToolRecipes).
        chitinPlateRecipe(recipeOutput);
        chitinArmorRecipes(recipeOutput);
        resinStorageRecipes(recipeOutput);
        amberGlassRecipe(recipeOutput);
        decorativeRecipes(recipeOutput);

        // Ep2 play-test revision (WP-1 item 2; reworked round 4 into end-goal, crest-gated
        // crafts -- see chitinToolRecipes' own javadoc): Mandible Pickaxe + Pincer Sword.
        chitinToolRecipes(recipeOutput);

        // Ep2 task H3: decorative families.
        decorativeFamilyRecipes(recipeOutput);

        // Ep2 task H4: the three colony foods, craftable.
        colonyFoodRecipes(recipeOutput);
    }

    /**
     * Ep2 task H4: the three D1 colony foods (already registered in {@link ModItems},
     * see that class's javadoc) become craftable. All three are shapeless -- nothing
     * about the arrangement should matter, the same reasoning {@code buildRecipes}
     * already gives for the Trail Pheromone and Pheromone Horn recipes above.
     */
    private void colonyFoodRecipes(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.HONEYED_COMB.get())
                .requires(ModItems.BROOD_COMB.get())
                .requires(Items.SUGAR)
                .unlockedBy(getHasName(ModItems.BROOD_COMB.get()), has(ModItems.BROOD_COMB.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.FUNGAL_STEW.get())
                .requires(Items.BOWL)
                .requires(ModItems.FUNGAL_BLOOM.get(), 2)
                .requires(ModItems.FUNGAL_SPORES.get())
                .unlockedBy(getHasName(ModItems.FUNGAL_BLOOM.get()), has(ModItems.FUNGAL_BLOOM.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.ROYAL_JELLY_TREAT.get())
                .requires(ModItems.ROYAL_JELLY.get())
                .requires(ModItems.HONEYED_COMB.get())
                .unlockedBy(getHasName(ModItems.ROYAL_JELLY.get()), has(ModItems.ROYAL_JELLY.get()))
                .save(recipeOutput);
    }

    /**
     * Round-4 play-test revision, item 3: the armor/tool crafting intermediate.
     * Shapeless, like netherite's own monster-part-plus-metal upgrade recipe -- nothing
     * about the arrangement should matter, the same reasoning every other shapeless
     * recipe in this class already gives. Unlocked by owning chitin, not the plate
     * itself (which would be circular).
     */
    private void chitinPlateRecipe(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CHITIN_PLATE.get())
                .requires(ModItems.CHITIN.get(), 2)
                .requires(Items.IRON_INGOT)
                .unlockedBy(getHasName(ModItems.CHITIN.get()), has(ModItems.CHITIN.get()))
                .save(recipeOutput);
    }

    /**
     * Round-4 play-test revision, item 2: the Pincer Sword and Mandible Pickaxe are now
     * end-goal items rather than early/mid crafts. Both use Chitin Plate heads (X) and a
     * Queen's Crest (C) as the guard/binding piece, replacing the old raw-chitin shapes
     * (2-over-1 sword, 3-over-2 pickaxe). {@code unlockedBy} keys off owning a crest, the
     * same advertise-after-the-fight logic {@code buildRecipes}'s Pheromone Horn recipe
     * uses (only the queen drops a crest, so the recipe book cannot advertise either tool
     * before that fight); one crest per tool means one queen kill per tool, which is
     * farmable end-game since colonies -- and their queens -- are infinite.
     */
    private void chitinToolRecipes(RecipeOutput recipeOutput) {
        String hasCrest = getHasName(ModItems.QUEENS_CREST.get());

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.PINCER_SWORD.get())
                .pattern("X")
                .pattern("C")
                .pattern("Y")
                .define('X', ModItems.CHITIN_PLATE.get())
                .define('C', ModItems.QUEENS_CREST.get())
                .define('Y', Items.STICK)
                .unlockedBy(hasCrest, has(ModItems.QUEENS_CREST.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.MANDIBLE_PICKAXE.get())
                .pattern("XCX")
                .pattern(" Y ")
                .pattern(" Y ")
                .define('X', ModItems.CHITIN_PLATE.get())
                .define('C', ModItems.QUEENS_CREST.get())
                .define('Y', Items.STICK)
                .unlockedBy(hasCrest, has(ModItems.QUEENS_CREST.get()))
                .save(recipeOutput);
    }

    /**
     * The four standard armor-shape recipes, deferred to M8 per the spec. Round-4 swaps
     * X from raw Chitin to Chitin Plate (see that item's own javadoc for the economy) --
     * shapes and yields are otherwise unchanged.
     */
    private void chitinArmorRecipes(RecipeOutput recipeOutput) {
        String hasPlate = getHasName(ModItems.CHITIN_PLATE.get());

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CHITIN_HELMET.get())
                .pattern("XXX")
                .pattern("X X")
                .define('X', ModItems.CHITIN_PLATE.get())
                .unlockedBy(hasPlate, has(ModItems.CHITIN_PLATE.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CHITIN_CHESTPLATE.get())
                .pattern("X X")
                .pattern("XXX")
                .pattern("XXX")
                .define('X', ModItems.CHITIN_PLATE.get())
                .unlockedBy(hasPlate, has(ModItems.CHITIN_PLATE.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CHITIN_LEGGINGS.get())
                .pattern("XXX")
                .pattern("X X")
                .pattern("X X")
                .define('X', ModItems.CHITIN_PLATE.get())
                .unlockedBy(hasPlate, has(ModItems.CHITIN_PLATE.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CHITIN_BOOTS.get())
                .pattern("X X")
                .pattern("X X")
                .define('X', ModItems.CHITIN_PLATE.get())
                .unlockedBy(hasPlate, has(ModItems.CHITIN_PLATE.get()))
                .save(recipeOutput);
    }

    /** Resin <-> Resin Block, the standard storage-block convention, both directions shapeless. */
    private void resinStorageRecipes(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.RESIN_BLOCK.get())
                .requires(ModItems.RESIN.get(), RESIN_PER_BLOCK)
                .unlockedBy(getHasName(ModItems.RESIN.get()), has(ModItems.RESIN.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.RESIN.get(), RESIN_PER_BLOCK)
                .requires(ModBlocks.RESIN_BLOCK.get())
                .unlockedBy(getHasName(ModBlocks.RESIN_BLOCK.get()), has(ModBlocks.RESIN_BLOCK.get()))
                .save(recipeOutput);
    }

    /** Amber is fossilized resin (spec section 8): smelt a Resin Block into Amber Glass. */
    private void amberGlassRecipe(RecipeOutput recipeOutput) {
        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(ModBlocks.RESIN_BLOCK.get()), RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.AMBER_GLASS.get(), AMBER_GLASS_SMELT_XP, AMBER_GLASS_SMELT_TIME)
                .unlockedBy(getHasName(ModBlocks.RESIN_BLOCK.get()), has(ModBlocks.RESIN_BLOCK.get()))
                .save(recipeOutput);
    }

    /**
     * Two decorative recipes (spec section 5's "decorative crafting" standing rule):
     * <ul>
     *   <li>Brood Comb from 4 Resin in a 2x2 square -- a literal reading of the spec's
     *       "4 resin in a square".</li>
     *   <li>Fungal Carpet, 3 for 2 Fungal Bloom items, in the exact 2-wide row shape
     *       vanilla uses for wool -&gt; carpet -- the same "carpet from the plant/wool
     *       above it" convention, just with a different source material.</li>
     * </ul>
     */
    private void decorativeRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.BROOD_COMB.get())
                .pattern("RR")
                .pattern("RR")
                .define('R', ModItems.RESIN.get())
                .unlockedBy(getHasName(ModItems.RESIN.get()), has(ModItems.RESIN.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.FUNGAL_CARPET.get(), FUNGAL_CARPET_PER_CRAFT)
                .pattern("FF")
                .define('F', ModItems.FUNGAL_BLOOM.get())
                .unlockedBy(getHasName(ModItems.FUNGAL_BLOOM.get()), has(ModItems.FUNGAL_BLOOM.get()))
                .save(recipeOutput);
    }

    /** Vanilla's own stairs yield: 6 source blocks (staircase pattern) -> 4 stairs. */
    private static final int STAIRS_PER_CRAFT = 4;
    /** Vanilla's own slab yield: 3 source blocks (single row) -> 6 slabs. */
    private static final int SLABS_PER_CRAFT = 6;
    /** Task brief H3: 4 source blocks in a 2x2 square makes 4 of the cut variant. */
    private static final int DECORATIVE_FAMILY_BASE_PER_CRAFT = 4;

    /**
     * Ep2 task H3: the three decorative families. Each base block is 4 of its parent
     * in a 2x2 square making 4 (a crafting-table-only ratio, the same "N in a square"
     * shape {@code decorativeRecipes} above already uses for Brood Comb, just 1:1
     * instead of 4:1); stairs/slab/wall use vanilla's own standard shapes and yields
     * exactly (verified against the well-known vanilla recipe book: 6 stairs, 3 slab,
     * 6 wall).
     */
    private void decorativeFamilyRecipes(RecipeOutput recipeOutput) {
        packedSoilBrickRecipes(recipeOutput);
        hardenedSoilTileRecipes(recipeOutput);
        polishedResinRecipes(recipeOutput);
    }

    private void packedSoilBrickRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PACKED_SOIL_BRICKS.get(),
                        DECORATIVE_FAMILY_BASE_PER_CRAFT)
                .pattern("XX")
                .pattern("XX")
                .define('X', ModBlocks.PACKED_SOIL.get())
                .unlockedBy(getHasName(ModBlocks.PACKED_SOIL.get()), has(ModBlocks.PACKED_SOIL.get()))
                .save(recipeOutput);

        String hasBricks = getHasName(ModBlocks.PACKED_SOIL_BRICKS.get());

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PACKED_SOIL_BRICK_STAIRS.get(),
                        STAIRS_PER_CRAFT)
                .pattern("X  ")
                .pattern("XX ")
                .pattern("XXX")
                .define('X', ModBlocks.PACKED_SOIL_BRICKS.get())
                .unlockedBy(hasBricks, has(ModBlocks.PACKED_SOIL_BRICKS.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PACKED_SOIL_BRICK_SLAB.get(),
                        SLABS_PER_CRAFT)
                .pattern("XXX")
                .define('X', ModBlocks.PACKED_SOIL_BRICKS.get())
                .unlockedBy(hasBricks, has(ModBlocks.PACKED_SOIL_BRICKS.get()))
                .save(recipeOutput);
    }

    private void hardenedSoilTileRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.HARDENED_SOIL_TILES.get(),
                        DECORATIVE_FAMILY_BASE_PER_CRAFT)
                .pattern("XX")
                .pattern("XX")
                .define('X', ModBlocks.HARDENED_SOIL.get())
                .unlockedBy(getHasName(ModBlocks.HARDENED_SOIL.get()), has(ModBlocks.HARDENED_SOIL.get()))
                .save(recipeOutput);

        String hasTiles = getHasName(ModBlocks.HARDENED_SOIL_TILES.get());

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.HARDENED_SOIL_TILE_STAIRS.get(),
                        STAIRS_PER_CRAFT)
                .pattern("X  ")
                .pattern("XX ")
                .pattern("XXX")
                .define('X', ModBlocks.HARDENED_SOIL_TILES.get())
                .unlockedBy(hasTiles, has(ModBlocks.HARDENED_SOIL_TILES.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.HARDENED_SOIL_TILE_SLAB.get(),
                        SLABS_PER_CRAFT)
                .pattern("XXX")
                .define('X', ModBlocks.HARDENED_SOIL_TILES.get())
                .unlockedBy(hasTiles, has(ModBlocks.HARDENED_SOIL_TILES.get()))
                .save(recipeOutput);
    }

    private void polishedResinRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.POLISHED_RESIN.get(),
                        DECORATIVE_FAMILY_BASE_PER_CRAFT)
                .pattern("XX")
                .pattern("XX")
                .define('X', ModBlocks.RESIN_BLOCK.get())
                .unlockedBy(getHasName(ModBlocks.RESIN_BLOCK.get()), has(ModBlocks.RESIN_BLOCK.get()))
                .save(recipeOutput);

        String hasPolished = getHasName(ModBlocks.POLISHED_RESIN.get());

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.POLISHED_RESIN_STAIRS.get(),
                        STAIRS_PER_CRAFT)
                .pattern("X  ")
                .pattern("XX ")
                .pattern("XXX")
                .define('X', ModBlocks.POLISHED_RESIN.get())
                .unlockedBy(hasPolished, has(ModBlocks.POLISHED_RESIN.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.POLISHED_RESIN_SLAB.get(),
                        SLABS_PER_CRAFT)
                .pattern("XXX")
                .define('X', ModBlocks.POLISHED_RESIN.get())
                .unlockedBy(hasPolished, has(ModBlocks.POLISHED_RESIN.get()))
                .save(recipeOutput);
    }
}
