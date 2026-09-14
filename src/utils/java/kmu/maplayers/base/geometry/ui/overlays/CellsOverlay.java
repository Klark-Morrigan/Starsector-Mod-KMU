package kmu.maplayers.base.geometry.ui.overlays;

import kmlib.math.geometry.Limits;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellEdges;
import kmu.maplayers.base.geometry.EdgeClassifier;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.SectorGeometry;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;

/**
 * The cells themselves, drawn: the partition underneath, the fills over it, the lines that
 * divide them, and the stars they were cut around.
 *
 * <p>Apart from the canvas that calls it, because the two answer to different things. The canvas
 * owns the view - where the map sits, what a click means, what a drag does - while what a cell
 * looks like answers to the settings and to the geometry it was handed. Held together, the
 * panning and the painting shared one class and neither could be read without the other.
 *
 * <p>In four passes rather than one, because the void's own layers are drawn between them: the
 * water a coast shuts in goes over the unclipped partition and under the fills, and a coastline
 * goes over the fills and under the stars. The caller owns that order; each pass here owns the
 * weight it draws at.
 *
 * <p>The geometry arrives per call rather than being held. It is rebuilt whenever a geometry
 * knob moves, and a copy captured when this was built would go on drawing the sector as it was
 * at startup - the same reason the colours are read off the settings each time.
 */
public final class CellsOverlay {

    private final ViewerSettings settings;

    public CellsOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * The unclipped partition, under everything else.
     *
     * <p>The cells as they were cut, before any reach bound clipped them down. Drawn underneath
     * so a stray edge reads as what the clip cut away rather than as another layer laid over the
     * top.
     *
     * @param g2             where to draw
     * @param unboundedCells each cell's uncut outline, or empty when the layer is off
     */
    public void paintBeneath(Graphics2D g2, List<List<double[]>> unboundedCells) {

        g2.setStroke(new BasicStroke(MapLook.CELL_STROKE));

        for (var cell : unboundedCells) {
            MapPainting.paintFilledShape(
                g2,
                MapPainting.buildPath(cell),
                new FillLook(
                    settings.unboundedCellColour,
                    settings.unboundedCellOpacity,
                    settings.unboundedCellEdge));
        }
    }

    /**
     * The channels, then the cells that leave them: unowned cells one by one, owners as fused
     * clusters.
     *
     * @param g2                    where to draw
     * @param geometry              the sector's cells, shaped and keyed
     * @param smoothedRingsByOwner  each owner's cluster rings, with their corners taken off
     */
    public void paintFills(
            Graphics2D g2,
            SectorGeometry geometry,
            Map<String, List<List<double[]>>> smoothedRingsByOwner) {

        g2.setStroke(new BasicStroke(MapLook.RING_STROKE));

        paintChannels(g2, geometry);
        paintUnownedCells(g2, geometry);
        paintOwnerClusters(g2, smoothedRingsByOwner);
    }

    /**
     * The lines over the fills: each cell's inset contour, and the true border under the channel
     * it opened.
     *
     * @param g2       where to draw
     * @param geometry the sector's cells, shaped and keyed
     */
    public void paintLines(Graphics2D g2, SectorGeometry geometry) {

        g2.setStroke(new BasicStroke(MapLook.CELL_STROKE));

        paintFillContours(g2, geometry);
        paintCentrelines(g2, geometry);
    }

    /**
     * Each system's own position, over everything.
     *
     * @param g2    where to draw
     * @param sites the star positions the cells were cut around
     */
    public void paintSites(Graphics2D g2, List<double[]> sites) {

        g2.setColor(settings.siteColour);

        for (var site : sites) {
            g2.fill(MapPainting.buildCircle(site, MapLook.SITE_RADIUS));
        }
    }

    // The channel is the ring a cell leaves between its true edge and its inset fill, so
    // painting the whole true cell and letting the fill cover the middle leaves exactly
    // that ring showing - no second shape to build, and it cannot disagree with where the
    // fill actually stops.
    //
    // Filled only. The outline of this shape is the shared cell boundary, which is the
    // centreline rather than the channel's own border; the border is the inset contour,
    // stroked later once the fills are down.
    private void paintChannels(Graphics2D g2, SectorGeometry geometry) {

        g2.setColor(MapPainting.applyAlpha(settings.channelColour, settings.channelOpacity));

        for (var cell : geometry.cellEdgesByCellKey().values()) {
            g2.fill(MapPainting.buildPath(CellEdges.convertEdgesToRing(cell)));
        }
    }

    // Unowned per the geometry's own keys, not the fixture's: a cell the build grouped or
    // unowned must be drawn as the build left it.
    private void paintUnownedCells(Graphics2D g2, SectorGeometry geometry) {

        for (var entry : geometry.shapedCellByCellKey().entrySet()) {

            if (geometry.ownerByCellKey().containsKey(entry.getKey())
                    || entry.getValue().fillPolygon().size()
                        < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                continue;
            }
            MapPainting.paintFilledShape(
                g2,
                MapPainting.buildPath(entry.getValue().fillPolygon()),
                new FillLook(
                    settings.jitterUnowned
                        ? MapPainting.jitterBrightness(settings.unownedCellColour,
                            entry.getKey().hashCode(),
                            settings.jitterStrength)
                        : settings.unownedCellColour,
                    settings.unownedCellOpacity,
                    settings.unownedCellEdge));
        }
    }

    // One path per owner, filled even-odd, so a ring wound against the rest cuts a hole in
    // it - an enclave - instead of painting over it solid. Filling each ring on its own
    // paints an enclave as another island of the owner's colour, which is the opposite of
    // what it means.
    //
    // Whatever the inset rule is. A rule under which nothing fuses gives an owner one ring
    // per cell rather than one per cluster, which is a count this reads no differently - an
    // owner already arrives here as several rings whenever its cells sit apart.
    private void paintOwnerClusters(
            Graphics2D g2, Map<String, List<List<double[]>>> smoothedRingsByOwner) {

        for (var entry : smoothedRingsByOwner.entrySet()) {

            var cluster = new Path2D.Double(Path2D.WIND_EVEN_ODD);

            for (var ring : entry.getValue()) {
                cluster.append(MapPainting.buildPath(ring), false);
            }
            MapPainting.paintFilledShape(
                g2,
                cluster,
                new FillLook(
                    settings.resolveOwnedColour(entry.getKey()),
                    settings.ownedCellOpacity,
                    settings.ownedCellEdge));
        }
    }

    // The fill contour, edge by edge, because one contour carries two different
    // things. A fill edge that was pulled in against ANOTHER CELL is the side of a
    // border channel; a fill edge pulled in at the cell's own reach bound is just where
    // the cell stops, with no channel behind it and nothing on the far side. Colouring
    // the whole contour one way makes one of those two invisible.
    //
    // Which is which comes from the true edge the fill edge was pulled off: the inset is
    // a parallel offset, so the nearest true edge to a fill edge's midpoint is the edge
    // it came from, and that edge is tagged with what lies across it.
    private void paintFillContours(Graphics2D g2, SectorGeometry geometry) {

        for (var entry : geometry.shapedCellByCellKey().entrySet()) {

            var shaped = entry.getValue();
            var trueEdges = geometry.cellEdgesByCellKey().get(entry.getKey());

            if (trueEdges == null
                    || shaped.fillPolygon().size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                continue;
            }

            var cellOwner = geometry.ownerByCellKey().get(entry.getKey());
            var cellEdge = cellOwner != null
                ? settings.ownedCellEdge
                : settings.unownedCellEdge;

            var fill = shaped.fillPolygon();

            for (var index = 0; index < fill.size(); index++) {

                var from = fill.get(index);
                var to = fill.get((index + 1) % fill.size());
                var facing = findEdgeFacing(trueEdges, from, to);

                // A seam left on the true cell border fuses two same-owner fills into
                // one shape. Stroking it would draw a division that the fill itself
                // deliberately does not have.
                //
                // Two questions, not one: the fill fuses only where the edge is BOTH a
                // same-owner seam and still on its true line. Read off the inset alone
                // this is right only while the inset follows the ownership - under a
                // rule that insets nothing every edge is on its true line, and a cell
                // would lose the outline it has against a rival.
                if (!shaped.edgeIsBoundary()[index] && isFusedSeamAt(geometry, facing, cellOwner)) {
                    continue;
                }

                g2.setColor(MapPainting.applyAlpha(
                    facing != null && facing.target() instanceof EdgeTarget.AcrossSystem
                        ? settings.channelEdge
                        : cellEdge,
                    MapLook.OPAQUE_ALPHA));

                g2.draw(new Line2D.Double(from[0], from[1], to[0], to[1]));
            }
        }
    }

    // The true cell border, but only where another cell is actually across it. That is
    // what makes a line a CENTRELINE: two cells meet along it and each backs off by the
    // same inset, leaving it running down the middle of what opens up. A reach-bound
    // edge has nothing on the far side to be the centre of - it is the cell's outer
    // silhouette, and belongs to the cell's own outline colour.
    private void paintCentrelines(Graphics2D g2, SectorGeometry geometry) {

        g2.setColor(MapPainting.applyAlpha(settings.centrelineColour, MapLook.OPAQUE_ALPHA));

        for (var edges : geometry.cellEdgesByCellKey().values()) {
            for (var edge : edges) {

                if (!(edge.target() instanceof EdgeTarget.AcrossSystem)) {
                    continue;
                }

                g2.draw(new Line2D.Double(edge.x1(), edge.y1(), edge.x2(), edge.y2()));
            }
        }
    }

    // Which of the cell's true edges a drawn edge came from, matched by its middle. A drawn
    // edge sits on its true edge's line or on a line parallel to it, so the middle lands
    // nearest the edge it was cut from whatever the inset rule pulled it back by.
    private static CellEdge findEdgeFacing(
            List<CellEdge> trueEdges,
            double[] from,
            double[] to) {

        return CellEdges.findNearestEdge(
            trueEdges,
            (from[0] + to[0]) / 2.0,
            (from[1] + to[1]) / 2.0);
    }

    // Whether this edge is one the cluster fuses along, which is a question about the
    // owners either side and not about where the edge was drawn. Asked through the shaper's
    // own rule rather than by comparing owners here, so the line the map does not stroke
    // and the line the shaper does not inset stay the same line.
    //
    // The owners are addressed by cell, which is the address the edge's target names while
    // each cell is its own star's - the identity the geometry is built under.
    private static boolean isFusedSeamAt(
            SectorGeometry geometry, CellEdge facing, String cellOwner) {

        return facing != null
            && !EdgeClassifier
                .classifyAcross(facing, cellOwner, geometry.ownerByCellKey())
                .isBoundary();
    }
}
