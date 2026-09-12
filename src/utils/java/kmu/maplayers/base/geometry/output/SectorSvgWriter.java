package kmu.maplayers.base.geometry.output;

import kmlib.math.geometry.Bounds;

import kmu.maplayers.base.geometry.BridgedContinents;
import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellEdges;
import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.CoastCrossings;
import kmu.maplayers.base.geometry.CoastPockets;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.DrawnSector;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometry;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.svg.SvgDrawing;
import kmu.svg.SvgPaint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    //
    // The whole construction, whatever the window had switched on: the water under the lines,
    // the spans, then the shores. A saved picture is for keeping or comparing, and one missing
    // a layer because a switch happened to be off at the time is a picture of a map that was
    // never built.
    private static void drawCoastlines(
            SvgDrawing drawing,
            List<double[]> sites,
            DrawnSector drawn) {

        var laid = drawn.laid();
        var traced = laid.traceCoasts();

        drawWater(drawing, sites, drawn);
        drawSpans(drawing, laid);

        // The ROUNDED line rather than the border underneath it. What the window strokes is the
        // line after its corners are taken off, and a picture stroking the border shows a coast
        // a degree sharper at every join than the one on screen. Lake shores in the coasts' own
        // colour, as the window draws them: a lake shore IS a coast of this construction, seen
        // from the water's side.
        var coastPaint = SvgPaint.outlineOnly(
            SvgDrawing.formatColour(MapLook.CONTINENT_COAST), MapLook.RING_STROKE);

        for (var ring : laid.roundCoasts().coasts()) {
            drawing.drawPolygon(ring, coastPaint);
        }
        for (var ring : laid.roundCoasts().lakes()) {
            drawing.drawPolygon(ring, coastPaint);
        }

        drawPenetrations(
            drawing,
            traced.union(),
            CoastCrossings.findVisibleCrossings(traced, MapLook.RING_STROKE));
    }

    // Every layer of water the construction fills, as ONE body in one colour - the way the
    // window paints it. Each pair of layers overlaps by construction and no wall can be laid
    // to keep a pair apart, so filled one over another the shared water would come out darker
    // and read as a third kind of thing. A lake's margin goes in as the band it is: its inner
    // ring is a hole, so the open water inside a drawn shore stays bare unless some other
    // layer covers it, which is what the two layers each mean with the other absent.
    //
    // With every site unowned, because this is a picture of the SHAPES: a pocket one owner
    // rings is pushed out into that owner's fills, and the shapes would move with a colouring.
    private static void drawWater(
            SvgDrawing drawing,
            List<double[]> sites,
            DrawnSector drawn) {

        var water = drawn.laid().fillWater(
            CoastPockets.markEverySiteUnowned(sites), drawn.shaping());

        var bodies = new ArrayList<List<double[]>>();
        var holes = new ArrayList<List<double[]>>();

        bodies.addAll(water.collectShoreWater());
        bodies.addAll(water.collectInletWater());
        bodies.addAll(water.collectLakeWater());
        bodies.addAll(water.collectPuddleWater());
        bodies.addAll(water.collectLinkWater());
        bodies.addAll(water.collectLinkedSectorWater());

        for (var margin : water.collectLakeMargins()) {

            bodies.add(margin.waterEdge());
            holes.add(margin.drawnShore());
        }

        var colour = SvgDrawing.formatColour(MapLook.CONTINENT_COASTAL_VOID);

        drawing.drawBodiesWithHoles(
            bodies,
            holes,
            SvgPaint.filledOutline(
                colour, FILL_OPACITY, colour, MapLook.RING_STROKE / TRAPPED_EDGE_STROKES));
    }

    // Every span the construction lays, end to end at its true extent, under the coast that
    // judged it. The first three sets in one colour because they are the same kind of claim -
    // "this much water is held between these cells" - and the links in their own, because a
    // link joins two shapes where a span rounds one out.
    private static void drawSpans(SvgDrawing drawing, BridgedContinents laid) {

        var spanPaint = SvgPaint.outlineOnly(
            SvgDrawing.formatColour(MapLook.CONTINENT_BRIDGE), MapLook.SPAN_STROKE);

        drawSpanSet(drawing, laid.layInletSpans(), spanPaint);
        drawSpanSet(drawing, laid.layLakeSpans(), spanPaint);
        drawSpanSet(drawing, laid.claimPuddleSpans(), spanPaint);

        drawSpanSet(
            drawing,
            laid.layLinks(),
            SvgPaint.outlineOnly(
                SvgDrawing.formatColour(MapLook.INTERCONTINENTAL_BRIDGE), MapLook.SPAN_STROKE));
    }

    private static void drawSpanSet(SvgDrawing drawing, List<CellGap> spans, SvgPaint paint) {

        for (var span : spans) {
            drawing.drawPolyline(List.of(span.start(), span.end()), paint);
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
