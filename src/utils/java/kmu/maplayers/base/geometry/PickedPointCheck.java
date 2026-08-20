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
    // How near two of the coast's landings on one cell must be to count as a tight turn: a
    // mouth's width, which is how far round a circle a tangent wall must go to be a channel
    // clear of its own line.
    private static final double TIGHT_TURN_WITHIN = 2000;

    private static final int ESCAPE_DIRECTIONS = 72;
    private static final double ESCAPE_STEP = 250;
    private static final double ESCAPE_RANGE = 250_000;

    private PickedPointCheck() {
    }

    /**
     * Reports what the constructions make of every point picked on one sector.
     *
     * <p>Run at the shipped knobs, which the header line states, so that a verdict here and a
     * number in the rest of the report describe one map. A pick taken with the viewer's
     * sliders moved can therefore disagree with the window, and the knobs printed beside it
     * are what says so.
     *
     * @param fixture    the sector the picks were taken on
     * @param sectorName its name, which the log records against every pick
     */
    static void reportPickedPoints(SectorFixture fixture, String sectorName) {

        var picks = readPicks(sectorName);

        if (picks.isEmpty()) {
            return;
        }

        var parameters = SectorGeometryParameters.createDefaults();
        var sites = fixture.getSites();
        var traced = Coastlines.traceSectorCoasts(
            sites, parameters, Coastlines.DEFAULT_RULES);

        // Built once and passed down. Every verdict below asks what the walk did with ONE
        // wall, and the walk answers about the wall it was handed - so a second build of the
        // same lines is a set of walls that no verdict can be about, however equal they look.
        var offered = CoastPockets.buildCoastWalls(traced);
        var walls = layWalls(traced, offered);

        reportCoastWallRefusals(traced, parameters, offered, walls);
        reportEachPick(picks, fixture, traced, parameters, offered, walls);
    }

    // Why the coast's own reaches are not laid, counted by reason at each reach. One gap on
    // screen is a case; a count says whether it is the case or one of dozens, which is what
    // decides whether the rule that refused it is worth changing.
    private static void reportCoastWallRefusals(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            List<DiscUnionBoundary.Chord> offered,
            DiscUnionBoundary.Walls walls) {

        var sites = traced.union().sites();

        var atCells = new DiscUnion(sites, parameters.cellRadius());

        System.out.printf(
            Locale.ROOT,
            "coast reaches offered %d: at the cells' own reach %s | a channel out %s%n",
            offered.size(),
            summariseRefusals(atCells, walls, offered),
            summariseRefusals(
                VoidPockets.buildDrawnUnion(sites, parameters), walls, offered));

        reportEachRefusal(atCells, walls, offered);
        reportWallCrossings(atCells, VoidPockets.buildDrawnUnion(sites, parameters), walls);
        reportWallSideStray(atCells, walls, parameters);
        reportCoastTurns(atCells, walls);

    }

    // Every reach the walk turned down at the cells' own reach, one line each, with where it
    // runs. A count says how many; a coast ring left open by one of them is found by knowing
    // WHICH, and there are few enough of these to name them all.
    private static void reportEachRefusal(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            List<DiscUnionBoundary.Chord> offered) {

        for (var chord : offered) {

            var refusal = DiscUnionBoundary.describeChordRefusal(union, walls, chord);

            if (refusal.reason() == DiscUnionBoundary.RefusalReason.LAID) {
                continue;
            }
            var line = chord.line();

            System.out.printf(
                Locale.ROOT,
                "  reach %d-%d from %.0f,%.0f to %.0f,%.0f: %s%n",
                chord.fromCircle(),
                chord.toCircle(),
                line.originX(),
                line.originY(),
                line.originX() + line.directionX(),
                line.originY() + line.directionY(),
                refusal);
        }
    }

    // How many laid walls cross another, which is the one thing on this map bounded by
    // something the walk never asks about. Counted at both reaches, since a pair that misses
    // at one can meet at the other.
    private static void reportWallCrossings(
            DiscUnion atCells,
            DiscUnion atDrawn,
            DiscUnionBoundary.Walls walls) {

        System.out.printf(
            Locale.ROOT,
            "walls crossing another wall: %d at the cells' own reach, %d a channel out%n",
            DiscUnionBoundary.findWallCrossings(atCells, walls).size(),
            DiscUnionBoundary.findWallCrossings(atDrawn, walls).size());
    }

    // How far a laid wall's two drawn sides sit from the wall itself.
    //
    // A wall's drawn ends are taken from the edges of the mouth it opens, and a mouth is as
    // wide as the channel is - measured round the circle. Across a wall that leaves a cell
    // along its tangent, being a channel clear of the line means travelling a long way round,
    // so those edges can sit far from the wall while being the right distance from its line.
    // The pocket then closes on a line that is nowhere near the coast it is supposed to close
    // on, which is what a spike out to sea is.
    //
    // The bridges have this check already and read 0 - a bridge crosses its circles steeply,
    // so its mouth edges are where the bridge is. This is the same question asked of the other
    // kind of wall.
    private static void reportWallSideStray(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            SectorGeometryParameters parameters) {

        var worst = 0.0;
        double[] worstAt = null;

        for (var chord : DiscUnionBoundary.findAttachableChords(union, walls)) {

            if (chord.kind() != DiscUnionBoundary.WallKind.COAST_REACH) {
                continue;
            }
            var line = chord.line();
            var start = new double[] {line.originX(), line.originY()};
            var end = new double[] {
                line.originX() + line.directionX(), line.originY() + line.directionY()};

            for (var side : DiscUnionBoundary.findChordSides(
                    union, chord, parameters.borderInset())) {

                for (var point : side) {

                    var away = kmlib.math.geometry.Segments.computeDistanceToPoint(
                        start, end, point);

                    if (away > worst) {
                        worst = away;
                        worstAt = point;
                    }
                }
            }
        }

        System.out.printf(
            Locale.ROOT,
            "worst coast wall side strays %.0f from its own reach%s (the channel is %.0f)%n",
            worst,
            worstAt == null
                ? ""
                : String.format(Locale.ROOT, ", at %.0f,%.0f", worstAt[0], worstAt[1]),
            parameters.borderInset());
    }

    // How tightly the coast turns on the cells it turns on.
    //
    // Two reaches leaving one cell land on it somewhere, and how far apart those landings are
    // is what decides whether their mouths nest - a mouth is a channel wide measured round the
    // circle, which for a tangent reach is a thousand units and more. A pair landing closer
    // than that is a cell the coast barely touches, kept as a corner it then has to cut.
    private static void reportCoastTurns(
            DiscUnion union,
            DiscUnionBoundary.Walls walls) {

        var laid = new ArrayList<DiscUnionBoundary.Chord>();

        for (var chord : DiscUnionBoundary.findAttachableChords(union, walls)) {

            if (chord.kind() == DiscUnionBoundary.WallKind.COAST_REACH) {
                laid.add(chord);
            }
        }

        var turns = 0;
        var tight = 0;

        for (var one = 0; one < laid.size(); one++) {
            for (var other = one + 1; other < laid.size(); other++) {

                var shared = findSharedCell(laid.get(one), laid.get(other));

                if (shared < 0) {
                    continue;
                }
                turns++;

                var apart = kmlib.math.geometry.Points.computeDistance(
                    findEndOn(laid.get(one), shared), findEndOn(laid.get(other), shared));

                if (apart < TIGHT_TURN_WITHIN) {

                    tight++;
                    System.out.printf(
                        Locale.ROOT,
                        "  coast turns on cell %d: reaches %d-%d and %d-%d land %.0f apart%n",
                        shared,
                        laid.get(one).fromCircle(),
                        laid.get(one).toCircle(),
                        laid.get(other).fromCircle(),
                        laid.get(other).toCircle(),
                        apart);
                }
            }
        }

        System.out.printf(
            Locale.ROOT,
            "the coast turns on a cell %d times, %d of them within %.0f%n",
            turns,
            tight,
            TIGHT_TURN_WITHIN);
    }

    // The cell two walls share, or none. Two reaches of one coast meet on the cell the coast
    // turned on, which is the only pair worth measuring.
    private static int findSharedCell(
            DiscUnionBoundary.Chord one,
            DiscUnionBoundary.Chord other) {

        if (one.fromCircle() == other.fromCircle() || one.fromCircle() == other.toCircle()) {
            return one.fromCircle();
        }

        if (one.toCircle() == other.fromCircle() || one.toCircle() == other.toCircle()) {
            return one.toCircle();
        }
        return -1;
    }

    private static double[] findEndOn(DiscUnionBoundary.Chord chord, int circle) {

        var line = chord.line();

        return chord.fromCircle() == circle
            ? new double[] {line.originX(), line.originY()}
            : new double[] {
                line.originX() + line.directionX(), line.originY() + line.directionY()};
    }

    // Each pick against every construction at both shapings, so which of them was meant to
    // draw the spot - and at which reach - is read off one line rather than guessed at.
    private static void reportEachPick(
            List<double[]> picks,
            SectorFixture fixture,
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            List<DiscUnionBoundary.Chord> offered,
            DiscUnionBoundary.Walls walls) {

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
                "    enclosed: hole true %s inset %s | with every wall %s | %s%n",
                describeHoleAt(holes, pick),
                describeHoleAt(drawnHoles, pick),
                describeWalledHoleAt(walledHoles, pick),
                describeEscape(traced, parameters, walls, pick));

            System.out.printf(
                Locale.ROOT,
                "    coast: %s, %s%n",
                describeCoastSide(traced, pick),
                describeNearestStep(traced, parameters, offered, walls, pick));

            System.out.printf(
                Locale.ROOT,
                "    walk: %s%n",
                describeNearestBrokenLink(traced, parameters, walls, pick));
        }
    }

    // One walk over the offered reaches, sorted into the reasons the walk had for each. Asked
    // of the verdict rather than of its wording, so a rule that renames a refusal cannot
    // quietly move a count into the wrong column.
    private static String summariseRefusals(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            List<DiscUnionBoundary.Chord> offered) {

        var laid = 0;
        var offBoundary = 0;
        var crowded = 0;
        var noMouth = 0;

        for (var chord : offered) {

            switch (DiscUnionBoundary.describeChordRefusal(union, walls, chord).reason()) {
                case LAID -> laid++;
                case OFF_BOUNDARY -> offBoundary++;
                case CROWDED_OUT -> crowded++;
                case NO_MOUTH -> noMouth++;
                default -> { }
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

    // The coast step running nearest a picked point, and what became of it.
    //
    // A line drawn across a gap is not a wall laid across it. The coast draws every step it
    // walks, while only a step long enough to hold a channel is offered as a reach, and only a
    // reach the walk can attach is laid - so a gap can be closed on screen and open to the
    // trace. Which of those three it is, is the whole question.
    private static String describeNearestStep(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            List<DiscUnionBoundary.Chord> offered,
            DiscUnionBoundary.Walls walls,
            double[] pick) {

        var step = findNearestStep(traced, pick);

        if (step == null) {
            return "no coast";
        }

        var wall = findWallFor(offered, step);

        if (wall == null) {
            return String.format(
                Locale.ROOT,
                "nearest step %.0f away, cells %d-%d, %.0f long: not offered as a reach",
                step.away(),
                step.from().circle(),
                step.to().circle(),
                step.length());
        }

        var sites = traced.union().sites();

        return String.format(
            Locale.ROOT,
            "nearest step %.0f away, cells %d-%d, %.0f long: reach, true [%s] inset [%s]",
            step.away(),
            step.from().circle(),
            step.to().circle(),
            step.length(),
            DiscUnionBoundary.describeChordRefusal(
                new DiscUnion(sites, parameters.cellRadius()), walls, wall),
            DiscUnionBoundary.describeChordRefusal(
                VoidPockets.buildDrawnUnion(sites, parameters), walls, wall));
    }

    // The nearest place the walk ran off the end of the boundary. Void that cannot be walked
    // out of and yet comes back as no pocket has to have lost its cycle somewhere, and a
    // broken link is the one way that happens - so this says where to look rather than that
    // something is wrong.
    private static String describeNearestBrokenLink(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            DiscUnionBoundary.Walls walls,
            double[] pick) {

        var broken = DiscUnionBoundary.findBrokenLinks(
            new DiscUnion(traced.union().sites(), parameters.cellRadius()), walls);

        if (broken.isEmpty()) {
            return "no broken links";
        }

        double[] nearest = null;
        var away = Double.MAX_VALUE;

        for (var point : broken) {

            var reach = kmlib.math.geometry.Points.computeDistance(point, pick);

            if (reach < away) {
                away = reach;
                nearest = point;
            }
        }
        return String.format(
            Locale.ROOT,
            "%d broken links, nearest %.0f away at %.0f,%.0f",
            broken.size(),
            away,
            nearest[0],
            nearest[1]);
    }

    // A way out of the void a point sits in, if there is one.
    //
    // Proof rather than inference. Whether a patch of void is enclosed is exactly whether
    // something can walk out of it, so this walks: straight lines in every direction, each
    // stopped by a cell it enters or a wall it crosses. One line that reaches open space is a
    // leak, and it names the direction and the gap it went through - which is a place to look
    // at rather than a claim about one. None getting out says only that none of THESE lines
    // did, so the answer is worded as it is measured.
    private static String describeEscape(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            DiscUnionBoundary.Walls walls,
            double[] pick) {

        var sites = traced.union().sites();
        var laid = DiscUnionBoundary.findAttachableChords(
            new DiscUnion(sites, parameters.cellRadius()), walls);

        for (var step = 0; step < ESCAPE_DIRECTIONS; step++) {

            var angle = kmlib.math.geometry.Angles.FULL_TURN * step / ESCAPE_DIRECTIONS;
            var away = walkOut(pick, angle, sites, laid, parameters.cellRadius());

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
            var kinds = new java.util.LinkedHashSet<DiscUnionBoundary.WallKind>();

            for (var wall : hole.walledBy()) {
                kinds.add(wall.kind());
            }
            return "#" + index + " walled by " + kinds;
        }
        return "none";
    }

    // Which side of the drawn coast a point is on. The question that separates "nothing
    // enclosed this" from "the line enclosed it and the trace did not": inside the coast, a
    // patch of black is void the map claims to have shut in, and the walls under that line are
    // where to look. Outside it, the void is open sea and no construction was ever going to
    // fill it.
    private static String describeCoastSide(Coastlines.TracedCoasts traced, double[] pick) {

        for (var coast : traced.coasts()) {

            if (kmlib.math.geometry.PolygonRegions.isPointInsideRing(
                    Coastlines.collectPoints(coast), pick[0], pick[1])) {

                return "inside the coast";
            }
        }
        return "out at sea";
    }

    // Every wall the coast trace lays, which is its own reaches and the bridges it was walled
    // by. Taken together because a reach can be crowded out by a bridge as easily as by
    // another reach, and asking about one kind alone would answer about a wall set nothing
    // uses.
    private static DiscUnionBoundary.Walls layWalls(
            Coastlines.TracedCoasts traced,
            List<DiscUnionBoundary.Chord> offered) {

        var all = new ArrayList<>(offered);

        all.addAll(traced.walls().chords());

        return new DiscUnionBoundary.Walls(all, traced.walls().channel());
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
