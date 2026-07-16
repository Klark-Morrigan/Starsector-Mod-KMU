package kmu.maplayers.politicalmap.base.geometry;

import kmlib.math.geometry.PolygonOffsets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes each system's raw Voronoi cell into a merged cluster polygon, fused by grouping key.
 *
 * <p>An edge shared with a same-key neighbour is left on the true cell border, so the two
 * cells' fills meet exactly along it and fuse into one cluster with no seam. Every other edge -
 * against a different key, ungrouped space, or the map frontier - is pulled inward, so a
 * cluster keeps the uniform border channel against everything outside it. A kept seam edge is
 * truncated where it runs into a pulled-in border, so its ends stay within the padded border
 * rather than reaching the raw cell corner on the midline between cells.
 *
 * <p>A factionless (dead or decivilised) cell's edge facing an owned neighbour - an open
 * frontier - recedes further than the plain channel: past it by the {@link FrontierSetback}
 * for that cell's spacing to the owned star, so the pocket the cell leaves around the star
 * shrinks toward the keep-out disk. That extra pull-in is inert on owned cells and while the
 * frontier toggle is off, so those cases inset by the plain channel exactly as before.
 *
 * <p>Pure geometry over the adjacency graph and the per-system grouping keys: it decides which
 * edges merge with {@link EdgeClassifier}'s rule and offsets the rest with
 * {@link PolygonOffsets#insetSelectedEdges}, handing the render layer a ready fill polygon and a
 * per-edge boundary flag so it never re-derives adjacency. The key is opaque here (the faction
 * layer keys by dominant-faction id), so the same clustering serves any layer.
 */
public final class CellShaper {

    private CellShaper() {
    }

    /**
     * Shapes every system's cell into its merged-cluster polygon.
     *
     * @param edgesBySystemId    each system's raw cell edges, in winding order,
     *                           tagged with the neighbour across them
     * @param groupKeyBySystemId the grouping key per system; a system absent from
     *                           the map counts as ungrouped
     * @param borderInset        inward inset applied to every border edge
     * @param frontier           the pass's frontier snapshot: a factionless cell's
     *                           open-frontier edge recedes past the channel by its
     *                           setback while it is enabled
     * @return one shaped cell per input system, keyed by system id, in iteration
     *         order
     */
    public static Map<String, ShapedCell> shapeCells(
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, String> groupKeyBySystemId,
            double borderInset,
            FrontierSettings frontier) {
        var shaped = new LinkedHashMap<String, ShapedCell>();
        for (var entry : edgesBySystemId.entrySet()) {
            var ownGroupKey = groupKeyBySystemId.get(entry.getKey());
            shaped.put(
                    entry.getKey(),
                    shapeCell(
                        entry.getKey(),
                        entry.getValue(),
                        ownGroupKey,
                        groupKeyBySystemId,
                        borderInset,
                        frontier));
        }
        return shaped;
    }

    /**
     * Shapes one system's cell into its merged-cluster polygon, for the incremental
     * refresh path that re-shapes just the cells around a grouping change rather
     * than the whole map. Same rule as {@link #shapeCells}, applied to one cell.
     *
     * @param systemId           this cell's system id, to look up its own site for the
     *                           frontier setback
     * @param edges              the cell's raw edges, in winding order, each tagged
     *                           with the neighbour across it
     * @param ownGroupKey        the grouping key of this cell, or null if ungrouped
     * @param groupKeyBySystemId the grouping key per system, to classify each edge
     *                           as a same-key seam or a border
     * @param borderInset        inward inset applied to every border edge
     * @param frontier           the pass's frontier snapshot: a factionless cell's
     *                           open-frontier edge recedes past the channel by its
     *                           setback while it is enabled
     * @return the shaped cell: its inset fill polygon and per-edge boundary flags
     */
    public static ShapedCell shapeCell(
            String systemId,
            List<CellEdge> edges,
            String ownGroupKey,
            Map<String, String> groupKeyBySystemId,
            double borderInset,
            FrontierSettings frontier) {
        var ownSite = frontier.siteBySystemId().get(systemId);
        var vertices = new ArrayList<double[]>(edges.size());
        var edgeInsets = new double[edges.size()];
        for (var i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            vertices.add(new double[] {edge.x1(), edge.y1()});
            edgeInsets[i] = computeEdgeInset(
                    edge, ownGroupKey, ownSite, groupKeyBySystemId, borderInset, frontier);
        }
        var inset = PolygonOffsets.insetSelectedEdges(vertices, edgeInsets);
        return new ShapedCell(inset.vertices(), inset.edgeIsInset());
    }

    // The inward inset one edge receives: none for a same-key seam (left on the raw cell
    // border to fuse), the border channel for a plain boundary, and the channel plus the
    // frontier setback for a factionless cell's edge facing an owned neighbour. That extra
    // pull-in carries the edge past the channel toward the owned star's keep-out pocket, so
    // the pocket shrinks toward the keep-out disk. Adding it to (not replacing) the channel
    // keeps the edge inset by at least the channel, so it stays a drawable border rather than
    // collapsing onto the raw line when the two systems are close.
    private static double computeEdgeInset(
            CellEdge edge,
            String ownGroupKey,
            double[] ownSite,
            Map<String, String> groupKeyBySystemId,
            double borderInset,
            FrontierSettings frontier) {
        var edgeClass = EdgeClassifier.classifyAcross(edge, ownGroupKey, groupKeyBySystemId);
        if (edgeClass == EdgeClass.INTERIOR_SEAM) {
            return 0.0;
        }
        return borderInset
                + computeFrontierPullIn(edge, ownGroupKey, ownSite, edgeClass, frontier);
    }

    // The extra inward distance a factionless cell's open-frontier edge recedes by beyond
    // the channel, to reach the keep-out line short of the owned neighbour's star. Zero for
    // any other edge, an owned cell, a missing site, or while the frontier toggle is off.
    private static double computeFrontierPullIn(
            CellEdge edge,
            String ownGroupKey,
            double[] ownSite,
            EdgeClass edgeClass,
            FrontierSettings frontier) {
        if (!frontier.isEnabled() || ownGroupKey != null
                || edgeClass != EdgeClass.OPEN_FRONTIER) {
            return 0.0;
        }
        var neighbourSite = frontier.siteBySystemId().get(edge.neighbourSystemId());
        if (ownSite == null || neighbourSite == null) {
            return 0.0;
        }
        return FrontierSetback.computeSetback(ownSite, neighbourSite, frontier.keepOutRadius());
    }
}
