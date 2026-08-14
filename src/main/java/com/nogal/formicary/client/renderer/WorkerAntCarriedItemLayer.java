package com.nogal.formicary.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nogal.formicary.client.model.WorkerAntModel;
import com.nogal.formicary.entity.CarriesItem;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the stack a worker is ferrying, clamped in its mandibles.
 *
 * <p>A plain {@code MobRenderer} draws nothing for the main-hand slot -- vanilla's
 * {@code ItemInHandLayer} needs an {@code ArmedModel}, which an ant is not -- so this
 * mirrors {@code FoxHeldItemLayer}: park the PoseStack on the head pivot, follow the
 * head's current rotation, then nudge forward to the jaws.
 *
 * <p>Generic over {@link CarriesItem} so the wild worker (one stack in its main hand) and
 * the tamed worker (a nine-slot pack) share the layer without either one's storage choice
 * reaching the renderer.
 *
 * <p>Offsets are in model space (+Y is DOWN, -Z is forward), so the translate below
 * moves the item down toward the mouth and out in front of the mandibles. The head
 * pivot is at model (0, 20, -2) -- {@code body} at y=20.5 plus {@code head} at -0.5/-2,
 * per {@code assets-src/models.py}.
 */
public class WorkerAntCarriedItemLayer<T extends Mob & CarriesItem> extends RenderLayer<T, WorkerAntModel<T>> {
    private static final float HEAD_PIVOT_Y = 20.0F / 16.0F;
    private static final float HEAD_PIVOT_Z = -2.0F / 16.0F;

    private final ItemInHandRenderer itemInHandRenderer;

    public WorkerAntCarriedItemLayer(RenderLayerParent<T, WorkerAntModel<T>> renderer,
            ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            T entity, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack carried = entity.getCarriedItem();
        if (carried.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, HEAD_PIVOT_Y, HEAD_PIVOT_Z);
        poseStack.mulPose(Axis.YP.rotationDegrees(netHeadYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(headPitch));
        poseStack.translate(0.0F, 0.09F, -0.36F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(0.55F, 0.55F, 0.55F);
        this.itemInHandRenderer.renderItem(entity, carried, ItemDisplayContext.GROUND, false,
                poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
