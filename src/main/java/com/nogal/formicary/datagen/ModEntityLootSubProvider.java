package com.nogal.formicary.datagen;

import java.util.List;
import java.util.stream.Stream;

import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Loot tables for the colony's castes. Play-test round 1 (spec item 1, "chitin is
 * soldier-only") narrowed chitin to {@link ModEntities#SOLDIER_ANT} and
 * {@link ModEntities#TAMED_SOLDIER_ANT} only -- it used to also drop from both worker
 * variants. The soldier's chitin is looting-sensitive (mirroring vanilla's {@code
 * EnchantedCountIncreaseFunction.lootingMultiplier} convention for entity drops -- see
 * {@code VanillaEntityLoot}) and has a chance at resin too; the larva drops nothing (it's
 * meant to be captured, not killed). M4b's Scent Gland (brewed into Pheromonal Disguise)
 * is unaffected by the chitin change: the wild worker keeps its pool exactly as before, and
 * the wild soldier keeps its own alongside its chitin. Neither tamed caste gets a Scent
 * Gland -- it is the colony's pheromone signature, and a tamed ant has been out of the
 * nest since it hatched.
 *
 * <p>{@link #getKnownEntityTypes()} is overridden to return only this mod's registered
 * entity types: the base implementation defaults to {@code BuiltInRegistries.ENTITY_TYPE}
 * (every entity type in the game), which would otherwise demand a loot table for every
 * vanilla mob too and throw "Missing loottable" for all of them -- the same trap
 * {@link ModBlockLootSubProvider#getKnownBlocks()} works around for blocks.
 */
public class ModEntityLootSubProvider extends EntityLootSubProvider {
    /**
     * Worker Scent Gland drop chance. Tunable -- soldiers get the better odds
     * ({@link #SOLDIER_SCENT_GLAND_CHANCE}). Halved from 0.5F in the Ep2 play-test
     * revision, round 2 (WP-R3 item 4): the gland is a farmed disguise ingredient, and
     * round 1's rate made it too easy to stockpile.
     */
    private static final float WORKER_SCENT_GLAND_CHANCE = 0.25F;

    /**
     * Soldier Scent Gland drop chance. Tunable. Down from 0.75F in the same round-2
     * revision as {@link #WORKER_SCENT_GLAND_CHANCE} -- soldiers still keep noticeably
     * better odds than a worker.
     */
    private static final float SOLDIER_SCENT_GLAND_CHANCE = 0.40F;

    /** The queen's Royal Jelly haul, before Looting. Spec says "several". Tunable. */
    private static final float QUEEN_JELLY_MIN = 3.0F;
    private static final float QUEEN_JELLY_MAX = 5.0F;

    public ModEntityLootSubProvider(HolderLookup.Provider registries) {
        super(FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    public void generate() {
        // Play-test round 1 (spec item 1): chitin is soldier-only now. The worker keeps
        // just its Scent Gland pool -- no chitin table at all.
        this.add(ModEntities.WORKER_ANT.get(),
                LootTable.lootTable().withPool(scentGlandPool(WORKER_SCENT_GLAND_CHANCE)));
        this.add(ModEntities.SOLDIER_ANT.get(),
                soldierTable().withPool(scentGlandPool(SOLDIER_SCENT_GLAND_CHANCE)));
        this.add(ModEntities.LARVA.get(), LootTable.lootTable());
        // A tamed worker drops nothing from this table: it never had a Scent Gland (see the
        // class javadoc), and it no longer has chitin either. Its pack is returned
        // separately by TamedWorkerAntEntity.dropCustomDeathLoot, which is inventory, not
        // loot.
        this.add(ModEntities.TAMED_WORKER_ANT.get(), LootTable.lootTable());
        // A tamed soldier keeps its chitin (+ resin chance) unchanged -- it was never in
        // scope for the redistribution, only the worker/tamed-worker chitin was.
        this.add(ModEntities.TAMED_SOLDIER_ANT.get(), soldierTable());
        this.add(ModEntities.QUEEN_ANT.get(), queenTable());
        this.add(ModEntities.ENDER_ANT.get(), enderAntTable());
    }

    /**
     * The ender ant's drop (Ep2 play-test revision -- pearl economy re-route, see
     * {@code docs/superpowers/plans/2026-08-19-ep2-playtest-revisions.md}): a guaranteed
     * ender pearl (+Looting).
     *
     * <p>One pool, one roll, an ender pearl at a constant count of 1 with the standard
     * Looting multiplier on top. This used to be a coin flip ({@code
     * UniformGenerator.between(0, 1)}, mirroring vanilla's enderman); the design now IS the
     * renewable exit floor -- every kill guarantees a way out, and 4-6 seeded ants per colony
     * (up from 2-3, see {@code COLONY_ENDER_ANTS_MIN/MAX}) make that floor reachable. Larder
     * pearls (section 2/3) drop to a rare bonus alongside it rather than being the guarantee
     * themselves.
     *
     * <p>Deliberately no chitin and no Scent Gland. Chitin is soldier-only since play-test
     * round 1, and the Scent Gland is the colony's own pheromone signature -- an ender ant
     * is not one of the colony's ants ({@code ColonyAnger.isColonyAnt} does not answer for
     * it), so a gland from one would brew a disguise out of the wrong smell.
     */
    private LootTable.Builder enderAntTable() {
        return LootTable.lootTable()
                .withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1.0F))
                                .add(
                                        LootItem.lootTableItem(Items.ENDER_PEARL)
                                                .apply(SetItemCountFunction.setCount(
                                                        ConstantValue.exactly(1.0F)))
                                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(
                                                        this.registries, UniformGenerator.between(0.0F, 1.0F)))));
    }

    /**
     * The queen's guaranteed drops (M7, spec section 3): "several Royal Jelly, one Queen's
     * Crest trophy block, and a Royal Pheromone Gland".
     *
     * <p>A loot table rather than {@code dropCustomDeathLoot} so Looting applies for free
     * and a datapack can retune the fight's payout. Each pool has constant rolls and no
     * conditions, which is what "guaranteed" means here -- unlike the caste tables above,
     * nothing is behind a {@code randomChance}. Looting is deliberately applied only to the
     * jelly: the Crest is a trophy and the gland is the horn's one and only ingredient, so
     * multiplying either would turn an enchantment into a balance decision.
     */
    private LootTable.Builder queenTable() {
        return LootTable.lootTable()
                .withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1.0F))
                                .add(
                                        LootItem.lootTableItem(ModItems.ROYAL_JELLY.get())
                                                .apply(SetItemCountFunction.setCount(
                                                        UniformGenerator.between(QUEEN_JELLY_MIN, QUEEN_JELLY_MAX)))
                                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(
                                                        this.registries, UniformGenerator.between(0.0F, 1.0F)))))
                .withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1.0F))
                                .add(LootItem.lootTableItem(ModBlocks.QUEENS_CREST.get())))
                .withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1.0F))
                                .add(LootItem.lootTableItem(ModItems.ROYAL_PHEROMONE_GLAND.get())));
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
                ModEntities.TAMED_SOLDIER_ANT.get(),
                ModEntities.QUEEN_ANT.get(),
                ModEntities.ENDER_ANT.get()).stream();
    }
}
