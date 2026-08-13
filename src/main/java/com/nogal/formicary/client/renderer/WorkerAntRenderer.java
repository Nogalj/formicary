package com.nogal.formicary.client.renderer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.WorkerAntModel;
import com.nogal.formicary.entity.WorkerAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class WorkerAntRenderer extends MobRenderer<WorkerAntEntity, WorkerAntModel<WorkerAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/worker_ant.png");

    public WorkerAntRenderer(EntityRendererProvider.Context context) {
        super(context, new WorkerAntModel<>(context.bakeLayer(WorkerAntModel.LAYER_LOCATION)), 0.4F);
        this.addLayer(new WorkerAntCarriedItemLayer(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(WorkerAntEntity entity) {
        return TEXTURE;
    }
}
