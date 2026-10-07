package kmu.maplayers.ownermap.picker;

import kmlib.starsector.systems.SystemKey;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;

/**
 * Poses an {@link OwnerPresenceIndex} as a walk would have left it, for the suites that consume one
 * rather than build one.
 *
 * <p>It exists because the index stores whatever iteration order it is handed, and a case reading it
 * back asserts that order. Posed with {@code Set.of} the order is randomised per JVM run, so such a
 * case passes or fails by which run it lands in - which is what these two builders take away by
 * being the only way a consuming suite poses one.
 *
 * <p>The systems are named by ID and keyed on the way in, each key stating the ID arm alone, so a
 * consuming suite reads its systems back as {@code buildCellKey} spells them.
 */
public final class OwnerPresenceIndexFixtures {

    private OwnerPresenceIndexFixtures() {
    }

    /**
     * One owner found in the systems named, in the order they are named.
     *
     * @param ownerId   the owner the walk surfaced
     * @param systemIds the systems it was found in, in walk order
     * @return an index holding that owner alone
     */
    public static OwnerPresenceIndex buildIndexOf(String ownerId, String... systemIds) {

        return buildIndexOf(Map.of(ownerId, List.of(systemIds)));
    }

    /**
     * Several owners at once, each found in the systems listed against it.
     *
     * @param systemIdsByOwnerId the systems each owner was found in, in walk order within each owner
     * @return an index holding exactly those owners
     */
    public static OwnerPresenceIndex buildIndexOf(Map<String, List<String>> systemIdsByOwnerId) {

        var orderedSystemKeysByOwnerId = new LinkedHashMap<String, Set<SystemKey>>();

        for (var entry : systemIdsByOwnerId.entrySet()) {

            orderedSystemKeysByOwnerId.put(
                entry.getKey(),
                new LinkedHashSet<>(buildCellKeys(entry.getValue().toArray(String[]::new))));
        }

        return new OwnerPresenceIndex(orderedSystemKeysByOwnerId);
    }
}
