package kmu.maplayers.base.geometry;

import kmlib.math.geometry.VoronoiCellBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reports what the exact void trace finds, so the rebuild can be judged before anything is
 * built on it.
 *
 * <p>Deliberately small. The raster prototype grew a dozen measurements, each added after a
 * defect was found by eye, and most of them measured artefacts of the grid rather than
 * anything about the map. These are the facts the design actually asks for: how many
 * cells touch void, how the void divides, which parts are enclosed, and how big those are
 * against a cell.
 *
 * <p>Run it with {@code gradlew writeVoidRegions}.
 */
final class VoidRegionsDump {

    private static final double PERCENT_SCALE = 100.0;
    private static final int CELL_BOUND_SEGMENTS = 24;
    private static final int POCKET_ARC_SEGMENTS = 12;

    // One cell across, which is the size a section of void is cut to: a piece of void the
    // size of a system's own cell is comparable to what surrounds it, and a longer one is
    // a corridor rather than a place.
    private static final double SECTION_LENGTH =
        2 * SectorGeometryParameters.DEFAULT_CELL_RADIUS;

    // Area percentiles worth naming when deciding where the "leave it alone" threshold sits.
    private static final double MEDIAN_FRACTION = 0.5;
    private static final double[] REPORTED_PERCENTILES = {MEDIAN_FRACTION, 0.9, 1.0};

    private VoidRegionsDump() {
    }

    public static void main(String[] args) {

        for (var sectorName : SectorFixture.listSectorNames()) {

            var fixture = SectorFixture.loadSector(sectorName);
            var sites = fixture.getSites();
            var cells = buildCells(sites);

            System.out.println("=== " + sectorName + " ===");

            reportCells(cells);
            reportChannelWidths(fixture);
            reportHolesAtReach(fixture);
            reportPockets(
                VoidPockets.findVoidPockets(
                    sites,
                    fixture.getOwnerBySite(),
                    SectorGeometryParameters.createDefaults(),
                    POCKET_ARC_SEGMENTS,
                    SECTION_LENGTH),
                fixture);

            System.out.println();
        }
    }

    private static List<VoronoiCellBuilder.LabelledCell> buildCells(List<double[]> sites) {

        var cells = new ArrayList<VoronoiCellBuilder.LabelledCell>(sites.size());

        for (var index = 0; index < sites.size(); index++) {

            cells.add(VoronoiCellBuilder.buildLabelledCell(
                index,
                sites,
                SectorGeometryParameters.DEFAULT_CELL_RADIUS,
                CELL_BOUND_SEGMENTS));
        }
        return cells;
    }

    private static void reportCells(List<VoronoiCellBuilder.LabelledCell> cells) {

        var touching = 0;
        for (var cell : cells) {
            if (touchesVoid(cell)) {
                touching++;
            }
        }

        System.out.printf(
            Locale.ROOT,
            "cells: %d, of which %d touch void (%.1f%%)%n",
            cells.size(),
            touching,
            PERCENT_SCALE * touching / cells.size());
    }

    // How wide the gap between a cell's fill and what lies across it actually is, split by
    // what that is. Two cells each give up the channel, so their fills sit two channels
    // apart; a cell facing void gives up one and the void's own outline gives up the other,
    // but the halves land on opposite sides of the reach and only one of them is painted as
    // channel. A cell facing void therefore SHOWS half the band a cell facing a cell shows,
    // with no change to either inset - which is what makes it read as a missing inset once
    // the void beside it is painted a bright colour instead of the backdrop's black.
    private static void reportChannelWidths(SectorFixture fixture) {

        var parameters = SectorGeometryParameters.createDefaults();
        var geometry = SectorGeometry.buildSectorGeometry(fixture, parameters);
        var reachFacing = new ArrayList<Double>();
        var cellFacing = new ArrayList<Double>();

        for (var entry : geometry.shapedCellByCellId().entrySet()) {

            var edges = geometry.cellEdgesByCellId().get(entry.getKey());
            var fill = entry.getValue().fillPolygon();

            if (edges == null || fill.size() < 3) {
                continue;
            }

            for (var index = 0; index < fill.size(); index++) {

                if (!entry.getValue().edgeIsBoundary()[index]) {
                    continue;
                }

                var from = fill.get(index);
                var to = fill.get((index + 1) % fill.size());
                var midX = (from[0] + to[0]) / 2;
                var midY = (from[1] + to[1]) / 2;
                var source = CellEdges.findNearestEdge(edges, midX, midY);

                if (source == null) {
                    continue;
                }

                (source.target() instanceof EdgeTarget.AcrossSystem
                    ? cellFacing
                    : reachFacing)
                        .add(CellEdges.measureGapToEdge(source, midX, midY));
            }
        }
        System.out.printf(
            Locale.ROOT,
            "fill pulled back from its own cell edge: facing another cell p50 %.0f "
                + "(%d edges), facing the reach bound p50 %.0f (%d edges)%n",
            findMedian(cellFacing),
            cellFacing.size(),
            findMedian(reachFacing),
            reachFacing.size());
    }

    private static double findMedian(List<Double> values) {

        if (values.isEmpty()) {
            return 0;
        }

        var sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);

        return findPercentile(sorted, MEDIAN_FRACTION);
    }

    // How many holes there are at the reach the channel is taken at, against how many
    // there are at the true reach. A pocket shaped by moving the reach is assumed to stay
    // ONE ring; if the counts differ, some pocket pinches in two when the channel comes out
    // of it, and reshaping its ring in place cannot express that.
    private static void reportHolesAtReach(SectorFixture fixture) {

        var shipped = SectorGeometryParameters.createDefaults();

        System.out.printf(
            Locale.ROOT,
            "holes at reach %.0f: %d, at %.0f (channel taken out): %d, at %.0f "
                + "(fills' own reach): %d%n",
            shipped.cellRadius(),
            countHolesAt(fixture, shipped.cellRadius()),
            shipped.cellRadius() + shipped.borderInset(),
            countHolesAt(fixture, shipped.cellRadius() + shipped.borderInset()),
            shipped.cellRadius() - shipped.borderInset(),
            countHolesAt(fixture, shipped.cellRadius() - shipped.borderInset()));
    }

    private static int countHolesAt(SectorFixture fixture, double reach) {

        var shipped = SectorGeometryParameters.createDefaults();

        return VoidPockets.findVoidPockets(
            fixture.getSites(),
            fixture.getOwnerBySite(),
            new SectorGeometryParameters(
                reach,
                shipped.boundSegments(),
                0,
                shipped.weldTolerance(),
                shipped.miterSpikeLimit()),
            POCKET_ARC_SEGMENTS,
            SECTION_LENGTH).size();
    }

    // The first split the design asks for: a cell buried among its neighbours has every edge
    // shared with one, so nothing about the void concerns it.
    private static boolean touchesVoid(VoronoiCellBuilder.LabelledCell cell) {

        for (var neighbour : cell.edgeNeighbourSiteIndices()) {

            if (neighbour == VoronoiCellBuilder.BOUND_EDGE) {
                return true;
            }
        }
        return false;
    }

    // Every pocket, one line each. A summary count says how many pockets came out one way
    // or another; it cannot say WHICH, and every question worth asking of this map so far has
    // turned out to be about a particular pocket.
    private static void reportEachPocket(List<VoidPockets.VoidPocket> pockets) {

        System.out.println(
            "  pocket        at          span  cells  cuts   shaping     sections");

        for (var index = 0; index < pockets.size(); index++) {

            var pocket = pockets.get(index);
            var centre = pocket.centre();

            System.out.printf(
                Locale.ROOT,
                "  %-6d %7.0f,%-7.0f %6.0f %4d %5d   %-10s  %s%n",
                index,
                centre[0],
                centre[1],
                pocket.span(),
                pocket.adjacentCells().size(),
                pocket.division().cuts().size(),
                describeShaping(pocket),
                describeSections(pocket));
        }
    }

    // The longest way across any one of a pocket's sections. Sections come back largest by
    // area, which is what they are chosen by, and largest by area is not always longest.
    private static double measureLongestSection(VoidPockets.VoidPocket pocket) {

        var longest = 0.0;

        for (var section : pocket.division().sections()) {
            longest = Math.max(longest, VoidSections.measureWidestSpan(section));
        }
        return longest;
    }

    // Every section's span, largest section first. The shape of the list is the answer: a run of
    // similar numbers is an even division, and one large number followed by small ones is a
    // pocket that had slivers taken off it rather than being divided.
    private static String describeSections(VoidPockets.VoidPocket pocket) {

        var spans = new StringBuilder();

        for (var section : pocket.division().sections()) {

            var span = VoidSections.measureWidestSpan(section);

            if (spans.length() > 0) {
                spans.append(" ");
            }
            spans.append(String.format(Locale.ROOT, "%.0f", span));
        }
        return spans.toString();
    }

    private static String describeShaping(VoidPockets.VoidPocket pocket) {

        var drawn = pocket.outlines().isEmpty()
            ? "mark"
            : "x" + pocket.outlines().size();

        return (pocket.absorbingOwner() != null ? "owned/" : "void/") + drawn;
    }

    // Why a pocket did or did not close into one owner. Zero absorbed says nothing on
    // its own: a pocket ringed by six cells of six different owners and a pocket ringed by
    // one owner plus a single unowned neighbour both report the same, and only one of those
    // is evidence that the rule is too strict.
    private static void reportRingingOwners(
            List<VoidPockets.VoidPocket> pockets,
            SectorFixture fixture) {

        var ownerBySite = fixture.getOwnerBySite();
        var touchUnowned = 0;
        var oneOwnerAmongOwned = 0;

        for (var pocket : pockets) {

            var owners = new java.util.LinkedHashSet<String>();
            var hasUnowned = false;

            for (var site : pocket.adjacentCells()) {

                var owner = ownerBySite.get(site);

                if (owner == null) {
                    hasUnowned = true;
                } else {
                    owners.add(owner);
                }
            }
            if (hasUnowned) {
                touchUnowned++;
            }
            if (owners.size() == 1) {
                oneOwnerAmongOwned++;
            }
        }
        System.out.printf(
            Locale.ROOT,
            "%d pockets touch at least one unowned cell; %d ring exactly one owner "
                + "once unowned cells are set aside%n",
            touchUnowned,
            oneOwnerAmongOwned);
    }

    private static void reportPockets(
            List<VoidPockets.VoidPocket> pockets,
            SectorFixture fixture) {

        // Against a cell's full width, because that is the threshold the design uses: a
        // pocket no wider than one cell has nothing to connect across it.
        var cellWidth = 2 * SectorGeometryParameters.DEFAULT_CELL_RADIUS;

        System.out.printf(
            Locale.ROOT,
            "void pockets bound by cells: %d%n",
            pockets.size());

        if (pockets.isEmpty()) {
            return;
        }

        var wide = 0;

        for (var pocket : pockets) {

            if (pocket.span() > cellWidth) {
                wide++;
            }
        }

        System.out.printf(
            Locale.ROOT,
            "of those, %d span more than one cell width (%.0f) and %d do not%n",
            wide,
            cellWidth,
            pockets.size() - wide);

        reportSectioning(pockets);

        var absorbed = 0;
        var closedOver = 0;
        var pinched = 0;

        for (var pocket : pockets) {

            if (pocket.absorbingOwner() != null) {
                absorbed++;
            }
            if (pocket.outlines().isEmpty()) {
                closedOver++;
            } else if (pocket.outlines().size() > 1) {
                pinched++;
            }
        }

        System.out.printf(
            Locale.ROOT,
            "%d sit inside one owner's area and close into it, %d close over when the "
                + "channel is taken out, %d pinch into more than one%n",
            absorbed,
            closedOver,
            pinched);

        reportRingingOwners(pockets, fixture);
        reportEachPocket(pockets);

        var shares = new ArrayList<Double>(pockets.size());
        var sections = new ArrayList<Double>(pockets.size());

        for (var pocket : pockets) {

            shares.add(pocket.span() / cellWidth);
            sections.add((double) pocket.adjacentCells().size());

        }

        shares.sort(Double::compare);
        sections.sort(Double::compare);

        System.out.printf(
            Locale.ROOT,
            "pocket span, in cell widths: p50 %.2f / p90 %.2f / max %.2f%n",
            findPercentile(shares, REPORTED_PERCENTILES[0]),
            findPercentile(shares, REPORTED_PERCENTILES[1]),
            findPercentile(shares, REPORTED_PERCENTILES[2]));
        System.out.printf(
            Locale.ROOT,
            "cells ringing a pocket: p50 %.0f / p90 %.0f / max %.0f%n",
            findPercentile(sections, REPORTED_PERCENTILES[0]),
            findPercentile(sections, REPORTED_PERCENTILES[1]),
            findPercentile(sections, REPORTED_PERCENTILES[2]));
    }

    // Whether the division actually divides. A pocket is cut until nothing in it is longer
    // than a section, so the count of cuts says nothing on its own - what matters is what is
    // left. A section still over length is a piece the cells offered nowhere to cut, which is
    // the one failure this construction can have.
    private static void reportSectioning(List<VoidPockets.VoidPocket> pockets) {

        var toDivide = 0;
        var cuts = 0;
        var overLength = 0;
        var longestSection = 0.0;

        for (var pocket : pockets) {

            if (pocket.span() <= SECTION_LENGTH) {
                continue;
            }

            toDivide++;
            cuts += pocket.division().cuts().size();

            var longestHere = measureLongestSection(pocket);

            if (longestHere > SECTION_LENGTH) {
                overLength++;
            }

            longestSection = Math.max(longestSection, longestHere);
        }

        System.out.printf(
            Locale.ROOT,
            "%d pockets want dividing into sections of %.0f: %d cuts taken, %d still hold a "
                + "section over length, longest %.0f%n",
            toDivide,
            SECTION_LENGTH,
            cuts,
            overLength,
            longestSection);
    }

    private static double findPercentile(List<Double> sorted, double fraction) {

        var index = (int) Math.min(
            sorted.size() - 1.0,
            Math.floor(fraction * (sorted.size() - 1)));
            
        return sorted.get(index);
    }
}
