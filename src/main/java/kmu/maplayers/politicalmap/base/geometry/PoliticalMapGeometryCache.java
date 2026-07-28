package kmu.maplayers.politicalmap.base.geometry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.VoronoiCellBuilder;
import kmlib.profiling.Timings;

import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;
import kmu.maplayers.politicalmap.base.visibility.DrawnSystemPositions;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the political map's cell outlines, keyed by system id, and updates them
 * incrementally as systems gain or lose access.
 *
 * <p>The cells are a Voronoi partition of the on-map systems. Recomputing the
 * whole partition is O(n^3), but a Voronoi cell is local: adding or removing one
 * site only changes that site's cell and the cells within twice the cell radius of
 * it - every farther cell is provably untouched (their shared bisector lies beyond
 * the cell's bounding radius). So
 * {@link #updateFromSector} diffs the reachable set against the cache and rebuilds
 * only the affected cells, leaving distant ones in place. The first update (empty
 * cache) rebuilds everything, since every system is "added". Two per-cell seed
 * inputs passed in also force a full rebuild when they change - the frontier
 * resolution and the cell radius - since each reseeds every cell, invalidating them
 * all regardless of the access diff.
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
 * across it, which the render layer classifies into interior seams and national
 * boundaries once it knows who owns what, then shapes into merged faction clusters.
 * The cells are keyed by cell id and paired with the system each draws as: here every
 * cell is one star's own ground, so the two coincide, but the cells are a partition of
 * space rather than a list of stars and a consumer must not assume a cell's key names a
 * system. The cache holds the
 * raw cells rather than any shaped outline: the inset that leaves a cluster its
 * border channel is ownership-dependent - two same-faction cells fuse along their
 * shared edge - so it belongs to the render pass, not to this ownership-agnostic
 * geometry that rebuilds only on an access change.
 *
 * <p>Geometry only, no GL: the partition can be reasoned about and tested on a
 * stub sector, independent of how the cells are drawn.
 */
public final class PoliticalMapGeometryCache {
    private static final Logger LOG = Global.getLogger(PoliticalMapGeometryCache.class);

    private final Map<String, double[]> siteBySystemId = new LinkedHashMap<>();
    // Each raw cell as adjacency edges, keyed by cell id: an affected cell's edges are
    // recomputed on an access change while distant cells keep their existing lists
    // (and their adjacency), so untouched cells stay the very same object.
    private final Map<String, List<CellEdge>> cellEdgesByCellId = new LinkedHashMap<>();
    // The system each cell draws as. Written in lockstep with the cells above, since a cell
    // and what it draws as are produced together and a reader of one always needs the other.
    private final Map<String, String> systemIdByCellId = new LinkedHashMap<>();

    // The frontier resolution the cached cells were seeded at. The bound-segment
    // count is a per-cell seed input, so a change invalidates every built cell, not
    // just the ones an access change touched. Held here so updateFromSector can spot
    // the change and force a full rebuild. Starts at a value no real count takes, so
    // the first update always rebuilds from scratch.
    private int lastBoundSegments = -1;

    // The cell radius the cached cells were seeded at - how far a cell reaches into empty
    // space. Like the bound-segment count it is a per-cell seed input, so a change
    // invalidates every built cell; it also sets the neighbourhood radius the incremental
    // diff scans (twice the reach), so it is held for both. Starts NaN so the first update
    // always rebuilds from scratch.
    private double lastCellRadius = Double.NaN;

    /**
     * Empties the cached partition and returns the seed inputs to their rebuild-forcing values, so
     * the next update builds every cell from scratch instead of diffing against what is held.
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
        lastBoundSegments = -1;
        lastCellRadius = Double.NaN;
    }

    /**
     * Brings the cache in line with the sector's current on-map systems,
     * rebuilding only the outlines affected by systems that joined or left the
     * partition - including a system that entered or left it by starting or stopping
     * moving. A no-op when the participating set is unchanged.
     *
     * @param sector          the sector to read; null clears nothing and does nothing
     * @param movingSystemIds the systems currently moving, left out of the partition -
     *                        each seeds no cell and clips no neighbour, so the cells
     *                        around it fill the space as if it were absent
     * @param boundSegments   the frontier resolution to seed each cell at (sides of
     *                        the bound polygon); a change from the last update reseeds
     *                        every cell, since it is a per-cell input
     * @param cellRadius      how far each cell may reach into empty space, world units;
     *                        a change from the last update reseeds every cell, since it
     *                        is a per-cell input, and it sets the diff's reach
     */
    public void updateFromSector(SectorAPI sector, Set<String> movingSystemIds,
            int boundSegments, double cellRadius) {
        updateFromSector(sector, movingSystemIds, boundSegments, cellRadius,
                PoliticalMapDevOverrides.NONE);
    }

    /**
     * Brings the cache in line with the sector's current on-map systems under the dev
     * reveal overrides, which widen the participating set: force-all-systems seeds a
     * cell for every star system, and show-all-factions admits systems inhabited only
     * by an undiscovered colony. A change to either override shifts the participating
     * set, so it reads through the same add/remove diff as an access change - the
     * plugin forces this update when a toggle flips.
     *
     * @param sector          the sector to read; null clears nothing and does nothing
     * @param movingSystemIds the systems currently moving, left out of the partition
     * @param boundSegments   the frontier resolution to seed each cell at; a change
     *                        reseeds every cell
     * @param cellRadius      how far each cell may reach into empty space, world units;
     *                        a change reseeds every cell and sets the diff's reach
     * @param overrides       the pass's dev reveal overrides, applied to the drawn set
     */
    public void updateFromSector(SectorAPI sector, Set<String> movingSystemIds,
            int boundSegments, double cellRadius, PoliticalMapDevOverrides overrides) {
        // Timed independently of the profiler so the per-update cost (the whole
        // diff, or a full rebuild) reads straight from the log.
        var start = System.nanoTime();
        var newSites = collectAccessibleSites(sector, movingSystemIds, overrides);

        // The bound-segment count and the cell radius are both per-cell seed inputs -
        // one sets each frontier polygon's resolution, the other its reach - so a change
        // to either invalidates all cached cells regardless of the access diff. Drop them
        // so the diff below reads every current system as "added" and reseeds it.
        if (boundSegments != lastBoundSegments || cellRadius != lastCellRadius) {
            siteBySystemId.clear();
            cellEdgesByCellId.clear();
            systemIdByCellId.clear();
            lastBoundSegments = boundSegments;
            lastCellRadius = cellRadius;
        }

        var added = new LinkedHashSet<String>(newSites.keySet());
        added.removeAll(siteBySystemId.keySet());
        var removed = new LinkedHashSet<String>(siteBySystemId.keySet());
        removed.removeAll(newSites.keySet());
        if (added.isEmpty() && removed.isEmpty()) {
            // A refresh was requested but the participating set is identical (e.g. a
            // fingerprint collision, or a non-access change). Logged so a "why did
            // nothing rebuild" question has an answer - and the diff cost (scanning
            // every system) shows even when nothing rebuilds.
            LOG.debug("Political map geometry unchanged; reachableSites=" + newSites.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - start));
            return;
        }

        // Outlines to recompute: each added site, plus every site near a site that
        // was added (it cedes area) or removed (it reclaims area). A system that
        // started or stopped moving is an add or a remove here like any other. Read
        // removed sites' positions from the old map before it is replaced. A change at
        // one site can only alter cells whose site is within twice the cell radius (the
        // bisector with the changed site reaches no farther), so that is the diff's reach.
        var neighbourhoodRadius = 2.0 * cellRadius;
        var affected = new LinkedHashSet<String>(added);
        for (var id : added) {
            affected.addAll(findSystemsNear(newSites, newSites.get(id), neighbourhoodRadius));
        }
        for (var id : removed) {
            affected.addAll(findSystemsNear(newSites, siteBySystemId.get(id), neighbourhoodRadius));
        }

        siteBySystemId.clear();
        siteBySystemId.putAll(newSites);
        for (var id : removed) {
            cellEdgesByCellId.remove(id);
            systemIdByCellId.remove(id);
        }
        // Ordered site list plus its parallel id list: the labelled cell builder
        // works in site indices, and the adjacency graph translates each edge's
        // neighbour index back to a system id through this list. The two stay
        // aligned because a LinkedHashMap iterates keys and values in lockstep.
        var allSiteIds = new ArrayList<String>(siteBySystemId.keySet());
        var allSites = new ArrayList<double[]>(siteBySystemId.values());
        var indexBySystemId = new HashMap<String, Integer>();
        for (var i = 0; i < allSiteIds.size(); i++) {
            indexBySystemId.put(allSiteIds.get(i), i);
        }
        var recomputedCellEdges = 0;
        for (var id : affected) {
            var cell = VoronoiCellBuilder.buildLabelledCell(
                    indexBySystemId.get(id), allSites, cellRadius, boundSegments);
            var edges = buildCellEdges(cell, allSiteIds);
            cellEdgesByCellId.put(id, edges);
            // Every cell here is one star's own ground, so it is keyed by that star and
            // draws as it.
            systemIdByCellId.put(id, id);
            recomputedCellEdges += edges.size();
        }

        // The geometric diff: which systems entered/left the partition (an access
        // change, or a system starting or stopping moving), how many cells were
        // recomputed and at what edge cost, and how long it took. The primary trace
        // for a cell that is misshapen, missing, or left behind after such a change,
        // and for how heavy a rebuild it triggered.
        LOG.debug("Political map geometry rebuilt; added=" + added.size()
                + " removed=" + removed.size() + " recomputedCells=" + affected.size()
                + " recomputedCellEdges=" + recomputedCellEdges
                + " totalSites=" + siteBySystemId.size()
                + " took=" + Timings.formatMillis(System.nanoTime() - start));
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
     *         ground, so each maps to the star it was seeded from. An unmodifiable live view.
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

    // The live sites the partition is built from: every drawn system's position,
    // minus the ones currently moving. A mover is left out so it seeds no cell and
    // clips no neighbour; the cells around it fill the space as if it were absent.
    // The dev reveal overrides pass through to the drawn-set walk, so a forced or
    // undiscovered-colony system enters the partition like any other site.
    private static Map<String, double[]> collectAccessibleSites(SectorAPI sector,
            Set<String> movingSystemIds, PoliticalMapDevOverrides overrides) {
        var sites = DrawnSystemPositions.collectLivePositions(sector, overrides);
        sites.keySet().removeAll(movingSystemIds);
        return sites;
    }

    // System ids whose site is within the neighbourhood radius of origin - the
    // cells a change at origin can reach.
    private static Set<String> findSystemsNear(Map<String, double[]> sites, double[] origin,
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
    private static List<CellEdge> buildCellEdges(VoronoiCellBuilder.LabelledCell cell,
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
}
