package kmu.maplayers.ownermap.render.clusters;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.ShapedCell;
import kmu.maplayers.base.render.clusters.BorderSmoothing;
import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledCluster;
import kmu.maplayers.base.render.clusters.VertexRuns;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.MapPaints;
import kmu.maplayers.ownermap.render.style.ResolvedOwnerPaint;
import kmu.maplayers.ownermap.render.style.UnownedStyleResolver;

import java.util.List;

/**
 * Bakes one shaped cell into the {@link PaintedCell} the map keeps: the {@link StyledCell} the
 * renderer paints, in whichever of the two forms the cell takes, and the ring that record's ink
 * was laid on.
 *
 * <p>An <em>owned</em> cell contributes only its interior seams: its fill and cluster border
 * are per cluster, drawn from the tessellated cluster in {@link StyledCluster}, because an
 * owner's cells fuse into one frontier. Keeping the seams is what lets a footprint read as its
 * constituent cells rather than one smooth blob - the spotlighted owner included, where the
 * solid-to-hatched transition is such a border like any other.
 *
 * <p>An <em>unowned</em> cell (inhabited, or uninhabited) does not fuse, so it has
 * no cluster to inherit from and keeps its own fill and outline. It names no owner's palette
 * either, so both its palette slots hold the shared neutral colour - until the pass recedes it,
 * which a settled unowned cell takes as readily as an owner does.
 *
 * <p>The ring is reported rather than left for the caller to pass alongside because the two forms
 * lay their ink on different rings - a fused cell on its raw extent, a lone cell on the rounded one
 * resolved here - and only this builder knows which form a cell took.
 *
 * <p>Stands up on two stated inputs: the paints the cells draw in, and who is in each system. It
 * reads nothing of the build those came from, so the full rebuild and the incremental re-shape hand
 * it the same two values and classify and style a cell identically - and a layer holding the two
 * can bake cells without the pipeline.
 */
public final class PaintedCellBuilder {

    private final SystemOccupancy occupancy;
    private final MapPaints paints;

    /**
     * @param paints    the paints every cell draws in, with the scheme its outline is rounded by
     * @param occupancy who is in each system: the owner a cell fuses under, whether anything stands
     *                  in it, and whether the spotlit owner does. Read per cell rather than copied,
     *                  so a batch folding a marked system before re-shaping it bakes what it folded
     */
    public PaintedCellBuilder(
            MapPaints paints,
            SystemOccupancy occupancy) {

        this.occupancy = occupancy;
        this.paints = paints;
    }

    /**
     * Builds one cell's draw record and the ring it was laid on, or null when the cell draws
     * nothing at all: an inset-collapsed cell, or an unowned cell with neither its fill nor its
     * outline drawn.
     *
     * <p>Takes the system the cell draws as rather than the cell itself, since everything read
     * here - the owner, the palette, whether the cell is settled - is known per system
     * and not per cell.
     *
     * @param systemKey the system the cell draws as, or null for a cell with no star of its
     *                  own, which draws as plain uninhabited - it has no owner to
     *                  colour it and nothing standing in it
     * @param shaped    the cell's inset shape
     * @return the cell's draw record and the ring it was laid on, or null when it puts no ink on
     *         the map
     */
    public PaintedCell buildPaintedCell(
            SystemKey systemKey,
            ShapedCell shaped) {

        // A cell the border inset consumed or collapsed comes back with an empty fill
        // (CellShaper via PolygonOffsets.insetSelectedEdges guarantees empty-or-drawable), so
        // there is nothing to fill or stroke.
        if (shaped.fillPolygon().isEmpty()) {
            return null;
        }

        // A cell with no star of its own is answered nobody, and draws as an unowned cell.
        var owner = occupancy.readOwnerOf(systemKey);

        return owner == null
            ? buildUnownedCell(systemKey, shaped)
            : buildOwnedCell(owner, shaped);
    }

    // The ring an unowned cell strokes and fills from: its inset outline, corner-rounded when
    // the sector-wide gate is on so it matches the cluster borders, raw when it is off. The cell
    // is a single convex inset polygon (all its edges are cluster border), so it needs no
    // chaining or envelope resolve - only the corner rounding.
    private static List<double[]> resolveOutlineOf(
            ShapedCell shaped,
            CornerRoundingStyle cornerRounding) {

        if (!cornerRounding.shouldRoundCorners()) {
            return shaped.fillPolygon();
        }

        // Through the shared pass rather than the smoothing library directly, so this outline
        // and the cluster borders around it round to one profile and cannot drift apart.
        return BorderSmoothing.roundLoopCorners(
            shaped.fillPolygon(),
            cornerRounding);
    }

    // A fused cell: its seams, in the owner's effective palette. Its fill and border are the
    // cluster's, drawn from the cluster's own shape, so this form has no slot for either, and its
    // painted ring is the raw inset extent - what bounds the ink is the cluster's own border rather
    // than anything resolved per cell. The paint comes from the pass's one reading of what this
    // owner draws in, so the seams paint exactly as the cluster paints its fill and border.
    private PaintedCell buildOwnedCell(
            SystemOwner owner,
            ShapedCell shaped) {

        var ownerPaint = paints.resolveOwnerPaintOf(owner);
        var style = ownerPaint.style();

        return new PaintedCell(
            new StyledCell.FusedCell(
                VertexRuns.flattenEdgesOfClass(shaped, false),
                ownerPaint.pickPaintOf(style.inner()),
                (float) style.innerWidth()),
            shaped.fillPolygon());
    }

    // A lone cell: its own fill and outline, and no seam - an unowned cell fuses with nothing, so it
    // has no sibling to divide from. Drops the cell when neither element puts ink down - a cell is
    // kept for its fill as readily as for its outline.
    private PaintedCell buildUnownedCell(
            SystemKey systemKey,
            ShapedCell shaped) {

        var cellPaint = resolveUnownedPaintOf(systemKey);
        var style = cellPaint.style();

        if (!style.outer().isDrawn() && !style.fill().isDrawn()) {
            return null;
        }

        return buildLoneCell(cellPaint, shaped);
    }

    // What a cell nobody owns paints in, classified off what stands in its system rather than off
    // the owner lookup that sent the cell here: an inhabited system this layer's owners do not
    // account for is not the empty backdrop, whatever the absent owner alone would suggest.
    //
    // The spotlit owner's own presence spares the cell the recede, so what a pick has standing there
    // is not sunk merely because this layer's owners could not attribute it to the pick. A cell
    // drawn as no system names nowhere anyone could be living, so the absent system skips the set
    // rather than probing one that may be immutable and null-hostile.
    private ResolvedOwnerPaint resolveUnownedPaintOf(SystemKey systemKey) {

        var isSettled = UnownedStyleResolver.isSettledSystem(
            occupancy.getInhabitedSystemKeys(),
            systemKey);

        var isSpotlitOwnerPresent = systemKey != null
            && occupancy.getSpotlitPresenceSystemKeys().contains(systemKey);

        return paints.resolveUnownedPaintOf(isSettled, isSpotlitOwnerPresent);
    }

    // The lone cell's geometry under the paint resolved for it. Its outline's corners are rounded
    // with the same corner settings and gate the cluster borders use, so a lone unowned system reads
    // as smoothly as a cluster when rounding is on and stays a sharp Voronoi cell when it is off; the
    // fill is triangulated from that same rounded ring, so it cannot spill past the line its own
    // outline strokes. That rounded ring is the cell's painted extent too - it is the whole of where
    // the cell puts ink, there being no cluster border to bound it - so it comes back as the ring
    // rather than being recomputed anywhere downstream.
    private PaintedCell buildLoneCell(
            ResolvedOwnerPaint cellPaint,
            ShapedCell shaped) {

        var style = cellPaint.style();

        // Only the rounding half of the profile: a lone cell has no spikes to sand, so the
        // sanding numbers are not this builder's to hold.
        var outline = resolveOutlineOf(
            shaped,
            paints.getStyling().renderStyle().global().borderSmoothing().cornerRounding());

        // Tessellate the fill only when it will actually be painted: an uninhabited cell is
        // outline-only and covers most of the sector, so triangulating every one of its cells
        // for a fill no pass emits would be the map's largest wasted rebuild cost.
        return new PaintedCell(
            new StyledCell.LoneCell(
                style.fill().isDrawn()
                    ? PolygonTessellator.tessellateToTriangles(List.of(outline))
                    : GlVertexRuns.NO_VERTICES,
                GlVertexRuns.flattenClosedLoopAsSegments(outline),
                cellPaint.pickPaintOf(style.fill()),
                cellPaint.pickPaintOf(style.outer()),
                (float) style.outerWidth()),
            outline);
    }
}
