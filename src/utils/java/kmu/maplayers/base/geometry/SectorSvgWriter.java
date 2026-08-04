package kmu.maplayers.base.geometry;

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
    private static final double MARGIN = 4000.0;
    private static final int VIEW_WIDTH = 1600;
    private static final String SITE_COLOUR = "#888";
    private static final String CELL_COLOUR = "#2a2a2a";
    private static final String NEUTRAL_COLOUR = "#555";
    private static final double CELL_STROKE = 30.0;
    private static final double RING_STROKE = 90.0;
    private static final double SITE_RADIUS = 120.0;
    // Spreads owner colours around the hue circle by id hash, so neighbouring owners are
    // very unlikely to share one and the eye can separate clusters at a glance.
    private static final int HUE_RANGE = 360;
    private static final String OWNER_FILL_OPACITY = "0.35";

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
    static void writeSectorSvg(Path target, SectorFixture fixture, SectorGeometry geometry) {
        var bounds = computeBounds(fixture.getSites());
        var svg = new StringBuilder();
        openSvg(svg, bounds);
        // The raw partition, drawn faintly underneath: it is the reference the shaped cells and
        // traced borders above are read against, so a channel or a fused seam can be seen against
        // the cell edge it came from.
        appendRawCells(svg, geometry.cellEdgesByCellId());
        appendNeutralCells(svg, geometry);
        appendOwnerRings(svg, geometry.ringsByOwner());
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
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(VIEW_WIDTH)
                .append("\" height=\"").append(viewHeight)
                .append("\" viewBox=\"0 0 ").append(fmt(width)).append(' ').append(fmt(height))
                .append("\">\n<rect width=\"100%\" height=\"100%\" fill=\"#111\"/>\n")
                .append("<g transform=\"translate(").append(fmt(-bounds.minX())).append(' ')
                .append(fmt(bounds.maxY())).append(") scale(1 -1)\">\n");
    }

    // The drawing's extent, padded. A record rather than a four-slot array because the slots
    // mean different things: reading a corner back out by index invites a transposed x for y
    // that draws a plausible, wrong map.
    private record Bounds(double minX, double minY, double maxX, double maxY) {
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
            appendPolygon(svg, entry.getValue().fillPolygon(), "none", NEUTRAL_COLOUR, RING_STROKE);
        }
    }

    // Each owner as ONE path of all its rings, filled under the even-odd rule, so a ring wound
    // against the rest reads as a hole in it rather than as another island of colour. An owner's
    // enclaves and the keep-out clearings punched into it are both carried that way, and drawing
    // each ring on its own would paint them solid - the exact opposite of what they mean.
    private static void appendOwnerRings(
            StringBuilder svg, Map<String, List<List<double[]>>> ringsByOwner) {
        for (var entry : ringsByOwner.entrySet()) {
            var colour = pickOwnerColour(entry.getKey());
            var subPaths = new StringBuilder();
            for (var ring : entry.getValue()) {
                if (ring.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                    continue;
                }
                for (var i = 0; i < ring.size(); i++) {
                    subPaths.append(i == 0 ? 'M' : 'L')
                            .append(fmt(ring.get(i)[0])).append(' ')
                            .append(fmt(ring.get(i)[1])).append(' ');
                }
                subPaths.append("Z ");
            }
            if (subPaths.length() == 0) {
                continue;
            }
            svg.append("<path fill-rule=\"evenodd\" d=\"").append(subPaths)
                    .append("\" fill=\"").append(colour)
                    .append("\" fill-opacity=\"").append(OWNER_FILL_OPACITY)
                    .append("\" stroke=\"").append(colour)
                    .append("\" stroke-width=\"").append(fmt(RING_STROKE)).append("\"/>\n");
        }
    }

    private static void appendSites(StringBuilder svg, List<double[]> sites) {
        for (var site : sites) {
            svg.append("<circle cx=\"").append(fmt(site[0])).append("\" cy=\"")
                    .append(fmt(site[1])).append("\" r=\"").append(fmt(SITE_RADIUS))
                    .append("\" fill=\"").append(SITE_COLOUR).append("\"/>\n");
        }
    }

    private static void appendPolygon(
            StringBuilder svg, List<double[]> ring, String fill, String stroke, double strokeWidth) {
        if (ring.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return;
        }
        svg.append("<polygon points=\"");
        for (var point : ring) {
            svg.append(fmt(point[0])).append(',').append(fmt(point[1])).append(' ');
        }
        svg.append("\" fill=\"").append(fill).append('"');
        if (!"none".equals(fill)) {
            svg.append(" fill-opacity=\"").append(OWNER_FILL_OPACITY).append('"');
        }
        svg.append(" stroke=\"").append(stroke).append("\" stroke-width=\"")
                .append(fmt(strokeWidth)).append("\"/>\n");
    }

    private static String pickOwnerColour(String ownerId) {
        return "hsl(" + Math.floorMod(ownerId.hashCode(), HUE_RANGE) + " 80% 55%)";
    }

    private static Bounds computeBounds(List<double[]> sites) {
        var minX = Double.MAX_VALUE;
        var minY = Double.MAX_VALUE;
        var maxX = -Double.MAX_VALUE;
        var maxY = -Double.MAX_VALUE;
        for (var site : sites) {
            minX = Math.min(minX, site[0]);
            minY = Math.min(minY, site[1]);
            maxX = Math.max(maxX, site[0]);
            maxY = Math.max(maxY, site[1]);
        }
        return new Bounds(minX - MARGIN, minY - MARGIN, maxX + MARGIN, maxY + MARGIN);
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
