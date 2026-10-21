package kmu.maplayers.base.geometry.output.v3;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.VoidHole;
import kmu.maplayers.base.geometry.v3.BridgedContinents;
import kmu.maplayers.base.geometry.v3.CoastVertex;
import kmu.maplayers.base.geometry.v3.FilledWater;
import kmu.maplayers.base.geometry.v3.LaidCoast;
import kmu.maplayers.base.geometry.v3.PickLog;
import kmu.maplayers.base.geometry.v3.TracedCoasts;
import kmu.maplayers.base.geometry.v3.VoidPockets;
import kmu.maplayers.base.geometry.walls.BareVoidBoundary;
import kmu.maplayers.base.geometry.walls.ChordRefusals;
import kmu.maplayers.base.geometry.walls.DiscUnionBoundary;
import kmu.maplayers.base.geometry.walls.Walls;

import java.util.ArrayList;
import java.util.Arrays;
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

    // What a layer answers when none of its rings holds the point, so the layers that do can
    // be picked out by comparing against one word rather than against each in turn.
    private static final String NO_HIT = "no";

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

        var picks = PickLog.readPicks(sectorName);
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
        var segments = laid.parameters().boundSegments();

        // At both reaches, because the reach a shape is DRAWN at is the one that decides what
        // a reader sees: a hole at the cells' own reach with nothing to show for it a channel
        // out is a wall the drawing's own inset refused, and no other column says so. Worked out
        // once each, since nothing about a reach depends on the point asked about.
        var atCells = PickReach.prepareAtReach(laid.atCells(), laid.walls(), segments);
        var atDrawn = PickReach.prepareAtReach(laid.atDrawnReach(), laid.walls(), segments);
        var coastOutlines = laid.traced().collectCoastOutlines();

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
                atCells.describeBareHoleAt(pick),
                atDrawn.describeBareHoleAt(pick),
                atCells.describeWalledHoleAt(pick),
                atDrawn.describeWalledHoleAt(pick),
                atCells.flood().describeEscape(pick)
                    + ", inset " + atDrawn.flood().describeEscape(pick));

            System.out.printf(
                Locale.ROOT,
                "    coast: %s, %s%n",
                describeCoastSide(coastOutlines, pick),
                describeNearestStep(laid, atCells, atDrawn, pick));

            System.out.printf(
                Locale.ROOT,
                "    walls: %s%n",
                describeWallsHolding(atCells, atDrawn, pick));

            System.out.printf(
                Locale.ROOT,
                "    walk: true %s | inset %s%n",
                atCells.describeNearestBrokenLink(pick),
                atDrawn.describeNearestBrokenLink(pick));
        }
    }

    // What the walk made, at each reach, of every wall that closed the void a point sits in.
    //
    // The question a missing fill comes down to once the void is known to be enclosed at the
    // cells' own reach: which of the walls that closed it was not laid a channel out. Asked of
    // the hole's OWN walls rather than of whatever runs nearest, because a wall that closed
    // this void is the only one whose refusal could have opened it.
    private static String describeWallsHolding(
            PickReach atCells,
            PickReach atDrawn,
            double[] pick) {

        var hole = atCells.findWalledHoleAt(pick);
        if (hole == null) {
            return "nothing walls it";
        }
        var verdicts = new StringBuilder();

        for (var wall : hole.walledBy()) {

            verdicts.append(verdicts.isEmpty() ? "" : " | ").append(String.format(
                Locale.ROOT,
                "%s %d-%d true [%s] inset [%s]",
                wall.kind(),
                wall.fromCircle(),
                wall.toCircle(),
                atCells.refusals().describeRefusal(wall),
                atDrawn.refusals().describeRefusal(wall)));
        }

        return verdicts.isEmpty()
            ? "the cells closed it unaided"
            : verdicts.toString();
    }

    // The coast step running nearest a picked point, and what became of it.
    //
    // A line drawn across a gap is not a wall laid across it. The coast draws every step it
    // walks, while only a step long enough to hold a channel is offered as a reach, and only a
    // reach the walk can attach is laid - so a gap can be closed on screen and open to the
    // trace. Which of those three it is, is the whole question.
    private static String describeNearestStep(
            LaidCoast laid,
            PickReach atCells,
            PickReach atDrawn,
            double[] pick) {

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
            atCells.refusals().describeRefusal(wall),
            atDrawn.refusals().describeRefusal(wall));
    }

    // Which side of the coast a point is on. The question that separates "nothing
    // enclosed this" from "the line enclosed it and the trace did not": inside the coast, a
    // patch of black is void the map claims to have shut in, and the walls under that line are
    // where to look. Outside it, the void is open sea and no construction was ever going to
    // fill it.
    private static String describeCoastSide(List<List<double[]>> coastOutlines, double[] pick) {

        return PolygonRegions.isPointInsideAnyRing(coastOutlines, pick[0], pick[1])
            ? "inside the coast"
            : "out at sea";
    }

    // The step of coast whose own run comes closest to a picked point. Measured to the step as
    // a segment rather than to its ends, so a point beside the middle of a long reach finds
    // that reach rather than whichever vertex happens to be nearer.
    private static CoastStep findNearestStep(TracedCoasts traced, double[] pick) {

        CoastStep nearest = null;

        for (var coast : traced.coasts()) {

            var vertices = coast.vertices();

            for (var index = 0; index < vertices.size(); index++) {

                var from = vertices.get(index);
                var to = vertices.get((index + 1) % vertices.size());
                var away = Segments.computeDistanceToPoint(
                    from.point(),
                    to.point(),
                    pick);

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
    private static Chord findWallFor(
            List<Chord> offered,
            CoastStep step) {

        for (var chord : offered) {

            if (chord.fromCircle() == step.from().circle()
                    && chord.toCircle() == step.to().circle()
                    && Arrays.equals(chord.findStart(), step.from().point())) {

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
                "linked shore",
                describeHit(water.collectLinkedSectorWater(), pick)},
            new String[] {"margin", describeHit(water.collectLakeMargins(), pick)});

        var holding = new ArrayList<String>();

        for (var layer : byLayer) {

            if (!layer[1].equals(NO_HIT)) {
                holding.add(layer[0] + " " + layer[1]);
            }
        }

        return holding.isEmpty()
            ? "none"
            : String.join(", ", holding);
    }

    // Which drawn outline holds a point, if any - asked once per layer and per reach, since a
    // pocket can exist at one reach and not the other.
    private static String describeHit(List<List<double[]>> outlines, double[] pick) {

        var index = PolygonRegions.findRingHolding(outlines, pick[0], pick[1]);
        return index == PolygonRegions.NO_RING
            ? NO_HIT
            : "#" + index;
    }

    // The holes as plain rings, which is what the ring-holding scan takes.
    private static List<List<double[]>> collectHoleBoundaries(List<VoidHole> holes) {

        return holes
            .stream()
            .map(VoidHole::boundary)
            .toList();
    }

    /**
     * One step of a walked coast, and how near a picked point it runs.
     *
     * @param from  the vertex it leaves
     * @param to    the vertex it lands on
     * @param away  how far the picked point lies from the run itself
     */
    private record CoastStep(
        CoastVertex from,
        CoastVertex to,
        double away) {

        // How long the step is, which is what decides whether it could be a reach at all.
        double length() {
            return Points.computeDistance(from.point(), to.point());
        }
    }

    /**
     * Everything the report asks of one reach, worked out once before any point is asked about.
     *
     * @param bareHoles        the void the cells enclose with nothing laid across it
     * @param bareBoundaries   those holes as plain rings
     * @param walledHoles      the void the walk finds with EVERY wall down - spans and coast
     *                         reaches together. No layer asks this question on its own: each
     *                         keeps only the holes its own walls closed, so a hole two kinds
     *                         close between them belongs to none of their answers and shows as
     *                         nothing at all
     * @param walledBoundaries those holes as plain rings
     * @param refusals         what the laying made of every wall on offer
     * @param brokenLinks      where the walk ran off the end of the boundary
     * @param flood            the escape flood at this reach
     */
    private record PickReach(
        List<VoidHole> bareHoles,
        List<List<double[]>> bareBoundaries,
        List<VoidHole> walledHoles,
        List<List<double[]>> walledBoundaries,
        ChordRefusals refusals,
        List<DiscUnionBoundary.BrokenLink> brokenLinks,
        EscapeFlood flood) {

        static PickReach prepareAtReach(DiscUnion union, Walls walls, int boundSegments) {

            var bareHoles = BareVoidBoundary.traceBareHoles(union, boundSegments);
            var walledHoles = DiscUnionBoundary.traceHolesAcrossWalls(union, walls, boundSegments);

            return new PickReach(
                bareHoles,
                collectHoleBoundaries(bareHoles),
                walledHoles,
                collectHoleBoundaries(walledHoles),
                ChordRefusals.judgeEveryRefusal(union, walls),
                DiscUnionBoundary.findBrokenLinks(union, walls),
                EscapeFlood.prepareAtReach(union, walls));
        }

        // Which hole of the union holds a point, if any. "None" is the answer that matters
        // most: it says the void there is open to the rest of the map, so no construction was
        // ever going to fill it and the question is why nothing enclosed it.
        String describeBareHoleAt(double[] pick) {

            var index = PolygonRegions.findRingHolding(bareBoundaries, pick[0], pick[1]);
            return index == PolygonRegions.NO_RING
                ? "none"
                : "#" + index + " (" + bareHoles.get(index).ringing().size() + " cells)";
        }

        // Whether the walk finds a closed cycle round a point once every wall is laid, and
        // what walled it. The question that tells a leak from a filing error: enclosed here and
        // drawn by nobody means the walk and the fills disagree about whose hole it is, while
        // not enclosed at all means the void really does run out to sea.
        String describeWalledHoleAt(double[] pick) {

            var index = PolygonRegions.findRingHolding(walledBoundaries, pick[0], pick[1]);
            if (index == PolygonRegions.NO_RING) {
                return "none";
            }

            var walling = new StringBuilder();

            // Each wall named rather than only its kind. Which wall closed a hole here and
            // failed to close it a channel out is the whole of the question, and a kind cannot
            // be looked up in the refusal census.
            for (var wall : walledHoles.get(index).walledBy()) {

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

        // The walled hole holding a point, or null where none does.
        VoidHole findWalledHoleAt(double[] pick) {

            var index = PolygonRegions.findRingHolding(walledBoundaries, pick[0], pick[1]);
            return index == PolygonRegions.NO_RING
                ? null
                : walledHoles.get(index);
        }

        // The nearest place the walk ran off the end of the boundary. Void that cannot be
        // walked out of and yet comes back as no pocket has to have lost its cycle somewhere,
        // and a broken link is the one way that happens - so this says where to look rather
        // than that something is wrong.
        String describeNearestBrokenLink(double[] pick) {

            if (brokenLinks.isEmpty()) {
                return "no broken links";
            }

            DiscUnionBoundary.BrokenLink nearest = null;

            var away = Double.MAX_VALUE;

            for (var link : brokenLinks) {

                var reach = Points.computeDistance(link.at(), pick);
                if (reach < away) {
                    away = reach;
                    nearest = link;
                }
            }

            return String.format(
                Locale.ROOT,
                "%d broken links, nearest %.0f away at %.0f,%.0f on cell %d, ran on to %s",
                brokenLinks.size(),
                away,
                nearest.at()[0],
                nearest.at()[1],
                nearest.circle(),
                nearest.endedOn());
        }
    }
}
