package kmu.maplayers.politicalmap.base.politics;

import kmlib.starsector.systems.SystemKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where each bloc was found: a bloc's ID against the star systems it is present in, resolved by
 * the same sector walk that totals the picker's stats beside it.
 *
 * <p>The systems are named by {@link SystemKey} because the surface reading them back lights cells,
 * and a cell is keyed that way: recorded by ID, a bloc present in one of two systems sharing an ID
 * would light the other's cell as readily as its own.
 *
 * <p>It exists so a surface can light every system a bloc reaches without moving any paint state.
 * Spotlighting a bloc through the filter re-fuses territories, re-traces borders and re-fits cluster
 * labels, which is far more than pointer motion down a list of rows can carry; a set resolved once
 * per rebuild and read back per hover carries it.
 *
 * <p>What "present" means is the walk's to say, and each layer answers it in its own vocabulary -
 * living somewhere under the dominance walk, claiming it under the claims one - so a bloc's set is
 * the territory the layer that built it actually paints.
 *
 * <p>The membership is recorded at the point the walk already decides it rather than tested a
 * second time, which is what keeps the two from drifting: a system joins a bloc's set exactly where
 * the walk counts that bloc, so a bloc's set size is the count beside it - {@link DominanceStats}'s
 * {@code presence}, {@link ClaimStats}'s {@code claims}.
 *
 * <p>Insertion-ordered at both levels, following the sector walk like the stats beside it, so two
 * reads of one sector answer in one order. Sealed on the way in, because a rebuild hands over the
 * sets it accumulated into and the index outlives that walk - it is read back per hover until the
 * next rebuild replaces it.
 *
 * <p>Plain data with no Starsector types, so it is built and asserted on hand-built inputs.
 *
 * @param systemKeysByBlocId the systems each present bloc was found in, keyed by bloc ID in walk
 *                           order. Whatever order the maps and sets handed over iterate in is the
 *                           order stored and read back, so a caller posing one has to hand over an
 *                           ordered collection to get an order it can rely on
 */
public record BlocPresenceIndex(Map<String, Set<SystemKey>> systemKeysByBlocId) {

    /** A walk that found no bloc present anywhere; the identity an accumulation begins from. */
    public static final BlocPresenceIndex EMPTY = new BlocPresenceIndex(Map.of());

    public BlocPresenceIndex {
        systemKeysByBlocId = copyPreservingOrder(systemKeysByBlocId);
    }

    /**
     * The systems one bloc is present in.
     *
     * <p>A lookup rather than the whole map, so a render pass asking about the one bloc under the
     * pointer never holds every bloc's set to get at it.
     *
     * @param blocId the bloc to look up; an ID this walk never surfaced answers empty, which is
     *               also the answer for a bloc that has since stopped being present anywhere
     * @return that bloc's systems in walk order, never null
     */
    public Set<SystemKey> readPresentSystemKeys(String blocId) {
        return systemKeysByBlocId.getOrDefault(blocId, Set.of());
    }

    // Map.copyOf and Set.copyOf would each seal one level and lose the walk order doing it, which
    // is the one property a caller reading the index back relies on.
    private static Map<String, Set<SystemKey>> copyPreservingOrder(
            Map<String, Set<SystemKey>> systemKeysByBlocId) {

        var copy = new LinkedHashMap<String, Set<SystemKey>>();

        for (var entry : systemKeysByBlocId.entrySet()) {
            copy.put(
                entry.getKey(),
                Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
