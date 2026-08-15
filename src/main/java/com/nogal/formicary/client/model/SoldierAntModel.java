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
 * Soldier ant: a visibly bulkier worker -- bigger armored head with a raised crest,
 * oversized mandibles, thicker thorax/gaster, longer legs.
 *
 * <p>Hand-translated from {@code assets-src/models.py}'s {@code SOLDIER_ANT} spec --
 * that python spec is the source of truth for every number below and it paints the
 * matching 64x32 atlas. Keep the two in step: change the spec, re-run it, then mirror
 * the change here.
 *
 * <p>Unlike the worker, the mandibles are their own {@link ModelPart}s (not baked into
 * the head's cube list) so {@link #setupAnim} can flex them independently. See the same
 * 1.21 rules documented on {@link WorkerAntModel}.
 */
public class SoldierAntModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "soldier_ant"), "main");

    /**
     * Play-test round 1, spec item 1: "the owner wants soldiers more intimidating."
     * Applied by both {@link com.nogal.formicary.client.renderer.SoldierAntRenderer} and
     * {@link com.nogal.formicary.client.renderer.TamedSoldierAntRenderer} via the vanilla
     * renderer-scale idiom ({@code LivingEntityRenderer#scale}, verified against
     * {@code SlimeRenderer#scale} in {@code reference/}) rather than the
     * {@code Attributes.SCALE} attribute, so it stays a pure render-time effect with no
     * side door into the attribute system. {@link com.nogal.formicary.entity.ModEntities}'s
     * {@code SOLDIER_ANT}/{@code TAMED_SOLDIER_ANT} hitbox is sized to the same factor by
     * hand so the two never drift apart.
     */
    public static final float RENDER_SCALE = 1.3F;

    private static final float REST_LEG_Z = 0.8378F;
    private static final float REST_LEG_Y_FRONT = -0.5236F;
    private static final float REST_LEG_Y_HIND = 0.5236F;
    private static final float REST_ANTENNA_X = 0.5236F;
    private static final float REST_ANTENNA_Z = 0.2618F;

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

    public SoldierAntModel(ModelPart root) {
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
                        .texOffs(0, 12).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 19.5F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(20, 12).addBox(-3.0F, -2.0F, -5.0F, 6.0F, 4.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 26).addBox(-1.5F, -3.0F, -3.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -0.5F, -2.5F));

        head.addOrReplaceChild("mandible_r",
                CubeListBuilder.create()
                        .texOffs(0, 21).addBox(-2.0F, -0.5F, -3.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.0F, 1.0F, -4.0F));

        head.addOrReplaceChild("mandible_l",
                CubeListBuilder.create()
                        .texOffs(0, 21).mirror().addBox(0.0F, -0.5F, -3.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(1.0F, 1.0F, -4.0F));

        head.addOrReplaceChild("antenna_r",
                CubeListBuilder.create()
                        .texOffs(10, 21).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.0F, -1.5F, -4.5F, REST_ANTENNA_X, 0.0F, -REST_ANTENNA_Z));

        head.addOrReplaceChild("antenna_l",
                CubeListBuilder.create()
                        .texOffs(10, 21).mirror().addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(2.0F, -1.5F, -4.5F, REST_ANTENNA_X, 0.0F, REST_ANTENNA_Z));

        body.addOrReplaceChild("gaster",
                CubeListBuilder.create()
                        .texOffs(14, 21).addBox(-1.0F, 0.5F, 2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-3.0F, -1.5F, 4.0F, 6.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.0F, 0.0F));

        CubeListBuilder legRight = CubeListBuilder.create()
                .texOffs(22, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F));
        CubeListBuilder legLeft = CubeListBuilder.create()
                .texOffs(22, 21).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false);

        body.addOrReplaceChild("leg_r1", legRight,
                PartPose.offsetAndRotation(-2.5F, 1.9F, -2.0F, 0.0F, REST_LEG_Y_FRONT, REST_LEG_Z));
        body.addOrReplaceChild("leg_r2", legRight,
                PartPose.offsetAndRotation(-2.5F, 1.9F, 0.0F, 0.0F, 0.0F, REST_LEG_Z));
        body.addOrReplaceChild("leg_r3", legRight,
                PartPose.offsetAndRotation(-2.5F, 1.9F, 2.0F, 0.0F, REST_LEG_Y_HIND, REST_LEG_Z));
        body.addOrReplaceChild("leg_l1", legLeft,
                PartPose.offsetAndRotation(2.5F, 1.9F, -2.0F, 0.0F, -REST_LEG_Y_FRONT, -REST_LEG_Z));
        body.addOrReplaceChild("leg_l2", legLeft,
                PartPose.offsetAndRotation(2.5F, 1.9F, 0.0F, 0.0F, 0.0F, -REST_LEG_Z));
        body.addOrReplaceChild("leg_l3", legLeft,
                PartPose.offsetAndRotation(2.5F, 1.9F, 2.0F, 0.0F, -REST_LEG_Y_HIND, -REST_LEG_Z));

        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        // Every rotation below is written absolutely, never read back and adjusted.
        this.head.yRot = netHeadYaw * DEG_TO_RAD;
        this.head.xRot = headPitch * DEG_TO_RAD;

        // Idle antenna bob -- same technique as the worker.
        float bob = Mth.cos(ageInTicks * 0.12F) * 0.13F;
        float sway = Mth.sin(ageInTicks * 0.09F) * 0.07F;
        this.antennaRight.xRot = REST_ANTENNA_X + bob;
        this.antennaRight.zRot = -REST_ANTENNA_Z - sway;
        this.antennaLeft.xRot = REST_ANTENNA_X - bob;
        this.antennaLeft.zRot = REST_ANTENNA_Z + sway;

        // Mandibles idle-open subtly, mirrored so they part and close together.
        float mandibleFlex = Mth.sin(ageInTicks * 0.045F) * 0.05F;
        this.mandibleRight.yRot = -mandibleFlex;
        this.mandibleLeft.yRot = mandibleFlex;

        // The gaster counter-swings a little as the ant walks.
        this.gaster.yRot = Mth.cos(limbSwing * 0.65F) * 0.10F * limbSwingAmount;

        for (int i = 0; i < this.legs.length; i++) {
            // 1.3 rather than the quadruped-standard 0.6662: ants scuttle (see the
            // worker model); the soldier steps a touch slower than the worker to
            // read heavier.
            float cycle = limbSwing * 1.3F + LEG_PHASE[i];
            this.legs[i].xRot = Mth.cos(cycle) * limbSwingAmount * 0.55F;
            this.legs[i].yRot = LEG_REST_Y[i];
            this.legs[i].zRot = LEG_REST_Z[i] + LEG_SIDE[i] * Mth.sin(cycle) * limbSwingAmount * 0.18F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color) {
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
