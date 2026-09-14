package kmu.maplayers.politicalmap.base.politics;

import kmlib.starsector.systems.SystemKey;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates a {@link BlocPresenceIndex} across one sector walk, a system at a time.
 *
 * <p>It exists so the rule the index rests on is written once. Membership has to be recorded at the
 * point the walk already decides it, and each walk decides it in a different arm of a different
 * fold - so what the two have in common is not a place to share code but a shape: keep a bloc's
 * systems in the order they were met, and add rather than replace when a bloc is met again. Left
 * inline, that shape was a nested collection type, a {@code computeIfAbsent} and the reasoning
 * behind both, copied into each walk and free to drift apart.
 *
 * <p>Mutable and single-use, which is what separates it from the sealed record it yields: a walk
 * accumulates into one of these and hands over an index that outlives it, read back per hover until
 * the next rebuild replaces it.
 *
 * <p>Package-private, because the walks that fill one are the aggregators beside it and an index
 * arriving from anywhere else would be an index no stats count stands behind.
 */
final class BlocPresenceIndexBuilder {

    private final Map<String, Set<SystemKey>> systemKeysByBlocId = new LinkedHashMap<>();

    /**
     * Records that a bloc was found present in a system, in the same step the walk counts it - which
     * is what holds a bloc's set and the count beside it to one answer.
     *
     * <p>Recording the same pair twice leaves one entry, so an arm that meets a bloc more than once
     * in a system needs no guard of its own.
     *
     * @param blocId    the bloc the walk just counted, already folded through the pass's grouping
     *                  so an alliance's members index under the alliance
     * @param systemKey the system it was counted in
     */
    void recordPresence(String blocId, SystemKey systemKey) {
        systemKeysByBlocId
            .computeIfAbsent(blocId, presentBlocId -> new LinkedHashSet<>())
            .add(systemKey);
    }

    /**
     * The index for everything recorded so far.
     *
     * @return a sealed index in walk order; empty when the walk found no bloc present anywhere
     */
    BlocPresenceIndex buildIndex() {
        return new BlocPresenceIndex(systemKeysByBlocId);
    }
}
