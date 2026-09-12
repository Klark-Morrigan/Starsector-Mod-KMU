package kmu.maplayers.base.refresh;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.SystemMotionTracker;

import kmu.maplayers.base.visibility.systems.DrawnSystemPositions;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;

import java.util.Set;

/**
 * One sector's motion tracking: which drawn systems are rewriting their own hyperspace position,
 * so the cell partition can leave them out rather than chase them.
 *
 * <p>Some mods make a system a mobile entity that drifts across hyperspace - the motivating case
 * is Legacy of Arkgneisis' Anarakis Reparations Society, whose capital patrols a waypoint loop.
 * What the partition does with a mover is {@code CellGeometryCache}'s; what is decided here is
 * which systems are movers, and by observation alone: a system reads as moving on the poll it
 * shifts past the noise floor and rejoins once it holds still, so a one-time relocation (a
 * rehomed colony) needs no special case.
 *
 * <p>Detection is {@link SystemMotionTracker}'s, fed the positions of the systems the shared
 * drawn-set rule ({@link MapVisibilityPass#isDrawn}) admits, so the motion walk sees exactly the
 * systems the geometry draws - including any a reveal override put on the map.
 *
 * <p>What stays here is the coupling the map needs: one tracker joins a sector's
 * campaign-thread writer (the poll) to its render-thread reader (the geometry cache, which
 * skips the movers) with no owner between them. One per sector, held by that sector's
 * installed map machinery, because an observation is keyed by {@link SystemKey}, whose
 * engine-minted arms one sector mints without regard to another's: two sectors sharing a
 * tracker would measure one sector's system against the last-seen position of the system
 * carrying that key in the other, and report a drift neither made. The observations go with
 * the machinery when it is released, so a sector's tracking begins from nothing rather than
 * from whatever the sector before it last saw.
 */
public final class MovingSystems {

    // Detects motion generically; this class supplies the drawn-set rule and the
    // cross-thread seam. The tracker's published set is read lock-free.
    private final SystemMotionTracker systemMotionTracker = new SystemMotionTracker();

    /**
     * @return the keys of systems currently moving - the ones the geometry cache leaves
     *         out of the Voronoi partition. An immutable snapshot safe to read from the
     *         render thread; empty until the first poll observes anything
     */
    public Set<SystemKey> getMovingSystemKeys() {
        return systemMotionTracker.getMovingSystemKeys();
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
        // Read by key, so two drawn systems sharing an id are two observations: a move by one is
        // reported for that one, rather than for whichever of the pair the sector lists last.
        return systemMotionTracker.updateMovingSystems(
            DrawnSystemPositions.collectLivePositions(pass));
    }
}
