package kmu.politicalmap.domain.geometry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.VoronoiCellBuilder;
import kmlib.profiling.Timings;

import kmu.politicalmap.domain.visibility.MapVisibleStars;
import kmu.politicalmap.domain.visibility.PoliticalMapVisibility;

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
 * site only changes that site's cell and the cells within
 * {@code 2 * MAX_CELL_RADIUS} of it - every farther cell is provably untouched
 * (their shared bisector lies beyond the cell's bounding radius). So
 * {@link #updateFromSector} diffs the reachable set against the cache and rebuilds
 * only the affected cells, leaving distant ones in place. The first update (empty
 * cache) rebuilds everything, since every system is "added".
 *
 * <p>Each system's cell is kept as a list of {@link CellEdge}s - the raw convex
 * cell and, at once, the cell-adjacency graph. Every edge is tagged with the
 * neighbouring system across it (or none, for a frontier into empty space), which
 * the render layer classifies into interior seams and national boundaries once it
 * knows who owns what, then shapes into merged faction clusters. The cache holds the
 * raw cells rather than any shaped outline: the inset that leaves a cluster its
 * border channel is ownership-dependent - two same-faction cells fuse along their
 * shared edge - so it belongs to the render pass, not to this ownership-agnostic
 * geometry that rebuilds only on an access change.
 *
 * <p>Geometry only, no GL: the partition can be reasoned about and tested on a
 * stub sector, independent of how the cells are drawn.
 */
public final class PoliticalMapGeometryCache {
    // How far a system's cell may reach - its zone of control.
    private static final double MAX_CELL_RADIUS = 4000.0;
    // A change at one site can only alter cells whose site is within twice the
    // cell radius (their bisector with the changed site reaches the cell).
    private static final double NEIGHBOURHOOD_RADIUS = 2.0 * MAX_CELL_RADIUS;

    private static final Logger LOG = Global.getLogger(PoliticalMapGeometryCache.class);

    private final Map<String, double[]> siteBySystemId = new LinkedHashMap<>();
    // The raw cell of each system as adjacency edges: an affected cell's edges are
    // recomputed on an access change while distant cells keep their existing lists
    // (and their adjacency), so untouched cells stay the very same object.
    private final Map<String, List<CellEdge>> cellEdgesBySystemId = new LinkedHashMap<>();

    /**
     * Brings the cache in line with the sector's current on-map systems,
     * rebuilding only the outlines affected by systems that joined or left the
     * map. A no-op when the on-map set is unchanged.
     *
     * @param sector the sector to read; null clears nothing and does nothing
     */
    public void updateFromSector(SectorAPI sector) {
        // Timed independently of the profiler so the per-update cost (the whole
        // diff, or a full rebuild) reads straight from the log.
        var start = System.nanoTime();
        var newSites = collectAccessibleSites(sector);

        var added = new LinkedHashSet<String>(newSites.keySet());
        added.removeAll(siteBySystemId.keySet());
        var removed = new LinkedHashSet<String>(siteBySystemId.keySet());
        removed.removeAll(newSites.keySet());
        if (added.isEmpty() && removed.isEmpty()) {
            // A refresh was requested but the reachable set is identical (e.g. a
            // fingerprint collision, or a non-access change). Logged so a "why
            // did nothing rebuild" question has an answer - and the diff cost
            // (scanning every system) shows even when nothing rebuilds.
            LOG.debug("Political map geometry unchanged; reachableSites=" + newSites.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - start));
            return;
        }

        // Outlines to recompute: each added site, plus every site near a site
        // that was added (it cedes area) or removed (it reclaims area). Read
        // removed sites' positions from the old map before it is replaced.
        var affected = new LinkedHashSet<String>(added);
        for (var id : added) {
            affected.addAll(findSystemsNear(newSites, newSites.get(id)));
        }
        for (var id : removed) {
            affected.addAll(findSystemsNear(newSites, siteBySystemId.get(id)));
        }

        siteBySystemId.clear();
        siteBySystemId.putAll(newSites);
        for (var id : removed) {
            cellEdgesBySystemId.remove(id);
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
                    indexBySystemId.get(id), allSites, MAX_CELL_RADIUS);
            var edges = buildCellEdges(cell, allSiteIds);
            cellEdgesBySystemId.put(id, edges);
            recomputedCellEdges += edges.size();
        }

        // The geometric diff: which systems entered/left the map, how many cells
        // were recomputed and at what edge cost, and how long it took. The primary
        // trace for a cell that is misshapen, missing, or left behind after an
        // access change, and for how heavy a rebuild the change triggered.
        LOG.debug("Political map geometry rebuilt; added=" + added.size()
                + " removed=" + removed.size() + " recomputedCells=" + affected.size()
                + " recomputedCellEdges=" + recomputedCellEdges
                + " totalSites=" + siteBySystemId.size()
                + " took=" + Timings.formatMillis(System.nanoTime() - start));
    }

    /**
     * @return the cell-adjacency graph keyed by system id; each value is the
     *         system's raw cell edges, every edge tagged with the neighbouring
     *         system across it (null for a frontier into empty space). An
     *         unmodifiable live view.
     */
    public Map<String, List<CellEdge>> getCellEdgesBySystemId() {
        return Collections.unmodifiableMap(cellEdgesBySystemId);
    }

    private static Map<String, double[]> collectAccessibleSites(SectorAPI sector) {
        var sites = new LinkedHashMap<String, double[]>();
        if (sector == null) {
            return sites;
        }
        // Scanned once for the whole walk so each system's access check is an
        // O(1) lookup rather than a per-system hyperspace rescan.
        var visibleStars = MapVisibleStars.scan(sector);
        for (var system : sector.getStarSystems()) {
            var location = system.getLocation();
            if (location == null
                    || !PoliticalMapVisibility.shouldAppearOnMap(sector, system, visibleStars)) {
                continue;
            }
            sites.put(system.getId(), new double[] {location.x, location.y});
        }
        return sites;
    }

    // System ids whose site is within the neighbourhood radius of origin - the
    // cells a change at origin can reach.
    private static Set<String> findSystemsNear(Map<String, double[]> sites, double[] origin) {
        var near = new LinkedHashSet<String>();
        var maxDistanceSquared = NEIGHBOURHOOD_RADIUS * NEIGHBOURHOOD_RADIUS;
        for (var entry : sites.entrySet()) {
            var deltaX = entry.getValue()[0] - origin[0];
            var deltaY = entry.getValue()[1] - origin[1];
            if (deltaX * deltaX + deltaY * deltaY <= maxDistanceSquared) {
                near.add(entry.getKey());
            }
        }
        return near;
    }

    // Turns one labelled cell into its adjacency edges: each edge as a world-space
    // segment tagged with the neighbouring system across it, or null for an edge
    // on the max-radius bound (a frontier into empty space). The builder reports
    // each edge's neighbour as a site index, resolved to a system id here through
    // the parallel id list.
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
            var neighbourId = neighbourIndex == VoronoiCellBuilder.BOUND_EDGE
                    ? null
                    : allSiteIds.get(neighbourIndex);
            edges.add(new CellEdge(start[0], start[1], end[0], end[1], neighbourId));
        }
        return edges;
    }
}
