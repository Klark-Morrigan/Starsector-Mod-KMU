package kmu.maplayers.base.geometry.output;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.VoronoiCellBuilder;

import kmu.maplayers.base.geometry.CellBoundSeams;
import kmu.maplayers.base.geometry.CellEdges;
import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.CoastVoidReport;
import kmu.maplayers.base.geometry.CoastWallReport;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.DiscUnionBoundary;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.LaidCoast;
import kmu.maplayers.base.geometry.PickedPointCheck;
import kmu.maplayers.base.geometry.ReportFigures;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometry;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.SettledCoast;
import kmu.maplayers.base.geometry.ShippedMap;
import kmu.maplayers.base.geometry.VoidBridgePockets;
import kmu.maplayers.base.geometry.VoidBridges;
import kmu.maplayers.base.geometry.VoidPockets;
import kmu.maplayers.base.geometry.VoidSectionReport;
import kmu.maplayers.base.geometry.WallRefusals;
import kmu.maplayers.base.geometry.render.MapLook;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
public final class VoidRegionsDump {

    // A share printed as a percentage rather than a fraction, which is how every share in
    // this report reads.
    private static final double PERCENT_SCALE = 100.0;

    // The knobs the map is shipped at, once for the whole report. Every measure below is
    // about the map a reader would open, so re-deriving them per measure buys nothing and
    // costs the one guarantee worth having: that no two numbers here describe different
    // geometry. The same reasoning the sites get, applied to the other half of the pair.
    private static final SectorGeometryParameters SHIPPED = ShippedMap.KNOBS;

    private static final double BRIDGE_REACH_MULTIPLE = ShippedMap.BRIDGE_REACH_MULTIPLE;

    // Area percentiles worth naming when deciding where the "leave it alone" threshold sits.
    private static final double MEDIAN_FRACTION = 0.5;
    private static final double[] REPORTED_PERCENTILES = {MEDIAN_FRACTION, 0.9, 1.0};

    private VoidRegionsDump() {
    }

    /**
     * Reports every fixture in turn.
     *
     * @param args ignored - which sectors are described is a property of the fixtures on
     *             disk, so there is nothing to choose here
     */
    public static void main(String[] args) {

        for (var sectorName : SectorFixture.listSectorNames()) {

            var fixture = SectorFixture.loadSector(sectorName);
            var sites = fixture.getSites();
            var cells = buildCells(sites);

            System.out.println("=== " + sectorName + " ===");

            // One laying of the coast for the whole sector. Everything below asks what the
            // walk did with a wall, and the walk answers about the walls it was handed - so a
            // second laying is a second map, however equal the lines look.
            // No spans of its own: this construction finds its bridges first and carries them
            // on the trace, so what it lays across the water is already in hand here.
            var laid = LaidCoast.layCoast(
                SettledCoast.traceAcrossBridges(sites, SHIPPED, Coastlines.DEFAULT_RULES),
                List.of(),
                SHIPPED);

            reportCells(cells);
            reportChannelWidths(fixture);
            reportHolesAtReach(fixture);
            reportBoundSeams(fixture);

            CoastWallReport.reportCoastWalls(laid);
            PickedPointCheck.reportPickedPoints(fixture, sectorName, laid);
            CoastVoidReport.reportCoastlines(laid, MapLook.RING_STROKE);
            VoidSectionReport.reportSections(fixture, laid);

            reportPockets(
                VoidPockets.findVoidPockets(
                    sites,
                    fixture.getOwnerBySite(),
                    new VoidPockets.PocketRules(
                        SHIPPED,
                        VoidPockets.PocketShaping.WITH_CHANNEL)),
                fixture,
                laid);

            System.out.println();
        }
    }

    // Built at the shipped knobs rather than at a count of its own, so what is counted here
    // is the map as it is drawn. The bound's resolution is also the set of angles everything
    // traced against these cells is flattened onto, so a second value for it here would be a
    // second convention rather than a coarser picture.
    private static List<VoronoiCellBuilder.LabelledCell> buildCells(List<double[]> sites) {

        var cells = new ArrayList<VoronoiCellBuilder.LabelledCell>(sites.size());

        for (var index = 0; index < sites.size(); index++) {

            cells.add(VoronoiCellBuilder.buildLabelledCell(
                index,
                sites,
                SHIPPED.cellRadius(),
                SHIPPED.boundSegments()));
        }
        return cells;
    }

    // How much of the map faces void at all. The population every count below is read
    // against: a fault touching one cell in three hundred and one touching one in three are
    // the same number and not the same problem.
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

        var geometry = SectorGeometry.buildSectorGeometry(fixture, SHIPPED);
        var reachFacing = new ArrayList<Double>();
        var cellFacing = new ArrayList<Double>();

        for (var entry : geometry.shapedCellByCellId().entrySet()) {

            var edges = geometry.cellEdgesByCellId().get(entry.getKey());
            var fill = entry.getValue().fillPolygon();

            if (edges == null || fill.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
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
            ReportFigures.findMedian(cellFacing),
            cellFacing.size(),
            ReportFigures.findMedian(reachFacing),
            reachFacing.size());
    }

    // How many holes there are at the reach the channel is taken at, against how many
    // there are at the true reach. A pocket shaped by moving the reach is assumed to stay
    // ONE ring; if the counts differ, some pocket pinches in two when the channel comes out
    // of it, and reshaping its ring in place cannot express that.
    private static void reportHolesAtReach(SectorFixture fixture) {

        System.out.printf(
            Locale.ROOT,
            "holes at reach %.0f: %d, at %.0f (channel taken out): %d, at %.0f "
                + "(fills' own reach): %d%n",
            SHIPPED.cellRadius(),
            countHolesAt(fixture, SHIPPED.cellRadius()),
            SHIPPED.measureDrawnReach(),
            countHolesAt(fixture, SHIPPED.measureDrawnReach()),
            SHIPPED.measureFilledReach(),
            countHolesAt(fixture, SHIPPED.measureFilledReach()));
    }

    // Whether the void and the cells agree about where they meet - the one thing that has to
    // hold before a piece of void can be handed to machinery built for cells.
    //
    // Traced at the cells' OWN reach, beside whatever the map is drawn from. A shape drawn a
    // channel out of position agrees with nothing at all, and the question here is about the
    // convention the two are flattened under rather than about what is drawn.
    private static void reportBoundSeams(SectorFixture fixture) {

        var seams = CellBoundSeams.measureSeamsAgainstCells(
            DiscUnionBoundary.traceHoles(
                new DiscUnion(fixture.getSites(), SHIPPED.cellRadius()),
                SHIPPED.boundSegments()),
            fixture.getSites(),
            SHIPPED);

        System.out.printf(
            Locale.ROOT,
            "void outline against the cells' own bound: %d of %d samples sit on a vertex of "
                + "the cell under them, worst stray %.6f (has to be 0), neighbouring samples "
                + "at worst %d vertices apart (has to be 1)%n",
            seams.onBoundVertex(),
            seams.samples(),
            seams.worstSampleStray(),
            seams.worstVertexStep());

        System.out.printf(
            Locale.ROOT,
            "its %d arc corners are crossings rather than vertices: worst stands %.1f off the "
                + "outline of the cell it sits on%n",
            seams.corners(),
            seams.worstCornerStray());
    }

    // How many pockets the cells close around at one reach, which is the count the line
    // above compares across three of them.
    private static int countHolesAt(SectorFixture fixture, double reach) {

        return VoidPockets.findVoidPockets(
            fixture.getSites(),
            fixture.getOwnerBySite(),
            new VoidPockets.PocketRules(
                new SectorGeometryParameters(
                    reach,
                    SHIPPED.boundSegments(),
                    0,
                    SHIPPED.weldTolerance(),
                    SHIPPED.miterSpikeLimit()),
                VoidPockets.PocketShaping.WITH_CHANNEL)).size();
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

            var owners = new LinkedHashSet<String>();
            var hasUnowned = false;

            for (var site : pocket.section().cells()) {

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
            SectorFixture fixture,
            LaidCoast laid) {

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

        reportPocketWidths(pockets, cellWidth);
        reportPocketShapes(pockets);

        reportRingingOwners(pockets, fixture);
        reportBridges(fixture, pockets, laid);

        var shares = new ArrayList<Double>(pockets.size());
        var sections = new ArrayList<Double>(pockets.size());

        for (var pocket : pockets) {

            shares.add(pocket.span() / cellWidth);
            sections.add((double) pocket.section().cells().size());
        }

        reportPercentiles("pocket span, in cell widths", "%.2f", shares);
        reportPercentiles("cells ringing a pocket", "%.0f", sections);
    }

    // How many pockets are wider than a cell. The one number the design turns on: a pocket no
    // wider than a cell has nothing that could connect across it, so the count either side of
    // that line says how much of the void is worth anything.
    private static void reportPocketWidths(
            List<VoidPockets.VoidPocket> pockets,
            double cellWidth) {

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
    }

    // The three shapes a pocket comes out in, counted. Together in one line because they are
    // one question - what became of the void - and reading them apart invites the three to be
    // compared against different totals.
    private static void reportPocketShapes(List<VoidPockets.VoidPocket> pockets) {

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
    }

    /**
     * One measure's spread, as the three figures every measure here is reported by.
     *
     * <p>The same three of everything, so two measures can be read against each other without
     * working out which quantile each was quoted at. A median says what is typical, a ninetieth
     * what the tail looks like, and a maximum whether that tail has one member or many.
     *
     * @param label        what is being measured
     * @param valueFormat  how to print one of its values, which differs by what the measure is
     *                     counted in - a share wants decimals where a count of cells does not
     * @param values       the measurements; sorted here, so no caller has to remember to
     */
    private static void reportPercentiles(
            String label,
            String valueFormat,
            List<Double> values) {

        values.sort(Double::compare);

        System.out.printf(
            Locale.ROOT,
            label + ": p50 " + valueFormat + " / p90 " + valueFormat
                + " / max " + valueFormat + "%n",
            ReportFigures.findPercentile(values, REPORTED_PERCENTILES[0]),
            ReportFigures.findPercentile(values, REPORTED_PERCENTILES[1]),
            ReportFigures.findPercentile(values, REPORTED_PERCENTILES[2]));
    }

    // What the other construction over the same void finds, and the one number that says
    // whether it is doing something different rather than the same thing another way: how
    // many of its bridges span void that no pocket encloses. A pocket has to be ringed by
    // cells to exist at all, so void that opens outward is invisible to that construction
    // and ordinary to this one. If that count were zero the two would only disagree about
    // where to draw, not about what there is.
    private static void reportBridges(
            SectorFixture fixture,
            List<VoidPockets.VoidPocket> pockets,
            LaidCoast laid) {

        // One site list and one set of knobs for every question asked below, so no two of
        // them can quietly measure against different geometry.
        var sites = fixture.getSites();

        var bridges = VoidBridges.findVoidBridges(
            sites,
            SHIPPED.cellRadius(),
            SHIPPED.cellRadius() * BRIDGE_REACH_MULTIPLE);

        if (bridges.isEmpty()) {
            System.out.println("void bridges: none");
            return;
        }

        var widths = new ArrayList<Double>(bridges.size());
        var outsideEveryPocket = 0;

        for (var bridge : bridges) {

            widths.add(bridge.width());

            if (!doesAnyPocketHold(pockets, findMidpoint(bridge))) {
                outsideEveryPocket++;
            }
        }
        widths.sort(Double::compare);

        var capturing = VoidBridgePockets.findCapturingBridges(
            sites, bridges, SHIPPED.cellRadius());

        System.out.printf(
            Locale.ROOT,
            "of those %d bridges, %d close a ring and shut void in; %d only chain systems "
                + "together and shut in nothing%n",
            bridges.size(),
            capturing.size(),
            bridges.size() - capturing.size());

        var captured = VoidBridgePockets.findCapturedPockets(
            sites,
            bridges,
            SHIPPED,
            VoidPockets.PocketShaping.WITH_CHANNEL);

        System.out.printf(
            Locale.ROOT,
            "%d of ALL %d are drawn as walls (the rest have closed over or crowd a mouth "
                + "already taken)%n",
            VoidBridgePockets.findLaidChords(sites, bridges, SHIPPED).size(),
            bridges.size());

        reportBridgeRefusals(sites, bridges);

        System.out.printf(
            Locale.ROOT,
            "walking the cells' borders with those bridges laid across them closes %d "
                + "pockets; worst fill edge strays %.1f from its bridge (has to be 0)%n",
            captured.size(),
            VoidBridgePockets.measureWorstChordStray(captured, sites, bridges, SHIPPED));

        System.out.printf(
            Locale.ROOT,
            "void bridges at %.0f cell radii apart: %d, of which %d span void no pocket "
                + "encloses; width p50 %.0f / p90 %.0f / max %.0f%n",
            BRIDGE_REACH_MULTIPLE,
            bridges.size(),
            outsideEveryPocket,
            ReportFigures.findPercentile(widths, REPORTED_PERCENTILES[0]),
            ReportFigures.findPercentile(widths, REPORTED_PERCENTILES[1]),
            ReportFigures.findPercentile(widths, REPORTED_PERCENTILES[2]));
    }

    // Why each bridge that is not drawn was turned down, at both reaches. The count above
    // says how many went; this says what took them, which is the difference between a gap the
    // cells have genuinely closed and a wall the drawing's own inset refused.
    private static void reportBridgeRefusals(
            List<double[]> sites,
            List<CellGap> bridges) {

        var walls = VoidBridgePockets.buildBridgeWalls(bridges, SHIPPED);

        System.out.printf(
            Locale.ROOT,
            "bridges offered %d: at the cells' own reach %s | a channel out %s%n",
            walls.chords().size(),
            WallRefusals.summariseRefusals(
                new DiscUnion(sites, SHIPPED.cellRadius()), walls, walls.chords()),
            WallRefusals.summariseRefusals(
                VoidPockets.buildDrawnUnion(sites, SHIPPED), walls, walls.chords()));

        WallRefusals.reportEachRefusal(
            VoidPockets.buildDrawnUnion(sites, SHIPPED), walls, walls.chords(), "bridge");
    }

    // Against the section rather than the pocket's own outline, because the section is the
    // pocket at the reach that defines the void, while an outline is the pocket pulled in by
    // the channel and would report a bridge near its rim as outside.
    private static boolean doesAnyPocketHold(
            List<VoidPockets.VoidPocket> pockets,
            double[] point) {

        for (var pocket : pockets) {

            if (PolygonRegions.isPointInsideRing(
                    pocket.section().outline(), point[0], point[1])) {
                return true;
            }
        }
        return false;
    }

    // The middle of a bridge, which is the point asked of the pockets when deciding whether
    // that bridge spans void any of them encloses. The middle rather than either end: an end
    // sits on a cell's own border, where every pocket has already given up the channel.
    private static double[] findMidpoint(CellGap bridge) {

        return new double[] {
            (bridge.start()[0] + bridge.end()[0]) / 2,
            (bridge.start()[1] + bridge.end()[1]) / 2};
    }
}
