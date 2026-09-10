package kmu.maplayers.base.refresh;

import kmlib.starsector.systems.SystemMotionTracker;

import kmu.maplayers.base.visibility.systems.DrawnSystemPositions;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;

import java.util.Set;

/**
 * One sector's motion tracking, and the reason a moving system is left out of an overlay.
 *
 * <p>The cell partition assumes a system's hyperspace position is fixed.
 * Some mods break that: a system can be a mobile entity that rewrites its own
 * {@code getLocation()} every frame and drifts across hyperspace (the motivating case
 * is Legacy of Arkgneisis' Anarakis Reparations Society, whose capital patrols a
 * waypoint loop). A moving site has no stable cell to draw, and letting it clip its
 * neighbours would drag their borders around with it. So a system in motion is dropped
 * from the partition: it seeds no cell and clips no neighbour, and the surrounding
 * cells fill the space as if it were absent. When it comes to rest it rejoins at
 * wherever it stopped. A one-time relocation (a rehomed colony) falls out for free: it
 * reads as moving on the poll it jumps, then rejoins once it holds still.
 *
 * <p>Detection is delegated to {@link SystemMotionTracker}, fed the shared drawn-set rule
 * ({@link MapVisibilityPass#isDrawn}) so the motion walk sees exactly
 * the systems the geometry draws - including any a reveal override put on the map.
 *
 * <p>What stays here is the coupling the map needs: one tracker joins a sector's
 * campaign-thread writer (the poll) to its render-thread reader (the geometry cache, which
 * skips the movers) with no owner between them. One per sector, held by that sector's
 * installed map machinery, because an observation is keyed by system id: two sectors sharing
 * a tracker would measure one sector's system against the last-seen position of the system
 * holding that id in the other, and report a drift neither made. The observations go with the
 * machinery when it is released, so a sector's tracking begins from nothing rather than
 * from whatever the sector before it last saw.
 */
public final class MovingSystems {

    // Detects motion generically; this class supplies the drawn-set rule and the
    // cross-thread seam. The tracker's published set is read lock-free.
    private final SystemMotionTracker systemMotionTracker = new SystemMotionTracker();

    /**
     * @return the ids of systems currently moving - the ones the geometry cache leaves
     *         out of the Voronoi partition. An immutable snapshot safe to read from the
     *         render thread; empty until the first poll observes anything
     */
    public Set<String> getMovingSystemIds() {
        return systemMotionTracker.getMovingSystemIds();
    }

    /**
     * Observes every drawn system's live position through the poll's pass, republishes the
     * moving set, and reports whether that set changed.
     *
     * <p>Takes the poll's own pass rather than a sector to open one over. The drawn-set rule
     * asks each system whether anybody lives there, and the poll has already asked that of
     * every system for its snapshot - so a walk given the sector here would select every
     * system's colonies a second time in the same tick. It also settles which systems count
     * as drawn identically to the geometry cache, both asking one rule.
     *
     * <p>The positions come off the pass as well, for the same reason and one more: the pass
     * holds the traversal of the system list its readers share, so a tracker handed the sector
     * would traverse it again for the very systems the pass is already holding.
     *
     * @param pass the poll's reading of the sector, which supplies the systems and decides the
     *             drawn set; a null pass - or one opened over no sector - observes nothing
     *             and reports no change
     * @return true when the moving set gained or lost a member this poll, so the caller
     *         requests a geometry refresh; false while it is steady
     */
    public boolean updateMovingSystems(MapVisibilityPass pass) {

        // A pass over no sector observes nothing rather than observing an empty sector: the
        // second would report every system that had been moving as having stopped, which is a
        // refresh asked for by a load that has not finished rather than by anything that moved.
        if (pass == null || pass.sector() == null) {
            return false;
        }
        return systemMotionTracker.updateMovingSystems(
            DrawnSystemPositions.collectLivePositions(pass));
    }
}
