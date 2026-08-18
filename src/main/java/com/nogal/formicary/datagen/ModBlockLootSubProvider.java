package com.nogal.formicary.datagen;

import java.util.Set;
import java.util.stream.Collectors;

import com.nogal.formicary.block.FungalSporeCropBlock;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.loot.WearingFullChitinCondition;

import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
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
 *   <li>Resin Weep -- drops 1-2 resin, fortune-sensitive (does NOT regenerate in M1,
 *       it's a plain block that just breaks like any node).</li>
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
        dropSelf(ModBlocks.DAYLIGHT_MEMBRANE.get());
        dropSelf(ModBlocks.ANTHILL_SOIL.get());
        dropSelf(ModBlocks.ANTHILL_CORE.get());
        dropSelf(ModBlocks.QUEENS_CREST.get());

        dropWhenSilkTouch(ModBlocks.AMBER_GLASS.get());

        add(ModBlocks.RESIN_WEEP.get(), resinWeepTable());
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
        dropSelf(ModBlocks.PACKED_SOIL_BRICK_WALL.get());
        dropSelf(ModBlocks.HARDENED_SOIL_TILES.get());
        dropSelf(ModBlocks.HARDENED_SOIL_TILE_STAIRS.get());
        add(ModBlocks.HARDENED_SOIL_TILE_SLAB.get(), createSlabItemTable(ModBlocks.HARDENED_SOIL_TILE_SLAB.get()));
        dropSelf(ModBlocks.POLISHED_RESIN.get());
        dropSelf(ModBlocks.POLISHED_RESIN_STAIRS.get());
        add(ModBlocks.POLISHED_RESIN_SLAB.get(), createSlabItemTable(ModBlocks.POLISHED_RESIN_SLAB.get()));
    }

    /**
     * Provision Comb's loot (spec section 2/3, Ep2 D1): the affordability floor for the
     * exit economy. Every break -- no Silk Touch special case, per the task brief --
     * guarantees 1-2 ender pearls (so a player can always find a way out, even having
     * arrived on their last one) plus 1-2 of the three new colony foods, picked at random
     * from an equal-weight pool of the three entries: one roll draws one entry, so a
     * {@code UniformGenerator(1, 2)} roll count over three equally-weighted
     * {@code LootItem} entries is "1-2 of the three foods" exactly, the same
     * rolls-over-weighted-entries shape {@code fungalSporeCropTable}'s mature pool uses
     * for a single item's count. Deliberately not wrapped in {@code applyExplosionDecay}/
     * {@code applyExplosionCondition} -- {@code fungalSporeCropTable} (multi-item, like
     * this one) sets that precedent in this same file; {@code resinWeepTable} (single
     * item) is the one that wraps.
     */
    private LootTable.Builder provisionCombTable() {
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(Items.ENDER_PEARL)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))))
                .withPool(LootPool.lootPool()
                        .setRolls(UniformGenerator.between(1.0F, 2.0F))
                        .add(LootItem.lootTableItem(ModItems.HONEYED_COMB.get()))
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_STEW.get()))
                        .add(LootItem.lootTableItem(ModItems.ROYAL_JELLY_TREAT.get())));
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

    private LootTable.Builder resinWeepTable() {
        HolderLookup.RegistryLookup<Enchantment> enchantments = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.applyExplosionDecay(
                ModItems.RESIN.get(),
                LootTable.lootTable()
                        .withPool(
                                LootPool.lootPool()
                                        .setRolls(ConstantValue.exactly(1.0F))
                                        .add(LootItem.lootTableItem(ModItems.RESIN.get()))
                                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                                        .apply(ApplyBonusCount.addUniformBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE)))));
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
