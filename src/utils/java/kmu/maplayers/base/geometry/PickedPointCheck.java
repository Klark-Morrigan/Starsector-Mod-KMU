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

        System.out.printf(
            Locale.ROOT,
            "coast reaches offered %d: at the cells' own reach %s | a channel out %s%n",
            offered.size(),
            summariseRefusals(new DiscUnion(sites, parameters.cellRadius()), walls, offered),
            summariseRefusals(
                VoidPockets.buildDrawnUnion(sites, parameters), walls, offered));
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
                describeNearestStep(traced, parameters, offered, walls, pick));
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
