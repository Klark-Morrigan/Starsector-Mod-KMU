package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolygonRegions;
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

    // One cell across, which is the size a section of void is cut to: a piece of void the
    // size of a system's own cell is comparable to what surrounds it, and a longer one is
    // a corridor rather than a place.
    private static final double SECTION_LENGTH =
        2 * SectorGeometryParameters.DEFAULT_CELL_RADIUS;

    // What the viewer opens on, so the table below describes the division a reader would see
    // if they opened it. The sweep at the end of the report is what that choice rests on.
    private static final double MIN_SECTION_SHARE = 0.4;

    private static final VoidSections.SectionRules SECTION_RULES =
        new VoidSections.SectionRules(SECTION_LENGTH, MIN_SECTION_SHARE);

    // What the viewer opens on, so this report describes what a reader would see there -
    // read off that setting rather than restated, which is the only way the two stay equal.
    private static final double BRIDGE_REACH_MULTIPLE =
        Coastlines.DEFAULT_RULES.bridgeReachMultiple();

    // Shares to sweep the division across, so the knob has a starting range instead of being
    // a bare slider. Spread over the whole span rather than clustered near the default,
    // because both ends of it are wrong in a different way and seeing where each one sets in
    // is the point.
    private static final double[] SWEPT_SHARES = {0, 0.2, 0.4, 0.6, 0.8, 1.0};

    // Area percentiles worth naming when deciding where the "leave it alone" threshold sits.
    private static final double MEDIAN_FRACTION = 0.5;
    private static final double[] REPORTED_PERCENTILES = {MEDIAN_FRACTION, 0.9, 1.0};

    // The low end as well as the high, because what is being asked of the frontages is
    // whether the crossed cells sit at the crowded end of the population.
    private static final double[] FRONTAGE_PERCENTILES = {0.1, MEDIAN_FRACTION, 0.9};

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
            reportBoundSeams(fixture);
            reportPickedPoints(fixture, sectorName);
            reportPockets(
                VoidPockets.findVoidPockets(
                    sites,
                    fixture.getOwnerBySite(),
                    new VoidPockets.PocketRules(
                        SectorGeometryParameters.createDefaults(),
                        SECTION_RULES,
                        VoidPockets.PocketShaping.WITH_CHANNEL)),
                fixture);

            System.out.println();
        }
    }

    // Built at the shipped knobs rather than at a count of its own, so what is counted here
    // is the map as it is drawn. The bound's resolution is also the set of angles everything
    // traced against these cells is flattened onto, so a second value for it here would be a
    // second convention rather than a coarser picture.
    private static List<VoronoiCellBuilder.LabelledCell> buildCells(List<double[]> sites) {

        var shipped = SectorGeometryParameters.createDefaults();
        var cells = new ArrayList<VoronoiCellBuilder.LabelledCell>(sites.size());

        for (var index = 0; index < sites.size(); index++) {

            cells.add(VoronoiCellBuilder.buildLabelledCell(
                index,
                sites,
                shipped.cellRadius(),
                shipped.boundSegments()));
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
            shipped.measureDrawnReach(),
            countHolesAt(fixture, shipped.measureDrawnReach()),
            shipped.measureFilledReach(),
            countHolesAt(fixture, shipped.measureFilledReach()));
    }

    // Whether the void and the cells agree about where they meet - the one thing that has to
    // hold before a piece of void can be handed to machinery built for cells.
    //
    // Traced at the cells' OWN reach, beside whatever the map is drawn from. A shape drawn a
    // channel out of position agrees with nothing at all, and the question here is about the
    // convention the two are flattened under rather than about what is drawn.
    private static void reportBoundSeams(SectorFixture fixture) {

        var shipped = SectorGeometryParameters.createDefaults();

        var seams = CellBoundSeams.measureSeamsAgainstCells(
            DiscUnionBoundary.traceHoles(
                new DiscUnion(fixture.getSites(), shipped.cellRadius()),
                shipped.boundSegments()),
            fixture.getSites(),
            shipped);

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

    // What claims each point picked in the viewer, read from the pick log the window writes.
    //
    // The bridge between what a reader sees and what the constructions think they built. A
    // patch of black raises one question - who was supposed to draw this - and answering it
    // by eye means guessing which of two constructions owns the spot, at which of two reaches,
    // and whether the void there is enclosed at all. Each of those is one line here.
    //
    // Driven by the log rather than by a list kept here, so a fresh set of clicks needs no
    // edit: pick in the window, run this, read the answers.
    private static void reportPickedPoints(SectorFixture fixture, String sectorName) {

        var picks = readPicks(sectorName);

        if (picks.isEmpty()) {
            return;
        }

        var sites = fixture.getSites();
        var parameters = new SectorGeometryParameters(4000, 24, 150, 200, 8);
        var sectionRules = new VoidSections.SectionRules(1.5 * parameters.cellRadius(), 0.4);
        var traced = Coastlines.traceSectorCoasts(
            sites, parameters, new Coastlines.CoastRules(4, 1, 5));
        var bridges = VoidBridges.findVoidBridges(
            sites, parameters.cellRadius(), parameters.cellRadius() * 4);

        var holes = DiscUnionBoundary.traceHoles(
            new DiscUnion(sites, parameters.cellRadius()), parameters.boundSegments());
        var drawnHoles = DiscUnionBoundary.traceHoles(
            VoidPockets.buildDrawnUnion(sites, parameters), parameters.boundSegments());

        var coastTrue = collectCoastOutlines(
            traced, fixture, parameters, sectionRules, VoidPockets.PocketShaping.AT_TRUE_EXTENT);
        var coastInset = collectCoastOutlines(
            traced, fixture, parameters, sectionRules, VoidPockets.PocketShaping.WITH_CHANNEL);
        var bridgeTrue = VoidBridgePockets.findCapturedPockets(
            sites, bridges, parameters, VoidPockets.PocketShaping.AT_TRUE_EXTENT);
        var bridgeInset = VoidBridgePockets.findCapturedPockets(
            sites, bridges, parameters, VoidPockets.PocketShaping.WITH_CHANNEL);

        reportCoastWallRefusals(traced, parameters);

        for (var pick : picks) {

            System.out.printf(
                Locale.ROOT,
                "  pick %.0f,%.0f: hole true %s inset %s | coast inset %s true %s "
                    + "| bridge inset %s true %s | %s%n",
                pick[0],
                pick[1],
                describeHoleAt(holes, pick),
                describeHoleAt(drawnHoles, pick),
                describeHit(coastInset, pick),
                describeHit(coastTrue, pick),
                describeHit(bridgeInset, pick),
                describeHit(bridgeTrue, pick),
                describeNearestStep(traced, parameters, pick));
        }
    }

    // Why the coast's own reaches are not laid, counted by reason at each reach. One gap on
    // screen is a case; a count says whether it is the case or one of dozens, which is what
    // decides whether the rule that refused it is worth changing.
    private static void reportCoastWallRefusals(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters) {

        var sites = traced.union().sites();
        var offered = buildCoastChords(traced);
        var all = new ArrayList<>(offered);

        all.addAll(traced.walls().chords());

        var walls = new DiscUnionBoundary.Walls(all, traced.walls().channel());

        System.out.printf(
            Locale.ROOT,
            "coast reaches offered %d: at the cells' own reach %s | a channel out %s%n",
            offered.size(),
            summariseRefusals(new DiscUnion(sites, parameters.cellRadius()), walls, offered),
            summariseRefusals(
                VoidPockets.buildDrawnUnion(sites, parameters), walls, offered));
    }

    private static String summariseRefusals(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            List<DiscUnionBoundary.Chord> offered) {

        var laid = 0;
        var offBoundary = 0;
        var crowded = 0;
        var noMouth = 0;

        for (var chord : offered) {

            var refusal = DiscUnionBoundary.describeChordRefusal(
                union, walls, chord.fromCircle(), chord.toCircle());

            if (refusal.equals("laid")) {
                laid++;
            } else if (refusal.startsWith("mouth on")) {
                crowded++;
            } else if (refusal.equals("no mouth")) {
                noMouth++;
            } else {
                offBoundary++;
            }
        }
        return String.format(
            Locale.ROOT,
            "%d laid, %d off the boundary, %d crowded out, %d with no mouth",
            laid,
            offBoundary,
            crowded,
            noMouth);
    }

    // The coast's straight reaches as the walls they are offered as, built the one way
    // CoastPockets builds them so a count here describes the walls it actually lays.
    private static List<DiscUnionBoundary.Chord> buildCoastChords(
            Coastlines.TracedCoasts traced) {

        var offered = new ArrayList<DiscUnionBoundary.Chord>();

        for (var reach : Coastlines.collectStraightReaches(traced)) {

            offered.add(new DiscUnionBoundary.Chord(
                reach.from().circle(),
                reach.to().circle(),
                new kmlib.math.geometry.DirectedLine(
                    reach.from().point()[0],
                    reach.from().point()[1],
                    reach.to().point()[0] - reach.from().point()[0],
                    reach.to().point()[1] - reach.from().point()[1]),
                DiscUnionBoundary.WallKind.COAST_REACH));
        }
        return offered;
    }

    // The coast step running nearest a picked point, and what became of it.
    //
    // A line drawn across a gap is not a wall laid across it. The coast draws every step it
    // walks, while only a step long enough to hold a channel is offered as a reach, and only
    // a reach whose ends are on the boundary is laid - so a gap can be closed on screen and
    // open to the trace. Which of those three it is, is the whole question.
    private static String describeNearestStep(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            double[] pick) {

        Coastlines.CoastVertex from = null;
        Coastlines.CoastVertex to = null;
        var nearest = Double.MAX_VALUE;

        for (var coast : traced.coasts()) {
            for (var index = 0; index < coast.size(); index++) {

                var start = coast.get(index);
                var end = coast.get((index + 1) % coast.size());
                var away = kmlib.math.geometry.Segments.computeDistanceToPoint(
                    start.point(), end.point(), pick);

                if (away < nearest) {
                    nearest = away;
                    from = start;
                    to = end;
                }
            }
        }

        if (from == null) {
            return "no coast";
        }

        var length = kmlib.math.geometry.Points.computeDistance(from.point(), to.point());
        var channel = traced.walls().channel();
        var isReach = from.circle() != to.circle() && length >= channel;

        if (!isReach) {
            return String.format(
                Locale.ROOT,
                "nearest step %.0f away, cells %d-%d, %.0f long: not offered as a reach",
                nearest,
                from.circle(),
                to.circle(),
                length);
        }

        var sites = traced.union().sites();
        var offered = new ArrayList<DiscUnionBoundary.Chord>();

        for (var reach : Coastlines.collectStraightReaches(traced)) {
            offered.add(new DiscUnionBoundary.Chord(
                reach.from().circle(),
                reach.to().circle(),
                new kmlib.math.geometry.DirectedLine(
                    reach.from().point()[0],
                    reach.from().point()[1],
                    reach.to().point()[0] - reach.from().point()[0],
                    reach.to().point()[1] - reach.from().point()[1]),
                DiscUnionBoundary.WallKind.COAST_REACH));
        }

        var mine = findChordFrom(offered, from, to);
        var all = new ArrayList<>(offered);

        all.addAll(traced.walls().chords());

        var walls = new DiscUnionBoundary.Walls(all, channel);
        var laidTrue = DiscUnionBoundary.findAttachableChords(
            new DiscUnion(sites, parameters.cellRadius()), walls);
        var laidInset = DiscUnionBoundary.findAttachableChords(
            VoidPockets.buildDrawnUnion(sites, parameters), walls);

        return String.format(
            Locale.ROOT,
            "nearest step %.0f away, cells %d-%d, %.0f long: reach, true [%s] inset [%s]",
            nearest,
            from.circle(),
            to.circle(),
            length,
            laidTrue.contains(mine)
                ? "laid"
                : DiscUnionBoundary.describeChordRefusal(
                    new DiscUnion(sites, parameters.cellRadius()),
                    walls,
                    from.circle(),
                    to.circle()),
            laidInset.contains(mine)
                ? "laid"
                : DiscUnionBoundary.describeChordRefusal(
                    VoidPockets.buildDrawnUnion(sites, parameters),
                    walls,
                    from.circle(),
                    to.circle()));
    }

    // The chord built from one coast step, found among the ones offered, so what is asked of
    // the wall set is the wall this step became rather than one that merely joins the same two
    // cells - a cell pair can be joined by more than one step of coast.
    private static DiscUnionBoundary.Chord findChordFrom(
            List<DiscUnionBoundary.Chord> offered,
            Coastlines.CoastVertex from,
            Coastlines.CoastVertex to) {

        for (var chord : offered) {

            if (chord.fromCircle() == from.circle()
                    && chord.toCircle() == to.circle()
                    && chord.line().originX() == from.point()[0]
                    && chord.line().originY() == from.point()[1]) {
                return chord;
            }
        }
        return null;
    }

    // The picks for one sector, as the viewer wrote them down. Missing file and foreign
    // sectors are ordinary: the log is emptied per session and holds whichever sectors were
    // looked at.
    private static List<double[]> readPicks(String sectorName) {

        var file = java.nio.file.Path.of(
            "build", "reports", "political-map", "viewer-picks.txt");
        var picks = new ArrayList<double[]>();

        if (!java.nio.file.Files.exists(file)) {
            return picks;
        }

        try {
            for (var line : java.nio.file.Files.readAllLines(file)) {

                if (!line.startsWith(sectorName)) {
                    continue;
                }
                var fields = line.substring(sectorName.length()).trim().split("[,\s]+");

                picks.add(new double[] {
                    Double.parseDouble(fields[0]), Double.parseDouble(fields[1])});
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("cannot read " + file, e);
        }
        return picks;
    }

    // Every coast pocket's outline at one shaping, flattened, because what a pick asks is
    // whether ANY of them holds the point rather than which pocket it belongs to.
    private static List<List<double[]>> collectCoastOutlines(
            Coastlines.TracedCoasts traced,
            SectorFixture fixture,
            SectorGeometryParameters parameters,
            VoidSections.SectionRules sectionRules,
            VoidPockets.PocketShaping shaping) {

        var outlines = new ArrayList<List<double[]>>();

        for (var walled : CoastPockets.findCoastPockets(
                traced,
                fixture.getOwnerBySite(),
                new VoidPockets.PocketRules(parameters, sectionRules, shaping))) {

            outlines.addAll(walled.pocket().outlines());
        }
        return outlines;
    }

    // Which hole of the union holds a point, if any. "None" is the answer that matters most:
    // it says the void there is open to the rest of the map, so no construction was ever going
    // to fill it and the question is why nothing enclosed it.
    private static String describeHoleAt(List<VoidHole> holes, double[] pick) {

        for (var index = 0; index < holes.size(); index++) {

            if (PolygonRegions.isPointInsideRing(
                    holes.get(index).boundary(), pick[0], pick[1])) {
                return "#" + index + " (" + holes.get(index).ringing().size() + " cells)";
            }
        }
        return "none";
    }

    // Which drawn outline holds a point, if any - asked once per construction and per reach,
    // since a pocket can exist at one reach and not the other.
    private static String describeHit(List<List<double[]>> outlines, double[] pick) {

        for (var index = 0; index < outlines.size(); index++) {

            if (PolygonRegions.isPointInsideRing(outlines.get(index), pick[0], pick[1])) {
                return "#" + index;
            }
        }
        return "no";
    }

    private static int countHolesAt(SectorFixture fixture, double reach) {

        var shipped = SectorGeometryParameters.createDefaults();

        return VoidPockets.findVoidPockets(
            fixture.getSites(),
            fixture.getOwnerBySite(),
            new VoidPockets.PocketRules(
                new SectorGeometryParameters(
                    reach,
                    shipped.boundSegments(),
                    0,
                    shipped.weldTolerance(),
                    shipped.miterSpikeLimit()),
                SECTION_RULES,
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

    // Every pocket, one line each. A summary count says how many pockets came out one way
    // or another; it cannot say WHICH, and every question worth asking of this map so far has
    // turned out to be about a particular pocket.
    private static void reportEachPocket(List<VoidPockets.VoidPocket> pockets) {

        System.out.println(
            "  pocket        at          span  cells  cuts   shaping     "
                + "sections            cut widths");

        for (var index = 0; index < pockets.size(); index++) {

            var pocket = pockets.get(index);
            var centre = pocket.centre();

            System.out.printf(
                Locale.ROOT,
                "  %-6d %7.0f,%-7.0f %6.0f %4d %5d   %-10s  %-18s  %s%n",
                index,
                centre[0],
                centre[1],
                pocket.span(),
                pocket.adjacentCells().size(),
                pocket.division().cuts().size(),
                describeShaping(pocket),
                describeSections(pocket),
                describeCutWidths(pocket));
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

    // How wide the corridor is at each cut. The number that says whether a cut is a pinch
    // or a jump: a cut across a genuine neck is a small fraction of a section, and one that
    // reads as leaping across open void is a large one.
    private static String describeCutWidths(VoidPockets.VoidPocket pocket) {

        var widths = new StringBuilder();

        for (var cut : pocket.division().cuts()) {

            if (widths.length() > 0) {
                widths.append(" ");
            }
            widths.append(String.format(Locale.ROOT, "%.0f", cut.width()));
        }
        return widths.toString();
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
        reportShareSweep(fixture);
        reportBridges(fixture, pockets);

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

        var summary = summariseDivision(pockets);

        System.out.printf(
            Locale.ROOT,
            "%d pockets want dividing into sections of %.0f, no cut leaving under %.0f%% of "
                + "one: %d cuts taken, %d still hold a section over length, longest %.0f%n",
            summary.toDivide(),
            SECTION_LENGTH,
            MIN_SECTION_SHARE * PERCENT_SCALE,
            summary.cuts(),
            summary.overLength(),
            summary.longestSection());
    }

    // How the division answers to the one knob that decides it. Three numbers say the whole
    // story: how many cuts were taken, how wide the worst of them was, and how long the worst
    // section left over was. A low share takes many narrow cuts and still leaves one huge
    // piece, because it is shaving the tips; a high share takes few wide ones, because
    // nothing but a chord across the open middle can leave that much on both sides. Where
    // those two failures stop overlapping is where the knob wants to sit.
    private static void reportShareSweep(SectorFixture fixture) {

        System.out.println("  share   cuts   widest cut   longest section");

        for (var share : SWEPT_SHARES) {

            var summary = summariseDivision(VoidPockets.findVoidPockets(
                fixture.getSites(),
                fixture.getOwnerBySite(),
                new VoidPockets.PocketRules(
                    SectorGeometryParameters.createDefaults(),
                    new VoidSections.SectionRules(SECTION_LENGTH, share),
                    VoidPockets.PocketShaping.WITH_CHANNEL)));

            System.out.printf(
                Locale.ROOT,
                "  %4.0f%%  %5d   %10.0f   %15.0f%n",
                share * PERCENT_SCALE,
                summary.cuts(),
                summary.widestCut(),
                summary.longestSection());
        }
    }

    // One walk over the pockets that want dividing, for both the line above and every row of
    // the sweep. Shared rather than written twice because the sweep's row at the share the
    // rest of the report runs at IS that line, and two walks could report it two ways.
    private static DivisionSummary summariseDivision(List<VoidPockets.VoidPocket> pockets) {

        var toDivide = 0;
        var cuts = 0;
        var overLength = 0;
        var widestCut = 0.0;
        var longestSection = 0.0;

        for (var pocket : pockets) {

            if (pocket.span() <= SECTION_LENGTH) {
                continue;
            }

            toDivide++;
            cuts += pocket.division().cuts().size();

            for (var cut : pocket.division().cuts()) {
                widestCut = Math.max(widestCut, cut.width());
            }

            var longestHere = measureLongestSection(pocket);

            if (longestHere > SECTION_LENGTH) {
                overLength++;
            }

            longestSection = Math.max(longestSection, longestHere);
        }
        return new DivisionSummary(toDivide, cuts, overLength, widestCut, longestSection);
    }

    // What the other construction over the same void finds, and the one number that says
    // whether it is doing something different rather than the same thing another way: how
    // many of its bridges span void that no pocket encloses. A pocket has to be ringed by
    // cells to exist at all, so void that opens outward is invisible to that construction
    // and ordinary to this one. If that count were zero the two would only disagree about
    // where to draw, not about what there is.
    private static void reportBridges(
            SectorFixture fixture,
            List<VoidPockets.VoidPocket> pockets) {

        // One site list and one set of knobs for every question asked below, so no two of
        // them can quietly measure against different geometry.
        var sites = fixture.getSites();
        var shipped = SectorGeometryParameters.createDefaults();

        var bridges = VoidBridges.findVoidBridges(
            sites,
            shipped.cellRadius(),
            shipped.cellRadius() * BRIDGE_REACH_MULTIPLE);

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
            sites, bridges, shipped.cellRadius());

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
            shipped,
            VoidPockets.PocketShaping.WITH_CHANNEL);

        System.out.printf(
            Locale.ROOT,
            "%d of them are drawn as walls (the rest have closed over or crowd a mouth "
                + "already taken)%n",
            VoidBridgePockets.findLaidChords(sites, bridges, shipped).size());

        System.out.printf(
            Locale.ROOT,
            "walking the cells' borders with those bridges laid across them closes %d "
                + "pockets; worst fill edge strays %.1f from its bridge (has to be 0)%n",
            captured.size(),
            VoidBridgePockets.measureWorstChordStray(captured, sites, bridges, shipped));

        reportCoastlines(sites, bridges, shipped);

        System.out.printf(
            Locale.ROOT,
            "void bridges at %.0f cell radii apart: %d, of which %d span void no pocket "
                + "encloses; width p50 %.0f / p90 %.0f / max %.0f%n",
            BRIDGE_REACH_MULTIPLE,
            bridges.size(),
            outsideEveryPocket,
            findPercentile(widths, REPORTED_PERCENTILES[0]),
            findPercentile(widths, REPORTED_PERCENTILES[1]),
            findPercentile(widths, REPORTED_PERCENTILES[2]));
    }

    // What the smoothing takes out, as the two counts that say whether it did anything. Marks
    // is how many stretches of coast the cells actually make; points is how many the smoothed
    // line passes through. Equal counts mean the skip rules refused every candidate, which
    // reads on screen exactly like the smoothing being switched off.
    private static void reportCoastlines(
            List<double[]> sites,
            List<CellGaps.CellGap> bridges,
            SectorGeometryParameters shipped) {

        var traced = Coastlines.traceSectorCoasts(sites, shipped, Coastlines.DEFAULT_RULES);
        var points = 0;

        for (var coast : traced.coasts()) {
            points += coast.size();
        }

        System.out.printf(
            Locale.ROOT,
            "smoothed outer edges: %d, over %d stretches of coast, drawn through %d points; "
                + "%d runs visibly cross a cell, worst reach in %.1f (both have to be 0)%n",
            traced.coasts().size(),
            CoastMeasures.countCoastMarks(traced),
            points,
            CoastCrossings.findVisibleCrossings(traced, ViewerPainting.RING_STROKE).size(),
            CoastCrossings.measureDeepestIncursion(traced));

        System.out.printf(
            Locale.ROOT,
            "how deep each one goes, worst first: %s%n",
            formatPenetrationDepths(CoastCrossings.findPenetrations(traced)));

        var frontages = new ArrayList<>(CoastMeasures.measureFrontages(traced));
        frontages.sort(Double::compare);

        System.out.printf(
            Locale.ROOT,
            "frontage offered, over every stretch: p10 %.0f / p50 %.0f / p90 %.0f%n",
            findPercentile(frontages, FRONTAGE_PERCENTILES[0]),
            findPercentile(frontages, FRONTAGE_PERCENTILES[1]),
            findPercentile(frontages, FRONTAGE_PERCENTILES[2]));

        System.out.printf(
            Locale.ROOT,
            "each crossing as depth/frontage of the cell crossed: %s%n",
            formatAgainstDepth(CoastMeasures.measureCrossingFrontages(traced)));

        System.out.printf(
            Locale.ROOT,
            "each crossing as depth/stretches skipped across it (0 = neighbours): %s%n",
            formatAgainstDepth(CoastMeasures.measureCrossingGaps(traced)));

        reportTrappedVoid(sites, traced, shipped);
    }

    // What the smoothing shut in behind it, as the pockets it becomes. A coast that traps
    // nothing has bought no pocket space and is only redrawing the cells' own outline, so the
    // count is the number that says whether the smoothing did the thing it exists to do -
    // and how many of them survive the channel is the number that says they can be drawn.
    private static void reportTrappedVoid(
            List<double[]> sites,
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters shipped) {

        var pockets = CoastPockets.findCoastPockets(
            traced,
            CoastPockets.markEverySiteUnowned(sites),
            new VoidPockets.PocketRules(
                shipped, SECTION_RULES, VoidPockets.PocketShaping.WITH_CHANNEL));

        if (pockets.isEmpty()) {
            System.out.println("the coast traps no void at all");
            return;
        }

        var spans = new ArrayList<Double>(pockets.size());
        var closedOver = 0;

        for (var pocket : pockets) {

            spans.add(pocket.pocket().span());

            if (pocket.pocket().outlines().isEmpty()) {
                closedOver++;
            }
        }
        spans.sort(Double::compare);

        var spills = CoastPocketFaults.findSpills(pockets, traced.union().sites());
        var overruns = CoastPocketFaults.findOverruns(pockets, traced.union().sites());

        System.out.printf(
            Locale.ROOT,
            "closest a pocket comes to the reach that closed it: %.0f (the channel, %.0f)%n",
            CoastPocketFaults.measureClosestApproach(pockets, traced.union().sites()),
            shipped.borderInset());

        // Two numbers rather than one. Seaward of the line is void claimed out at sea; past a
        // reach's end is a pocket longer than the piece of coast that closed it, which the cut
        // is what holds in - so a run of them says the cut did not take rather than that the
        // shape is out to sea.
        System.out.printf(
            Locale.ROOT,
            "%d runs of pocket outline sit seaward of the reach that closed them, worst by "
                + "%.0f (has to be 0); %d runs past a reach's end, worst by %.0f (has to be 0 "
                + "once the cut has run)%n",
            spills.size(),
            spills.isEmpty() ? 0 : spills.get(0).depth(),
            overruns.size(),
            overruns.isEmpty() ? 0 : overruns.get(0).depth());

        System.out.printf(
            Locale.ROOT,
            "void the coast traps: %d pockets, %d of them drawn once the channel is taken "
                + "out; span p50 %.0f / p90 %.0f / max %.0f%n",
            pockets.size(),
            pockets.size() - closedOver,
            findPercentile(spans, REPORTED_PERCENTILES[0]),
            findPercentile(spans, REPORTED_PERCENTILES[1]),
            findPercentile(spans, REPORTED_PERCENTILES[2]));

        reportTrappedVoidAtTrueExtent(sites, traced, shipped);
    }

    // The same void with nothing given up, which is the map the viewer opens on and the one
    // step 5 moves everything to. Reported because the two faults answer differently here:
    // nothing cuts a pocket at its true extent, so running past a reach's end is what a hole
    // does and only the seaward count still has to be zero. One line is what keeps that from
    // being rediscovered by eye every time a pocket is seen sticking out past a reach.
    private static void reportTrappedVoidAtTrueExtent(
            List<double[]> sites,
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters shipped) {

        var pockets = CoastPockets.findCoastPockets(
            traced,
            CoastPockets.markEverySiteUnowned(sites),
            new VoidPockets.PocketRules(
                shipped, SECTION_RULES, VoidPockets.PocketShaping.AT_TRUE_EXTENT));

        var spills = CoastPocketFaults.findSpills(pockets, traced.union().sites());
        var overruns = CoastPocketFaults.findOverruns(pockets, traced.union().sites());

        System.out.printf(
            Locale.ROOT,
            "at their true extent: %d pockets, %d runs seaward of a reach, worst by %.0f "
                + "(has to be 0); %d runs past a reach's end, worst by %.0f (expected - "
                + "nothing cuts them here)%n",
            pockets.size(),
            spills.size(),
            spills.isEmpty() ? 0 : spills.get(0).depth(),
            overruns.size(),
            overruns.isEmpty() ? 0 : overruns.get(0).depth());
    }

    // Each crossing as its depth beside one other number about it. Shared by every such
    // report, because what varies between them is which number is asked for, not how a list
    // of them reads.
    private static String formatAgainstDepth(List<double[]> rows) {

        var listed = new StringBuilder();

        for (var row : rows) {
            listed.append(listed.isEmpty() ? "" : " ").append(String.format(
                Locale.ROOT, "%.0f/%.0f", row[0], row[1]));
        }
        return listed.isEmpty() ? "none" : listed.toString();
    }

    // Every one of them rather than a summary, because the question is whether they are one
    // population or two - a graze along a border the run is already leaving from, against a
    // run cutting a cell in half - and a mean or a worst case cannot tell those apart.
    private static String formatPenetrationDepths(List<CoastCrossings.Penetration> penetrations) {

        var depths = new ArrayList<Double>(penetrations.size());

        for (var penetration : penetrations) {
            depths.add(penetration.depth());
        }
        depths.sort(java.util.Comparator.reverseOrder());

        var listed = new StringBuilder();

        for (var depth : depths) {
            listed.append(listed.isEmpty() ? "" : " ").append(String.format(
                Locale.ROOT, "%.0f", depth));
        }
        return listed.isEmpty() ? "none" : listed.toString();
    }

    // Against the sections rather than the pockets' own outlines, because the sections are
    // the pocket at the reach that defines the void and tile it exactly, while an outline is
    // the pocket pulled in by the channel and would report a bridge near its rim as outside.
    private static boolean doesAnyPocketHold(
            List<VoidPockets.VoidPocket> pockets,
            double[] point) {

        for (var pocket : pockets) {
            for (var section : pocket.division().sections()) {

                if (PolygonRegions.isPointInsideRing(section, point[0], point[1])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double[] findMidpoint(CellGaps.CellGap bridge) {

        return new double[] {
            (bridge.start()[0] + bridge.end()[0]) / 2,
            (bridge.start()[1] + bridge.end()[1]) / 2};
    }

    private static double findPercentile(List<Double> sorted, double fraction) {

        var index = (int) Math.min(
            sorted.size() - 1.0,
            Math.floor(fraction * (sorted.size() - 1)));
            
        return sorted.get(index);
    }

    /**
     * What one run of the division came to, across every pocket long enough to want it.
     *
     * @param toDivide       how many pockets spanned more than a section
     * @param cuts           how many cuts were taken across all of them
     * @param overLength     how many still hold a section longer than one
     * @param widestCut      the widest corridor any cut crossed - the number that says
     *                       whether cuts landed at pinches or were thrown across open void
     * @param longestSection the longest way across any section left
     */
    private record DivisionSummary(
        int toDivide,
        int cuts,
        int overLength,
        double widestCut,
        double longestSection) {
    }
}
