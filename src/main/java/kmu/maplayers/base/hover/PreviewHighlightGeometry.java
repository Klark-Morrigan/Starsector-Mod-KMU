package kmu.maplayers.base.hover;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Works out what a whole set of cells lights up: the cells grouped by the cluster each sits in,
 * every group washed as one joined region, and the outline of those regions.
 *
 * <p>It exists so a set of cells resolved elsewhere can be lit over the map that is already
 * painting, with no paint state moving: no rebuild, no refilter, no re-clustering, no border
 * re-trace. The only work per previewed set is this assembly, and it is memoised so a pointer
 * resting on one costs nothing after the first frame.
 *
 * <p>Cells are joined only where the map already joins them. Grouping is by the identity of the
 * frontier {@link CellFrontierGeometry#findEnclosingLoop} returns, so two lit cells of one cluster
 * wash as one shape with the edge between them dropped, while two in rival clusters keep the seam
 * the map itself draws. A cell no loop encloses is its own group, so cells fusing into nothing
 * never join anything.
 *
 * <p>The halo traces those joined outlines rather than any cluster frontier: what a lit set says is
 * the reach of the set, not the shape of whoever holds the cells under it. A cell walled in by lit
 * neighbours of its own cluster therefore contributes no outline at all, which is the point of
 * joining - the halo follows the edge of a lit region instead of every seam inside it.
 */
public final class PreviewHighlightGeometry {

    // What the retained answer was resolved from: the key it was lit for, and the geometry each of
    // its cells was drawn as. Cells are held as what the source handed back rather than as whatever
    // it derived that from, and the containing map is not held at all - an incremental re-shape
    // replaces individual entries, which a map-identity key would miss.
    private String resolvedPreviewKey;

    private List<DrawnCell> resolvedDrawnCells = List.of();

    private HoverHighlight resolvedHighlight = HoverHighlight.NONE;

    /**
     * The geometry one previewed set lights up.
     *
     * @param source     the layer's answers about the frame it painted - every extent and every
     *                   candidate loop comes from here, so the preview can only ever trace what
     *                   was painted
     * @param previewKey what names the set being lit; it is the memo's cheap first test, failing
     *                   the moment the pointer moves to another set, so two different sets must
     *                   never share one. Null - nothing previewed - lights nothing up
     * @param cellIds    the cells to light, in the order they should assemble; ones the layer
     *                   painted nothing for are skipped
     * @return the loops and runs to draw, or {@link HoverHighlight#NONE} when nothing is previewed
     *         or none of the previewed cells has a drawable shape
     */
    public HoverHighlight resolveHighlightFor(
            HoverHighlightSource source,
            String previewKey,
            Collection<String> cellIds) {

        if (previewKey == null) {
            return HoverHighlight.NONE;
        }
        // Gathered before the memo is tested rather than after, because the gathering is what the
        // memo compares: map lookups per cell are cheap, and re-tessellating the set is not.
        var drawnCells = gatherDrawnCells(source, cellIds);

        if (previewKey.equals(resolvedPreviewKey)
                && hasSameGeometry(drawnCells, resolvedDrawnCells)) {

            return resolvedHighlight;
        }
        resolvedPreviewKey = previewKey;
        resolvedDrawnCells = drawnCells;
        resolvedHighlight = buildHighlight(drawnCells);

        return resolvedHighlight;
    }

    // Groups the cells by the cluster each is in, clips each group to that cluster's frontier, and
    // washes and traces the lot from one resolved set of loops - so every lit region's fill and its
    // outline are the same shape by construction.
    private static HoverHighlight buildHighlight(List<DrawnCell> drawnCells) {

        var washLoops = new ArrayList<List<double[]>>();

        for (var group : groupCellsByCluster(drawnCells)) {

            washLoops.addAll(
                CellFrontierGeometry.clipCellsToFrontier(group.paintedExtents(), group.frontier()));
        }
        if (washLoops.isEmpty()) {
            return HoverHighlight.NONE;
        }
        var outline = GlVertexRuns.flattenLoops(washLoops);

        // One tessellation over every group's loops rather than one per group: no two groups
        // overlap - a cell is painted by one of them and clipped into one region - so their
        // windings cannot interfere, and one run over the set costs less than a run each and
        // spares stitching the triangle soups back together afterwards.
        return new HoverHighlight(
            outline,
            PolygonTessellator.tessellateToTriangles(washLoops),
            outline);
    }

    // A linear identity scan rather than an identity map: a lit set spans a handful of clusters at
    // most, and the walk keeps the groups in the order the cells arrived in.
    private static PreviewCellGroup findGroupSharingFrontier(
            List<PreviewCellGroup> groups,
            float[] frontier) {

        for (var group : groups) {
            if (group.frontier() == frontier) {
                return group;
            }
        }
        return null;
    }

    // The previewed cells the layer actually drew, each paired with the loops it might sit inside.
    // A set can name a cell the frame is not drawing, which is one to pass over rather than an
    // empty shape to light.
    private static List<DrawnCell> gatherDrawnCells(
            HoverHighlightSource source,
            Collection<String> cellIds) {

        var drawnCells = new ArrayList<DrawnCell>(cellIds.size());

        for (var cellId : cellIds) {

            var paintedExtent = source.resolvePaintedExtentOf(cellId);
            if (paintedExtent.isEmpty()) {
                continue;
            }
            drawnCells.add(
                new DrawnCell(paintedExtent, source.resolveCandidateFrontierLoopsOf(cellId)));
        }
        return drawnCells;
    }

    // The cells split into clusters, keeping the order they arrived in. Groups are matched on the
    // identity of the frontier rather than its contents, which is the same test the map's own
    // clustering already settled - and a cell no frontier encloses opens a group of its own, since
    // there is nothing to say it shares a cluster with anything.
    private static List<PreviewCellGroup> groupCellsByCluster(List<DrawnCell> drawnCells) {

        var groups = new ArrayList<PreviewCellGroup>();

        for (var drawnCell : drawnCells) {

            var frontier = CellFrontierGeometry.findEnclosingLoop(
                drawnCell.frontierLoops(),
                drawnCell.paintedExtent());

            var group = frontier == null
                ? null
                : findGroupSharingFrontier(groups, frontier);

            if (group == null) {
                groups.add(
                    new PreviewCellGroup(
                        frontier,
                        new ArrayList<>(List.of(drawnCell.paintedExtent()))));
            } else {
                group.paintedExtents().add(drawnCell.paintedExtent());
            }
        }
        return groups;
    }

    // Whether the set is drawn exactly as the retained answer was resolved from - the memo's test,
    // cell for cell in order, since a set gaining or losing a drawn cell changes what lights up as
    // surely as one of them being re-shaped does.
    private static boolean hasSameGeometry(
            List<DrawnCell> candidates,
            List<DrawnCell> resolved) {

        if (candidates.size() != resolved.size()) {
            return false;
        }
        for (var index = 0; index < candidates.size(); index++) {
            if (!candidates.get(index).isSameGeometryAs(resolved.get(index))) {
                return false;
            }
        }
        return true;
    }

    // One previewed cell as the frame drew it. Pairing the extent with its candidate loops is what
    // keeps the two from drifting apart: they are read together, memoised together and grouped
    // together, and a cell holding one without the other is not a state this can be left in.
    private record DrawnCell(
        List<double[]> paintedExtent,
        List<float[]> frontierLoops) {

        /**
         * Whether this cell is drawn as the other one was.
         *
         * <p>Identity per half rather than equality: a rebuild or an incremental re-shape replaces
         * a whole extent or a whole loop list rather than editing either in place, so references
         * that still match are the same geometry - and comparing the vertices themselves would
         * cost more per frame than the tessellation the memo is here to save.
         */
        boolean isSameGeometryAs(DrawnCell other) {
            return paintedExtent == other.paintedExtent()
                && frontierLoops == other.frontierLoops();
        }
    }

    // One cluster's lit cells, gathered so they can be clipped and washed as a single region. The
    // frontier is the group's identity; the extents accumulate as the walk finds cells inside it.
    private record PreviewCellGroup(
        float[] frontier,
        List<List<double[]>> paintedExtents) {
    }
}
