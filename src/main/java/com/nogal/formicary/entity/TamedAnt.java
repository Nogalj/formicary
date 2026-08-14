package com.nogal.formicary.entity;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Marker for the player-owned castes (spec section 4). Both implementors extend vanilla's
 * {@code TamableAnimal}, so this interface carries no state of its own -- it exists so the
 * colony's rules can say "is this one of ours or one of theirs" in one place.
 *
 * <p>Two consequences follow purely from tamed ants being <em>outside</em> the
 * {@code WorkerAntEntity}/{@code SoldierAntEntity}/{@code LarvaEntity} hierarchy, and both
 * are requirements of the spec rather than accidents:
 * <ul>
 *   <li>{@code ColonyAnger.isColonyAnt} is false for them, so hurting your own ant -- or
 *       your ant being killed by anything -- never raises the colony's alarm.</li>
 *   <li>{@code ColonyAnger.offenderOf} only ever resolves a {@code Player}, so a tamed
 *       ant's own attacks cannot provoke the colony or strip its owner's disguise.</li>
 * </ul>
 *
 * <p>What wild soldiers do about tamed ants is the one part that is <em>not</em> automatic:
 * see {@link TamedAntTargetGoal}.
 */
public interface TamedAnt {
    /** The owning player's UUID, or {@code null} for an ant that somehow has no owner. */
    @Nullable
    UUID getOwnerUUID();
}
