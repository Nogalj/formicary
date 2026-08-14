package com.nogal.formicary.entity;

import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * The queen attacks on sight, like her deep-tier soldiers (spec section 3) -- and, like
 * them, is blind to a player wearing the Pheromonal Disguise, which is what lets a
 * disguised player walk her chamber to scout.
 *
 * <p>Gated on {@link ColonyAnger#isValidDeepTierTarget} alone rather than on
 * {@link ColonyAnger#isDeepTierHostileAt}: that composite also asks whether the block she
 * is standing on is in a {@code #formicary:deep_colony} biome, which is redundant for a mob
 * that only exists inside a Royal Depths chamber, and would make her harmless in the one
 * situation the biome lookup can be wrong. She is the deep colony.
 *
 * <p>Attacking her still strips the disguise the ordinary way -- she is registered in
 * {@link ColonyAnger#isColonyAnt}, so hurting her runs the same
 * {@code ColonyAngerEvents.onColonyAntHurt} -> {@code provoke} path any other ant does.
 */
public class QueenHostilityGoal extends NearestAttackableTargetGoal<Player> {
    public QueenHostilityGoal(QueenAntEntity queen) {
        super(queen, Player.class, 10, true, false,
                living -> living instanceof Player player && ColonyAnger.isValidDeepTierTarget(player));
    }
}
