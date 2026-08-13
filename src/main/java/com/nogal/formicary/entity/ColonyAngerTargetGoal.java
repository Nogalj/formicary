package com.nogal.formicary.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * The target goal that turns colony anger into an actual attack.
 *
 * <p>Gated entirely on {@link SoldierAntEntity#isAngry()}: a soldier the colony has not
 * provoked never picks a player through this goal, which is what keeps the colony
 * bee-style neutral. While angry it prefers the offender recorded by
 * {@code ColonyAnger.provoke}, and only when that player is gone (logged out, dead,
 * out of range, disguised in M4) does it fall back to the nearest other valid player --
 * spec section 3's "hostile to every non-disguised player in the radius, prioritizing
 * the offender".
 *
 * <p>The search radius is the mob's {@code FOLLOW_RANGE} attribute, which
 * {@link SoldierAntEntity#createAttributes()} pins to
 * {@link ColonyAnger#ANGER_RADIUS}.
 */
public class ColonyAngerTargetGoal extends NearestAttackableTargetGoal<Player> {
    private final SoldierAntEntity soldier;

    public ColonyAngerTargetGoal(SoldierAntEntity soldier) {
        super(soldier, Player.class, 10, false, false,
                living -> living instanceof Player player && ColonyAnger.isValidTarget(player));
        this.soldier = soldier;
    }

    @Override
    public boolean canUse() {
        return this.soldier.isAngry() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.soldier.isAngry() && super.canContinueToUse();
    }

    @Override
    protected void findTarget() {
        Player offender = this.resolveOffender();
        if (offender != null && this.targetConditions.test(this.soldier, offender)) {
            this.target = offender;
            return;
        }
        // Offender unreachable or gone -- any other valid player inside the radius will do.
        super.findTarget();
    }

    @Nullable
    private Player resolveOffender() {
        UUID uuid = this.soldier.getPersistentAngerTarget();
        if (uuid == null || !(this.soldier.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(uuid) instanceof Player player && ColonyAnger.isValidTarget(player)
                ? player
                : null;
    }
}
