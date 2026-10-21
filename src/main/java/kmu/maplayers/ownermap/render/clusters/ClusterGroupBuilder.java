package kmu.maplayers.ownermap.render.clusters;

import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.RingRegion;
import kmlib.opengl.GlVertexRuns;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.BorderSmoothing;
import kmu.maplayers.base.render.clusters.FillSplit;
import kmu.maplayers.base.render.clusters.SplitFillBuilder;
import kmu.maplayers.base.render.clusters.StyledCluster;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.render.clusters.TracedFill;
import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.MapPaints;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bakes one owner's cells into the {@link StyledClusterGroup} the renderer paints: every body
 * the owner holds as its own {@link StyledCluster}, traced across the systems in it so a
 * multi-system body reads as one continuous frontier, under the one set of paints the owner
 * draws in wherever it is.
 *
 * <p>What it produces is a framework draw record, but what it decides is the owner map's throughout -
 * whose palette the fill resolves against, which systems the filter has left contested, which
 * cells the spotlight recedes. So it keeps its own vocabulary and stays on this side of the
 * seam, handing over a record that says nothing about owners.
 *
 * <p>Fill and border come from the same loops - triangulated for the one, flattened for the
 * other - so they can never drift apart, whichever smoothing passes ran. Disjoint bodies and
 * enclaves each come back as their own ring and are sorted into bodies from their winding and
 * containment, so rebuilding an owner from its current members alone re-splits or re-merges its
 * bodies when the incremental refresh gains or loses one.
 *
 * <p>Membership is the cells an owner draws, not the systems it holds: those differ wherever an
 * owner's cluster group includes a cell no star of its own sits in, and it is the cells that carry the
 * edges a border is traced from.
 *
 * <p>Stands up on stated inputs: the paints, the cells and how they group, and the two sets the
 * fill splits by. It reads nothing of the build those came from, so the full rebuild and the
 * incremental refresh hand it the same values and trace an owner identically - and a layer holding
 * them can trace owners without the pipeline.
 */
public final class ClusterGroupBuilder {

    private final Map<SystemKey, List<CellEdge>> cellEdgesByCellKey;
    private final CellGrouping cellGrouping;
    private final Set<SystemKey> contestedSystemKeys;
    private final MapPaints paints;
    private final Set<SystemKey> unfilledSystemKeys;

    /**
     * @param paints              the paints every owner draws in, with the scheme its border is
     *                            traced and smoothed under
     * @param cellGrouping        how the drawn cells group under the owners: it spans every cell on
     *                            the map rather than one owner's, so it is resolved once by the
     *                            caller - an owner resolving its own would rebuild the whole map's
     *                            grouping per owner, and two owners could be traced against two
     *                            readings of it
     * @param cellEdgesByCellKey  the raw cells the borders are traced from
     * @param unfilledSystemKeys  the owned systems drawn with no fill inside their owner's frontier
     * @param contestedSystemKeys the spotlit systems the pick is present in but does not win, which
     *                            hatch inside the spotlit frontier; empty off filter
     */
    public ClusterGroupBuilder(
            MapPaints paints,
            CellGrouping cellGrouping,
            Map<SystemKey, List<CellEdge>> cellEdgesByCellKey,
            Set<SystemKey> unfilledSystemKeys,
            Set<SystemKey> contestedSystemKeys) {

        this.cellEdgesByCellKey = cellEdgesByCellKey;
        this.cellGrouping = cellGrouping;
        this.contestedSystemKeys = contestedSystemKeys;
        this.paints = paints;
        this.unfilledSystemKeys = unfilledSystemKeys;
    }

    /**
     * Builds one owner's cluster group from its member cells.
     *
     * @param owner          the owner the cells are grouped under - one the owner source resolved,
     *                       or one of the filter's synthetic spotlight keys - whose ID keys the
     *                       group and whose shades it paints in
     * @param memberCellKeys the cells this owner draws
     * @return the owner's bodies with the paints they share, or null when it paints neither fill
     *         nor border, or when its cells yield no borderable geometry
     */
    public StyledClusterGroup buildClusterGroup(
            SystemOwner owner,
            List<SystemKey> memberCellKeys) {

        // What this owner's elements paint from, in one read: the bundle, the recede over it, and
        // the shades those resolve against - a desaturated owner swapping to the pass's shared
        // desaturation palette, exactly as its cells' seams do off this same call.
        var ownerPaint = paints.resolveOwnerPaintOf(owner);
        var style = ownerPaint.style();
        var fillColour = ownerPaint.pickColourOf(style.fill());
        var borderColour = ownerPaint.pickColourOf(style.outer());

        if (fillColour == null && borderColour == null) {
            return null;
        }

        var clusterRegions = traceClusterRegions(memberCellKeys);

        if (clusterRegions.isEmpty()) {
            return null;
        }

        // A "No color" fill tessellates nothing for any body, rather than triangles the draw pass
        // would then skip; the bodies still stroke and hold a label.
        var tracedFill = fillColour == null
            ? TracedFill.UNPAINTED
            : traceOwnerFill(owner.ownerId(), memberCellKeys);

        return new StyledClusterGroup(
            buildClusters(clusterRegions, tracedFill, borderColour),
            ownerPaint.pickPaintOf(style.fill()),
            ownerPaint.pickPaintOf(style.outer()),
            (float) style.outerWidth());
    }

    // Cuts each body's fill and flattens its loops into the GL runs the stroke walks. The fill is
    // asked for where the body is named, so a body cannot be paired with another body's geometry.
    // A "No color" border bakes no loops at all rather than runs the draw pass would skip; the
    // body still carries its fill, which is what an unstroked owner draws.
    private static List<StyledCluster> buildClusters(
            List<RingRegion> clusterRegions,
            TracedFill tracedFill,
            Color borderColour) {

        var isBorderDrawn = borderColour != null;
        var clusters = new ArrayList<StyledCluster>(clusterRegions.size());

        for (var clusterRegion : clusterRegions) {

            var fill = tracedFill.buildFillFor(clusterRegion);

            clusters.add(new StyledCluster(
                fill.solidTriangles(),
                fill.hatchSegments(),
                isBorderDrawn
                    ? GlVertexRuns.flattenVertices(clusterRegion.outerRing())
                    : GlVertexRuns.NO_VERTICES,
                isBorderDrawn
                    ? GlVertexRuns.flattenLoops(clusterRegion.holeRings())
                    : List.of()));
        }

        return clusters;
    }

    // The owner's bodies: one trace for the whole cluster group, smoothed, then its loops sorted
    // back into the bodies they bound. An owner holding cells in two places traces two outer rings
    // plus whatever enclaves each encloses, and which enclave belongs to which body is what decides
    // where its fill stops. Empty when the cells yield no borderable geometry.
    //
    // The trace and smoothing are the scheme's own, so a group the incremental refresh redraws is
    // traced as the full build traced its neighbours.
    private List<RingRegion> traceClusterRegions(List<SystemKey> memberCellKeys) {

        var insetRings = paints.getStyling().borderTrace().traceRings(
            memberCellKeys,
            cellEdgesByCellKey,
            cellGrouping);

        if (insetRings.isEmpty()) {
            return List.of();
        }

        var borderLoops = BorderSmoothing.resolveSmoothedBorderLoops(
            insetRings,
            paints.getStyling().renderStyle().global().borderSmoothing());

        return PolygonRegions.groupRingsIntoRegions(borderLoops);
    }

    // The owner's fill, split by fill state inside each of its bodies: the spotlighted owner's whole
    // footprint - won and contested systems alike - shares one key, so it clusters under one owner
    // and then splits its fill. Whether that split happens is the fill builder's own call.
    //
    // Traced under the same parameters as the border, so the two cannot drift apart.
    private TracedFill traceOwnerFill(
            String ownerId,
            List<SystemKey> memberCellKeys) {

        var styling = paints.getStyling();

        return new SplitFillBuilder(
                cellEdgesByCellKey,
                cellGrouping,
                styling.borderTrace(),
                styling.renderStyle().global().hatch().pattern())
            .traceFill(
                SpotlitOwners.isSpotlitOwner(ownerId),
                FillSplit.splitMembersByFillState(
                    cellGrouping,
                    memberCellKeys,
                    contestedSystemKeys,
                    unfilledSystemKeys),
                ownerId);
    }
}
