package com.nogal.formicary.client.renderer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.LarvaModel;
import com.nogal.formicary.entity.LarvaEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class LarvaRenderer extends MobRenderer<LarvaEntity, LarvaModel<LarvaEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/larva.png");

    public LarvaRenderer(EntityRendererProvider.Context context) {
        super(context, new LarvaModel<>(context.bakeLayer(LarvaModel.LAYER_LOCATION)), 0.2F);
    }

    @Override
    public ResourceLocation getTextureLocation(LarvaEntity entity) {
        return TEXTURE;
    }
}
