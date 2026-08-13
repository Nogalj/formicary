package com.nogal.formicary.client.renderer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.SoldierAntModel;
import com.nogal.formicary.entity.SoldierAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SoldierAntRenderer extends MobRenderer<SoldierAntEntity, SoldierAntModel<SoldierAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/soldier_ant.png");

    public SoldierAntRenderer(EntityRendererProvider.Context context) {
        super(context, new SoldierAntModel<>(context.bakeLayer(SoldierAntModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierAntEntity entity) {
        return TEXTURE;
    }
}
