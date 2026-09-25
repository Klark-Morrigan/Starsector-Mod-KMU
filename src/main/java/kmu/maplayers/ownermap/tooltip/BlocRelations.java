package kmu.maplayers.ownermap.tooltip;

import kmu.maplayers.ownermap.holding.BlocAffiliation;
import kmu.maplayers.ownermap.holding.BlocFriendliness;

import java.util.Objects;

/**
 * The two ways a bloc may stand with whoever holds a hovered system: grouped with it, or merely on
 * good terms with it.
 *
 * <p>Sampled as one value at the moment a box reads its system, because both are live - a group
 * forms, a disposition slides past neutral - and a box that took them one block at a time could file
 * a group as an ally under one heading and as a rival under the next, out of a single hover.
 *
 * <p>Two values rather than one reading, because they are two axes: which of them places a bloc, and
 * in what order, is the asking box's business rather than this pair's.
 *
 * @param affiliation  who stands together, which the holder's own allies are lifted out by; it is
 *                     {@link BlocAffiliation#NONE} wherever nothing groups factions
 * @param friendliness whether two blocs are on good terms, read over both memberships whole
 */
public record BlocRelations(
    BlocAffiliation affiliation,
    BlocFriendliness friendliness) {

    public BlocRelations {

        Objects.requireNonNull(affiliation, "affiliation");
        Objects.requireNonNull(friendliness, "friendliness");
    }
}
