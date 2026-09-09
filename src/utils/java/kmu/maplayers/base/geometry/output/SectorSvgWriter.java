package kmu.maplayers.base.geometry.output;

import kmlib.math.geometry.Bounds;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellEdges;
import kmu.maplayers.base.geometry.CoastCrossings;
import kmu.maplayers.base.geometry.CoastPockets;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.DrawnSector;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometry;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.VoidBridgePockets;
import kmu.maplayers.base.geometry.VoidBridges;
import kmu.maplayers.base.geometry.VoidPockets;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.svg.SvgDrawing;
import kmu.svg.SvgPaint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Draws the map's geometry to an SVG file, so a shape can be looked at without the game.
 *
 * <p>Every artifact the political map has actually shipped - overlapping fills, a border
 * flush against its neighbour, a triangular poke, an orphaned loop - is present in the
 * plain vertex arrays long before OpenGL rasterises them. GL only paints what the geometry
 * already got wrong, so standing up a context to inspect it would test the one layer least
 * likely to be at fault while needing a GPU to do it.
 *
 * <p>An SVG needs no context, no driver, and no game: it is deterministic, diffable, and
 * opens in a browser. It answers "what shape did we build", which assertions cannot,
 * because an assertion only catches what someone thought to assert.
 *
 * <p>Everything here is a decision about WHICH of the map's shapes are worth drawing and what
 * each should look like. How a shape becomes SVG text is {@link SvgDrawing}'s, which knows
 * nothing of sectors - so the two questions can be got wrong separately, and a mistake in
 * either is findable without reading the other.
 *
 * <p>What this deliberately cannot answer is anything about GL state - blend modes,
 * attribute stack leaks, line smoothing, or how a pass layers against vanilla's own
 * drawing. Those depend on the state Starsector's renderer is in when it calls the map,
 * which only the game can supply, so they stay an in-game check.
 */
public final class SectorSvgWriter {

    // A trapped pocket's outline is read against its own fill rather than against the
    // black, so it wants a fraction of the weight the coast line itself needs.
    private static final float TRAPPED_EDGE_STROKES = 4f;

    private static final double MARGIN = 4000.0;
    private static final int VIEW_WIDTH = 1600;

    // Near-black rather than black, so a shape drawn in true black still reads against it.
    private static final String BACKDROP = "#111";

    private static final String SITE_COLOUR = "#888";
    private static final String CELL_COLOUR = "#2a2a2a";
    private static final String NEUTRAL_COLOUR = "#555";

    private static final double CELL_STROKE = 30.0;
    private static final double SITE_RADIUS = 120.0;

    // Spreads owner colours around the hue circle by id hash, so neighbouring owners are
    // very unlikely to share one and the eye can separate clusters at a glance.
    private static final int HUE_RANGE = 360;

    // How solid every filled shape on this map is. One figure for all of them because they
    // are all stacked over the raw partition drawn underneath, and that partition is the
    // reference the fills are read against: a fill that hid it would remove the only thing
    // saying whether the shape follows its cells or strays off them.
    private static final double FILL_OPACITY = 0.35;

    private SectorSvgWriter() {
    }

    /**
     * Writes the sector as it is being drawn: every raw cell, each unowned cell's shaped
     * outline, and every owner's smoothed cluster rings, over the sites they were built from.
     *
     * <p>Takes what was drawn rather than the geometry alone. Given only the geometry this
     * traced its own coast at the shipped defaults and stroked the cluster rings unsmoothed,
     * so a file saved from a window whose knobs had been moved was a picture of a different
     * map - and a picture that disagrees silently with the thing it is evidence about is
     * worse than none.
     *
     * @param target  file to write; parent directories are created
     * @param fixture the sector the geometry was built from
     * @param drawn   the sector as the last rebuild produced it
     */
    public static void writeSectorSvg(Path target, SectorFixture fixture, DrawnSector drawn) {

        var bounds = expandBy(Bounds.computeEnclosingBounds(fixture.getSites()), MARGIN);
        var drawing = new SvgDrawing(bounds, VIEW_WIDTH, BACKDROP);

        // The raw partition, drawn faintly underneath: it is the reference the shaped cells and
        // traced borders above are read against, so a channel or a fused seam can be seen against
        // the cell edge it came from.
        drawRawCells(drawing, drawn.geometry().cellEdgesByCellId());
        drawNeutralCells(drawing, drawn.geometry());
        drawOwnerRings(drawing, drawn.smoothedRingsByOwner());
        drawCoastlines(drawing, fixture.getSites(), drawn);
        drawSites(drawing, fixture.getSites());

        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, drawing.finishDrawing(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + target, e);
        }
    }

    // The same box with room left around it, which kmlib's own box does not offer:
    // that is a measurement of where points are, and padding one is a decision about
    // a drawing.
    private static Bounds expandBy(Bounds bounds, double margin) {

        return new Bounds(
            bounds.minX() - margin,
            bounds.minY() - margin,
            bounds.maxX() + margin,
            bounds.maxY() + margin);
    }

    private static void drawRawCells(
            SvgDrawing drawing,
            Map<String, List<CellEdge>> cellEdges) {

        for (var edges : cellEdges.values()) {

            drawing.drawPolygon(
                CellEdges.convertEdgesToRing(edges),
                SvgPaint.outlineOnly(CELL_COLOUR, CELL_STROKE));
        }
    }

    // Only unowned cells draw their own fill and outline on the real map; a owned cell
    // contributes its fill through its owner's traced cluster instead, so drawing it here
    // too would show a border the game never paints. The key comes off the geometry rather
    // than the fixture, so a cell the build itself grouped - or unowned - is drawn as the
    // build left it, not as the sector was handed in.
    private static void drawNeutralCells(SvgDrawing drawing, SectorGeometry geometry) {

        for (var entry : geometry.shapedCellByCellId().entrySet()) {

            if (geometry.ownerByCellId().containsKey(entry.getKey())) {
                continue;
            }

            drawing.drawPolygon(
                entry.getValue().fillPolygon(),
                SvgPaint.outlineOnly(NEUTRAL_COLOUR, MapLook.RING_STROKE));
        }
    }

    // Each owner as ONE shape of all its rings, so a ring wound against the rest reads as a
    // hole in it rather than as another island of colour. An owner's enclaves and the keep-out
    // clearings punched into it are both carried that way.
    private static void drawOwnerRings(
            SvgDrawing drawing,
            Map<String, List<List<double[]>>> ringsByOwner) {

        for (var entry : ringsByOwner.entrySet()) {

            var colour = pickOwnerColour(entry.getKey());

            drawing.drawRingsAsOneShape(
                entry.getValue(),
                SvgPaint.filledOutline(colour, FILL_OPACITY, colour, MapLook.RING_STROKE));
        }
    }

    // Over the cells rather than under them, because the question a coast is drawn to answer
    // is where it runs against the shapes it was traced from - and a coast passing INSIDE a
    // cell is the failure worth seeing, which a cell drawn over the top would hide.
    private static void drawCoastlines(
            SvgDrawing drawing,
            List<double[]> sites,
            DrawnSector drawn) {

        var traced = drawn.coast();

        drawCapturedVoid(drawing, sites, drawn);
        drawTrappedVoid(drawing, traced, sites, drawn.parameters(), drawn.shaping());

        // The ROUNDED line rather than the border underneath it. What the window strokes is the
        // line after its corners are taken off, and a picture stroking the border shows a coast
        // a degree sharper at every join than the one on screen.
        for (var ring : drawn.roundedCoast().coasts()) {

            drawing.drawPolygon(
                ring,
                SvgPaint.outlineOnly(
                    SvgDrawing.formatColour(MapLook.COASTLINE), MapLook.RING_STROKE));
        }

        drawPenetrations(
            drawing,
            traced.union(),
            CoastCrossings.findVisibleCrossings(traced, MapLook.RING_STROKE));
    }

    // The void the BRIDGES shut in, filled. Drawn beside the coast's own pockets because the
    // two constructions divide the map's void between them: a picture holding one of them
    // shows half the answer, and a change that empties the other leaves that half looking
    // exactly as it did.
    private static void drawCapturedVoid(
            SvgDrawing drawing,
            List<double[]> sites,
            DrawnSector drawn) {

        var parameters = drawn.parameters();

        var bridges = VoidBridges.findVoidBridges(
            sites,
            parameters.cellRadius(),
            parameters.cellRadius() * drawn.coastRules().bridgeReachMultiple());

        var colour = SvgDrawing.formatColour(MapLook.INLAND_VOID);

        for (var outline : VoidBridgePockets.findCapturedPockets(
                sites, bridges, parameters, drawn.shaping())) {

            drawing.drawPolygon(
                outline,
                SvgPaint.filledOutline(
                    colour, FILL_OPACITY, colour, MapLook.RING_STROKE / TRAPPED_EDGE_STROKES));
        }
    }

    // The void the coast shut in, filled, under the line that shut it in. Drawn together
    // because the question either one answers is about the other: a pocket is right only if
    // it stops a channel short of the coast, and no number reads as an answer to that.
    private static void drawTrappedVoid(
            SvgDrawing drawing,
            Coastlines.TracedCoasts traced,
            List<double[]> sites,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        var colour = SvgDrawing.formatColour(MapLook.COASTLINE);

        for (var pocket : CoastPockets.findCoastPockets(
                traced,
                CoastPockets.markEverySiteUnowned(sites),
                new VoidPockets.PocketRules(parameters, shaping))) {

            for (var outline : pocket.pocket().outlines()) {

                drawing.drawPolygon(
                    outline,
                    SvgPaint.filledOutline(
                        colour,
                        FILL_OPACITY,
                        colour,
                        MapLook.RING_STROKE / TRAPPED_EDGE_STROKES));
            }
        }
    }

    // The runs that go inside a cell, and the cells they go inside, both called out in their
    // own colours and drawn last so nothing can cover them. A report saying nineteen runs
    // cross something is a number to argue with; nineteen runs marked on the map, each beside
    // the cell it crosses, is a thing to look at.
    private static void drawPenetrations(
            SvgDrawing drawing,
            DiscUnion union,
            List<CoastCrossings.Penetration> penetrations) {

        var cellPaint = SvgPaint.outlineOnly(
            SvgDrawing.formatColour(MapLook.PIERCED_CELL), MapLook.RING_STROKE);

        for (var penetration : penetrations) {
            for (var circle : penetration.circles()) {

                drawing.drawCircle(union.sites().get(circle), union.reach(), cellPaint);
            }
        }

        var crossingPaint = SvgPaint.outlineOnly(
            SvgDrawing.formatColour(MapLook.COAST_CROSSING), MapLook.CROSSING_STROKE);

        for (var penetration : penetrations) {

            drawing.drawPolyline(
                List.of(penetration.from().point(), penetration.to().point()),
                crossingPaint);
        }
    }

    private static void drawSites(SvgDrawing drawing, List<double[]> sites) {

        var paint = SvgPaint.fillOnly(SITE_COLOUR);

        for (var site : sites) {
            drawing.drawCircle(site, SITE_RADIUS, paint);
        }
    }

    // Spelled in the notation SVG reads rather than kept as an AWT colour, because the hue
    // circle is walked directly: an owner's shade is a position on it, not a value chosen
    // from a palette anything else shares.
    private static String pickOwnerColour(String ownerId) {
        return "hsl(" + Math.floorMod(ownerId.hashCode(), HUE_RANGE) + " 80% 55%)";
    }
}
