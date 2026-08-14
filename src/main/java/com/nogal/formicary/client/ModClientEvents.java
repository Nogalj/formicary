package com.nogal.formicary.client;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.LarvaModel;
import com.nogal.formicary.client.model.QueenAntModel;
import com.nogal.formicary.client.model.SoldierAntModel;
import com.nogal.formicary.client.model.WorkerAntModel;
import com.nogal.formicary.client.renderer.LarvaRenderer;
import com.nogal.formicary.client.renderer.QueenAntRenderer;
import com.nogal.formicary.client.renderer.SoldierAntRenderer;
import com.nogal.formicary.client.renderer.TamedSoldierAntRenderer;
import com.nogal.formicary.client.renderer.TamedWorkerAntRenderer;
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
        event.registerLayerDefinition(SoldierAntModel.LAYER_LOCATION, SoldierAntModel::createBodyLayer);
        event.registerLayerDefinition(LarvaModel.LAYER_LOCATION, LarvaModel::createBodyLayer);
        event.registerLayerDefinition(QueenAntModel.LAYER_LOCATION, QueenAntModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WORKER_ANT.get(), WorkerAntRenderer::new);
        event.registerEntityRenderer(ModEntities.SOLDIER_ANT.get(), SoldierAntRenderer::new);
        event.registerEntityRenderer(ModEntities.LARVA.get(), LarvaRenderer::new);
        event.registerEntityRenderer(ModEntities.TAMED_WORKER_ANT.get(), TamedWorkerAntRenderer::new);
        event.registerEntityRenderer(ModEntities.TAMED_SOLDIER_ANT.get(), TamedSoldierAntRenderer::new);
        event.registerEntityRenderer(ModEntities.QUEEN_ANT.get(), QueenAntRenderer::new);
    }

    private ModClientEvents() {
    }
}
