package kmu.maplayers.base.geometry;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.visibility.systems.MapVisibilityPass;

/**
 * What settles which of two star systems standing on one hyperspace point keeps it.
 *
 * <p>One point seeds one cell: two sites on the same coordinate have no bisector between them, so
 * neither clips the other and the pair would come back as two cells covering identical area with no
 * edge between them. One of the two therefore seeds nothing, and nothing about the geometry can
 * choose which - both are equally near every point around them. The choice is made on what the map
 * knows about each system instead, which is why it is stated here rather than as a condition inside
 * the collection that applies it.
 *
 * <p>Declaration order is precedence order. The first tie-breaker that prefers one system over the
 * other settles the point and the ones below it are never asked, so a new consideration is a new
 * constant placed where its precedence belongs. Where none of them separates the two, the system
 * already holding the point keeps it - which is the sector's own listing order, and the same system
 * every ID-keyed read names of a colliding pair.
 *
 * <p>Each states a preference rather than a score, so a tie-breaker cannot be read backwards: a
 * constant is named for what it favours and answers only whether a system has that. A consideration
 * that ranks rather than prefers - more colonies over fewer - does not fit this shape, and would
 * have the fold compare ranks in place of preferences.
 */
public enum SiteTieBreaker {

    /**
     * A system the map shows in its own right keeps the point over one that is on the map only
     * because hidden systems are being shown.
     *
     * <p>A hidden system leaves the map again the moment that setting goes off, so letting one take
     * the point would make what the system beside it is drawn as turn on a toggle about something
     * else - and would draw a cell for a system the player has not been shown in place of one they
     * have.
     */
    SHOWN_IN_ITS_OWN_RIGHT {
        @Override
        boolean isPreferred(MapVisibilityPass pass, StarSystemAPI system) {
            return !pass.isHiddenSystem(system);
        }
    };

    /**
     * Whether the contender takes the point from the system standing on it.
     *
     * @param pass      the rebuild's reading of the sector, which every tie-breaker judges through
     * @param holder    the system holding the point
     * @param contender the system that has arrived on it
     * @return true when the first tie-breaker to separate them prefers the contender; false when it
     *         prefers the holder, and false when none of them separates the two, the holder keeping
     *         what it already has
     */
    public static boolean shouldTakePoint(
            MapVisibilityPass pass,
            StarSystemAPI holder,
            StarSystemAPI contender) {

        for (var tieBreaker : values()) {

            var isHolderPreferred = tieBreaker.isPreferred(pass, holder);

            // Decisive only where the two answer differently: a tie-breaker both systems satisfy,
            // or neither does, has said nothing about which of them should keep the point.
            if (isHolderPreferred != tieBreaker.isPreferred(pass, contender)) {
                return !isHolderPreferred;
            }
        }
        return false;
    }

    // Whether this tie-breaker favours the system, asked of each contender in turn.
    abstract boolean isPreferred(MapVisibilityPass pass, StarSystemAPI system);
}
