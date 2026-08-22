package com.nogal.formicary.client;

import javax.annotation.Nullable;

import com.nogal.formicary.client.model.ChitinArmorModel;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Swaps the Chitin Helmet's armor model for {@link ChitinArmorModel}, so the
 * helmet carries real antenna geometry instead of a painted squiggle.
 *
 * <p>The hook is
 * {@code IClientItemExtensions#getHumanoidArmorModel(LivingEntity, ItemStack,
 * EquipmentSlot, HumanoidModel)}, verified against
 * {@code reference/net/neoforged/neoforge/client/extensions/common/IClientItemExtensions.java}
 * re-extracted from {@code neoforge-21.0.167-sources.jar} (the class was absent
 * from the partial extraction; see {@code docs/gotchas/reference-extraction.md}).
 * Overriding this narrower hook rather than {@code getGenericArmorModel} is
 * deliberate: the generic one's default body is what calls
 * {@code ClientHooks.copyModelProperties(original, replacement)}, which copies
 * the parent model's part poses and the per-slot part visibility
 * {@code HumanoidArmorLayer#setPartVisibility} had already written onto the
 * original. Take over the generic hook and that copying becomes ours to do.
 *
 * <p>Registered on the HELMET only -- see {@link ChitinArmorModel}'s javadoc for
 * why handing this outer-deformation model to the leggings would fatten them.
 *
 * <p>This is a client-only type. It is referenced only from
 * {@link ModClientEvents}, which is gated {@code value = Dist.CLIENT}, so a
 * dedicated server never loads it.
 */
public final class ChitinArmorClientExtensions implements IClientItemExtensions {
    public static final ChitinArmorClientExtensions INSTANCE = new ChitinArmorClientExtensions();

    @Nullable
    private HumanoidModel<LivingEntity> model;

    private ChitinArmorClientExtensions() {
    }

    /**
     * (Re)bake the helmet model. Called from
     * {@code EntityRenderersEvent.AddLayers}, which fires once at client start
     * and again after every resource reload -- the same points at which the
     * {@link EntityModelSet} itself is rebuilt, so the cached model never
     * outlives the model set it was baked from.
     */
    public void rebake(EntityModelSet models) {
        this.model = new ChitinArmorModel(models.bakeLayer(ChitinArmorModel.LAYER_LOCATION));
    }

    @Override
    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                  EquipmentSlot equipmentSlot,
                                                  HumanoidModel<?> original) {
        // Falling back to `original` is not just defensive tidiness: returning
        // it is how the hook says "no replacement", and the caller then skips
        // copyModelProperties entirely rather than copying onto null.
        return this.model != null ? this.model : original;
    }
}
