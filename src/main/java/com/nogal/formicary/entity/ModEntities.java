package com.nogal.formicary.entity;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity types for the colony's castes. M2 registers the worker only; the soldier,
 * larva and queen arrive in M3/M7.
 *
 * <p>No spawn placement is registered: the ant dimension does not exist yet, so the
 * spawn egg is the only way to get a worker into the world. M4 wires natural spawning
 * data-driven via biome modifiers.
 */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Formicary.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WorkerAntEntity>> WORKER_ANT =
            ENTITY_TYPES.register("worker_ant",
                    () -> EntityType.Builder.of(WorkerAntEntity::new, MobCategory.CREATURE)
                            .sized(0.9F, 0.6F)
                            .build("worker_ant"));

    /** Chitin red-brown shell, amber highlight -- the same pair the texture uses. */
    public static final DeferredItem<DeferredSpawnEggItem> WORKER_ANT_SPAWN_EGG =
            ModItems.ITEMS.registerItem("worker_ant_spawn_egg",
                    props -> new DeferredSpawnEggItem(WORKER_ANT, 0x783C1E, 0xE8A040, props));

    @EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerAttributes(EntityAttributeCreationEvent event) {
            event.put(WORKER_ANT.get(), WorkerAntEntity.createAttributes().build());
        }
    }

    private ModEntities() {
    }
}
