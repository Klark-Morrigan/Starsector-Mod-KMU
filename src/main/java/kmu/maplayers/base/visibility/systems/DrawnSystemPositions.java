package kmu.maplayers.base.visibility.systems;

import kmlib.starsector.systems.SystemKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where each drawn system sits: the read that turns a pass's membership answer into the
 * point cloud a partition is cut from and a motion poll compares between ticks.
 *
 * <p>Membership itself is {@link MapVisibilityPass}'s, so this states the positions alone.
 * The two were one class while the rule needed somewhere to live; a pass that answers the
 * rule leaves this what its name says.
 *
 * <p>Taken off the pass's own index rather than by traversing the sector here, which is what
 * keeps a rebuild inside the one traversal the frame allows it: the band bake resolves its
 * systems off that same pass, so a second traversal opened here would be charged to the rebuild
 * whichever of the two ran first.
 *
 * <p>Read off the systems the pass holds by {@link SystemKey} rather than by id, because a system
 * id is not unique: a live modded sector holds several systems sharing one, and a point cloud
 * gathered under ids is short a site for each of them - a system with no cell on a map that draws
 * every other one.
 */
public final class DrawnSystemPositions {

    private DrawnSystemPositions() {
    }

    /**
     * Records the live {@code {x, y}} position of every on-map system under the pass's drawn-set
     * rule, keyed by {@link SystemKey}.
     *
     * @param pass the pass whose reading of the sector supplies the systems and decides
     *             membership; a pass over no sector yields an empty map
     * @return each drawn system's live hyperspace position keyed by its key, in the sector's
     *         star-system order; a system with no location is skipped, since it has no site to
     *         place a cell at. Two systems sharing an id hold a position each
     */
    public static Map<SystemKey, double[]> collectLivePositions(MapVisibilityPass pass) {

        var positions = new LinkedHashMap<SystemKey, double[]>();

        for (var indexed : pass.sectorIndex().readSystemsByKey().entrySet()) {

            var system = indexed.getValue();
            var location = system.getLocation();

            // Where it sits is asked before whether it is drawn, so a system with nowhere to place
            // a cell is never read for the membership answer that could not be used anyway.
            if (location == null || !pass.isDrawn(system)) {
                continue;
            }
            positions.put(indexed.getKey(), new double[] {location.x, location.y});
        }
        return positions;
    }

    /**
     * The same drawn positions addressed by system id, for a structure keyed that way.
     *
     * <p>An id is not unique, so this is the lossy address: several systems sharing one leave a
     * single entry, holding the position of the first of them the sector lists - the same system
     * every other id-keyed read of the sector answers with.
     *
     * @param pass the pass whose reading of the sector supplies the systems and decides
     *             membership; a pass over no sector yields an empty map
     * @return each drawn system's live hyperspace position keyed by id, in the sector's
     *         star-system order
     */
    public static Map<String, double[]> collectLivePositionsById(MapVisibilityPass pass) {

        var positionById = new LinkedHashMap<String, double[]>();

        // Re-addressed rather than gathered afresh, so which systems are drawn and where they sit
        // is decided once above and this differs from it in the address alone.
        for (var placed : collectLivePositions(pass).entrySet()) {
            positionById.putIfAbsent(placed.getKey().systemId(), placed.getValue());
        }
        return positionById;
    }
}
