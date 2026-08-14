package com.nogal.formicary.datagen;

import java.util.List;
import java.util.stream.Stream;

import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Loot tables for the M2/M3a castes: worker and soldier drop chitin (looting-sensitive,
 * mirroring vanilla's {@code EnchantedCountIncreaseFunction.lootingMultiplier} convention
 * for entity drops -- see {@code VanillaEntityLoot}), the soldier has a chance at resin
 * too, and the larva drops nothing (it's meant to be captured, not killed). M4b adds the
 * Scent Gland (brewed into Pheromonal Disguise) to both castes.
 *
 * <p>{@link #getKnownEntityTypes()} is overridden to return only this mod's registered
 * entity types: the base implementation defaults to {@code BuiltInRegistries.ENTITY_TYPE}
 * (every entity type in the game), which would otherwise demand a loot table for every
 * vanilla mob too and throw "Missing loottable" for all of them -- the same trap
 * {@link ModBlockLootSubProvider#getKnownBlocks()} works around for blocks.
 */
public class ModEntityLootSubProvider extends EntityLootSubProvider {
    /** Worker Scent Gland drop chance. Tunable -- soldiers get the better odds (0.75F). */
    private static final float WORKER_SCENT_GLAND_CHANCE = 0.5F;

    /** Soldier Scent Gland drop chance. Tunable. */
    private static final float SOLDIER_SCENT_GLAND_CHANCE = 0.75F;

    public ModEntityLootSubProvider(HolderLookup.Provider registries) {
        super(FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    public void generate() {
        this.add(ModEntities.WORKER_ANT.get(),
                chitinTable(0.0F, 2.0F).withPool(scentGlandPool(WORKER_SCENT_GLAND_CHANCE)));
        this.add(ModEntities.SOLDIER_ANT.get(),
                soldierTable().withPool(scentGlandPool(SOLDIER_SCENT_GLAND_CHANCE)));
        this.add(ModEntities.LARVA.get(), LootTable.lootTable());
        // M6: a tamed ant drops the same chitin its wild counterpart does -- no Scent Gland
        // though, since that is the colony's pheromone signature and a tamed ant has been
        // out of the nest since it hatched. Its pack is returned separately by
        // TamedWorkerAntEntity.dropCustomDeathLoot, which is inventory, not loot.
        this.add(ModEntities.TAMED_WORKER_ANT.get(), chitinTable(0.0F, 2.0F));
        this.add(ModEntities.TAMED_SOLDIER_ANT.get(), soldierTable());
    }

    private LootTable.Builder chitinTable(float min, float max) {
        return LootTable.lootTable()
                .withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1.0F))
                                .add(
                                        LootItem.lootTableItem(ModItems.CHITIN.get())
                                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))
                                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(
                                                        this.registries, UniformGenerator.between(0.0F, 1.0F)))));
    }

    private LootTable.Builder soldierTable() {
        return chitinTable(1.0F, 2.0F)
                .withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1.0F))
                                .add(LootItem.lootTableItem(ModItems.RESIN.get()))
                                .when(LootItemRandomChanceCondition.randomChance(0.25F)));
    }

    private LootPool.Builder scentGlandPool(float chance) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(ModItems.SCENT_GLAND.get()))
                .when(LootItemRandomChanceCondition.randomChance(chance));
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return List.<EntityType<?>>of(
                ModEntities.WORKER_ANT.get(),
                ModEntities.SOLDIER_ANT.get(),
                ModEntities.LARVA.get(),
                ModEntities.TAMED_WORKER_ANT.get(),
                ModEntities.TAMED_SOLDIER_ANT.get()).stream();
    }
}
