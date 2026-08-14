package com.nogal.formicary.entity;

import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Deep-tier hostility (spec section 3): "in the Nurseries and Royal Depths, soldiers are
 * hostile on sight -- the deep colony tolerates no strangers." A soldier standing in a
 * {@code #formicary:deep_colony} biome targets the nearest non-disguised, non-creative
 * player purely on sight, with no provocation needed -- gated entirely through
 * {@link ColonyAnger#isDeepTierHostileAt} so the decision is identical whether it's read
 * from here or from a test.
 *
 * <p>Registered at lower priority than {@link ColonyAngerTargetGoal}: a soldier that is
 * already angry keeps chasing its offender first (that goal wins the target-selector
 * pick), and this one only ever engages once the anger goal has nothing to offer.
 * {@code mustSee = true} matches the "on sight" wording -- unlike provoked colony anger,
 * which can be raised by an offence the soldier never witnessed.
 */
public class DeepTierHostilityGoal extends NearestAttackableTargetGoal<Player> {
    private final SoldierAntEntity soldier;

    public DeepTierHostilityGoal(SoldierAntEntity soldier) {
        super(soldier, Player.class, 10, true, false,
                living -> living instanceof Player player && isCandidateValid(soldier, player));
        this.soldier = soldier;
    }

    @Override
    public boolean canUse() {
        return this.isInDeepColonyBiome() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.isInDeepColonyBiome() && super.canContinueToUse();
    }

    private boolean isInDeepColonyBiome() {
        return this.soldier.level() instanceof ServerLevel level
                && ColonyAnger.isDeepColonyBiome(level, this.soldier.blockPosition());
    }

    private static boolean isCandidateValid(SoldierAntEntity soldier, Player player) {
        return soldier.level() instanceof ServerLevel level
                && ColonyAnger.isDeepTierHostileAt(level, soldier.blockPosition(), player);
    }
}
