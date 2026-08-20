package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Limits;

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
 * <p>What this deliberately cannot answer is anything about GL state - blend modes,
 * attribute stack leaks, line smoothing, or how a pass layers against vanilla's own
 * drawing. Those depend on the state Starsector's renderer is in when it calls the map,
 * which only the game can supply, so they stay an in-game check.
 */
final class SectorSvgWriter {

    // A trapped pocket's outline is read against its own fill rather than against the
    // black, so it wants a fraction of the weight the coast line itself needs.
    private static final float TRAPPED_EDGE_STROKES = 4f;

    private static final double MARGIN = 4000.0;
    private static final int VIEW_WIDTH = 1600;

    private static final String SITE_COLOUR = "#888";
    private static final String CELL_COLOUR = "#2a2a2a";
    private static final String NEUTRAL_COLOUR = "#555";

    private static final double CELL_STROKE = 30.0;
    private static final double SITE_RADIUS = 120.0;

    // Spreads owner colours around the hue circle by id hash, so neighbouring owners are
    // very unlikely to share one and the eye can separate clusters at a glance.
    private static final int HUE_RANGE = 360;
    private static final String OWNER_FILL_OPACITY = "0.35";

    private static final int HEX_DIGITS = 6;

    // Drops the alpha byte an AWT colour packs above its three channels, which SVG has no
    // notation for here.
    private static final int RGB_MASK = 0xffffff;

    private SectorSvgWriter() {
    }

    /**
     * Writes the sector's geometry: every raw cell, each unowned cell's shaped outline, and
     * every owner's traced cluster rings, over the sites they were built from.
     *
     * @param target   file to write; parent directories are created
     * @param fixture  the sector the geometry was built from
     * @param geometry the assembled geometry to draw
     */
    static void writeSectorSvg(
            Path target,
            SectorFixture fixture,
            SectorGeometry geometry,
            VoidPockets.PocketShaping shaping) {

        var bounds = expandBy(Bounds.computeEnclosingBounds(fixture.getSites()), MARGIN);
        var svg = new StringBuilder();

        openSvg(svg, bounds);

        // The raw partition, drawn faintly underneath: it is the reference the shaped cells and
        // traced borders above are read against, so a channel or a fused seam can be seen against
        // the cell edge it came from.
        appendRawCells(svg, geometry.cellEdgesByCellId());
        appendNeutralCells(svg, geometry);
        appendOwnerRings(svg, geometry.ringsByOwner());
        appendCoastlines(svg, fixture.getSites(), shaping);
        appendSites(svg, fixture.getSites());

        svg.append("</g>\n</svg>\n");

        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, svg.toString(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + target, e);
        }
    }

    // World y grows upward and SVG y grows downward, so the whole drawing is flipped once
    // here rather than at every vertex - a map drawn upside down would read as a geometry
    // bug that is not there.
    private static void openSvg(StringBuilder svg, Bounds bounds) {

        var width = bounds.maxX() - bounds.minX();
        var height = bounds.maxY() - bounds.minY();
        var viewHeight = (int) Math.round(VIEW_WIDTH * height / width);

        svg
            .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(VIEW_WIDTH)
            .append("\" height=\"")
            .append(viewHeight)
            .append("\" viewBox=\"0 0 ")
            .append(fmt(width))
            .append(' ')
            .append(fmt(height))
            .append("\">\n<rect width=\"100%\" height=\"100%\" fill=\"#111\"/>\n")
            .append("<g transform=\"translate(")
            .append(fmt(-bounds.minX()))
            .append(' ')
            .append(fmt(bounds.maxY()))
            .append(") scale(1 -1)\">\n");
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

    private static void appendRawCells(StringBuilder svg, Map<String, List<CellEdge>> cellEdges) {

        for (var edges : cellEdges.values()) {
            var ring = new ArrayList<double[]>(edges.size());
            for (var edge : edges) {
                ring.add(new double[] {edge.x1(), edge.y1()});
            }
            appendPolygon(svg, ring, "none", CELL_COLOUR, CELL_STROKE);
        }
    }

    // Only unowned cells draw their own fill and outline on the real map; a owned cell
    // contributes its fill through its owner's traced cluster instead, so drawing it here
    // too would show a border the game never paints. The key comes off the geometry rather
    // than the fixture, so a cell the build itself grouped - or unowned - is drawn as the
    // build left it, not as the sector was handed in.
    private static void appendNeutralCells(StringBuilder svg, SectorGeometry geometry) {

        for (var entry : geometry.shapedCellByCellId().entrySet()) {
            if (geometry.ownerByCellId().containsKey(entry.getKey())) {
                continue;
            }
            appendPolygon(svg, entry.getValue().fillPolygon(), "none", NEUTRAL_COLOUR, MapLook.RING_STROKE);
        }
    }

    // Each owner as ONE path of all its rings, filled under the even-odd rule, so a ring wound
    // against the rest reads as a hole in it rather than as another island of colour. An owner's
    // enclaves and the keep-out clearings punched into it are both carried that way, and drawing
    // each ring on its own would paint them solid - the exact opposite of what they mean.
    private static void appendOwnerRings(
            StringBuilder svg,
            Map<String, List<List<double[]>>> ringsByOwner) {

        for (var entry : ringsByOwner.entrySet()) {

            var colour = pickOwnerColour(entry.getKey());
            var subPaths = new StringBuilder();

            for (var ring : entry.getValue()) {
                if (ring.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                    continue;
                }
                for (var i = 0; i < ring.size(); i++) {
                    subPaths
                        .append(i == 0 ? 'M' : 'L')
                        .append(fmt(ring.get(i)[0]))
                        .append(' ')
                        .append(fmt(ring.get(i)[1]))
                        .append(' ');
                }
                subPaths.append("Z ");
            }
            if (subPaths.length() == 0) {
                continue;
            }
            svg.append("<path fill-rule=\"evenodd\" d=\"")
                .append(subPaths)
                .append("\" fill=\"")
                .append(colour)
                .append("\" fill-opacity=\"")
                .append(OWNER_FILL_OPACITY)
                .append("\" stroke=\"")
                .append(colour)
                .append("\" stroke-width=\"")
                .append(fmt(MapLook.RING_STROKE))
                .append("\"/>\n");
        }
    }

    // Over the cells rather than under them, because the question a coast is drawn to answer
    // is where it runs against the shapes it was traced from - and a coast passing INSIDE a
    // cell is the failure worth seeing, which a cell drawn over the top would hide.
    private static void appendCoastlines(
            StringBuilder svg,
            List<double[]> sites,
            VoidPockets.PocketShaping shaping) {

        var parameters = SectorGeometryParameters.createDefaults();

        var traced = Coastlines.traceSectorCoasts(
            sites, parameters, Coastlines.DEFAULT_RULES);

        appendCapturedVoid(svg, sites, parameters, shaping);
        appendTrappedVoid(svg, traced, sites, parameters, shaping);

        for (var coast : traced.coasts()) {

            appendPolygon(
                svg,
                Coastlines.collectPoints(coast),
                "none",
                formatColour(MapLook.COASTLINE),
                MapLook.RING_STROKE);
        }
        appendPenetrations(
            svg,
            traced.union(),
            CoastCrossings.findVisibleCrossings(traced, MapLook.RING_STROKE));
    }

    // The void the BRIDGES shut in, filled. Drawn beside the coast's own pockets because the
    // two constructions divide the map's void between them: a picture holding one of them
    // shows half the answer, and a change that empties the other leaves that half looking
    // exactly as it did.
    private static void appendCapturedVoid(
            StringBuilder svg,
            List<double[]> sites,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        var bridges = VoidBridges.findVoidBridges(
            sites,
            parameters.cellRadius(),
            parameters.cellRadius() * Coastlines.DEFAULT_RULES.bridgeReachMultiple());

        for (var outline : VoidBridgePockets.findCapturedPockets(
                sites, bridges, parameters, shaping)) {

            appendPolygon(
                svg,
                outline,
                formatColour(MapLook.WIDE_VOID),
                formatColour(MapLook.WIDE_VOID),
                MapLook.RING_STROKE / TRAPPED_EDGE_STROKES);
        }
    }

    // The void the coast shut in, filled, under the line that shut it in. Drawn together
    // because the question either one answers is about the other: a pocket is right only if
    // it stops a channel short of the coast, and no number reads as an answer to that.
    private static void appendTrappedVoid(
            StringBuilder svg,
            Coastlines.TracedCoasts traced,
            List<double[]> sites,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        for (var pocket : CoastPockets.findCoastPockets(
                traced,
                CoastPockets.markEverySiteUnowned(sites),
                new VoidPockets.PocketRules(
                    parameters, ShippedMap.SECTION_RULES, shaping))) {

            for (var outline : pocket.pocket().outlines()) {

                appendPolygon(
                    svg,
                    outline,
                    formatColour(MapLook.COASTLINE),
                    formatColour(MapLook.COASTLINE),
                    MapLook.RING_STROKE / TRAPPED_EDGE_STROKES);
            }
        }
    }

    // The runs that go inside a cell, and the cells they go inside, both called out in their
    // own colours and drawn last so nothing can cover them. A report saying nineteen runs
    // cross something is a number to argue with; nineteen runs marked on the map, each beside
    // the cell it crosses, is a thing to look at.
    private static void appendPenetrations(
            StringBuilder svg,
            DiscUnion union,
            List<CoastCrossings.Penetration> penetrations) {

        for (var penetration : penetrations) {
            for (var circle : penetration.circles()) {

                appendCircle(
                    svg,
                    union.sites().get(circle),
                    union.reach(),
                    formatColour(MapLook.PIERCED_CELL),
                    MapLook.RING_STROKE);
            }
        }

        for (var penetration : penetrations) {

            appendPolyline(
                svg,
                List.of(penetration.from().point(), penetration.to().point()),
                formatColour(MapLook.COAST_CROSSING),
                MapLook.CROSSING_STROKE);
        }
    }

    // An open run rather than a closed one: a coast segment has two ends and joining them
    // back up would draw a line that is not there.
    private static void appendPolyline(
            StringBuilder svg,
            List<double[]> points,
            String stroke,
            double strokeWidth) {

        svg.append("<polyline points=\"");

        for (var point : points) {

            svg.append(fmt(point[0]))
                .append(',')
                .append(fmt(point[1]))
                .append(' ');
        }

        svg.append("\" fill=\"none\" stroke=\"")
            .append(stroke)
            .append("\" stroke-width=\"")
            .append(fmt(strokeWidth))
            .append("\"/>\n");
    }

    private static void appendCircle(
            StringBuilder svg,
            double[] centre,
            double radius,
            String stroke,
            double strokeWidth) {

        svg.append("<circle cx=\"")
            .append(fmt(centre[0]))
            .append("\" cy=\"")
            .append(fmt(centre[1]))
            .append("\" r=\"")
            .append(fmt(radius))
            .append("\" fill=\"none\" stroke=\"")
            .append(stroke)
            .append("\" stroke-width=\"")
            .append(fmt(strokeWidth))
            .append("\"/>\n");
    }

    private static void appendSites(StringBuilder svg, List<double[]> sites) {

        for (var site : sites) {

            svg.append("<circle cx=\"")
                .append(fmt(site[0]))
                .append("\" cy=\"")
                .append(fmt(site[1]))
                .append("\" r=\"")
                .append(fmt(SITE_RADIUS))
                .append("\" fill=\"")
                .append(SITE_COLOUR)
                .append("\"/>\n");
        }
    }

    private static void appendPolygon(
            StringBuilder svg,
            List<double[]> ring,
            String fill,
            String stroke,
            double strokeWidth) {

        if (ring.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return;
        }
        svg.append("<polygon points=\"");

        for (var point : ring) {

            svg.append(fmt(point[0]))
                .append(',')
                .append(fmt(point[1]))
                .append(' ');
        }
        svg.append("\" fill=\"")
            .append(fill)
            .append('"');

        if (!"none".equals(fill)) {

            svg.append(" fill-opacity=\"")
                .append(OWNER_FILL_OPACITY)
                .append('"');
        }
        svg.append(" stroke=\"")
            .append(stroke)
            .append("\" stroke-width=\"")
            .append(fmt(strokeWidth))
            .append("\"/>\n");
    }

    private static String pickOwnerColour(String ownerId) {
        return "hsl(" + Math.floorMod(ownerId.hashCode(), HUE_RANGE) + " 80% 55%)";
    }

    // The one place a colour is chosen is beside the viewer's own defaults, so the two
    // drawings of the same map cannot come to disagree about which mark means what. Spelled
    // out here in the notation SVG reads rather than kept as a second copy of the value.
    private static String formatColour(java.awt.Color colour) {

        var packed = Integer.toHexString(colour.getRGB() & RGB_MASK);
        return "#" + "0".repeat(HEX_DIGITS - packed.length()) + packed;
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
