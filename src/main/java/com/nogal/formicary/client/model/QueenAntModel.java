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
 * <p>The leg root height, leg length and rest splay are one number in three parts -- the
 * foot lands at {@code 9.5 + 16 * cos(0.6109) = 22.6}, just above the model's ground plane
 * at 24 -- so changing any one alone lifts her off the floor or sinks her into it. Same
 * 1.21 entity-model rules as {@link WorkerAntModel}: absolute rest poses every frame, int
 * colour in {@code renderToBuffer}, and the baked texture size must match the painted atlas.
 *
 * <p>The antenna segments are the one place this model nests parts more than one level
 * deep, and that is load-bearing rather than tidy: {@code setupAnim} sways the scape alone
 * and the pedicel and flagellum ride along with it. Rotating three parts about three
 * separate pivots by the same angle would pull a 15-unit chain visibly apart.
 */
public class QueenAntModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "queen_ant"), "main");

    private static final float REST_LEG_Z = 0.6109F;
    private static final float REST_LEG_Y_FRONT = -0.4363F;
    private static final float REST_LEG_Y_HIND = 0.4363F;

    /**
     * Leg geometry, mirroring {@code assets-src/models.py}'s {@code QUEEN_LEG_*}. The root
     * is inside the thorax on both axes: {@code X} 6 against the shell's own 7 buries the
     * 2-wide leg cube two units in, and {@code Y} 3.5 (relative to {@code body}, i.e.
     * absolute 9.5 against a thorax spanning 1..11) buries its top 1.5 units. Before this
     * the roots sat at 7 / 12 -- flush with the side plane and a whole unit BELOW the
     * underside, so every leg started in mid-air.
     */
    private static final float LEG_ROOT_X = 6.0F;
    private static final float LEG_ROOT_Y = 3.5F;
    private static final float LEG_LENGTH = 16.0F;

    /**
     * The three antenna hinges, in order out from the skull. The pitches add to
     * {@code 0.4363 + 0.5236 + 0.6109 = 1.5708} rad -- 90 degrees exactly -- which is the
     * entire point of the shape: the flagellum finishes level and forward, pointing at
     * whatever she is looking at, instead of standing up like the single straight spike
     * this replaced. Each pair matches {@code assets-src/models.py}'s
     * {@code QUEEN_ANTENNA_*} exactly; that file is the source of truth.
     */
    private static final float REST_ANTENNA_BASE_X = 0.4363F;
    private static final float REST_ANTENNA_BASE_Z = 0.2618F;
    private static final float REST_ANTENNA_MID_X = 0.5236F;
    private static final float REST_ANTENNA_MID_Z = 0.1745F;
    private static final float REST_ANTENNA_TIP_X = 0.6109F;
    private static final float ANTENNA_BASE_LENGTH = 6.0F;
    private static final float ANTENNA_MID_LENGTH = 5.0F;
    private static final float ANTENNA_TIP_LENGTH = 4.0F;

    /**
     * Play-test round 1, spec item 2: "rework to slimmer, tapered mandibles (e.g. two
     * thinner angled segments per side)." The tip curls toward the midline by this much;
     * sign is per-side (see {@link #createBodyLayer}). Matches
     * {@code assets-src/models.py}'s {@code QUEEN_MANDIBLE_TIP_ANGLE} exactly -- that file
     * is the source of truth, this is the hand translation.
     */
    private static final float MANDIBLE_TIP_ANGLE = 0.3491F;

    /** The gaster's rest offset under {@code body}; the idle pulse is written around it. */
    private static final float GASTER_REST_Y = -2.0F;

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private final ModelPart body;
    public final ModelPart head;
    private final ModelPart mandibleBaseRight;
    private final ModelPart mandibleTipRight;
    private final ModelPart mandibleBaseLeft;
    private final ModelPart mandibleTipLeft;
    private final ModelPart antennaBaseRight;
    private final ModelPart antennaMidRight;
    private final ModelPart antennaTipRight;
    private final ModelPart antennaBaseLeft;
    private final ModelPart antennaMidLeft;
    private final ModelPart antennaTipLeft;
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
        this.mandibleBaseRight = this.head.getChild("mandible_r_base");
        this.mandibleTipRight = this.head.getChild("mandible_r_tip");
        this.mandibleBaseLeft = this.head.getChild("mandible_l_base");
        this.mandibleTipLeft = this.head.getChild("mandible_l_tip");
        this.antennaBaseRight = this.head.getChild("antenna_r_base");
        this.antennaMidRight = this.antennaBaseRight.getChild("antenna_r_mid");
        this.antennaTipRight = this.antennaMidRight.getChild("antenna_r_tip");
        this.antennaBaseLeft = this.head.getChild("antenna_l_base");
        this.antennaMidLeft = this.antennaBaseLeft.getChild("antenna_l_mid");
        this.antennaTipLeft = this.antennaMidLeft.getChild("antenna_l_tip");
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

        // Play-test round 1, spec item 2: two tapered segments per side instead of one
        // uniform 5x4x8 slab. Both stay children of `head` (not nested tip-under-base)
        // so their rest pose is a direct hand translation of the python spec's flat,
        // absolute-pose part list -- see QueenAntModel's class javadoc and
        // assets-src/models.py's QUEEN_MANDIBLE_TIP_ANGLE comment. setupAnim flexes both
        // segments of a side by the same angle, so they move together as a rigid unit.
        head.addOrReplaceChild("mandible_r_base",
                CubeListBuilder.create()
                        .texOffs(94, 27).addBox(-4.0F, -1.5F, -4.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.0F, 5.5F, -4.0F));

        head.addOrReplaceChild("mandible_r_tip",
                CubeListBuilder.create()
                        .texOffs(94, 34).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-5.0F, 5.5F, -8.0F, 0.0F, -MANDIBLE_TIP_ANGLE, 0.0F));

        head.addOrReplaceChild("mandible_l_base",
                CubeListBuilder.create()
                        .texOffs(94, 27).mirror().addBox(0.0F, -1.5F, -4.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(3.0F, 5.5F, -4.0F));

        head.addOrReplaceChild("mandible_l_tip",
                CubeListBuilder.create()
                        .texOffs(94, 34).mirror().addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(5.0F, 5.5F, -8.0F, 0.0F, MANDIBLE_TIP_ANGLE, 0.0F));

        // Antennae: three hinged segments per side, each a CHILD of the last, unlike every
        // other part of this model. See the class javadoc -- the nesting is what lets the
        // idle sway move the whole sweep from one rotation on the scape.
        addAntenna(head, "r", -1.0F);
        addAntenna(head, "l", 1.0F);

        body.addOrReplaceChild("gaster",
                CubeListBuilder.create()
                        .texOffs(72, 20).addBox(-3.0F, 3.0F, 5.0F, 6.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-9.0F, -4.0F, 9.0F, 18.0F, 14.0F, 18.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, GASTER_REST_Y, 0.0F));

        CubeListBuilder legRight = CubeListBuilder.create()
                .texOffs(56, 32).addBox(-1.0F, 0.0F, -1.0F, 2.0F, LEG_LENGTH, 2.0F, new CubeDeformation(0.0F));
        CubeListBuilder legLeft = CubeListBuilder.create()
                .texOffs(56, 32).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, LEG_LENGTH, 2.0F, new CubeDeformation(0.0F)).mirror(false);

        body.addOrReplaceChild("leg_r1", legRight,
                PartPose.offsetAndRotation(-LEG_ROOT_X, LEG_ROOT_Y, -5.0F, 0.0F, REST_LEG_Y_FRONT, REST_LEG_Z));
        body.addOrReplaceChild("leg_r2", legRight,
                PartPose.offsetAndRotation(-LEG_ROOT_X, LEG_ROOT_Y, 0.0F, 0.0F, 0.0F, REST_LEG_Z));
        body.addOrReplaceChild("leg_r3", legRight,
                PartPose.offsetAndRotation(-LEG_ROOT_X, LEG_ROOT_Y, 5.0F, 0.0F, REST_LEG_Y_HIND, REST_LEG_Z));
        body.addOrReplaceChild("leg_l1", legLeft,
                PartPose.offsetAndRotation(LEG_ROOT_X, LEG_ROOT_Y, -5.0F, 0.0F, -REST_LEG_Y_FRONT, -REST_LEG_Z));
        body.addOrReplaceChild("leg_l2", legLeft,
                PartPose.offsetAndRotation(LEG_ROOT_X, LEG_ROOT_Y, 0.0F, 0.0F, 0.0F, -REST_LEG_Z));
        body.addOrReplaceChild("leg_l3", legLeft,
                PartPose.offsetAndRotation(LEG_ROOT_X, LEG_ROOT_Y, 5.0F, 0.0F, -REST_LEG_Y_HIND, -REST_LEG_Z));

        return LayerDefinition.create(mesh, 128, 64);
    }

    /**
     * One antenna chain under {@code head}: scape -> pedicel -> flagellum, each the child
     * of the last.
     *
     * <p>{@code side} is {@code -1} for the right and {@code +1} for the left, and
     * multiplies both the root offset and every outward ({@code zRot}) hinge -- the same
     * sign convention {@code assets-src/models.py}'s {@code queen_antenna(side)} uses, so
     * the two files can be read against each other line for line. Each child's offset is
     * {@code (0, -<parent length>, 0)}, i.e. exactly where the parent segment ended.
     */
    private static void addAntenna(PartDefinition head, String tag, float side) {
        boolean mirror = side > 0.0F;
        PartDefinition base = head.addOrReplaceChild("antenna_" + tag + "_base",
                antennaSegment(64, 32, 3.0F, ANTENNA_BASE_LENGTH, mirror),
                PartPose.offsetAndRotation(3.0F * side, -2.5F, -9.0F,
                        REST_ANTENNA_BASE_X, 0.0F, REST_ANTENNA_BASE_Z * side));
        PartDefinition mid = base.addOrReplaceChild("antenna_" + tag + "_mid",
                antennaSegment(76, 32, 2.0F, ANTENNA_MID_LENGTH, mirror),
                PartPose.offsetAndRotation(0.0F, -ANTENNA_BASE_LENGTH, 0.0F,
                        REST_ANTENNA_MID_X, 0.0F, REST_ANTENNA_MID_Z * side));
        mid.addOrReplaceChild("antenna_" + tag + "_tip",
                antennaSegment(84, 32, 2.0F, ANTENNA_TIP_LENGTH, mirror),
                PartPose.offsetAndRotation(0.0F, -ANTENNA_MID_LENGTH, 0.0F,
                        REST_ANTENNA_TIP_X, 0.0F, 0.0F));
    }

    /**
     * A square column {@code thickness} across and {@code length} long, growing UP out of
     * its own pivot (negative Y is up in model space), so a segment's far end is at
     * {@code -length} and its child hangs there.
     */
    private static CubeListBuilder antennaSegment(int u, int v, float thickness, float length,
            boolean mirror) {
        CubeListBuilder builder = CubeListBuilder.create().texOffs(u, v);
        if (mirror) {
            builder.mirror();
        }
        builder.addBox(-thickness / 2.0F, -length, -thickness / 2.0F,
                thickness, length, thickness, new CubeDeformation(0.0F));
        if (mirror) {
            builder.mirror(false);
        }
        return builder;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        // Every rotation and offset below is written absolutely, never read back and
        // adjusted -- nothing resets a plain EntityModel's parts between frames.
        this.head.yRot = netHeadYaw * DEG_TO_RAD;
        this.head.xRot = headPitch * DEG_TO_RAD;

        // The idle sway is written on the SCAPE only: the pedicel and flagellum are its
        // children and ride along, so the sweep stays one rigid curve. Adding the same
        // angle to all three -- the shape the mandibles below use -- would rotate each
        // about its own pivot and visibly pull a 15-unit chain apart.
        float bob = Mth.cos(ageInTicks * 0.09F) * 0.11F;
        float sway = Mth.sin(ageInTicks * 0.07F) * 0.06F;
        this.antennaBaseRight.xRot = REST_ANTENNA_BASE_X + bob;
        this.antennaBaseRight.zRot = -REST_ANTENNA_BASE_Z - sway;
        this.antennaBaseLeft.xRot = REST_ANTENNA_BASE_X - bob;
        this.antennaBaseLeft.zRot = REST_ANTENNA_BASE_Z + sway;
        this.antennaMidRight.xRot = REST_ANTENNA_MID_X;
        this.antennaMidRight.zRot = -REST_ANTENNA_MID_Z;
        this.antennaMidLeft.xRot = REST_ANTENNA_MID_X;
        this.antennaMidLeft.zRot = REST_ANTENNA_MID_Z;
        // The flagellum flicks on its own, faster and out of phase with the sway. It is
        // the difference between a feeler and a horn.
        float flick = Mth.sin(ageInTicks * 0.17F) * 0.13F;
        this.antennaTipRight.xRot = REST_ANTENNA_TIP_X + flick;
        this.antennaTipLeft.xRot = REST_ANTENNA_TIP_X - flick;

        // Mandibles work slowly and wide -- she is not in a hurry. Both segments of a
        // side get the same flex added on top of their own rest yRot (the tip's rest
        // already carries MANDIBLE_TIP_ANGLE), so base and tip swing together as one
        // rigid jaw instead of the tip lagging or drifting apart from the base.
        float mandibleFlex = Mth.sin(ageInTicks * 0.035F) * 0.09F;
        this.mandibleBaseRight.yRot = -mandibleFlex;
        this.mandibleTipRight.yRot = -MANDIBLE_TIP_ANGLE - mandibleFlex;
        this.mandibleBaseLeft.yRot = mandibleFlex;
        this.mandibleTipLeft.yRot = MANDIBLE_TIP_ANGLE + mandibleFlex;

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
