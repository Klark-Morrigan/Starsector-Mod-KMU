package kmu.maplayers.politicalmap.base.politics;

import kmlib.starsector.systems.SystemKey;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;

/**
 * Poses a {@link BlocPresenceIndex} as a walk would have left it, for the suites that consume one
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
public final class BlocPresenceIndexFixtures {

    private BlocPresenceIndexFixtures() {
    }

    /**
     * One bloc found in the systems named, in the order they are named.
     *
     * @param blocId    the bloc the walk surfaced
     * @param systemIds the systems it was found in, in walk order
     * @return an index holding that bloc alone
     */
    public static BlocPresenceIndex buildIndexOf(String blocId, String... systemIds) {
        return buildIndexOf(Map.of(blocId, List.of(systemIds)));
    }

    /**
     * Several blocs at once, each found in the systems listed against it.
     *
     * @param systemIdsByBlocId the systems each bloc was found in, in walk order within each bloc
     * @return an index holding exactly those blocs
     */
    public static BlocPresenceIndex buildIndexOf(Map<String, List<String>> systemIdsByBlocId) {
        var orderedSystemKeysByBlocId = new LinkedHashMap<String, Set<SystemKey>>();

        for (var entry : systemIdsByBlocId.entrySet()) {
            orderedSystemKeysByBlocId.put(
                entry.getKey(),
                new LinkedHashSet<>(buildCellKeys(entry.getValue().toArray(String[]::new))));
        }
        return new BlocPresenceIndex(orderedSystemKeysByBlocId);
    }
}
