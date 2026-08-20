package kmu.maplayers.base.geometry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What claims each point picked in the viewer, and why nothing claims the rest.
 *
 * <p>The bridge between what a reader sees and what the constructions think they built. A
 * patch of black raises one question - who was supposed to draw this - and answering it by eye
 * means guessing which of two constructions owns the spot, at which of two reaches, and
 * whether the void there is enclosed at all. Each of those is one column here.
 *
 * <p>Driven by the log the window writes rather than by a list kept in the source, so a fresh
 * set of clicks needs no edit: pick in the viewer, run the report, read the answers.
 *
 * <p>Its own class rather than another block of the report, because it asks a different kind
 * of question. The report describes the whole map in numbers; this one answers about one point
 * a person put their cursor on, and the two grow in different directions.
 */
final class PickedPointCheck {

    // How many ways out to try, how big a stride to take, and how far counts as out. The
    // stride is well under a cell so a walk cannot step over one, and the range is wider than
    // either fixture, so a walk that runs the whole way has genuinely left the sector.
    private static final int ESCAPE_DIRECTIONS = 72;
    private static final double ESCAPE_STEP = 250;
    private static final double ESCAPE_RANGE = 250_000;

    private PickedPointCheck() {
    }

    /**
     * Reports what the constructions make of every point picked on one sector.
     *
     * <p>Run at the shipped knobs, which the rest of the report states, so that a verdict here
     * and a number elsewhere in it describe one map. A pick taken with the viewer's sliders
     * moved can therefore disagree with the window.
     *
     * @param fixture    the sector the picks were taken on
     * @param sectorName its name, which the log records against every pick
     * @param laid       the coast with its walls down, as the rest of the report has them.
     *                   Handed in rather than laid again here, because the walk answers about
     *                   the walls it was given and a second laying is a second answer
     */
    static void reportPickedPoints(SectorFixture fixture, String sectorName, LaidCoast laid) {

        var picks = readPicks(sectorName);

        if (!picks.isEmpty()) {
            reportEachPick(picks, fixture, laid);
        }
    }

    // Each pick against every construction at both shapings, so which of them was meant to
    // draw the spot - and at which reach - is read off one line rather than guessed at.
    private static void reportEachPick(
            List<double[]> picks,
            SectorFixture fixture,
            LaidCoast laid) {

        var traced = laid.traced();
        var parameters = laid.parameters();
        var walls = laid.walls();
        var sites = fixture.getSites();
        var sectionRules = new VoidSections.SectionRules(
            ViewerSettings.VOID_SPAN_DEFAULT * parameters.cellRadius(),
            ViewerSettings.MIN_SECTION_DEFAULT / ViewerSettings.MIN_SECTION_SCALE);

        var bridges = VoidBridges.findVoidBridges(
            sites,
            parameters.cellRadius(),
            parameters.cellRadius() * Coastlines.DEFAULT_RULES.bridgeReachMultiple());

        var holes = DiscUnionBoundary.traceHoles(
            new DiscUnion(sites, parameters.cellRadius()), parameters.boundSegments());

        // The void as the walk sees it with EVERY wall down - bridges and coast reaches
        // together. Neither construction asks this question: one lays bridges alone and the
        // other keeps only the holes a coast reach walled, so a hole the two kinds close
        // between them belongs to neither of their answers and shows as nothing at all.
        var walledHoles = DiscUnionBoundary.traceHolesAcrossWalls(
            new DiscUnion(sites, parameters.cellRadius()), walls, parameters.boundSegments());

        // The same walk at the reach a shape is DRAWN at, which is the one that decides what a
        // reader sees. A hole at the cells' own reach with nothing to show for it a channel
        // out is a wall the drawing's own inset refused, and no other column says so.
        var drawnWalledHoles = DiscUnionBoundary.traceHolesAcrossWalls(
            VoidPockets.buildDrawnUnion(sites, parameters),
            walls,
            parameters.boundSegments());
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

        // A block per pick rather than one long line. The columns answer four different
        // questions - who drew it, whether it is enclosed, what the coast did there, and where
        // the walk broke - and a reader following one of them along a 400-character line reads
        // the wrong column as often as the right one.
        for (var pick : picks) {

            System.out.printf(Locale.ROOT, "  pick %.0f,%.0f%n", pick[0], pick[1]);

            System.out.printf(
                Locale.ROOT,
                "    drawn by: coast inset %s true %s | bridge inset %s true %s%n",
                describeHit(coastInset, pick),
                describeHit(coastTrue, pick),
                describeHit(bridgeInset, pick),
                describeHit(bridgeTrue, pick));

            System.out.printf(
                Locale.ROOT,
                "    enclosed: hole true %s inset %s | with every wall, true %s inset %s "
                    + "| %s%n",
                describeHoleAt(holes, pick),
                describeHoleAt(drawnHoles, pick),
                describeWalledHoleAt(walledHoles, pick),
                describeWalledHoleAt(drawnWalledHoles, pick),
                describeEscape(laid.atCells(), walls, pick)
                    + ", inset " + describeEscape(laid.atDrawnReach(), walls, pick));

            System.out.printf(
                Locale.ROOT,
                "    coast: %s, %s%n",
                describeCoastSide(traced, pick),
                describeNearestStep(laid, pick));

            System.out.printf(
                Locale.ROOT,
                "    walls: %s%n",
                describeWallsHolding(walledHoles, laid, pick));

            System.out.printf(
                Locale.ROOT,
                "    walk: true %s | inset %s%n",
                describeNearestBrokenLink(laid.atCells(), walls, pick),
                describeNearestBrokenLink(laid.atDrawnReach(), walls, pick));
        }
    }

    // What the walk made, at each reach, of every wall that closed the void a point sits in.
    //
    // The question a missing fill comes down to once the void is known to be enclosed at the
    // cells' own reach: which of the walls that closed it was not laid a channel out. Asked of
    // the hole's OWN walls rather than of whatever runs nearest, because a wall that closed
    // this void is the only one whose refusal could have opened it.
    private static String describeWallsHolding(
            List<VoidHole> holes,
            LaidCoast laid,
            double[] pick) {

        for (var hole : holes) {

            if (!kmlib.math.geometry.PolygonRegions.isPointInsideRing(
                    hole.boundary(), pick[0], pick[1])) {

                continue;
            }
            var verdicts = new StringBuilder();

            for (var wall : hole.walledBy()) {

                verdicts.append(verdicts.isEmpty() ? "" : " | ").append(String.format(
                    Locale.ROOT,
                    "%s %d-%d true [%s] inset [%s]",
                    wall.kind(),
                    wall.fromCircle(),
                    wall.toCircle(),
                    DiscUnionBoundary.describeChordRefusal(
                        laid.atCells(), laid.walls(), wall),
                    DiscUnionBoundary.describeChordRefusal(
                        laid.atDrawnReach(), laid.walls(), wall)));
            }
            return verdicts.isEmpty() ? "the cells closed it unaided" : verdicts.toString();
        }
        return "nothing walls it";
    }

    // The coast step running nearest a picked point, and what became of it.
    //
    // A line drawn across a gap is not a wall laid across it. The coast draws every step it
    // walks, while only a step long enough to hold a channel is offered as a reach, and only a
    // reach the walk can attach is laid - so a gap can be closed on screen and open to the
    // trace. Which of those three it is, is the whole question.
    private static String describeNearestStep(LaidCoast laid, double[] pick) {

        var step = findNearestStep(laid.traced(), pick);

        if (step == null) {
            return "no coast";
        }

        var wall = findWallFor(laid.offered(), step);

        if (wall == null) {
            return String.format(
                Locale.ROOT,
                "nearest step %.0f away, cells %d-%d, %.0f long: not offered as a reach",
                step.away(),
                step.from().circle(),
                step.to().circle(),
                step.length());
        }

        return String.format(
            Locale.ROOT,
            "nearest step %.0f away, cells %d-%d, %.0f long: reach, true [%s] inset [%s]",
            step.away(),
            step.from().circle(),
            step.to().circle(),
            step.length(),
            DiscUnionBoundary.describeChordRefusal(laid.atCells(), laid.walls(), wall),
            DiscUnionBoundary.describeChordRefusal(
                laid.atDrawnReach(), laid.walls(), wall));
    }

    // The nearest place the walk ran off the end of the boundary. Void that cannot be walked
    // out of and yet comes back as no pocket has to have lost its cycle somewhere, and a
    // broken link is the one way that happens - so this says where to look rather than that
    // something is wrong.
    private static String describeNearestBrokenLink(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            double[] pick) {

        var broken = DiscUnionBoundary.findBrokenLinks(union, walls);

        if (broken.isEmpty()) {
            return "no broken links";
        }

        DiscUnionBoundary.BrokenLink nearest = null;
        var away = Double.MAX_VALUE;

        for (var link : broken) {

            var reach = kmlib.math.geometry.Points.computeDistance(link.at(), pick);

            if (reach < away) {
                away = reach;
                nearest = link;
            }
        }
        return String.format(
            Locale.ROOT,
            "%d broken links, nearest %.0f away at %.0f,%.0f on cell %d, ran on to %s",
            broken.size(),
            away,
            nearest.at()[0],
            nearest.at()[1],
            nearest.circle(),
            nearest.endedOn());
    }

    // A way out of the void a point sits in, if there is one.
    //
    // Proof rather than inference. Whether a patch of void is enclosed is exactly whether
    // something can walk out of it, so this walks: straight lines in every direction, each
    // stopped by a cell it enters or a wall it crosses. One line that reaches open space is a
    // leak, and it names the direction and the gap it went through - which is a place to look
    // at rather than a claim about one. None getting out says only that none of THESE lines
    // did, so the answer is worded as it is measured.
    // Asked at whichever reach the caller is asking about, since the two can differ: void
    // enclosed at the cells' own reach and open a channel out is exactly the case where a
    // pocket exists and nothing is drawn for it.
    private static String describeEscape(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            double[] pick) {

        var sites = union.sites();
        var laid = DiscUnionBoundary.findAttachableChords(union, walls);

        for (var step = 0; step < ESCAPE_DIRECTIONS; step++) {

            var angle = kmlib.math.geometry.Angles.FULL_TURN * step / ESCAPE_DIRECTIONS;
            var away = walkOut(pick, angle, sites, laid, union.reach());

            if (away > 0) {
                return String.format(
                    Locale.ROOT,
                    "escapes %.0f degrees, clear after %.0f",
                    Math.toDegrees(angle),
                    away);
            }
        }
        return "no way out in " + ESCAPE_DIRECTIONS + " directions";
    }

    // How far a straight walk from a point gets before a cell or a wall stops it, or zero
    // where it got all the way out. Stepped rather than solved: what is being asked is whether
    // a way out exists, and a step short enough to fall inside any cell it passes through
    // answers that without intersecting circles by hand.
    private static double walkOut(
            double[] from,
            double angle,
            List<double[]> sites,
            List<DiscUnionBoundary.Chord> laid,
            double reach) {

        var alongX = Math.cos(angle);
        var alongY = Math.sin(angle);
        var at = from;

        for (var step = 1; step * ESCAPE_STEP <= ESCAPE_RANGE; step++) {

            var next = new double[] {
                from[0] + alongX * step * ESCAPE_STEP,
                from[1] + alongY * step * ESCAPE_STEP};

            if (isInsideAnyCell(next, sites, reach) || crossesAnyWall(at, next, laid)) {
                return 0;
            }
            at = next;
        }
        return ESCAPE_RANGE;
    }

    private static boolean isInsideAnyCell(double[] point, List<double[]> sites, double reach) {

        for (var site : sites) {

            if (kmlib.math.geometry.Points.computeDistance(point, site) < reach) {
                return true;
            }
        }
        return false;
    }

    // Whether one step of a walk crosses a laid wall. The wall is the stretch between its two
    // ends, not the line it lies on, because a wall bounds void only where it actually runs.
    private static boolean crossesAnyWall(
            double[] from,
            double[] to,
            List<DiscUnionBoundary.Chord> laid) {

        for (var chord : laid) {

            var line = chord.line();
            var start = new double[] {line.originX(), line.originY()};
            var end = new double[] {
                line.originX() + line.directionX(), line.originY() + line.directionY()};

            if (doSegmentsCross(from, to, start, end)) {
                return true;
            }
        }
        return false;
    }

    // Two segments cross when each straddles the other's line, read off the sign of the four
    // turns. No intersection point is wanted, so none is worked out.
    private static boolean doSegmentsCross(
            double[] oneFrom,
            double[] oneTo,
            double[] otherFrom,
            double[] otherTo) {

        var a = turnsLeft(oneFrom, oneTo, otherFrom);
        var b = turnsLeft(oneFrom, oneTo, otherTo);
        var c = turnsLeft(otherFrom, otherTo, oneFrom);
        var d = turnsLeft(otherFrom, otherTo, oneTo);

        return a != b && c != d;
    }

    private static boolean turnsLeft(double[] from, double[] to, double[] point) {

        return (to[0] - from[0]) * (point[1] - from[1])
            - (to[1] - from[1]) * (point[0] - from[0]) > 0;
    }

    // Whether the walk finds a closed cycle round a point once every wall is laid, and what
    // walled it. The question that tells a leak from a filing error: enclosed here and drawn
    // by nobody means the two constructions disagree about whose hole it is, while not
    // enclosed at all means the void really does run out to sea.
    private static String describeWalledHoleAt(List<VoidHole> holes, double[] pick) {

        for (var index = 0; index < holes.size(); index++) {

            var hole = holes.get(index);

            if (!kmlib.math.geometry.PolygonRegions.isPointInsideRing(
                    hole.boundary(), pick[0], pick[1])) {

                continue;
            }
            var walling = new StringBuilder();

            // Each wall named rather than only its kind. Which wall closed a hole here and
            // failed to close it a channel out is the whole of the question, and a kind cannot
            // be looked up in the refusal census.
            for (var wall : hole.walledBy()) {

                walling
                    .append(walling.isEmpty() ? "" : ", ")
                    .append(wall.kind())
                    .append(' ')
                    .append(wall.fromCircle())
                    .append('-')
                    .append(wall.toCircle());
            }
            return "#" + index + " walled by " + walling;
        }
        return "none";
    }

    // Which side of the drawn coast a point is on. The question that separates "nothing
    // enclosed this" from "the line enclosed it and the trace did not": inside the coast, a
    // patch of black is void the map claims to have shut in, and the walls under that line are
    // where to look. Outside it, the void is open sea and no construction was ever going to
    // fill it.
    private static String describeCoastSide(Coastlines.TracedCoasts traced, double[] pick) {

        return Coastlines.isInsideCoast(Coastlines.collectCoastRings(traced), pick)
            ? "inside the coast"
            : "out at sea";
    }

    // The step of coast whose own run comes closest to a picked point. Measured to the step as
    // a segment rather than to its ends, so a point beside the middle of a long reach finds
    // that reach rather than whichever vertex happens to be nearer.
    private static CoastStep findNearestStep(Coastlines.TracedCoasts traced, double[] pick) {

        CoastStep nearest = null;

        for (var coast : traced.coasts()) {
            for (var index = 0; index < coast.size(); index++) {

                var from = coast.get(index);
                var to = coast.get((index + 1) % coast.size());
                var away = kmlib.math.geometry.Segments.computeDistanceToPoint(
                    from.point(), to.point(), pick);

                if (nearest == null || away < nearest.away()) {
                    nearest = new CoastStep(from, to, away);
                }
            }
        }
        return nearest;
    }

    // The wall one step of coast became, found among the ones actually offered to the walk
    // rather than rebuilt here - the walk answers about the wall it was handed, so a fresh
    // build of the same line is a wall no verdict can be about. Null where the step was never
    // offered as a reach at all.
    private static DiscUnionBoundary.Chord findWallFor(
            List<DiscUnionBoundary.Chord> offered,
            CoastStep step) {

        for (var chord : offered) {

            if (chord.fromCircle() == step.from().circle()
                    && chord.toCircle() == step.to().circle()
                    && chord.line().originX() == step.from().point()[0]
                    && chord.line().originY() == step.from().point()[1]) {

                return chord;
            }
        }
        return null;
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

            if (kmlib.math.geometry.PolygonRegions.isPointInsideRing(
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

            if (kmlib.math.geometry.PolygonRegions.isPointInsideRing(
                    outlines.get(index), pick[0], pick[1])) {

                return "#" + index;
            }
        }
        return "no";
    }

    // The picks for one sector, as the viewer wrote them down. A missing file and picks from
    // another sector are both ordinary: the log is emptied per session and holds whichever
    // sectors were looked at.
    private static List<double[]> readPicks(String sectorName) {

        var file = ViewerPickLog.getPickFile();
        var picks = new ArrayList<double[]>();

        if (!Files.exists(file)) {
            return picks;
        }

        try {
            for (var line : Files.readAllLines(file)) {

                if (!line.startsWith(sectorName)) {
                    continue;
                }
                var fields = line.substring(sectorName.length()).trim().split("[,\\s]+");

                picks.add(new double[] {
                    Double.parseDouble(fields[0]), Double.parseDouble(fields[1])});
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        return picks;
    }

    /**
     * One step of a walked coast, and how near a picked point it runs.
     *
     * @param from  the vertex it leaves
     * @param to    the vertex it lands on
     * @param away  how far the picked point lies from the run itself
     */
    private record CoastStep(
        Coastlines.CoastVertex from,
        Coastlines.CoastVertex to,
        double away) {

        // How long the step is, which is what decides whether it could be a reach at all.
        double length() {
            return kmlib.math.geometry.Points.computeDistance(from.point(), to.point());
        }
    }
}
