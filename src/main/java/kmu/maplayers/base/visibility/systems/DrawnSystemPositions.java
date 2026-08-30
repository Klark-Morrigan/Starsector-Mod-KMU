package kmu.maplayers.base.visibility.systems;

import kmlib.starsector.systems.SectorStarSystems;

import java.util.Map;

/**
 * Where each drawn system sits: the walk that turns a pass's membership answer into the
 * point cloud a partition is cut from.
 *
 * <p>Membership itself is {@link MapVisibilityPass}'s, so this states the positions alone.
 * The two were one class while the rule needed somewhere to live; a pass that answers the
 * rule leaves this what its name says.
 */
public final class DrawnSystemPositions {

    private DrawnSystemPositions() {
    }

    /**
     * Walks the sector once under the pass's drawn-set rule and records the live
     * {@code {x, y}} position of every on-map system, keyed by system id.
     *
     * @param pass the pass whose reading of the sector decides membership, and whose sector
     *             is walked; a pass over no sector yields an empty map
     * @return each drawn system's live hyperspace position keyed by id; a system with
     *         no location is skipped, since it has no site to place a cell at
     */
    public static Map<String, double[]> collectLivePositions(MapVisibilityPass pass) {
        return SectorStarSystems.collectPositionsById(pass.sector(), pass::isDrawn);
    }
}
