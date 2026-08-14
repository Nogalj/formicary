package com.nogal.formicary.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;

/**
 * What a stationed tamed soldier engages (spec section 4): "stays where it is, still
 * attacks hostiles in range ~8 targeting owner or itself".
 *
 * <p>Note the shape of that rule -- it is not "attack anything hostile nearby". A guard
 * post only reacts to a mob that has <em>already picked</em> the owner or the ant as its
 * target, which is what keeps a stationed soldier from wandering off after a skeleton that
 * was minding its own business and abandoning the post it was planted on.
 *
 * <p>The 8-block figure is this goal's own, not the mob's {@code FOLLOW_RANGE}:
 * {@code NearestAttackableTargetGoal} would otherwise size both the search box and the
 * targeting conditions from that attribute, which also governs how far a
 * {@code MeleeAttackGoal} will chase. Guard radius and pursuit range are different
 * questions and are answered separately here.
 */
public class GuardPostTargetGoal extends NearestAttackableTargetGoal<Mob> {
    /** Spec section 4: "still attacks hostiles in range ~8". */
    public static final double GUARD_RADIUS = 8.0;

    private final TamedSoldierAntEntity ant;

    public GuardPostTargetGoal(TamedSoldierAntEntity ant) {
        super(ant, Mob.class, 10, true, false, living -> living instanceof Mob mob && threatens(ant, mob));
        this.ant = ant;
        this.targetConditions = TargetingConditions.forCombat()
                .range(GUARD_RADIUS)
                .selector(living -> living instanceof Mob mob && threatens(ant, mob));
    }

    @Override
    public boolean canUse() {
        return this.ant.isStationed() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.ant.isStationed() && super.canContinueToUse();
    }

    /** Search the guard radius, not the follow-range the superclass would use. */
    @Override
    protected AABB getTargetSearchArea(double targetDistance) {
        return super.getTargetSearchArea(GUARD_RADIUS);
    }

    /**
     * Whether {@code mob} has taken aim at this guard or the player it belongs to. Public
     * and static so the rule can be asserted directly by a test instead of inferred from
     * whether a goal happened to fire on a given tick.
     */
    public static boolean threatens(TamedSoldierAntEntity ant, Mob mob) {
        if (mob == ant || mob instanceof TamedAnt) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return false;
        }
        return target == ant || target.getUUID().equals(ant.getOwnerUUID());
    }
}
