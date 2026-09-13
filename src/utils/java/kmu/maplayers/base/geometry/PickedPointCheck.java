package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.Segments;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What claims each point picked in the viewer, and why nothing claims the rest.
 *
 * <p>The bridge between what a reader sees and what the layers think they drew. A patch of
 * black raises one question - who was supposed to draw this - and answering it by eye means
 * guessing which of seven layers owns the spot, at which of two reaches, and whether the void
 * there is enclosed at all. Each of those is one column here.
 *
 * <p>Driven by the log the window writes rather than by a list kept in the source, so a fresh
 * set of clicks needs no edit: pick in the viewer, run the report, read the answers.
 *
 * <p>Its own class rather than another block of the report, because it asks a different kind
 * of question. The report describes the whole map in numbers; this one answers about one point
 * a person put their cursor on, and the two grow in different directions.
 */
public final class PickedPointCheck {

    // No ring in the set holds the point, which is a verdict rather than a failure: void with
    // nothing round it is exactly what several of the questions here are looking for.
    private static final int NOTHING_HOLDS_IT = -1;

    // What a layer answers when none of its rings holds the point, so the layers that do can
    // be picked out by comparing against one word rather than against each in turn.
    private static final String NO_HIT = "no";

    // How the void is followed outward: one step of the flood, how far past the cells counts as
    // open sea, and how many places to visit before giving up.
    //
    // The stride is well under a cell, so the flood cannot hop one. The margin is a couple of
    // cells past the outermost site, which is past every wall the map lays. The cap is what
    // stops a pick in genuinely open water from flooding the whole sector: an answer of "still
    // going after this many" is the same news as "it got out", and costs a fraction of the time.
    private static final double FLOOD_STEP = 250;
    private static final double OPEN_SEA_MARGIN = 2;
    private static final int FLOOD_CAP = 40_000;

    // Where a flooded place's two grid coordinates are packed into one key, as the boundary's
    // own terminals are: a sector is a few hundred thousand units across, so each fits an int.
    private static final int PLACE_KEY_SHIFT = 32;
    private static final long PLACE_KEY_MASK = 0xffffffffL;

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
     * @param continents the laying the rest of the report has, with its walls and its water.
     *                   Handed in rather than laid again here, because the walk answers about
     *                   the walls it was given and a second laying is a second answer
     */
    public static void reportPickedPoints(
            SectorFixture fixture,
            String sectorName,
            BridgedContinents continents) {

        var picks = readPicks(sectorName);

        if (!picks.isEmpty()) {
            reportEachPick(picks, fixture, continents);
        }
    }

    // Each pick against every layer at both shapings, so which of them was meant to draw the
    // spot - and at which reach - is read off one line rather than guessed at.
    private static void reportEachPick(
            List<double[]> picks,
            SectorFixture fixture,
            BridgedContinents continents) {

        var laid = continents.layEveryWall();
        var traced = laid.traced();
        var walls = laid.walls();
        var segments = laid.parameters().boundSegments();

        var holes = DiscUnionBoundary.traceHoles(laid.atCells(), segments);
        var drawnHoles = DiscUnionBoundary.traceHoles(laid.atDrawnReach(), segments);

        // The void as the walk sees it with EVERY wall down - spans and coast reaches
        // together. No layer asks this question on its own: each keeps only the holes its own
        // walls closed, so a hole two kinds close between them belongs to none of their answers
        // and shows as nothing at all.
        //
        // At both reaches, because the reach a shape is DRAWN at is the one that decides what
        // a reader sees: a hole at the cells' own reach with nothing to show for it a channel
        // out is a wall the drawing's own inset refused, and no other column says so.
        var walledHoles = DiscUnionBoundary.traceHolesAcrossWalls(
            laid.atCells(), walls, segments);
        var drawnWalledHoles = DiscUnionBoundary.traceHolesAcrossWalls(
            laid.atDrawnReach(), walls, segments);

        // Against the fixture's own owners, because a pick is a question about the picture the
        // reader was looking at, and that picture is coloured.
        var owners = fixture.getOwnerBySite();
        var trueWater = continents.fillWater(owners, VoidPockets.PocketShaping.AT_TRUE_EXTENT);
        var insetWater = continents.fillWater(owners, VoidPockets.PocketShaping.WITH_CHANNEL);

        // A block per pick rather than one long line. The columns answer four different
        // questions - who drew it, whether it is enclosed, what the coast did there, and where
        // the walk broke - and a reader following one of them along a 400-character line reads
        // the wrong column as often as the right one.
        for (var pick : picks) {

            System.out.printf(Locale.ROOT, "  pick %.0f,%.0f%n", pick[0], pick[1]);

            System.out.printf(
                Locale.ROOT,
                "    drawn by: inset %s | true %s%n",
                describeLayerHolding(insetWater, pick),
                describeLayerHolding(trueWater, pick));

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

            if (!PolygonRegions.isPointInsideRing(
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

            var reach = Points.computeDistance(link.at(), pick);

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

        // A pick inside the cells has no void to follow at all, which is a different answer from
        // one whose void is shut in - and at the drawn reach the cells stand a channel wider, so
        // a point in a narrow corridor is inside them there and outside them at their own reach.
        if (union.isPointInside(pick)) {
            return "inside the cells at this reach, so there is no void here to follow";
        }

        var laid = DiscUnionBoundary.findAttachableChords(union, walls);
        var openSea = measureOpenSea(union);

        // Only places the flood actually reached. Counting the ones it tried and refused says
        // five for a point that never moved, which reads as a tiny pocket rather than as a
        // flood that went nowhere.
        var reached = new java.util.HashSet<Long>();
        var queue = new java.util.ArrayDeque<double[]>();

        reached.add(buildPlaceKey(pick));
        queue.add(pick);

        while (!queue.isEmpty()) {

            if (reached.size() > FLOOD_CAP) {
                return "still spreading after " + FLOOD_CAP + " places, so not shut in";
            }

            var at = queue.poll();

            if (openSea.isPastTheCells(at)) {
                return String.format(
                    Locale.ROOT, "reaches open sea, %d places flooded", reached.size());
            }

            for (var step : FLOOD_STEPS) {

                var next = new double[] {
                    at[0] + step[0] * FLOOD_STEP,
                    at[1] + step[1] * FLOOD_STEP};

                // Whether a wall is in the way depends on which side the flood arrives from, so
                // a place refused from here may be reached from elsewhere and is not written off.
                if (reached.contains(buildPlaceKey(next))
                        || union.isPointInside(next)
                        || crossesAnyWall(at, next, laid)) {

                    continue;
                }
                reached.add(buildPlaceKey(next));
                queue.add(next);
            }
        }

        return reached.size() == 1
            ? "boxed in where it stands - every way out is a cell or a wall within one step"
            : "shut in, " + reached.size() + " places flooded";
    }

    // The four ways out of a place. Diagonals are left out on purpose: a diagonal step slips
    // between two cells that meet at a corner, which is land rather than a way through.
    private static final int[][] FLOOD_STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // How far out counts as open sea: past the furthest site by a couple of cells, which is
    // past every wall as well, since a wall runs between two of them.
    private static OpenSea measureOpenSea(DiscUnion union) {

        var low = new double[] {Double.MAX_VALUE, Double.MAX_VALUE};
        var high = new double[] {-Double.MAX_VALUE, -Double.MAX_VALUE};

        for (var site : union.sites()) {

            low[0] = Math.min(low[0], site[0]);
            low[1] = Math.min(low[1], site[1]);
            high[0] = Math.max(high[0], site[0]);
            high[1] = Math.max(high[1], site[1]);
        }

        var margin = OPEN_SEA_MARGIN * union.reach();

        return new OpenSea(
            low[0] - margin, low[1] - margin, high[0] + margin, high[1] + margin);
    }

    /**
     * Where the cells stop and the open sea begins, as a box the flood is out once it leaves.
     *
     * @param lowX  the western edge
     * @param lowY  the southern edge
     * @param highX the eastern edge
     * @param highY the northern edge
     */
    private record OpenSea(double lowX, double lowY, double highX, double highY) {

        /**
         * @param at where the flood has reached
         * @return true where it has left the cells behind
         */
        boolean isPastTheCells(double[] at) {

            return at[0] < lowX || at[1] < lowY || at[0] > highX || at[1] > highY;
        }
    }

    // One flooded place as a single key, so the same place is not visited twice. Quantised to
    // the stride, which is what makes the flood a grid rather than a drift.
    private static long buildPlaceKey(double[] at) {

        var alongX = (int) Math.round(at[0] / FLOOD_STEP);
        var alongY = (int) Math.round(at[1] / FLOOD_STEP);

        return ((long) alongX << PLACE_KEY_SHIFT) ^ (alongY & PLACE_KEY_MASK);
    }

    // Whether one step of a walk crosses a laid wall. The wall is the stretch between its two
    // ends, not the line it lies on, because a wall bounds void only where it actually runs.
    private static boolean crossesAnyWall(
            double[] from,
            double[] to,
            List<DiscUnionBoundary.Chord> laid) {

        for (var chord : laid) {

            // The library's reading, not a local one. Written out here it came with a strict
            // sign test, which reports a walk grazing a wall exactly at one of its ends as
            // NOT crossing - so the one direction that slips past a wall's end is the one
            // direction the escape walk believes in, and enclosed void reads as leaking.
            if (Segments.intersectSegments(
                    from, to, chord.findStart(), chord.findEnd()) != null) {

                return true;
            }
        }
        return false;
    }

    // Whether the walk finds a closed cycle round a point once every wall is laid, and what
    // walled it. The question that tells a leak from a filing error: enclosed here and drawn
    // by nobody means the walk and the fills disagree about whose hole it is, while not
    // enclosed at all means the void really does run out to sea.
    private static String describeWalledHoleAt(List<VoidHole> holes, double[] pick) {

        var index = findRingHolding(collectHoleBoundaries(holes), pick);

        if (index == NOTHING_HOLDS_IT) {
            return "none";
        }
        var walling = new StringBuilder();

        // Each wall named rather than only its kind. Which wall closed a hole here and failed
        // to close it a channel out is the whole of the question, and a kind cannot be looked
        // up in the refusal census.
        for (var wall : holes.get(index).walledBy()) {

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

    // Which side of the coast a point is on. The question that separates "nothing
    // enclosed this" from "the line enclosed it and the trace did not": inside the coast, a
    // patch of black is void the map claims to have shut in, and the walls under that line are
    // where to look. Outside it, the void is open sea and no construction was ever going to
    // fill it.
    private static String describeCoastSide(Coastlines.TracedCoasts traced, double[] pick) {

        return Coastlines.isInsideCoast(Coastlines.collectCoastOutlines(traced), pick)
            ? "inside the coast"
            : "out at sea";
    }

    // The step of coast whose own run comes closest to a picked point. Measured to the step as
    // a segment rather than to its ends, so a point beside the middle of a long reach finds
    // that reach rather than whichever vertex happens to be nearer.
    private static CoastStep findNearestStep(Coastlines.TracedCoasts traced, double[] pick) {

        CoastStep nearest = null;

        for (var coast : traced.coasts()) {

            var vertices = coast.vertices();

            for (var index = 0; index < vertices.size(); index++) {

                var from = vertices.get(index);
                var to = vertices.get((index + 1) % vertices.size());
                var away = Segments.computeDistanceToPoint(
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

    // Which layer of the map's water holds a point, and which of its rings - or none. Every
    // layer in the order the sheet fills them.
    private static String describeLayerHolding(FilledWater water, double[] pick) {

        var byLayer = List.of(
            new String[] {"shore", describeHit(water.collectShoreWater(), pick)},
            new String[] {"inlet", describeHit(water.collectInletWater(), pick)},
            new String[] {"lake", describeHit(water.collectLakeWater(), pick)},
            new String[] {"puddle", describeHit(water.collectPuddleWater(), pick)},
            new String[] {"link", describeHit(water.collectLinkWater(), pick)},
            new String[] {
                "linked shore", describeHit(water.collectLinkedSectorWater(), pick)},
            new String[] {"margin", describeHit(water.collectLakeMargins(), pick)});

        var holding = new ArrayList<String>();

        for (var layer : byLayer) {

            if (!layer[1].equals(NO_HIT)) {
                holding.add(layer[0] + " " + layer[1]);
            }
        }
        return holding.isEmpty() ? "none" : String.join(", ", holding);
    }

    // Which hole of the union holds a point, if any. "None" is the answer that matters most:
    // it says the void there is open to the rest of the map, so no construction was ever going
    // to fill it and the question is why nothing enclosed it.
    private static String describeHoleAt(List<VoidHole> holes, double[] pick) {

        var index = findRingHolding(collectHoleBoundaries(holes), pick);

        return index == NOTHING_HOLDS_IT
            ? "none"
            : "#" + index + " (" + holes.get(index).ringing().size() + " cells)";
    }

    // Which drawn outline holds a point, if any - asked once per layer and per reach, since a
    // pocket can exist at one reach and not the other.
    private static String describeHit(List<List<double[]>> outlines, double[] pick) {

        var index = findRingHolding(outlines, pick);

        return index == NOTHING_HOLDS_IT ? NO_HIT : "#" + index;
    }

    // Which of a set of rings holds a point, by position, or none. Every verdict here is a
    // wording put round this one scan, and each of them written out separately is a chance for
    // one to answer about the first ring that holds the point and another about the last.
    private static int findRingHolding(List<List<double[]>> rings, double[] pick) {

        for (var index = 0; index < rings.size(); index++) {

            if (PolygonRegions.isPointInsideRing(rings.get(index), pick[0], pick[1])) {
                return index;
            }
        }
        return NOTHING_HOLDS_IT;
    }

    // The holes as plain rings, which is what the scan above takes.
    private static List<List<double[]>> collectHoleBoundaries(List<VoidHole> holes) {

        var rings = new ArrayList<List<double[]>>(holes.size());

        for (var hole : holes) {
            rings.add(hole.boundary());
        }
        return rings;
    }

    // The picks for one sector, as the viewer wrote them down. A missing file and picks from
    // another sector are both ordinary: the log is emptied per session and holds whichever
    // sectors were looked at.
    private static List<double[]> readPicks(String sectorName) {

        var file = PickLog.getPickFile();
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
            return Points.computeDistance(from.point(), to.point());
        }
    }
}
