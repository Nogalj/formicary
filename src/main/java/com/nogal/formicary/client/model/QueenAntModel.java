package com.nogal.formicary.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nogal.formicary.Formicary;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The queen (M7): the soldier's armoured language at roughly double scale, plus the one
 * thing no other caste has -- an egg-swollen gaster that is the visual mass of her.
 *
 * <p>Hand-translated from {@code assets-src/models.py}'s {@code QUEEN_ANT} spec, which is
 * the source of truth for every number below and paints the matching 128x64 atlas. Keep
 * the two in step: change the spec, re-run it, then mirror the change here.
 *
 * <p>The leg length and the rest splay are tied together -- the foot lands at
 * {@code 12 + 13 * cos(0.6109) = 22.7}, just above the model's ground plane at 24 -- so
 * changing one without the other lifts her off the floor. Same 1.21 entity-model rules as
 * {@link WorkerAntModel}: absolute rest poses every frame, int colour in
 * {@code renderToBuffer}, and the baked texture size must match the painted atlas.
 */
public class QueenAntModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "queen_ant"), "main");

    private static final float REST_LEG_Z = 0.6109F;
    private static final float REST_LEG_Y_FRONT = -0.4363F;
    private static final float REST_LEG_Y_HIND = 0.4363F;
    private static final float REST_ANTENNA_X = 0.5236F;
    private static final float REST_ANTENNA_Z = 0.2618F;

    /** The gaster's rest offset under {@code body}; the idle pulse is written around it. */
    private static final float GASTER_REST_Y = -2.0F;

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private final ModelPart body;
    public final ModelPart head;
    private final ModelPart mandibleRight;
    private final ModelPart mandibleLeft;
    private final ModelPart antennaRight;
    private final ModelPart antennaLeft;
    private final ModelPart gaster;
    private final ModelPart[] legs;

    private static final float[] LEG_REST_Y = {
            REST_LEG_Y_FRONT, 0.0F, REST_LEG_Y_HIND,
            -REST_LEG_Y_FRONT, 0.0F, -REST_LEG_Y_HIND
    };
    private static final float[] LEG_REST_Z = {
            REST_LEG_Z, REST_LEG_Z, REST_LEG_Z,
            -REST_LEG_Z, -REST_LEG_Z, -REST_LEG_Z
    };
    private static final float[] LEG_SIDE = { 1.0F, 1.0F, 1.0F, -1.0F, -1.0F, -1.0F };
    private static final float[] LEG_PHASE = {
            0.0F, (float) Math.PI, 0.0F,
            (float) Math.PI, 0.0F, (float) Math.PI
    };

    public QueenAntModel(ModelPart root) {
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.mandibleRight = this.head.getChild("mandible_r");
        this.mandibleLeft = this.head.getChild("mandible_l");
        this.antennaRight = this.head.getChild("antenna_r");
        this.antennaLeft = this.head.getChild("antenna_l");
        this.gaster = this.body.getChild("gaster");
        this.legs = new ModelPart[] {
                this.body.getChild("leg_r1"),
                this.body.getChild("leg_r2"),
                this.body.getChild("leg_r3"),
                this.body.getChild("leg_l1"),
                this.body.getChild("leg_l2"),
                this.body.getChild("leg_l3")
        };
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-7.0F, -5.0F, -7.0F, 14.0F, 10.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(72, 0).addBox(-6.0F, -4.5F, -11.0F, 12.0F, 9.0F, 11.0F, new CubeDeformation(0.0F))
                        .texOffs(94, 20).addBox(-3.0F, -6.5F, -8.0F, 6.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.5F, -7.0F));

        head.addOrReplaceChild("mandible_r",
                CubeListBuilder.create()
                        .texOffs(94, 27).addBox(-5.0F, -2.0F, -8.0F, 5.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.0F, 5.5F, -11.0F));

        head.addOrReplaceChild("mandible_l",
                CubeListBuilder.create()
                        .texOffs(94, 27).mirror().addBox(0.0F, -2.0F, -8.0F, 5.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(3.0F, 5.5F, -11.0F));

        head.addOrReplaceChild("antenna_r",
                CubeListBuilder.create()
                        .texOffs(64, 32).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.0F, -2.5F, -9.0F, REST_ANTENNA_X, 0.0F, -REST_ANTENNA_Z));

        head.addOrReplaceChild("antenna_l",
                CubeListBuilder.create()
                        .texOffs(64, 32).mirror().addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(3.0F, -2.5F, -9.0F, REST_ANTENNA_X, 0.0F, REST_ANTENNA_Z));

        body.addOrReplaceChild("gaster",
                CubeListBuilder.create()
                        .texOffs(72, 20).addBox(-3.0F, 3.0F, 5.0F, 6.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-9.0F, -4.0F, 9.0F, 18.0F, 14.0F, 18.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, GASTER_REST_Y, 0.0F));

        CubeListBuilder legRight = CubeListBuilder.create()
                .texOffs(56, 32).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 13.0F, 2.0F, new CubeDeformation(0.0F));
        CubeListBuilder legLeft = CubeListBuilder.create()
                .texOffs(56, 32).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 13.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false);

        body.addOrReplaceChild("leg_r1", legRight,
                PartPose.offsetAndRotation(-7.0F, 6.0F, -5.0F, 0.0F, REST_LEG_Y_FRONT, REST_LEG_Z));
        body.addOrReplaceChild("leg_r2", legRight,
                PartPose.offsetAndRotation(-7.0F, 6.0F, 0.0F, 0.0F, 0.0F, REST_LEG_Z));
        body.addOrReplaceChild("leg_r3", legRight,
                PartPose.offsetAndRotation(-7.0F, 6.0F, 5.0F, 0.0F, REST_LEG_Y_HIND, REST_LEG_Z));
        body.addOrReplaceChild("leg_l1", legLeft,
                PartPose.offsetAndRotation(7.0F, 6.0F, -5.0F, 0.0F, -REST_LEG_Y_FRONT, -REST_LEG_Z));
        body.addOrReplaceChild("leg_l2", legLeft,
                PartPose.offsetAndRotation(7.0F, 6.0F, 0.0F, 0.0F, 0.0F, -REST_LEG_Z));
        body.addOrReplaceChild("leg_l3", legLeft,
                PartPose.offsetAndRotation(7.0F, 6.0F, 5.0F, 0.0F, -REST_LEG_Y_HIND, -REST_LEG_Z));

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        // Every rotation and offset below is written absolutely, never read back and
        // adjusted -- nothing resets a plain EntityModel's parts between frames.
        this.head.yRot = netHeadYaw * DEG_TO_RAD;
        this.head.xRot = headPitch * DEG_TO_RAD;

        float bob = Mth.cos(ageInTicks * 0.09F) * 0.11F;
        float sway = Mth.sin(ageInTicks * 0.07F) * 0.06F;
        this.antennaRight.xRot = REST_ANTENNA_X + bob;
        this.antennaRight.zRot = -REST_ANTENNA_Z - sway;
        this.antennaLeft.xRot = REST_ANTENNA_X - bob;
        this.antennaLeft.zRot = REST_ANTENNA_Z + sway;

        // Mandibles work slowly and wide -- she is not in a hurry.
        float mandibleFlex = Mth.sin(ageInTicks * 0.035F) * 0.09F;
        this.mandibleRight.yRot = -mandibleFlex;
        this.mandibleLeft.yRot = mandibleFlex;

        // The idle the spec asks for: the gaster breathes, laying eggs even at rest. A
        // translation rather than a scale, because ModelPart has no scale of its own and
        // pushing one through the PoseStack would need a whole render layer for one bob.
        this.gaster.y = GASTER_REST_Y + Mth.sin(ageInTicks * 0.06F) * 0.45F;
        this.gaster.yRot = Mth.cos(limbSwing * 0.4F) * 0.07F * limbSwingAmount;
        this.gaster.xRot = Mth.sin(ageInTicks * 0.06F) * 0.02F;

        for (int i = 0; i < this.legs.length; i++) {
            // 0.55 rather than the soldier's 1.3: she is enormous, and a fast scuttle on
            // 13-pixel legs reads as a spider rather than as something that weighs a lot.
            float cycle = limbSwing * 0.55F + LEG_PHASE[i];
            this.legs[i].xRot = Mth.cos(cycle) * limbSwingAmount * 0.42F;
            this.legs[i].yRot = LEG_REST_Y[i];
            this.legs[i].zRot = LEG_REST_Z[i] + LEG_SIDE[i] * Mth.sin(cycle) * limbSwingAmount * 0.14F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color) {
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
