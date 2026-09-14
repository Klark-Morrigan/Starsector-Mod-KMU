package kmu.maplayers.base.geometry;

import com.fs.starfarer.api.Global;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.visibility.systems.DrawnSystemPositions;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;

import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which systems seed the partition, and where each of them sits.
 *
 * <p>Three things decide it. The pass's drawn-set rule admits a system to the map at all; a system
 * currently moving is left out, having no stable cell to draw and no business dragging its
 * neighbours' borders around; and where two admitted systems stand on one hyperspace point, only
 * one of them may seed a site.
 *
 * <p>Kept apart from the partition it feeds because the two answer different questions. What may
 * seed a site is a fact about the systems - what the map shows of each, which is moving, which is
 * standing where another already stands - while the cells cut from those sites are geometry that
 * neither knows nor asks any of it.
 *
 * <p>Addressed by {@link SystemKey} throughout, because a system ID is not unique: a sector holding
 * several systems under one would collect a single site for them, leaving every system after the
 * first with no cell on a map that cells its neighbours.
 *
 * <p>Holds what it has already said about a coincidence, so an instance belongs to the partition it
 * collects for rather than being shared or rebuilt per update.
 */
public final class PartitionSites {

    private static final Logger LOG = Global.getLogger(PartitionSites.class);

    // The systems the last reported coincidence dropped, so the same pair is not named again on
    // every update that re-collects the same sites. Not a warn-once mute: what a repeat line would
    // add is a *different* pair, and an occupied point that later frees up - a mobile colony coming
    // to rest on another system's point, and leaving again - changes this set and is said out loud
    // both times.
    private Set<SystemKey> lastReportedCoincidentKeys = Set.of();

    /**
     * The live sites a partition is built from: every drawn system's {@code {x, y}} hyperspace
     * position, minus the ones currently moving and the ones standing on a point already taken.
     *
     * <p>The pass's visibility rules reach the drawn-set walk, so a system on the map by an
     * override enters like any other site - subject to losing a contested point to one the map
     * shows in its own right.
     *
     * @param pass             the rebuild's reading of the sector, whose walk this shares; taken as
     *                         the caller's rather than opened here, so a rebuild stays inside the
     *                         one traversal the frame allows it
     * @param movingSystemKeys the systems currently moving, left out so each seeds no cell and
     *                         clips no neighbour, the cells around it filling the space as if it
     *                         were absent
     * @return each seeding system's position, in the sector's star-system order; a fresh map the
     *         caller owns
     */
    public Map<SystemKey, double[]> collectSitesFrom(
            MapVisibilityPass pass,
            Collection<SystemKey> movingSystemKeys) {

        var sites = DrawnSystemPositions.collectLivePositions(pass);

        sites.keySet().removeAll(movingSystemKeys);
        dropSitesLosingTheirPoint(pass, sites);

        return sites;
    }

    // One site per point, because one point is all a partition can cut a cell around. Two sites on
    // the same coordinate are equally near every point in the plane, so the half-plane each of
    // their cells would be clipped to has a zero normal and neither takes anything from the other:
    // the pair comes back as two cells covering identical area with no shared edge between them.
    // The fills then paint over one another, the hit test answers whichever it reaches first, and
    // the clusters never union across a border the cells do not state. Dropping one leaves the
    // single cell every consumer downstream already assumes.
    //
    // Coincidence is exact, two positions being one point only where they are the same pair of
    // numbers. Sites a hair apart do cut slivers, but a tolerance wide enough to catch those would
    // also merge systems that genuinely deserve cells of their own, and nothing in the sector says
    // where that line falls.
    //
    // The sector places such a pair today - RAT's two abyss systems sit on one hyperspace point -
    // and only one of them reaches here, the other being cut off from hyperspace and declined by
    // the drawn-set rule. The rule is stated all the same, because the drawn set is free to widen -
    // showing hidden systems already does - and the geometry is what breaks when it does.
    private void dropSitesLosingTheirPoint(
            MapVisibilityPass pass,
            Map<SystemKey, double[]> sites) {

        var systemsByKey = pass.sectorIndex().readSystemsByKey();
        var holderByPoint = new HashMap<SitePoint, SystemKey>();
        var pointByDroppedKey = new LinkedHashMap<SystemKey, SitePoint>();

        for (var site : sites.entrySet()) {

            var position = site.getValue();
            var point = new SitePoint(position[0], position[1]);
            var holder = holderByPoint.putIfAbsent(point, site.getKey());

            if (holder == null) {
                continue;
            }
            // Which of the two keeps it is the tie-breakers' to say, the two sites being equally
            // near every point around them and so telling this apart in no way at all.
            if (SiteTieBreaker.shouldTakePoint(
                    pass,
                    systemsByKey.get(holder),
                    systemsByKey.get(site.getKey()))) {
                holderByPoint.put(point, site.getKey());
                pointByDroppedKey.put(holder, point);
            } else {
                pointByDroppedKey.put(site.getKey(), point);
            }
        }
        // Removed after the walk rather than during it, since the site losing a point can be one
        // already passed over.
        sites.keySet().removeAll(pointByDroppedKey.keySet());

        reportCoincidentSites(pointByDroppedKey, holderByPoint);
    }

    // Names each dropped system beside the one holding its point, so a cell missing from the map
    // is one line away from its cause rather than a shape nobody can account for. Said only when
    // the dropped set moves: the same pair is re-collected on every update, and a line per update
    // would bury the one that is new.
    private void reportCoincidentSites(
            Map<SystemKey, SitePoint> pointByDroppedKey,
            Map<SitePoint, SystemKey> holderByPoint) {

        if (pointByDroppedKey.keySet().equals(lastReportedCoincidentKeys)) {
            return;
        }
        lastReportedCoincidentKeys = new LinkedHashSet<>(pointByDroppedKey.keySet());

        for (var dropped : pointByDroppedKey.entrySet()) {

            // Read off the point rather than remembered per drop, so a system displaced by one
            // that was itself displaced still names whoever ended up holding the point.
            var point = dropped.getValue();

            LOG.warn("Two star systems sit on one hyperspace point [" + point.x() + ", " + point.y()
                + "]: '" + holderByPoint.get(point).systemId() + "' takes the cell there, so '"
                + dropped.getKey().systemId() + "' is drawn no cell at all");
        }
    }

    /**
     * One point in hyperspace, compared by what it is rather than by which array holds it - the
     * address a site occupies, which a raw {@code {x, y}} pair cannot be, two equal arrays being
     * two distinct keys.
     *
     * @param x the point's hyperspace x
     * @param y the point's hyperspace y
     */
    private record SitePoint(double x, double y) {
    }
}
