package kmu.maplayers.base.visibility.systems;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where each drawn system sits: the read that turns a pass's membership answer into the
 * point cloud a partition is cut from.
 *
 * <p>Membership itself is {@link MapVisibilityPass}'s, so this states the positions alone.
 * The two were one class while the rule needed somewhere to live; a pass that answers the
 * rule leaves this what its name says.
 *
 * <p>Taken off the pass's own index rather than by traversing the sector here, which is what
 * keeps a rebuild inside the one traversal the frame allows it: the band bake resolves systems
 * by id off that same index, so a second traversal opened here would be charged to the rebuild
 * whichever of the two ran first.
 */
public final class DrawnSystemPositions {

    private DrawnSystemPositions() {
    }

    /**
     * Records the live {@code {x, y}} position of every on-map system under the pass's drawn-set
     * rule, keyed by system id.
     *
     * @param pass the pass whose reading of the sector supplies the systems and decides
     *             membership; a pass over no sector yields an empty map
     * @return each drawn system's live hyperspace position keyed by id, in the sector's
     *         star-system order; a system with no location is skipped, since it has no site to
     *         place a cell at
     */
    public static Map<String, double[]> collectLivePositions(MapVisibilityPass pass) {

        var positions = new LinkedHashMap<String, double[]>();

        for (var system : pass.sectorIndex().readSystemsById().values()) {

            var location = system.getLocation();

            // Where it sits is asked before whether it is drawn, so a system with nowhere to place
            // a cell is never read for the membership answer that could not be used anyway.
            if (location == null || !pass.isDrawn(system)) {
                continue;
            }
            positions.put(system.getId(), new double[] {location.x, location.y});
        }
        return positions;
    }
}
