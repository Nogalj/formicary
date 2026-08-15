package com.nogal.formicary.entity;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity types for the colony's castes. M2 registered the worker; M3a adds the soldier
 * and larva. The queen arrives in M7.
 *
 * <p>M4 adds spawn placements and the four tier biomes' spawn lists, so the castes now
 * populate the colony as its chunks generate.
 */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Formicary.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WorkerAntEntity>> WORKER_ANT =
            ENTITY_TYPES.register("worker_ant",
                    () -> EntityType.Builder.of(WorkerAntEntity::new, MobCategory.CREATURE)
                            .sized(0.9F, 0.6F)
                            .build("worker_ant"));

    /**
     * Play-test round 1, spec item 1: "soldiers more intimidating." 1.1x0.8 scaled by
     * {@link com.nogal.formicary.client.model.SoldierAntModel#RENDER_SCALE} (1.3) and
     * rounded to two places -- the hitbox tracks the renderer scale exactly so neither
     * drifts out of sync with the other. Combat stats untouched.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SoldierAntEntity>> SOLDIER_ANT =
            ENTITY_TYPES.register("soldier_ant",
                    () -> EntityType.Builder.of(SoldierAntEntity::new, MobCategory.CREATURE)
                            .sized(1.43F, 1.04F)
                            .build("soldier_ant"));

    public static final DeferredHolder<EntityType<?>, EntityType<LarvaEntity>> LARVA =
            ENTITY_TYPES.register("larva",
                    () -> EntityType.Builder.of(LarvaEntity::new, MobCategory.CREATURE)
                            .sized(0.45F, 0.35F)
                            .build("larva"));

    /**
     * The taming loop's output (M6). Separate entity types rather than a flag on the wild
     * ones: the tamed castes carry vanilla's whole {@code TamableAnimal} machinery, which
     * the wild colony has no business inheriting, and being outside the wild hierarchy is
     * exactly what makes {@code ColonyAnger.isColonyAnt} false for them.
     *
     * <p>No spawn eggs for either: an egg-spawned tamed ant would have no owner, which is
     * a broken object that half the goals here silently no-op on. The only way to get one
     * is to raise a larva.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<TamedWorkerAntEntity>> TAMED_WORKER_ANT =
            ENTITY_TYPES.register("tamed_worker_ant",
                    () -> EntityType.Builder.of(TamedWorkerAntEntity::new, MobCategory.CREATURE)
                            .sized(0.9F, 0.6F)
                            .build("tamed_worker_ant"));

    /** Matches {@link #SOLDIER_ANT}'s enlarged hitbox -- "tamed soldier should match." */
    public static final DeferredHolder<EntityType<?>, EntityType<TamedSoldierAntEntity>> TAMED_SOLDIER_ANT =
            ENTITY_TYPES.register("tamed_soldier_ant",
                    () -> EntityType.Builder.of(TamedSoldierAntEntity::new, MobCategory.CREATURE)
                            .sized(1.43F, 1.04F)
                            .build("tamed_soldier_ant"));

    /**
     * The boss (M7). Placed by {@code ColonyChunkGenerator} in a throne chamber and nowhere
     * else: no spawn placement, no spawn list entry, and deliberately no spawn egg, so the
     * only queen in a world is the one the chamber generated.
     *
     * <p>2.4 x 1.8 is a size, not a fit: her model's mandibles and abdomen tip overhang it
     * (she is about 3 blocks nose to tail), which is normal for a long mob and keeps the
     * collision box something that can path a 3-wide corridor.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<QueenAntEntity>> QUEEN_ANT =
            ENTITY_TYPES.register("queen_ant",
                    () -> EntityType.Builder.of(QueenAntEntity::new, MobCategory.MONSTER)
                            .sized(2.4F, 1.8F)
                            .fireImmune()
                            .clientTrackingRange(16)
                            .build("queen_ant"));

    /** Chitin red-brown shell, amber highlight -- the same pair the texture uses. */
    public static final DeferredItem<DeferredSpawnEggItem> WORKER_ANT_SPAWN_EGG =
            ModItems.ITEMS.registerItem("worker_ant_spawn_egg",
                    props -> new DeferredSpawnEggItem(WORKER_ANT, 0x783C1E, 0xE8A040, props));

    /** Deep maroon shell, near-black head highlight -- the soldier's armored palette. */
    public static final DeferredItem<DeferredSpawnEggItem> SOLDIER_ANT_SPAWN_EGG =
            ModItems.ITEMS.registerItem("soldier_ant_spawn_egg",
                    props -> new DeferredSpawnEggItem(SOLDIER_ANT, 0x5C1410, 0x1A0908, props));

    /** Pale cream shell, amber segment-line highlight -- the larva's palette. */
    public static final DeferredItem<DeferredSpawnEggItem> LARVA_SPAWN_EGG =
            ModItems.ITEMS.registerItem("larva_spawn_egg",
                    props -> new DeferredSpawnEggItem(LARVA, 0xEEE0C4, 0xDE9E46, props));

    @EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerAttributes(EntityAttributeCreationEvent event) {
            event.put(WORKER_ANT.get(), WorkerAntEntity.createAttributes().build());
            event.put(SOLDIER_ANT.get(), SoldierAntEntity.createAttributes().build());
            event.put(LARVA.get(), LarvaEntity.createAttributes().build());
            event.put(TAMED_WORKER_ANT.get(), TamedWorkerAntEntity.createAttributes().build());
            event.put(TAMED_SOLDIER_ANT.get(), TamedSoldierAntEntity.createAttributes().build());
            event.put(QUEEN_ANT.get(), QueenAntEntity.createAttributes().build());
        }

        /**
         * Without a registered placement an entity type falls back to
         * {@code SpawnPlacementTypes.NO_RESTRICTIONS}, which is literally "yes" to every
         * position -- ants would happily spawn inside solid soil. {@code ON_GROUND} is the
         * rule that actually wants a sturdy block underneath and two free blocks above.
         *
         * <p>The predicate itself is unconditional on purpose: the colony has no sky and
         * only 0.3 ambient light, so any light or surface test vanilla animals use would
         * reject every position in the dimension. Where ants may appear is decided entirely
         * by the four tier biomes' spawn lists.
         */
        @SubscribeEvent
        public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
            event.register(WORKER_ANT.get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, spawnType, pos, random) -> true,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
            event.register(SOLDIER_ANT.get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, spawnType, pos, random) -> true,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
            event.register(LARVA.get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, spawnType, pos, random) -> true,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    private ModEntities() {
    }
}
