package com.nogal.formicary.client.renderer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.QueenAntModel;
import com.nogal.formicary.entity.QueenAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class QueenAntRenderer extends MobRenderer<QueenAntEntity, QueenAntModel<QueenAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/queen_ant.png");

    public QueenAntRenderer(EntityRendererProvider.Context context) {
        super(context, new QueenAntModel<>(context.bakeLayer(QueenAntModel.LAYER_LOCATION)), 1.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(QueenAntEntity entity) {
        return TEXTURE;
    }
}
