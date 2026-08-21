package com.nogal.formicary.datagen;

import java.util.Set;
import java.util.stream.Collectors;

import com.nogal.formicary.block.FungalSporeCropBlock;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.loot.WearingFullChitinCondition;

import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Loot tables for the M1 block set. Everything self-drops except:
 * <ul>
 *   <li>Amber Glass -- vanilla-glass convention, self-drops only with silk touch.</li>
 *   <li>The four {@code #formicary:colony_fabric} soils (M3b) -- self-drop only for a
 *       player in the full Chitin Armor set.</li>
 *   <li>Egg Cluster (play-test round 1) -- drops no items on a normal break; its XP comes
 *       from {@link com.nogal.formicary.block.ModBlocks#EGG_CLUSTER} being a {@code
 *       DropExperienceBlock}, entirely outside this loot table (see that field's javadoc).
 *       Silk Touch takes the block whole instead and forfeits the XP -- the same {@code
 *       dropWhenSilkTouch} convention Amber Glass uses above, chosen deliberately over Royal
 *       Comb's {@code createSingleItemTableWithSilkTouch} pattern (below), which still drops
 *       something on a *non*-silk-touch break and would have undercut "drops no items".
 *       Silk Touch forfeiting the XP is not this table's choice either way: vanilla's own
 *       {@code SILK_TOUCH} enchantment zeroes block-break XP unconditionally (a {@code
 *       BLOCK_EXPERIENCE} effect of {@code SetValue(constant(0.0F))}, verified in {@code
 *       Enchantments.java}), so a silk-touched break was always going to forfeit it.</li>
 * </ul>
 * Fungal Bloom deliberately self-drops as an M1 placeholder (spores land in M8 -- see
 * {@code docs/DECISIONS.md}). Royal Comb's placeholder is gone: M7 makes it drop Royal
 * Jelly, or itself with Silk Touch.
 *
 * <p>{@link #getKnownBlocks()} is overridden to return only this mod's registered
 * blocks: the base implementation defaults to {@code BuiltInRegistries.BLOCK} (every
 * block in the game), which would otherwise demand a loot table for every vanilla
 * block too and throw "Missing loottable" for all of them.
 */
public class ModBlockLootSubProvider extends BlockLootSubProvider {
    /** Royal Jelly per Royal Comb broken without Silk Touch. Tunable per spec ("1"). */
    private static final float ROYAL_COMB_JELLY = 1.0F;

    /**
     * Chance a Provision Comb carries an ender pearl (play-test round 2). A bonus, not the
     * exit economy's floor -- that moved to the ender ant. See {@link #provisionCombTable}.
     */
    private static final float PROVISION_PEARL_CHANCE = 0.05F;

    public ModBlockLootSubProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        // The four #formicary:colony_fabric blocks: self-drop ONLY behind the full
        // chitin set (spec section 5). Break speed is gated separately in ChitinArmor.
        dropSelfWearingFullChitin(ModBlocks.PACKED_SOIL.get());
        dropSelfWearingFullChitin(ModBlocks.AMBER_EARTH.get());
        dropSelfWearingFullChitin(ModBlocks.DEEP_LOAM.get());
        dropSelfWearingFullChitin(ModBlocks.HARDENED_SOIL.get());

        dropSelf(ModBlocks.RESIN_BLOCK.get());
        // M8: wheat-style harvest (spec section 5) -- the wild bush always drops itself
        // (foraging it doesn't cost the plant) plus a chance at the seed that lets a
        // player start the farmable crop. No age property to condition on here -- that's
        // fungalSporeCropTable()'s job, for the crop this seed grows into.
        add(ModBlocks.FUNGAL_BLOOM.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModBlocks.FUNGAL_BLOOM.get())))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_SPORES.get()))
                        .when(LootItemRandomChanceCondition.randomChance(0.5F))));
        dropSelf(ModBlocks.FUNGAL_CARPET.get());
        dropSelf(ModBlocks.BROOD_COMB.get());
        // Play-test round 1 (spec item 3): no items on a normal break -- see the class
        // javadoc for the full silk-touch reasoning. XP comes from ModBlocks.EGG_CLUSTER
        // being a DropExperienceBlock, not from this table.
        dropWhenSilkTouch(ModBlocks.EGG_CLUSTER.get());
        // No entry for DAYLIGHT_MEMBRANE (play-test round 2, item 7): it went bedrock-class
        // -- strength(-1.0F, 3_600_000.0F) + noLootTable() -- and noLootTable() makes
        // Block#getLootTable() resolve to the shared BuiltInLootTables.EMPTY sentinel, not a
        // per-block path. BlockLootSubProvider#generate's own completeness check SKIPS any
        // block resolving to EMPTY (verified in reference/), so no entry is required; the
        // opposite was tried first and failed datagen outright -- add()/dropSelf() key their
        // builder by the block's OWN getLootTable(), so calling either here registers a
        // builder under the EMPTY key that no block-loop iteration ever claims, and
        // generate() throws "Created block loot tables for non-blocks: [minecraft:empty]" at
        // the end. Blocks.BEDROCK carries no loot-table datagen entry either, for the same
        // reason.
        dropSelf(ModBlocks.ANTHILL_SOIL.get());
        // No entry for ANTHILL_CORE (play-test round 4): it went noLootTable() -- see the
        // block's javadoc for why it must never reach a player's inventory, and the
        // DAYLIGHT_MEMBRANE comment above for why a noLootTable() block cannot carry a
        // datagen entry here (dropSelf would register a builder under the shared EMPTY
        // key and fail generate()'s completeness check).
        dropSelf(ModBlocks.QUEENS_CREST.get());

        dropWhenSilkTouch(ModBlocks.AMBER_GLASS.get());

        add(ModBlocks.FUNGAL_SPORE_CROP.get(), fungalSporeCropTable());

        // M7 replaces the M1 placeholder self-drop: breaking Royal Comb yields the jelly it
        // is full of (spec section 5). Silk Touch still lifts the block whole -- the comb is
        // a decorative build material as well as a jelly source, and gating that behind an
        // enchantment matches how every other "take the container, not the contents" block
        // in vanilla behaves. Royal Comb is hive-tagged, so taking it angers the colony
        // either way; that wiring is M3b's and is untouched here.
        add(ModBlocks.ROYAL_COMB.get(),
                createSingleItemTableWithSilkTouch(ModBlocks.ROYAL_COMB.get(), ModItems.ROYAL_JELLY.get(),
                        ConstantValue.exactly(ROYAL_COMB_JELLY)));

        // D1: the larder's stockpile block -- see provisionCombTable() for the reasoning.
        add(ModBlocks.PROVISION_COMB.get(), provisionCombTable());

        // Ep2 task H3: decorative families -- every shape drops itself, same as every
        // other plain building block in this table. Slabs use the inherited
        // createSlabItemTable() rather than plain dropSelf(): a broken DOUBLE slab
        // (SlabBlock.TYPE == DOUBLE, two slabs merged into one block) has to drop 2
        // items, not 1 -- the same vanilla shape every stone/wood slab's own loot table
        // uses (verified in the decompiled BlockLootSubProvider.createSlabItemTable).
        dropSelf(ModBlocks.PACKED_SOIL_BRICKS.get());
        dropSelf(ModBlocks.PACKED_SOIL_BRICK_STAIRS.get());
        add(ModBlocks.PACKED_SOIL_BRICK_SLAB.get(), createSlabItemTable(ModBlocks.PACKED_SOIL_BRICK_SLAB.get()));
        dropSelf(ModBlocks.HARDENED_SOIL_TILES.get());
        dropSelf(ModBlocks.HARDENED_SOIL_TILE_STAIRS.get());
        add(ModBlocks.HARDENED_SOIL_TILE_SLAB.get(), createSlabItemTable(ModBlocks.HARDENED_SOIL_TILE_SLAB.get()));
        dropSelf(ModBlocks.POLISHED_RESIN.get());
        dropSelf(ModBlocks.POLISHED_RESIN_STAIRS.get());
        add(ModBlocks.POLISHED_RESIN_SLAB.get(), createSlabItemTable(ModBlocks.POLISHED_RESIN_SLAB.get()));
    }

    /**
     * Provision Comb's loot: the colony's larder, reworked in play-test round 2 and
     * expanded further in round 2's follow-up revision (WP-R3 item 1).
     *
     * <p><b>The pearls moved out.</b> Ep2 made this block the exit economy's affordability
     * floor -- an unconditional 1-2 ender pearls per break, so a player who arrived on their
     * last one could always leave. Round 2 re-routes that floor to the ender ant (a
     * guaranteed pearl per kill, and 4-6 seeded per colony), which puts the way home behind
     * an encounter rather than behind a wall you already found. What is left here is a 5%
     * bonus: still a reason to break every comb in the room, no longer the reason.
     *
     * <p>Three pools, each a different shape on purpose:
     * <ul>
     *   <li><b>Pearls</b> -- one roll of exactly 1, behind {@code random_chance 0.05}. A
     *       chance condition rather than an {@code empty} entry with a weight, because at
     *       one entry the two are equivalent and the condition says what it means.</li>
     *   <li><b>Food</b> -- exactly 1 roll (down from 1-2 in round 2 -- WP-R3 item 1 tightens
     *       every break's per-item yield now that the sundries pool below adds its own 1-2
     *       rolls) over three weighted entries (Honeyed Comb 6, Fungal Stew 4, Royal Jelly
     *       Treat 1). The weights are the change from an equal-weight pool: an equal-weight
     *       pool made Royal Jelly Treat, the expensive one, as common as bread. 6/4/1 keeps
     *       every break useful and the treat a find.</li>
     *   <li><b>Sundries</b> -- 1-2 rolls over a single merged weighted table, replacing
     *       round 2's separate ore pool (which had its own heavy {@code empty} entry). WP-R3
     *       item 1: a larder is where a colony puts what it dragged home, and the old ore
     *       pool's ~29% miss rate undercut that -- every entry here is a real item and there
     *       is no {@code empty}, so the pool always pays out on every roll. The weights span
     *       building/farming odds-and-ends (stick 14 down to feather 6) through vanilla's
     *       own ore scarcity order (coal 16 down to emerald 1) to a rare set of loose leather
     *       armor pieces (1 each) -- the same "common junk down to a find" shape the old ore
     *       pool had, just without ever coming up empty. Net across both non-pearl pools:
     *       roughly 2-4 items per break (1 food + 1-2 sundries, each sundries roll itself
     *       worth 1-2 of an item where the entry says so).</li>
     * </ul>
     *
     * <p>Deliberately not wrapped in {@code applyExplosionDecay}/{@code applyExplosionCondition}
     * -- {@code fungalSporeCropTable} (multi-item, like this one) sets that precedent in this
     * same file.
     */
    private LootTable.Builder provisionCombTable() {
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(PROVISION_PEARL_CHANCE))
                        .add(LootItem.lootTableItem(Items.ENDER_PEARL)))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.HONEYED_COMB.get()).setWeight(6))
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_STEW.get()).setWeight(4))
                        .add(LootItem.lootTableItem(ModItems.ROYAL_JELLY_TREAT.get()).setWeight(1)))
                .withPool(LootPool.lootPool()
                        .setRolls(UniformGenerator.between(1.0F, 2.0F))
                        .add(sundriesEntry(Items.STICK, 14, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.FLINT, 10, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.ROTTEN_FLESH, 8, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.BONE, 8, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.STRING, 8, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.LEATHER, 6, 1.0F, 1.0F))
                        .add(sundriesEntry(Items.FEATHER, 6, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.COAL, 16, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.RAW_COPPER, 12, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.RAW_IRON, 10, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.RAW_GOLD, 5, 1.0F, 1.0F))
                        .add(sundriesEntry(Items.LAPIS_LAZULI, 4, 1.0F, 2.0F))
                        .add(sundriesEntry(Items.DIAMOND, 1, 1.0F, 1.0F))
                        .add(sundriesEntry(Items.EMERALD, 1, 1.0F, 1.0F))
                        .add(sundriesEntry(Items.LEATHER_HELMET, 1, 1.0F, 1.0F))
                        .add(sundriesEntry(Items.LEATHER_CHESTPLATE, 1, 1.0F, 1.0F))
                        .add(sundriesEntry(Items.LEATHER_LEGGINGS, 1, 1.0F, 1.0F))
                        .add(sundriesEntry(Items.LEATHER_BOOTS, 1, 1.0F, 1.0F)));
    }

    /**
     * One weighted entry of {@link #provisionCombTable}'s sundries pool. {@code min == max}
     * still goes through {@code SetItemCountFunction} rather than being left implicit, so
     * every row of that pool reads as the same kind of thing.
     */
    private static LootPoolSingletonContainer.Builder<?> sundriesEntry(Item item, int weight, float min, float max) {
        return LootItem.lootTableItem(item)
                .setWeight(weight)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }

    /**
     * {@code dropSelf} plus the {@code formicary:wearing_full_chitin} condition on the
     * pool, so the block yields nothing to a player without the set.
     */
    private void dropSelfWearingFullChitin(Block block) {
        add(block, LootTable.lootTable().withPool(
                this.applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(block))
                        .when(WearingFullChitinCondition.wearingFullChitin()))));
    }

    /**
     * The Fungal Spore crop's own loot (spec section 5/8, M8): mature (age {@link
     * FungalSporeCropBlock#MAX_AGE}) safely yields the bloom item plus 1-2 spores; an
     * immature break -- the plant was cut down early -- yields a single spore only, wheat-
     * style. Age-condition shape verified against vanilla's own {@code createCropDrops}
     * (a {@code BlockLootSubProvider} method this class inherits): {@code
     * LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(
     * StatePropertiesPredicate.Builder.properties().hasProperty(AGE, max))}, chained with
     * {@code .apply(...).when(...).otherwise(...)} in exactly that order -- see {@code
     * createDoublePlantWithSeedDrops} for the same three-method chain on a single entry.
     * A flat {@code UniformGenerator(1, 2)} is used instead of vanilla's Fortune-binomial
     * second pool: the spec asks for "1-2", not a fortune-scaled count, and Resin Weep
     * already sets the precedent in this file for a plain uniform range over Fortune math.
     */
    private LootTable.Builder fungalSporeCropTable() {
        Block crop = ModBlocks.FUNGAL_SPORE_CROP.get();
        LootItemCondition.Builder mature = LootItemBlockStatePropertyCondition.hasBlockStateProperties(crop)
                .setProperties(StatePropertiesPredicate.Builder.properties()
                        .hasProperty(FungalSporeCropBlock.AGE, FungalSporeCropBlock.MAX_AGE));
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(mature)
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_BLOOM.get())))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_SPORES.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                                .when(mature)
                                .otherwise(LootItem.lootTableItem(ModItems.FUNGAL_SPORES.get()))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .map(DeferredHolder::get)
                .collect(Collectors.toList());
    }
}
