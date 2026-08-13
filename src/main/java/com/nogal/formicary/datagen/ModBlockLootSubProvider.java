package com.nogal.formicary.datagen;

import java.util.Set;
import java.util.stream.Collectors;

import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.loot.WearingFullChitinCondition;

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
 * </ul>
 * Royal Comb and Fungal Bloom deliberately self-drop as M1 placeholders (Royal Jelly
 * lands in M7, spores in M8 -- see {@code docs/DECISIONS.md}).
 *
 * <p>{@link #getKnownBlocks()} is overridden to return only this mod's registered
 * blocks: the base implementation defaults to {@code BuiltInRegistries.BLOCK} (every
 * block in the game), which would otherwise demand a loot table for every vanilla
 * block too and throw "Missing loottable" for all of them.
 */
public class ModBlockLootSubProvider extends BlockLootSubProvider {
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
        dropSelf(ModBlocks.FUNGAL_BLOOM.get());
        dropSelf(ModBlocks.FUNGAL_CARPET.get());
        dropSelf(ModBlocks.BROOD_COMB.get());
        dropSelf(ModBlocks.ROYAL_COMB.get());
        dropSelf(ModBlocks.EGG_CLUSTER.get());
        dropSelf(ModBlocks.DAYLIGHT_MEMBRANE.get());
        dropSelf(ModBlocks.ANTHILL_SOIL.get());
        dropSelf(ModBlocks.ANTHILL_CORE.get());
        dropSelf(ModBlocks.QUEENS_CREST.get());

        dropWhenSilkTouch(ModBlocks.AMBER_GLASS.get());

        add(ModBlocks.RESIN_WEEP.get(), resinWeepTable());
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

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .map(DeferredHolder::get)
                .collect(Collectors.toList());
    }
}
