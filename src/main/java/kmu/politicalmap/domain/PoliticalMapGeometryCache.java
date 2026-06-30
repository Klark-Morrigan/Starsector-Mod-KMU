package kmu.politicalmap.domain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.Polygons;
import kmlib.math.geometry.VoronoiCellBuilder;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the political map's cell outlines, keyed by system id, and updates them
 * incrementally as systems gain or lose access.
 *
 * <p>The cells are a Voronoi partition of the on-map systems, each inset into
 * a rounded province outline. Recomputing the whole partition is O(n^3), but a
 * Voronoi cell is local: adding or removing one site only changes that site's
 * cell and the cells within {@code 2 * MAX_CELL_RADIUS} of it - every farther
 * cell is provably untouched (their shared bisector lies beyond the cell's
 * bounding radius). So {@link #updateFromSector} diffs the reachable set against
 * the cache and rebuilds only the affected outlines, leaving distant ones in
 * place. The first update (empty cache) rebuilds everything, since every system
 * is "added".
 *
 * <p>Geometry only, no GL: the partition can be reasoned about and tested on a
 * stub sector, independent of how the outlines are drawn.
 */
public final class PoliticalMapGeometryCache {
    // How far a system's cell may reach - its zone of control.
    private static final double MAX_CELL_RADIUS = 4000.0;
    // Inset from the true cell edge, so neighbours leave a uniform channel.
    private static final double BORDER_OFFSET = 150.0;
    // Fixed corner radius and arc segments for the rounded outline.
    private static final double CORNER_ROUNDING_RADIUS = 300.0;
    private static final int CORNER_ROUNDING_SEGMENTS = 4;
    // Corners sharper than this are chamfered flat instead of rounded: a rounded
    // arc on an acute Voronoi sliver still reads as a spike.
    private static final double BEVEL_BELOW_ANGLE_DEGREES = 45.0;
    // A change at one site can only alter cells whose site is within twice the
    // cell radius (their bisector with the changed site reaches the cell).
    private static final double NEIGHBOURHOOD_RADIUS = 2.0 * MAX_CELL_RADIUS;

    private static final Logger LOG = Global.getLogger(PoliticalMapGeometryCache.class);

    private final Map<String, double[]> siteBySystemId = new LinkedHashMap<>();
    private final Map<String, List<double[]>> outlineBySystemId = new LinkedHashMap<>();

    /**
     * Brings the cache in line with the sector's current on-map systems,
     * rebuilding only the outlines affected by systems that joined or left the
     * map. A no-op when the on-map set is unchanged.
     *
     * @param sector the sector to read; null clears nothing and does nothing
     */
    public void updateFromSector(SectorAPI sector) {
        var newSites = collectAccessibleSites(sector);

        var added = new LinkedHashSet<String>(newSites.keySet());
        added.removeAll(siteBySystemId.keySet());
        var removed = new LinkedHashSet<String>(siteBySystemId.keySet());
        removed.removeAll(newSites.keySet());
        if (added.isEmpty() && removed.isEmpty()) {
            // A refresh was requested but the reachable set is identical (e.g. a
            // fingerprint collision, or a non-access change). Logged so a "why
            // did nothing rebuild" question has an answer.
            LOG.debug("Political map geometry unchanged; reachableSites=" + newSites.size());
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
            outlineBySystemId.remove(id);
        }
        var allSites = new ArrayList<double[]>(siteBySystemId.values());
        for (var id : affected) {
            outlineBySystemId.put(id, buildOutline(siteBySystemId.get(id), allSites));
        }

        // The geometric diff: which systems entered/left the map and how many
        // outlines were recomputed. The primary trace for a province that is
        // misshapen, missing, or left behind after an access change.
        LOG.debug("Political map geometry rebuilt; added=" + added.size()
                + " removed=" + removed.size() + " recomputedOutlines=" + affected.size()
                + " totalSites=" + siteBySystemId.size());
    }

    /**
     * @return the cached province outlines keyed by system id; each outline is a
     *         list of {x, y} vertices (empty for a cell consumed by the inset).
     *         An unmodifiable live view.
     */
    public Map<String, List<double[]>> getOutlineBySystemId() {
        return Collections.unmodifiableMap(outlineBySystemId);
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

    // One system's province outline: its Voronoi cell, inset into a channel and
    // its corners rounded.
    private static List<double[]> buildOutline(double[] site, List<double[]> allSites) {
        var cell = VoronoiCellBuilder.buildCell(site, allSites, MAX_CELL_RADIUS);
        var inset = Polygons.insetConvexPolygon(cell, BORDER_OFFSET);
        return Polygons.roundCorners(inset, CORNER_ROUNDING_RADIUS, CORNER_ROUNDING_SEGMENTS,
                Math.toRadians(BEVEL_BELOW_ANGLE_DEGREES));
    }
}
