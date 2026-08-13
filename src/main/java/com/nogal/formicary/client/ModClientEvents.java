package com.nogal.formicary.client;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.WorkerAntModel;
import com.nogal.formicary.client.renderer.WorkerAntRenderer;
import com.nogal.formicary.entity.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only mod-bus handlers: bake the entity layer definitions and bind renderers.
 * Gated with {@code value = Dist.CLIENT} so none of this is loaded on a dedicated server.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WorkerAntModel.LAYER_LOCATION, WorkerAntModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WORKER_ANT.get(), WorkerAntRenderer::new);
    }

    private ModClientEvents() {
    }
}
