package kmu.maplayers.ownermap.picker;

import kmlib.starsector.systems.SystemKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where each owner was found: an owner's ID against the star systems it is present in, resolved by
 * the same sector walk that totals the picker's stats beside it.
 *
 * <p>The systems are named by {@link SystemKey} because the surface reading them back lights cells,
 * and a cell is keyed that way: recorded by ID, an owner present in one of two systems sharing an ID
 * would light the other's cell as readily as its own.
 *
 * <p>It exists so a surface can light every system an owner reaches without moving any paint state.
 * Spotlighting an owner through the filter re-fuses clusters, re-traces borders and re-fits cluster
 * labels, which is far more than pointer motion down a list of rows can carry; a set resolved once
 * per rebuild and read back per hover carries it.
 *
 * <p>What "present" means is the walk's to say, and each layer answers it in its own vocabulary -
 * living somewhere under one layer's walk, something else under another's - so an owner's set is
 * the cluster group the layer that built it actually paints.
 *
 * <p>The membership is recorded at the point the walk already decides it rather than tested a
 * second time, which is what keeps the two from drifting: a system joins an owner's set exactly where
 * the walk counts that owner, so an owner's set size is the count its own stats state beside it -
 * whatever that layer calls the number.
 *
 * <p>Insertion-ordered at both levels, following the sector walk like the stats beside it, so two
 * reads of one sector answer in one order. Sealed on the way in, because a rebuild hands over the
 * sets it accumulated into and the index outlives that walk - it is read back per hover until the
 * next rebuild replaces it.
 *
 * <p>Plain data with no Starsector types, so it is built and read with nothing loaded.
 *
 * @param systemKeysByOwnerId the systems each present owner was found in, keyed by owner ID in walk
 *                           order. Whatever order the maps and sets handed over iterate in is the
 *                           order stored and read back, so a caller building one has to hand over an
 *                           ordered collection to get an order it can rely on
 */
public record OwnerPresenceIndex(
    Map<String, Set<SystemKey>> systemKeysByOwnerId) {

    /** A walk that found no owner present anywhere; the identity an accumulation begins from. */
    public static final OwnerPresenceIndex EMPTY = new OwnerPresenceIndex(Map.of());

    public OwnerPresenceIndex {
        systemKeysByOwnerId = copyPreservingOrder(systemKeysByOwnerId);
    }

    /**
     * The systems one owner is present in.
     *
     * <p>A lookup rather than the whole map, so a render pass asking about the one owner under the
     * pointer never holds every owner's set to get at it.
     *
     * @param ownerId the owner to look up; an ID this walk never surfaced answers empty, which is
     *               also the answer for an owner that has since stopped being present anywhere
     * @return that owner's systems in walk order, never null
     */
    public Set<SystemKey> readPresentSystemKeys(String ownerId) {
        return systemKeysByOwnerId.getOrDefault(ownerId, Set.of());
    }

    // Map.copyOf and Set.copyOf would each seal one level and lose the walk order doing it, which
    // is the one property a caller reading the index back relies on.
    private static Map<String, Set<SystemKey>> copyPreservingOrder(
            Map<String, Set<SystemKey>> systemKeysByOwnerId) {

        var copy = new LinkedHashMap<String, Set<SystemKey>>();

        for (var entry : systemKeysByOwnerId.entrySet()) {
            copy.put(
                entry.getKey(),
                Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
