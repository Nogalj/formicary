package com.nogal.formicary.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.AcidSpitModel;
import com.nogal.formicary.entity.AcidSpitProjectile;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws {@link AcidSpitProjectile}. A near-verbatim copy of {@code LlamaSpitRenderer}
 * (verified in {@code reference/net/minecraft/client/renderer/entity/LlamaSpitRenderer
 * .java}) -- the same two rotations that point a blob along its flight path, and nothing
 * else, because nothing else about it moves.
 *
 * <p>{@code EntityRenderer}, not {@code MobRenderer}: a projectile is not a
 * {@code LivingEntity} and has neither shadow-radius handling nor the {@code scale(-1, -1,
 * 1)} flip a mob renderer applies. See {@link AcidSpitModel} for what that flip's absence
 * means for the model's geometry.
 */
public class AcidSpitRenderer extends EntityRenderer<AcidSpitProjectile> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/acid_spit.png");

    private final AcidSpitModel<AcidSpitProjectile> model;

    public AcidSpitRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new AcidSpitModel<>(context.bakeLayer(AcidSpitModel.LAYER_LOCATION));
    }

    @Override
    public void render(AcidSpitProjectile entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // -90 on the yaw is vanilla's: the model's "forward" is +X, and this is what turns
        // that into the direction the entity is actually travelling.
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        this.model.setupAnim(entity, partialTick, 0.0F, -0.1F, 0.0F, 0.0F);
        VertexConsumer consumer = bufferSource.getBuffer(this.model.renderType(TEXTURE));
        this.model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AcidSpitProjectile entity) {
        return TEXTURE;
    }
}
