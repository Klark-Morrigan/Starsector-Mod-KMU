package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.VoronoiCellBuilder;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileScope;
import kmlib.profiling.ProfileSection;

import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.profiling.RebuildStepTerms;
import kmu.maplayers.base.visibility.systems.DrawnSystemPositions;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds a map layer's cell outlines, keyed by system id, and updates them
 * incrementally as systems gain or lose access.
 *
 * <p>The cells are a Voronoi partition of the on-map systems. Recomputing the
 * whole partition is O(n^3), but a Voronoi cell is local: adding or removing one
 * site only changes that site's cell and the cells within twice the cell radius of
 * it - every farther cell is provably untouched (their shared bisector lies beyond
 * the cell's bounding radius). So
 * {@link #updateFromSector} diffs the reachable set against the cache and rebuilds
 * only the affected cells, leaving distant ones in place. The first update (empty
 * cache) rebuilds everything, since every system is "added". The
 * {@link CellSeedInputs} passed in also force a full rebuild when they change, since
 * each reseeds every cell, invalidating them all regardless of the access diff.
 *
 * <p>Moving systems are excluded from the partition. A system that rewrites its own
 * hyperspace position (a mobile colony) has no stable cell to draw, and letting it
 * clip its neighbours would drag their borders around with it, so the moving-system
 * ids passed in are dropped from the site set: a mover seeds no cell and clips no
 * neighbour, and the surrounding cells fill the space as if it were absent. Because a
 * mover is simply out of the site set, its entering or leaving that set reads as an
 * ordinary remove or add in the diff - it comes to rest and rejoins with no special
 * case here.
 *
 * <p>Each cell is kept as a list of {@link CellEdge}s - the raw convex
 * cell and, at once, the cell-adjacency graph. Every edge is tagged with what lies
 * across it, which the render layer classifies into interior seams and cluster
 * boundaries once it knows each cell's owner, then shapes into merged cell clusters.
 * The cells are keyed by cell id and paired with the system each draws as: here every
 * cell is one star's own, so the two coincide, but the cells are a partition of
 * space rather than a list of stars and a consumer must not assume a cell's key names a
 * system. The cache holds the
 * raw cells rather than any shaped outline: the inset that leaves a cluster its
 * border channel is ownership-dependent - two same-owner cells fuse along their
 * shared edge - so it belongs to the render pass, not to this ownership-agnostic
 * geometry that rebuilds only on an access change.
 *
 * <p>Geometry only, no GL: the partition can be reasoned about from the sector
 * alone, independent of how the cells are drawn.
 */
public final class CellGeometryCache {

    // Every update writes its line: the cut runs when something moved rather than on a clock, so
    // each of its calls is an event a reader following a rebuild through the log wants to see -
    // the fast ones included, a cut that recomputed nothing being an answer in itself.
    private static final ProfileSection UPDATE_SECTION = ProfileSection.registerSection(
        "mapLayer.updateGeometry", RebuildStepTerms.LOGGED_EVERY_CALL);

    // What the two outcomes of an update are named on their row's slowest call and in its line,
    // since the counts alone cannot say whether a scan that recomputed nothing found nothing to do
    // or was handed nothing at all.
    private static final String UNCHANGED_CALL = "unchanged";
    private static final String REBUILT_CALL = "rebuilt";

    private final Map<String, double[]> siteBySystemId = new LinkedHashMap<>();

    // Each raw cell as adjacency edges, keyed by cell id: an affected cell's edges are
    // recomputed on an access change while distant cells keep their existing lists
    // (and their adjacency), so untouched cells stay the very same object.
    private final Map<String, List<CellEdge>> cellEdgesByCellId = new LinkedHashMap<>();

    // The system each cell draws as. Written in lockstep with the cells above, since a cell
    // and what it draws as are produced together and a reader of one always needs the other.
    private final Map<String, String> systemIdByCellId = new LinkedHashMap<>();

    // The seed inputs the cached cells were built at. Both invalidate every built cell when
    // they move, not just the ones an access change touched, so updateFromSector compares
    // this to spot the change and force a full rebuild. Null until the first update, which
    // is what makes that update rebuild from scratch rather than diff against nothing.
    private CellSeedInputs lastSeedInputs;

    /**
     * Empties the cached partition and forgets the seed inputs it was built at, so the next update
     * builds every cell from scratch instead of diffing against what is held.
     *
     * <p>Wanted when the cells belong to a sector that is no longer the live one. The incremental
     * path cannot get there on its own: it reconciles by comparing system <em>ids</em>, so a system
     * that appears in both sectors at a different position is not seen as a change and would keep the
     * cell built around its old position.
     */
    public void clearCachedCells() {
        siteBySystemId.clear();
        cellEdgesByCellId.clear();
        systemIdByCellId.clear();
        lastSeedInputs = null;
    }

    /**
     * Brings the cache in line with the sector's current on-map systems,
     * rebuilding only the outlines affected by systems that joined or left the
     * partition - including a system that entered or left it by starting or stopping
     * moving. A no-op when the participating set is unchanged.
     *
     * <p>The pass's visibility rules widen the participating set: a forced system seeds a cell
     * whatever the normal gates say, and a system inhabited only by an undiscovered
     * colony is admitted. A change to either shifts the participating set, so it reads
     * through the same add/remove diff as an access change - the caller forces this
     * update when one moves.
     *
     * @param pass            the rebuild's reading of the sector, whose walk of each system this
     *                        update shares and whose rules the drawn set is taken under; a pass
     *                        over no sector seeds nothing
     * @param movingSystemIds the systems currently moving, left out of the partition -
     *                        each seeds no cell and clips no neighbour, so the cells
     *                        around it fill the space as if it were absent
     * @param seedInputs      what to seed each cell with - its frontier resolution and its
     *                        reach; a change from the last update reseeds every cell, and
     *                        the reach also sets how far the diff scans
     */
    public void updateFromSector(
            MapVisibilityPass pass,
            Set<String> movingSystemIds,
            CellSeedInputs seedInputs) {

        // The whole update under one scope: what the diff cost and what a rebuild it triggered
        // cost are one duration, and the counts it is read against are added to the same scope
        // rather than printed beside a clock read of this method's own.
        try (var updateScope = ActiveProfiler.resolveProfiler().open(UPDATE_SECTION)) {
            updateCellsInScope(pass, movingSystemIds, seedInputs, updateScope);
        }
    }

    /**
     * @return the cell-adjacency graph keyed by cell id; each value is that cell's raw
     *         edges, every edge tagged with what lies across it. An unmodifiable live view.
     */
    public Map<String, List<CellEdge>> getCellEdgesByCellId() {
        return Collections.unmodifiableMap(cellEdgesByCellId);
    }

    /**
     * @return the system each cell draws as, keyed by cell id - the map a consumer resolves a
     *         cell's owner, palette, and name through. Every cell here is one star's own
     *         cell, so each maps to the star it was seeded from. An unmodifiable live view.
     */
    public Map<String, String> getSystemIdByCellId() {
        return Collections.unmodifiableMap(systemIdByCellId);
    }

    /**
     * @return each on-map system's Voronoi site - its {@code {x, y}} hyperspace
     *         position and the natural centre of its cell - keyed by system id. The
     *         point cloud a cluster's label anchor is fitted to. An unmodifiable live
     *         view.
     */
    public Map<String, double[]> getSiteBySystemId() {
        return Collections.unmodifiableMap(siteBySystemId);
    }

    // The update itself, reporting what it did onto the scope the entry point opened: the cells
    // it recomputed as a count, and which of its two outcomes this call was as the call's name.
    // Both are what the row's duration is read against, and both reach the log through the scope
    // rather than through a line of this class's own.
    private void updateCellsInScope(
            MapVisibilityPass pass,
            Set<String> movingSystemIds,
            CellSeedInputs seedInputs,
            ProfileScope updateScope) {

        var newSites = collectAccessibleSites(pass, movingSystemIds);

        reseedIfSeedInputsMoved(seedInputs);

        var diff = diffSitesAgainstCache(newSites);

        if (diff.isEmpty()) {
            // A refresh was requested but the participating set is identical (e.g. a
            // fingerprint collision, or a non-access change). Named so a "why did
            // nothing rebuild" question has an answer - and the diff cost (scanning
            // every system) is still the call's duration, so it shows even when nothing
            // rebuilds.
            updateScope.tagCall(UNCHANGED_CALL + " sites=" + newSites.size());
            return;
        }
        var affected = findAffectedCells(newSites, diff, seedInputs.cellRadius());

        replaceSites(newSites, diff.removed());

        var recomputedCellEdges = recomputeCells(affected, seedInputs);

        // The geometric diff: how many cells were recomputed as the count this call's duration is
        // read against, and beside it which systems entered or left the partition (an access
        // change, or a system starting or stopping moving), at what edge cost, and over how large
        // a partition. The primary trace for a cell that is misshapen, missing, or left behind
        // after such a change, and for how heavy a rebuild it triggered.
        updateScope.addCount(MapBuildCounters.CELLS, affected.size());
        updateScope.tagCall(REBUILT_CALL
            + " added=" + diff.added().size()
            + " removed=" + diff.removed().size()
            + " cellEdges=" + recomputedCellEdges
            + " sites=" + siteBySystemId.size());
    }

    // The seed inputs are what every cell is cut from, so a change to either of them invalidates
    // all cached cells regardless of the access diff. Dropping them makes the diff read every
    // current system as "added" and reseed it.
    private void reseedIfSeedInputsMoved(CellSeedInputs seedInputs) {

        if (seedInputs.equals(lastSeedInputs)) {
            return;
        }
        siteBySystemId.clear();
        cellEdgesByCellId.clear();
        systemIdByCellId.clear();
        lastSeedInputs = seedInputs;
    }

    // Which systems entered and which left the partition since the cells were last cut. A system
    // that started or stopped moving is an add or a remove here like any other.
    private SiteDiff diffSitesAgainstCache(Map<String, double[]> newSites) {

        var added = new LinkedHashSet<String>(newSites.keySet());
        added.removeAll(siteBySystemId.keySet());

        var removed = new LinkedHashSet<String>(siteBySystemId.keySet());
        removed.removeAll(newSites.keySet());

        return new SiteDiff(added, removed);
    }

    // The outlines to recompute: each added site, plus every site near a site that was added (it
    // cedes area) or removed (it reclaims area). Removed sites' positions are read from the old map
    // before it is replaced. A change at one site can only alter cells whose site is within twice
    // the cell radius (the bisector with the changed site reaches no farther), so that is the
    // diff's reach.
    private Set<String> findAffectedCells(
            Map<String, double[]> newSites,
            SiteDiff diff,
            double cellRadius) {

        var neighbourhoodRadius = 2.0 * cellRadius;
        var affected = new LinkedHashSet<String>(diff.added());

        for (var id : diff.added()) {
            affected.addAll(findSystemsNear(newSites, newSites.get(id), neighbourhoodRadius));
        }
        for (var id : diff.removed()) {
            affected.addAll(findSystemsNear(newSites, siteBySystemId.get(id), neighbourhoodRadius));
        }
        return affected;
    }

    // Takes the new site set as the partition's, dropping the cells of the systems that left it.
    private void replaceSites(Map<String, double[]> newSites, Set<String> removed) {

        siteBySystemId.clear();
        siteBySystemId.putAll(newSites);
        for (var id : removed) {
            cellEdgesByCellId.remove(id);
            systemIdByCellId.remove(id);
        }
    }

    // Recuts every affected cell against the current sites, answering how many edges that came to.
    // Ordered site list plus its parallel id list: the labelled cell builder works in site
    // indices, and the adjacency graph translates each edge's neighbour index back to a system id
    // through this list. The two stay aligned because a LinkedHashMap iterates keys and values in
    // lockstep.
    private int recomputeCells(Set<String> affected, CellSeedInputs seedInputs) {

        var allSiteIds = new ArrayList<String>(siteBySystemId.keySet());
        var allSites = new ArrayList<double[]>(siteBySystemId.values());
        var indexBySystemId = new HashMap<String, Integer>();

        for (var i = 0; i < allSiteIds.size(); i++) {
            indexBySystemId.put(allSiteIds.get(i), i);
        }

        var recomputedCellEdges = 0;
        for (var id : affected) {
            var cell = VoronoiCellBuilder.buildLabelledCell(
                indexBySystemId.get(id),
                allSites,
                seedInputs.cellRadius(),
                seedInputs.boundSegments());
            var edges = buildCellEdges(cell, allSiteIds);
            cellEdgesByCellId.put(id, edges);

            // Every cell here is one star's own, so it is keyed by that star and
            // draws as it.
            systemIdByCellId.put(id, id);
            recomputedCellEdges += edges.size();
        }
        return recomputedCellEdges;
    }

    // The live sites the partition is built from: every drawn system's position,
    // minus the ones currently moving. A mover is left out so it seeds no cell and
    // clips no neighbour; the cells around it fill the space as if it were absent.
    // The pass's visibility rules reach the drawn-set walk, so a forced or
    // undiscovered-colony system enters the partition like any other site.
    //
    // The pass is the rebuild's rather than one opened here, so this walk of the sector is
    // the same walk the stages after it make. A geometry update handed a sector could open a
    // reading of its own, which is what put three readings in one rebuild.
    private static Map<String, double[]> collectAccessibleSites(
            MapVisibilityPass pass,
            Set<String> movingSystemIds) {

        // Addressed by id because a cell is keyed by the system it is cut for and a cell id is a
        // system id, which is the address the movers are named by as well.
        var sites = DrawnSystemPositions.collectLivePositionsById(pass);
        sites.keySet().removeAll(movingSystemIds);
        return sites;
    }

    // System ids whose site is within the neighbourhood radius of origin - the
    // cells a change at origin can reach.
    private static Set<String> findSystemsNear(
            Map<String, double[]> sites,
            double[] origin,
            double neighbourhoodRadius) {

        var near = new LinkedHashSet<String>();
        var maxDistanceSquared = neighbourhoodRadius * neighbourhoodRadius;
        for (var entry : sites.entrySet()) {
            if (Points.computeDistanceSquared(origin, entry.getValue()) <= maxDistanceSquared) {
                near.add(entry.getKey());
            }
        }
        return near;
    }

    // Turns one labelled cell into its adjacency edges: each edge as a world-space
    // segment tagged with what lies across it - the neighbouring system, or the cell's
    // own outer reach bound. The builder reports each edge's neighbour as a site index,
    // resolved to a system id here through the parallel id list.
    private static List<CellEdge> buildCellEdges(
            VoronoiCellBuilder.LabelledCell cell,
            List<String> allSiteIds) {

        var vertices = cell.vertices();
        var neighbourIndices = cell.edgeNeighbourSiteIndices();
        var count = vertices.size();
        var edges = new ArrayList<CellEdge>(count);

        for (var i = 0; i < count; i++) {
            var start = vertices.get(i);
            var end = vertices.get((i + 1) % count);
            var neighbourIndex = neighbourIndices[i];
            var target = neighbourIndex == VoronoiCellBuilder.BOUND_EDGE
                ? EdgeTarget.REACH_BOUND
                : new EdgeTarget.AcrossSystem(allSiteIds.get(neighbourIndex));
            edges.add(new CellEdge(start[0], start[1], end[0], end[1], target));
        }
        return edges;
    }

    /**
     * Which systems entered and which left the partition since the cells were last cut.
     *
     * @param added   the systems now on the map that seeded no cell before
     * @param removed the systems that seeded a cell before and are off the map now
     */
    private record SiteDiff(Set<String> added, Set<String> removed) {

        boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }
}
