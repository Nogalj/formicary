package com.nogal.formicary.client.renderer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.SoldierAntModel;
import com.nogal.formicary.entity.TamedSoldierAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The tamed soldier reuses the wild soldier's baked model and texture -- see
 * {@link TamedWorkerAntRenderer} for why no second layer definition is needed.
 */
public class TamedSoldierAntRenderer
        extends MobRenderer<TamedSoldierAntEntity, SoldierAntModel<TamedSoldierAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/soldier_ant.png");

    public TamedSoldierAntRenderer(EntityRendererProvider.Context context) {
        super(context, new SoldierAntModel<>(context.bakeLayer(SoldierAntModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TamedSoldierAntEntity entity) {
        return TEXTURE;
    }
}
