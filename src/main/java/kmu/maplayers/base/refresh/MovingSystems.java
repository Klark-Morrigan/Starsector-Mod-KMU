package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemMotionTracker;

import kmu.maplayers.base.visibility.DrawnSystemPositions;
import kmu.maplayers.base.visibility.MapVisibilityRules;

import java.util.Set;

/**
 * The shared seam that couples the map's motion tracking to its two threads, and the
 * reason a moving system is left out of an overlay.
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
 * ({@link DrawnSystemPositions#buildDrawnSystemPredicate}) so the motion walk sees exactly
 * the systems the geometry draws - including any a reveal override put on the map. What
 * stays here is the coupling the map needs: a single shared instance joins the
 * campaign-thread writer (the poll) to the render-thread reader (the geometry cache, which
 * skips the movers) with no owner between them, the same seam {@link MapLayerRefresh}
 * provides for its counters.
 */
public final class MovingSystems {

    // The one shared tracker the watcher writes and the geometry cache reads.
    private static final MovingSystems INSTANCE = new MovingSystems();

    // Detects motion generically; this class supplies the drawn-set rule and the
    // cross-thread seam. The tracker's published set is read lock-free.
    private final SystemMotionTracker systemMotionTracker = new SystemMotionTracker();

    // The shared tracker is reached through getInstance(); the motion detection stands
    // on its own instance, so the constructor is package-visible rather than sealed to
    // the singleton.
    MovingSystems() {
    }

    /**
     * @return the one shared tracker both the poll and the geometry cache reach, since
     *         neither owns the other
     */
    public static MovingSystems getInstance() {
        return INSTANCE;
    }

    /**
     * @return the ids of systems currently moving - the ones the geometry cache leaves
     *         out of the Voronoi partition. An immutable snapshot safe to read from the
     *         render thread; empty until the first poll observes anything
     */
    public Set<String> getMovingSystemIds() {
        return systemMotionTracker.getMovingSystemIds();
    }

    /**
     * Clears all observations. Called when a save loads so a system id shared with a
     * previous save in the same app session starts fresh rather than comparing against
     * the earlier save's last-seen position.
     */
    public void reset() {
        systemMotionTracker.clearObservations();
    }

    /**
     * Observes every drawn system's live position under the pass's visibility rules,
     * republishes the moving set, and reports whether that set changed.
     *
     * @param sector          the sector to walk; null observes nothing and reports no change
     * @param visibilityRules the pass's visibility rules, matching the set the geometry cache
     *                  draws, so both agree on which systems are on the map
     * @return true when the moving set gained or lost a member this poll, so the caller
     *         requests a geometry refresh; false while it is steady
     */
    public boolean updateMovingSystems(SectorAPI sector, MapVisibilityRules visibilityRules) {
        if (sector == null) {
            return false;
        }
        return systemMotionTracker.updateMovingSystems(
            sector,
            DrawnSystemPositions.buildDrawnSystemPredicate(sector, visibilityRules));
    }
}
