package kmu.politicalmap.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.Points;

import kmu.politicalmap.domain.visibility.DrawnSystemPositions;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which on-map systems are moving, so the political map can leave them out of
 * its cell geometry.
 *
 * <p>The overlay's Voronoi partition assumes a system's hyperspace position is fixed.
 * Some mods break that: a system can be a mobile entity that rewrites its own
 * {@code getLocation()} every frame and drifts across hyperspace (the motivating case
 * is Legacy of Arkgneisis' Anarakis Reparations Society, whose capital patrols a
 * waypoint loop). A moving site has no stable cell to draw, and letting it clip its
 * neighbours would drag their borders around with it. So a system in motion is not
 * tracked into the partition - it is dropped from it: it seeds no cell and clips no
 * neighbour, and the surrounding cells fill the space as if it were absent. When it
 * comes to rest it rejoins at wherever it stopped.
 *
 * <p>Motion is read by observation, not by asking how any particular mod moves a
 * system: each poll compares every drawn system's live position against the one seen
 * last poll, and a change past a small noise floor marks it moving. This stays true
 * for a per-frame teleporter (its sampled position differs every poll) and a steady
 * patrol alike, with no coupling to the moving entity's implementation. A one-time
 * relocation (a rehomed colony) falls out for free: it reads as moving on the poll it
 * jumps, then rejoins once it holds still.
 *
 * <p>A single shared instance couples the campaign-thread writer (the sector watcher,
 * which observes each poll) to the render-thread reader (the geometry cache, which
 * skips the movers) with no owner between them, the same seam {@link PoliticalMapRefresh}
 * provides for its counters. The moving set is republished through a volatile field so
 * the render thread reads it without locking.
 */
public final class MovingSystems {
    // The one shared tracker the watcher writes and the geometry cache reads.
    private static final MovingSystems INSTANCE = new MovingSystems();

    // The distance (world units) a system's position must change between polls to count
    // as motion rather than float noise. Systems sit thousands of units apart, so a
    // one-unit floor separates a genuinely mobile system from numerical jitter without
    // risking a real move slipping under it.
    private static final double MOTION_THRESHOLD = 1.0;

    // Each drawn system's position as seen last poll, the baseline the next poll's
    // motion check compares against. Mutated only on the campaign thread (the watcher
    // poll).
    private final Map<String, double[]> lastObservedPositionBySystemId = new LinkedHashMap<>();
    // The systems observed to be moving, republished each poll for the render thread.
    // Volatile so a reader sees each new publication as a whole.
    private volatile Set<String> publishedMovingSystemIds = Set.of();

    // The shared tracker is reached through getInstance(); the motion detection itself
    // is a self-contained state machine that stands on its own instance, so the
    // constructor is package-visible rather than sealed to the singleton.
    MovingSystems() {
    }

    /**
     * @return the one shared tracker both the sector watcher and the geometry cache
     *         reach, since neither owns the other
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
        return publishedMovingSystemIds;
    }

    /**
     * Clears all observations. Called when a save loads so a system id shared with a
     * previous save in the same app session starts fresh rather than comparing against
     * the earlier save's last-seen position.
     */
    public void reset() {
        lastObservedPositionBySystemId.clear();
        publishedMovingSystemIds = Set.of();
    }

    /**
     * Observes every drawn system's live position, republishes the moving set, and
     * reports whether that set changed.
     *
     * @param sector the sector to walk; null observes nothing and reports no change
     * @return true when the moving set gained or lost a member this poll, so the caller
     *         requests a geometry refresh; false while it is steady (including a system
     *         that keeps moving - it is already excluded, so nothing rebuilds)
     */
    public boolean updateMovingSystems(SectorAPI sector) {
        if (sector == null) {
            return false;
        }
        return updateMovingSystems(DrawnSystemPositions.collectLivePositions(sector));
    }

    /**
     * Detects motion in an explicit set of live positions - the testable core the
     * sector walk feeds.
     *
     * @param livePositionBySystemId the current live position of each drawn system
     * @return true when the moving set changed from the last observation
     */
    boolean updateMovingSystems(Map<String, double[]> livePositionBySystemId) {
        var movingSystemIds = new LinkedHashSet<String>();
        var thresholdSquared = MOTION_THRESHOLD * MOTION_THRESHOLD;
        for (var entry : livePositionBySystemId.entrySet()) {
            var lastPosition = lastObservedPositionBySystemId.get(entry.getKey());
            // A first-seen system has no baseline, so it cannot be judged moving yet -
            // its motion is decided next poll. Only a position that shifted past the
            // noise floor marks a system moving.
            if (lastPosition != null
                    && Points.computeDistanceSquared(lastPosition, entry.getValue())
                            > thresholdSquared) {
                movingSystemIds.add(entry.getKey());
            }
        }
        // Replace the baseline with this poll's positions (dropping systems no longer
        // drawn) so the next poll measures poll-to-poll motion.
        lastObservedPositionBySystemId.clear();
        lastObservedPositionBySystemId.putAll(livePositionBySystemId);
        var hasSetChanged = !movingSystemIds.equals(publishedMovingSystemIds);
        publishedMovingSystemIds = Set.copyOf(movingSystemIds);
        return hasSetChanged;
    }
}
