package com.nogal.formicary.datagen;

import java.util.Set;
import java.util.stream.Collectors;

import com.nogal.formicary.block.FungalSporeBlock;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.loot.WearingFullChitinCondition;

import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlockLootSubProvider extends BlockLootSubProvider {

    private static final float ROYAL_COMB_JELLY = 1.0F;

    public ModBlockLootSubProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

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

    private LootTable.Builder fungalSporeTable() {
        var maturePredicate = LootItemBlockStatePropertyCondition
                .hasBlockStateProperties(ModBlocks.FUNGAL_SPORE.get())
                .setProperties(StatePropertiesPredicate.Builder.properties()
                        .hasProperty(FungalSporeBlock.AGE, 3));
        return LootTable.lootTable()
                // Pool 1: always yield 1 spore (the seed)
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_SPORE.get())))
                // Pool 2: mature-only bloom
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_BLOOM.get()))
                        .when(maturePredicate))
                // Pool 3: 50% chance of a second spore at maturity
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_SPORE.get()))
                        .when(maturePredicate)
                        .when(LootItemRandomChanceCondition.randomChance(0.5F)));
    }

    @Override
    protected void generate() {
        dropSelfWearingFullChitin(ModBlocks.PACKED_SOIL.get());
        dropSelfWearingFullChitin(ModBlocks.AMBER_EARTH.get());
        dropSelfWearingFullChitin(ModBlocks.DEEP_LOAM.get());
        dropSelfWearingFullChitin(ModBlocks.HARDENED_SOIL.get());

        dropSelf(ModBlocks.RESIN_BLOCK.get());

        // M8: Fungal Bloom drops 0-1 spores (50% chance); no self-drop.
        add(ModBlocks.FUNGAL_BLOOM.get(), LootTable.lootTable().withPool(
                LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.FUNGAL_SPORE.get()))
                        .when(LootItemRandomChanceCondition.randomChance(0.5F))));

        dropSelf(ModBlocks.FUNGAL_CARPET.get());
        dropSelf(ModBlocks.BROOD_COMB.get());
        dropSelf(ModBlocks.EGG_CLUSTER.get());
        dropSelf(ModBlocks.DAYLIGHT_MEMBRANE.get());
        dropSelf(ModBlocks.ANTHILL_SOIL.get());
        dropSelf(ModBlocks.ANTHILL_CORE.get());
        dropSelf(ModBlocks.QUEENS_CREST.get());

        dropWhenSilkTouch(ModBlocks.AMBER_GLASS.get());

        add(ModBlocks.RESIN_WEEP.get(), resinWeepTable());

        add(ModBlocks.ROYAL_COMB.get(),
                createSingleItemTableWithSilkTouch(ModBlocks.ROYAL_COMB.get(), ModItems.ROYAL_JELLY.get(),
                        ConstantValue.exactly(ROYAL_COMB_JELLY)));

        // M8: Fungal Spore crop loot.
        add(ModBlocks.FUNGAL_SPORE.get(), fungalSporeTable());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .map(DeferredHolder::get)
                .collect(Collectors.toList());
    }
}
