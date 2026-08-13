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

    // As the shipped shaping treats it: unowned space is a border, so a pocket touching one
    // keeps its channel rather than closing into the owner holding the rest.
    private static final boolean UNOWNED_BLOCKS_ABSORPTION = true;

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
                    UNOWNED_BLOCKS_ABSORPTION),
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
            UNOWNED_BLOCKS_ABSORPTION).size();
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

    // What the same sector does when an unowned neighbour is allowed not to block
    // absorption - the case the shipped rule never reaches here.
    private static void reportRelaxedAbsorption(SectorFixture fixture) {

        var relaxed = VoidPockets.findVoidPockets(
            fixture.getSites(),
            fixture.getOwnerBySite(),
            SectorGeometryParameters.createDefaults(),
            POCKET_ARC_SEGMENTS,
            !UNOWNED_BLOCKS_ABSORPTION);

        var absorbed = 0;
        var absorbedFellBack = 0;

        for (var pocket : relaxed) {

            if (pocket.absorbingOwner() == null) {
                continue;
            }

            absorbed++;

            if (pocket.outlines().isEmpty()) {
                absorbedFellBack++;
            }
        }

        System.out.printf(
            Locale.ROOT,
            "with unowned set aside: %d absorb, of which %d could not reach the "
                + "surrounding fills and kept their true outline%n",
            absorbed,
            absorbedFellBack);

        reportFallbackReason(relaxed, fixture);
        reportEachPocket(relaxed, fixture);
    }

    // Every pocket, side by side under both absorption rules. A summary count cannot say
    // whether ticking the box changes what is DRAWN, only what is classified, and those are
    // different questions when a classification lands on a pocket that had already fallen
    // back to its true outline for another reason.
    private static void reportEachPocket(
            List<VoidPockets.VoidPocket> relaxed,
            SectorFixture fixture) {

        var strict = VoidPockets.findVoidPockets(
            fixture.getSites(),
            fixture.getOwnerBySite(),
            SectorGeometryParameters.createDefaults(),
            POCKET_ARC_SEGMENTS,
            UNOWNED_BLOCKS_ABSORPTION);

        System.out.println("  pocket        at          span  cells   strict      relaxed");

        for (var index = 0; index < strict.size(); index++) {

            var one = strict.get(index);
            var other = relaxed.get(index);
            var centre = one.centre();

            System.out.printf(
                Locale.ROOT,
                "  %-6d %7.0f,%-7.0f %6.0f %4d   %-10s  %-10s%n",
                index,
                centre[0],
                centre[1],
                one.span(),
                one.adjacentCells().size(),
                describeShaping(one),
                describeShaping(other));
        }
    }

    private static String describeShaping(VoidPockets.VoidPocket pocket) {

        var drawn = pocket.outlines().isEmpty()
            ? "mark"
            : "x" + pocket.outlines().size();

        return (pocket.absorbingOwner() != null ? "owned/" : "void/") + drawn;
    }

    // Whether a pocket that could not reach the surrounding fills failed because two of
    // the cells ringing it no longer meet at the fills' own reach. If they do not, the void
    // there runs out into the channels rather than staying a closed pocket, and no outline
    // for the pocket alone can close the gap.
    private static void reportFallbackReason(
            List<VoidPockets.VoidPocket> pockets,
            SectorFixture fixture) {

        var sites = fixture.getSites();
        var fillReach = 2
            * (SectorGeometryParameters.DEFAULT_CELL_RADIUS - CellShaper.BORDER_INSET_DISTANCE);

        var parted = 0;
        var counted = 0;

        for (var pocket : pockets) {

            if (pocket.absorbingOwner() == null || !pocket.outlines().isEmpty()) {
                continue;
            }

            counted++;

            var ringing = pocket.adjacentCells();

            for (var index = 0; index < ringing.size(); index++) {

                var here = sites.get(ringing.get(index));
                var next = sites.get(ringing.get((index + 1) % ringing.size()));

                if (Math.hypot(next[0] - here[0], next[1] - here[1]) > fillReach) {
                    parted++;
                    break;
                }
            }
        }
        System.out.printf(
            Locale.ROOT,
            "of those %d, %d have two neighbouring cells whose fills do not meet "
                    + "(further apart than %.0f)%n",
            counted,
            parted,
            fillReach);
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
        reportRelaxedAbsorption(fixture);

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

    private static double findPercentile(List<Double> sorted, double fraction) {

        var index = (int) Math.min(
            sorted.size() - 1.0,
            Math.floor(fraction * (sorted.size() - 1)));
            
        return sorted.get(index);
    }
}
