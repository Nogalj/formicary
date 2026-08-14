package com.nogal.formicary.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * How a <em>wild</em> soldier treats a tamed ant (spec section 4): "wild soldiers treat
 * them as they treat you -- safe while you're disguised, targets when you're not".
 *
 * <p>So the rule is written as exactly that indirection: a tamed ant is a valid target iff
 * <em>its owner</em> would be a valid target for this soldier right now, through the two
 * hostility routes the colony already has --
 * {@link SoldierAntEntity#isAngryAtPlayer} (provoked colony anger, M3b) and
 * {@link ColonyAnger#isDeepTierHostileAt} (deep-tier on-sight hostility, M4b). Both routes
 * already refuse a disguised player, so "safe while you're disguised" falls straight out of
 * reusing them rather than being a second copy of the disguise rule that could drift.
 *
 * <p>Registered below the three existing target goals on {@link SoldierAntEntity}, so a
 * soldier that can reach the offending player themselves always prefers the player.
 */
public class TamedAntTargetGoal extends NearestAttackableTargetGoal<TamableAnimal> {
    public TamedAntTargetGoal(SoldierAntEntity soldier) {
        super(soldier, TamableAnimal.class, 10, true, false,
                living -> living instanceof TamedAnt
                        && living instanceof TamableAnimal tamed
                        && isOwnerHostileTo(soldier, tamed));
    }

    /**
     * Whether {@code soldier} is currently hostile to the player who owns {@code ant}.
     * Resolving the owner is plumbing; {@link #isHostileToOwner} is the rule.
     */
    public static boolean isOwnerHostileTo(SoldierAntEntity soldier, TamableAnimal ant) {
        if (!(soldier.level() instanceof ServerLevel level)) {
            return false;
        }
        return isHostileToOwner(soldier, resolveOwner(level, ant.getOwnerUUID()));
    }

    /**
     * The rule itself: is {@code soldier} hostile to {@code owner} right now?
     *
     * <p>Split out from the owner lookup and made public so a test can assert it directly.
     * That split is not cosmetic here -- a {@code GameTestHelper} mock player is never
     * added to the level, so {@code level.getEntity(uuid)} cannot find one and a test that
     * went through {@link #isOwnerHostileTo} could only ever observe {@code false}.
     */
    public static boolean isHostileToOwner(SoldierAntEntity soldier, @Nullable Player owner) {
        if (owner == null || !ColonyAnger.isValidTarget(owner)) {
            return false;
        }
        if (soldier.isAngryAtPlayer(owner)) {
            return true;
        }
        return soldier.level() instanceof ServerLevel level
                && ColonyAnger.isDeepTierHostileAt(level, soldier.blockPosition(), owner);
    }

    @Nullable
    private static Player resolveOwner(ServerLevel level, @Nullable UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        return level.getEntity(ownerId) instanceof Player player ? player : null;
    }
}
