package kmu.maplayers.ownermap.picker;

import kmlib.starsector.systems.SystemKey;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates an {@link OwnerPresenceIndex} across one sector walk, a system at a time.
 *
 * <p>It exists so the rule the index rests on is written once. Membership has to be recorded at the
 * point the walk already decides it, and each walk decides it in a different arm of a different
 * fold - so what the walks have in common is not a place to share code but a shape: keep an owner's
 * systems in the order they were met, and add rather than replace when an owner is met again. Left
 * inline, that shape would be a nested collection type, a {@code computeIfAbsent} and the reasoning
 * behind both, copied into each walk and free to drift apart.
 *
 * <p>Mutable and single-use, which is what separates it from the sealed record it yields: a walk
 * accumulates into one of these and hands over an index that outlives it, read back per hover until
 * the next rebuild replaces it.
 *
 * <p>The walks that fill one are a layer's own aggregators, outside this package, so the rule it
 * rests on is stated here rather than enforced by visibility: an index is only ever folded by the
 * walk that totalled the stats beside it, because an index arriving from anywhere else is an index
 * no stats count stands behind.
 */
public final class OwnerPresenceIndexBuilder {

    private final Map<String, Set<SystemKey>> systemKeysByOwnerId = new LinkedHashMap<>();

    /**
     * Records that an owner was found present in a system, in the same step the walk counts it - which
     * is what holds an owner's set and the count beside it to one answer.
     *
     * <p>Recording the same pair twice leaves one entry, so an arm that meets an owner more than once
     * in a system needs no guard of its own.
     *
     * @param ownerId   the owner the walk just counted, already folded as the layer folds owners
     *                  so a group's members index under the group
     * @param systemKey the system it was counted in
     */
    public void recordPresence(String ownerId, SystemKey systemKey) {
        systemKeysByOwnerId
            .computeIfAbsent(ownerId, presentOwnerId -> new LinkedHashSet<>())
            .add(systemKey);
    }

    /**
     * The index for everything recorded so far.
     *
     * @return a sealed index in walk order; empty when the walk found no owner present anywhere
     */
    public OwnerPresenceIndex buildIndex() {
        return new OwnerPresenceIndex(systemKeysByOwnerId);
    }
}
