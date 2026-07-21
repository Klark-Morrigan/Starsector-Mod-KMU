package kmu.maplayers.politicalmap.base.geometry;

import kmlib.math.geometry.HalfPlane;
import kmlib.math.geometry.LabelledPolygon;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.VoronoiCellBuilder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prototype bench for the POWER (Laguerre) diagram as a weighted replacement for the plain
 * Voronoi partition.
 *
 * <p>The one change from plain Voronoi is the weighted bisector: each cell clips against the
 * radical axis - the perpendicular bisector slid toward the weaker site by {@code (w_i-w_j)/(2D)}.
 * Weight is per system (local domination presence). The reach bound is the power level set
 * {@code sqrt(R^2 + w)}, which is what makes a frontier corner - where a pushed border meets the
 * reach - land where both cells' power distance equals {@code R^2}, so neighbours' frontier arcs
 * meet at one point. Everything else is untouched, so the diagram tiles exactly (it is a valid
 * power diagram) and needs no clamp: a strongly dominated site is simply allowed to submerge.
 */
final class PowerDiagramPrototypeTest {
    private static final Path SVG_DIRECTORY = Path.of("build", "reports", "political-map");
    private static final double WELD_TOLERANCE = 100.0;
    // Guaranteed keep-out radius a dead star's pocket must hold, as a fraction of median spacing.
    private static final double KEEP_OUT_FRACTION = 0.15;

    @Nested
    class Bench {

        @Test
        void sweepPowerDiagram() {
            var fixture = SectorFixture.loadSector(SectorFixture.listSectorNames().get(0));
            var params = SectorGeometryParameters.createDefaults();
            var sites = fixture.getSites();
            var systemIds = fixture.getSystemIds();
            var scores = fixture.getScoreBySystemId();
            var groupKeys = fixture.getGroupKeyBySystemId();

            var spacing = medianNearestSpacing(sites);
            var maxScore = maxOwnedScore(systemIds, scores, groupKeys);
            var keepOut = KEEP_OUT_FRACTION * spacing;
            System.out.printf(Locale.ROOT,
                    "sites=%d medianSpacing=%.0f maxOwnedScore=%d weld=%.0f keepOut=%.0f%n",
                    sites.size(), spacing, maxScore, WELD_TOLERANCE, keepOut);

            var plainAreas = plainVoronoiAreas(sites, params);
            var plainCells = plainCellsFor(sites, params);

            for (var alpha : new double[] {0.0, 0.25, 0.5, 1.0, 2.0}) {
                var weights = weightsFor(sites, systemIds, scores, groupKeys, alpha, spacing, maxScore);

                if (alpha == 0.0) {
                    var baseCells = buildCells(sites, weights, params);
                    System.out.printf(Locale.ROOT,
                            "alpha=0.00 [self-check] maxAreaMismatch-vs-kmlib=%.2e  overWeld=%d%n",
                            maxAreaMismatchVsPlain(baseCells, plainAreas),
                            countOver(sharedEdgeMismatches(sites, baseCells), WELD_TOLERANCE));
                    continue;
                }

                // The keep-out buff: give each dead star just enough weight to hold a keepOut pocket
                // against its strongest owned neighbour. A weight bump is a valid power diagram, so
                // it stays tiled - unlike a border clamp.
                var buffed = buffDeadStars(weights, sites, systemIds, groupKeys, keepOut);
                var noBuff = buildCells(sites, weights, params);
                var cells = buildCells(sites, buffed, params);

                var pushes = borderPushes(sites, weights, cells);
                pushes.sort(Double::compare);
                var pocketNoBuff = deadStarPocketRadii(noBuff, sites, systemIds, groupKeys);
                var pocketBuff = deadStarPocketRadii(cells, sites, systemIds, groupKeys);
                var sideEffect = countDeadOverOwned(buffed, sites, systemIds, groupKeys);
                var lost = ownedAreaLostToBuff(noBuff, cells, systemIds, groupKeys);

                System.out.printf(Locale.ROOT,
                        "alpha=%.2f push median=%.0f max=%.0f%n"
                        + "           pocketRadius min: noBuff=%.0f BUFF=%.0f (keepOut=%.0f)  "
                        + "submerged: noBuff=%d BUFF=%d  overWeld=%d%n"
                        + "           sideEffect: deadOutweighsOwnedPairs=%d  ownedCellsShrunk=%d "
                        + "medianLoss=%.0f%% maxLoss=%.0f%%%n",
                        alpha, percentile(pushes, 0.5), max(pushes),
                        min(pocketNoBuff), min(pocketBuff), keepOut,
                        countSubmerged(noBuff), countSubmerged(cells),
                        countOver(sharedEdgeMismatches(sites, cells), WELD_TOLERANCE),
                        sideEffect, lost.size(), median(lost), max(lost));

                writeSvg(String.format(Locale.ROOT, "power-diagram-a%.2f.svg", alpha),
                        sites, systemIds, groupKeys, cells, plainCells);
            }
        }

        // ----- power cells ---------------------------------------------------------------

        private static List<LabelledPolygon> buildCells(
                List<double[]> sites, double[] weights, SectorGeometryParameters params) {
            var cells = new ArrayList<LabelledPolygon>(sites.size());
            for (var i = 0; i < sites.size(); i++) {
                cells.add(buildPowerCell(i, sites, weights, params));
            }
            return cells;
        }

        private static LabelledPolygon buildPowerCell(
                int siteIndex, List<double[]> sites, double[] weights, SectorGeometryParameters params) {
            var keep = sites.get(siteIndex);
            var boundRadius = Math.sqrt(
                    params.cellRadius() * params.cellRadius() + weights[siteIndex]);
            var cell = regularPolygon(keep, boundRadius, params.boundSegments());
            for (var other = 0; other < sites.size() && !cell.isEmpty(); other++) {
                if (other == siteIndex) {
                    continue;
                }
                cell = cell.clipToHalfPlane(
                        radicalAxis(keep, sites.get(other), weights[siteIndex], weights[other]), other);
            }
            return cell;
        }

        // The true radical axis: normal (keep - drop), plane point the midpoint slid toward the
        // weaker side by (w_keep - w_drop)/(2D). Unclamped - a cap moves the plane off the axis and
        // desyncs three-way junctions into gaps; a dominated site is allowed to submerge instead.
        private static HalfPlane radicalAxis(
                double[] keep, double[] drop, double keepWeight, double dropWeight) {
            var dx = keep[0] - drop[0];
            var dy = keep[1] - drop[1];
            var dist = Math.hypot(dx, dy);
            var offset = (keepWeight - dropWeight) / (2.0 * dist);
            return new HalfPlane(
                    (keep[0] + drop[0]) * 0.5 - offset * dx / dist,
                    (keep[1] + drop[1]) * 0.5 - offset * dy / dist,
                    dx, dy);
        }

        // ----- measurements --------------------------------------------------------------

        // Each dead star's weight bumped just enough to hold a keepOut pocket against its strongest
        // owned neighbour: the border between a dead star (weight w_d) and an owned site j sits
        // (D^2 - w_j + w_d)/(2D) from the dead star, so keeping that >= keepOut needs
        // w_d >= w_j - D^2 + 2*keepOut*D. Taking the max over owned j (far/weak ones give a negative,
        // ignored) guards every side in one pass. Owned weights are untouched.
        private static double[] buffDeadStars(
                double[] weights, List<double[]> sites, List<String> systemIds,
                Map<String, String> groupKeys, double keepOut) {
            var buffed = weights.clone();
            for (var d = 0; d < sites.size(); d++) {
                if (groupKeys.containsKey(systemIds.get(d))) {
                    continue;
                }
                var need = 0.0;
                for (var j = 0; j < sites.size(); j++) {
                    if (j == d || !groupKeys.containsKey(systemIds.get(j))) {
                        continue;
                    }
                    var dist = distance(sites.get(d), sites.get(j));
                    need = Math.max(need, weights[j] - dist * dist + 2.0 * keepOut * dist);
                }
                buffed[d] = need;
            }
            return buffed;
        }

        // Each dead star's pocket radius: the largest disk centred on its star that fits in its
        // cell (min perpendicular distance from the star to a cell edge). Zero when submerged.
        private static List<Double> deadStarPocketRadii(
                List<LabelledPolygon> cells, List<double[]> sites, List<String> systemIds,
                Map<String, String> groupKeys) {
            var radii = new ArrayList<Double>();
            for (var i = 0; i < cells.size(); i++) {
                if (groupKeys.containsKey(systemIds.get(i))) {
                    continue;
                }
                radii.add(pocketRadius(cells.get(i), sites.get(i)));
            }
            return radii;
        }

        private static double pocketRadius(LabelledPolygon cell, double[] site) {
            var v = cell.getVertices();
            if (v.size() < 3) {
                return 0.0;
            }
            var minDist = Double.POSITIVE_INFINITY;
            for (var i = 0; i < v.size(); i++) {
                var a = v.get(i);
                var b = v.get((i + 1) % v.size());
                var dx = b[0] - a[0];
                var dy = b[1] - a[1];
                var len = Math.hypot(dx, dy);
                if (len > 1e-9) {
                    minDist = Math.min(minDist,
                            Math.abs((site[0] - a[0]) * dy - (site[1] - a[1]) * dx) / len);
                }
            }
            return minDist == Double.POSITIVE_INFINITY ? 0.0 : minDist;
        }

        // The side effect: adjacent (dead star, owned) pairs where the buffed dead star out-weighs
        // the colony, so its border bulges INTO the colony rather than the colony into it.
        private static int countDeadOverOwned(
                double[] buffed, List<double[]> sites, List<String> systemIds,
                Map<String, String> groupKeys) {
            var count = 0;
            for (var d = 0; d < sites.size(); d++) {
                if (groupKeys.containsKey(systemIds.get(d))) {
                    continue;
                }
                for (var j = 0; j < sites.size(); j++) {
                    if (groupKeys.containsKey(systemIds.get(j))
                            && distance(sites.get(d), sites.get(j)) < 2.0 * 4000.0
                            && buffed[d] > buffed[j]) {
                        count++;
                    }
                }
            }
            return count;
        }

        // For each owned cell, the percent area it lost from the buff (its dead-star neighbours
        // pushing in). Only cells that actually shrank are returned.
        private static List<Double> ownedAreaLostToBuff(
                List<LabelledPolygon> noBuff, List<LabelledPolygon> buffed, List<String> systemIds,
                Map<String, String> groupKeys) {
            var lost = new ArrayList<Double>();
            for (var i = 0; i < noBuff.size(); i++) {
                if (!groupKeys.containsKey(systemIds.get(i))) {
                    continue;
                }
                var before = areaOf(noBuff.get(i).getVertices());
                var after = areaOf(buffed.get(i).getVertices());
                if (before > 0 && after < before - 1.0) {
                    lost.add(100.0 * (before - after) / before);
                }
            }
            return lost;
        }

        private static double min(List<Double> values) {
            var min = Double.POSITIVE_INFINITY;
            for (var v : values) {
                min = Math.min(min, v);
            }
            return values.isEmpty() ? 0.0 : min;
        }

        private static List<Double> borderPushes(
                List<double[]> sites, double[] weights, List<LabelledPolygon> cells) {
            var pushes = new ArrayList<Double>();
            for (var i = 0; i < cells.size(); i++) {
                for (var edge : neighbourLabels(cells.get(i))) {
                    if (edge > i) {
                        pushes.add(Math.abs(weights[i] - weights[edge])
                                / (2.0 * distance(sites.get(i), sites.get(edge))));
                    }
                }
            }
            return pushes;
        }

        private static List<Double> sharedEdgeMismatches(
                List<double[]> sites, List<LabelledPolygon> cells) {
            var edgesByCell = new ArrayList<Map<Integer, double[][]>>(cells.size());
            for (var cell : cells) {
                edgesByCell.add(edgesByNeighbour(cell));
            }
            var mismatches = new ArrayList<Double>();
            for (var i = 0; i < cells.size(); i++) {
                for (var entry : edgesByCell.get(i).entrySet()) {
                    var j = entry.getKey();
                    if (j < 0 || j <= i || edgesByCell.get(j).get(i) == null) {
                        continue;
                    }
                    var a = entry.getValue();
                    var back = edgesByCell.get(j).get(i);
                    mismatches.add(Math.max(
                            Math.hypot(a[0][0] - back[1][0], a[0][1] - back[1][1]),
                            Math.hypot(a[1][0] - back[0][0], a[1][1] - back[0][1])));
                }
            }
            return mismatches;
        }

        private static Map<Integer, double[][]> edgesByNeighbour(LabelledPolygon cell) {
            var edges = new HashMap<Integer, double[][]>();
            var vertices = cell.getVertices();
            var labels = cell.getEdgeLabels();
            for (var i = 0; i < vertices.size(); i++) {
                edges.putIfAbsent(labels[i],
                        new double[][] {vertices.get(i), vertices.get((i + 1) % vertices.size())});
            }
            return edges;
        }

        private static Set<Integer> neighbourLabels(LabelledPolygon cell) {
            var labels = new HashSet<Integer>();
            for (var label : cell.getEdgeLabels()) {
                if (label >= 0) {
                    labels.add(label);
                }
            }
            return labels;
        }

        private static int countSubmerged(List<LabelledPolygon> cells) {
            var count = 0;
            for (var cell : cells) {
                if (areaOf(cell.getVertices()) <= 0) {
                    count++;
                }
            }
            return count;
        }

        private static double maxAreaMismatchVsPlain(List<LabelledPolygon> cells, double[] plainAreas) {
            var max = 0.0;
            for (var i = 0; i < cells.size(); i++) {
                if (plainAreas[i] > 0) {
                    max = Math.max(max,
                            Math.abs(areaOf(cells.get(i).getVertices()) - plainAreas[i]) / plainAreas[i]);
                }
            }
            return max;
        }

        // ----- fixtures / helpers --------------------------------------------------------

        private static double medianNearestSpacing(List<double[]> sites) {
            var nearest = new ArrayList<Double>(sites.size());
            for (var i = 0; i < sites.size(); i++) {
                var best = Double.POSITIVE_INFINITY;
                for (var j = 0; j < sites.size(); j++) {
                    if (i != j) {
                        best = Math.min(best, distance(sites.get(i), sites.get(j)));
                    }
                }
                nearest.add(best);
            }
            nearest.sort(Double::compare);
            return nearest.get(nearest.size() / 2);
        }

        private static int maxOwnedScore(
                List<String> systemIds, Map<String, Integer> scores, Map<String, String> groupKeys) {
            var max = 1;
            for (var id : systemIds) {
                if (groupKeys.containsKey(id)) {
                    max = Math.max(max, scores.getOrDefault(id, 0));
                }
            }
            return max;
        }

        private static double[] weightsFor(
                List<double[]> sites, List<String> systemIds, Map<String, Integer> scores,
                Map<String, String> groupKeys, double alpha, double spacing, int maxScore) {
            var weights = new double[sites.size()];
            for (var i = 0; i < sites.size(); i++) {
                var id = systemIds.get(i);
                weights[i] = groupKeys.containsKey(id)
                        ? alpha * (scores.getOrDefault(id, 0) / (double) maxScore) * spacing * spacing
                        : 0.0;
            }
            return weights;
        }

        private static double[] plainVoronoiAreas(
                List<double[]> sites, SectorGeometryParameters params) {
            var areas = new double[sites.size()];
            for (var i = 0; i < sites.size(); i++) {
                areas[i] = areaOf(VoronoiCellBuilder.buildLabelledCell(
                        i, sites, params.cellRadius(), params.boundSegments()).vertices());
            }
            return areas;
        }

        private static List<List<double[]>> plainCellsFor(
                List<double[]> sites, SectorGeometryParameters params) {
            var cells = new ArrayList<List<double[]>>(sites.size());
            for (var i = 0; i < sites.size(); i++) {
                cells.add(VoronoiCellBuilder.buildLabelledCell(
                        i, sites, params.cellRadius(), params.boundSegments()).vertices());
            }
            return cells;
        }

        private static LabelledPolygon regularPolygon(double[] centre, double radius, int segments) {
            var vertices = new ArrayList<double[]>(segments);
            var labels = new int[segments];
            Arrays.fill(labels, VoronoiCellBuilder.BOUND_EDGE);
            for (var i = 0; i < segments; i++) {
                var angle = 2.0 * Math.PI * i / segments;
                vertices.add(new double[] {
                        centre[0] + radius * Math.cos(angle), centre[1] + radius * Math.sin(angle)});
            }
            return LabelledPolygon.fromLabelledEdges(vertices, labels);
        }

        private static double areaOf(List<double[]> ring) {
            return ring.size() < 3 ? 0.0 : Math.abs(PolygonRegions.computeSignedArea(ring));
        }

        private static double distance(double[] a, double[] b) {
            return Math.hypot(a[0] - b[0], a[1] - b[1]);
        }

        private static double max(List<Double> values) {
            var max = 0.0;
            for (var v : values) {
                max = Math.max(max, v);
            }
            return max;
        }

        private static double median(List<Double> values) {
            if (values.isEmpty()) {
                return 0;
            }
            var sorted = new ArrayList<>(values);
            sorted.sort(Double::compare);
            return sorted.get(sorted.size() / 2);
        }

        private static double percentile(List<Double> sortedAscending, double fraction) {
            if (sortedAscending.isEmpty()) {
                return 0;
            }
            return sortedAscending.get(
                    Math.min(sortedAscending.size() - 1, (int) (fraction * sortedAscending.size())));
        }

        private static int countOver(List<Double> values, double threshold) {
            var count = 0;
            for (var v : values) {
                if (v > threshold) {
                    count++;
                }
            }
            return count;
        }

        // ----- picture -------------------------------------------------------------------

        private static void writeSvg(
                String fileName, List<double[]> sites, List<String> systemIds,
                Map<String, String> groupKeys, List<LabelledPolygon> cells,
                List<List<double[]>> plainCells) {
            var minX = Double.POSITIVE_INFINITY;
            var minY = Double.POSITIVE_INFINITY;
            var maxX = Double.NEGATIVE_INFINITY;
            var maxY = Double.NEGATIVE_INFINITY;
            for (var site : sites) {
                minX = Math.min(minX, site[0] - 5000);
                minY = Math.min(minY, site[1] - 5000);
                maxX = Math.max(maxX, site[0] + 5000);
                maxY = Math.max(maxY, site[1] + 5000);
            }
            var svg = new StringBuilder();
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"")
                    .append(fmt(minX)).append(' ').append(fmt(minY)).append(' ')
                    .append(fmt(maxX - minX)).append(' ').append(fmt(maxY - minY)).append("\">")
                    .append("<rect x=\"").append(fmt(minX)).append("\" y=\"").append(fmt(minY))
                    .append("\" width=\"").append(fmt(maxX - minX)).append("\" height=\"")
                    .append(fmt(maxY - minY)).append("\" fill=\"#111\"/>\n<g transform=\"translate(0,")
                    .append(fmt(minY + maxY)).append(") scale(1,-1)\">\n");
            for (var cell : plainCells) {
                appendRing(svg, cell, "none", "#333", 40);
            }
            var ringsByBloc = new LinkedHashMap<String, List<List<double[]>>>();
            for (var i = 0; i < sites.size(); i++) {
                var ring = cells.get(i).getVertices();
                var bloc = groupKeys.get(systemIds.get(i));
                if (bloc == null) {
                    appendRing(svg, ring, "none", "#7fd4ff", 60);
                } else {
                    ringsByBloc.computeIfAbsent(bloc, k -> new ArrayList<>()).add(ring);
                }
            }
            for (var bloc : ringsByBloc.entrySet()) {
                var colour = "hsl(" + Math.floorMod(bloc.getKey().hashCode(), 360) + " 80% 55%)";
                var d = new StringBuilder();
                for (var loop : kmlib.opengl.PolygonTessellator.tessellateToBoundaryLoops(
                        bloc.getValue())) {
                    for (var v = 0; v < loop.size(); v++) {
                        d.append(v == 0 ? 'M' : 'L').append(fmt(loop.get(v)[0])).append(' ')
                                .append(fmt(loop.get(v)[1])).append(' ');
                    }
                    d.append("Z ");
                }
                svg.append("<path fill-rule=\"evenodd\" d=\"").append(d).append("\" fill=\"")
                        .append(colour).append("\" fill-opacity=\"0.4\" stroke=\"").append(colour)
                        .append("\" stroke-width=\"40\"/>\n");
            }
            for (var i = 0; i < sites.size(); i++) {
                var owned = groupKeys.containsKey(systemIds.get(i));
                svg.append("<circle cx=\"").append(fmt(sites.get(i)[0])).append("\" cy=\"")
                        .append(fmt(sites.get(i)[1])).append("\" r=\"").append(owned ? "90" : "150")
                        .append("\" fill=\"").append(owned ? "#bbbbbb" : "#ffffff").append("\"/>\n");
            }
            svg.append("</g></svg>\n");
            try {
                Files.createDirectories(SVG_DIRECTORY);
                Files.writeString(SVG_DIRECTORY.resolve(fileName), svg.toString(),
                        StandardCharsets.US_ASCII);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            System.out.println("wrote " + SVG_DIRECTORY.resolve(fileName).toAbsolutePath());
        }

        private static void appendRing(
                StringBuilder svg, List<double[]> ring, String fill, String stroke, double width) {
            if (ring.size() < 3) {
                return;
            }
            svg.append("<polygon points=\"");
            for (var point : ring) {
                svg.append(fmt(point[0])).append(',').append(fmt(point[1])).append(' ');
            }
            svg.append("\" fill=\"").append(fill).append('"');
            if (!"none".equals(fill)) {
                svg.append(" fill-opacity=\"0.4\"");
            }
            svg.append(" stroke=\"").append(stroke).append("\" stroke-width=\"")
                    .append(fmt(width)).append("\"/>\n");
        }

        private static String fmt(double value) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
    }
}
