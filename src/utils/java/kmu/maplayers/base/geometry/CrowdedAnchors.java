package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Anchors more than one span wants, and the room along the frontage to give each its own.
 *
 * <p><b>A fan is a picture of where anchors were allowed, not of where the void is narrow.</b>
 * Several spans leaving one point read as a spray from a hub, and the eye takes the hub for a
 * feature of the map when it is an artefact of every one of them having settled on the same
 * vertex of the coast.
 *
 * <p><b>And it costs walls, not merely looks.</b> The boundary walk takes a mouth out of a
 * cell's border for each wall that meets it, and gives a mouth to one wall only - so at a
 * crowded anchor every span but one goes unlaid, the water it would have closed never appears,
 * and the span beside it reads as though it faced the open sea. Spreading the feet is what lets
 * a fan's spans all be laid as the walls they were offered as.
 *
 * <p><b>Which is why the remedy here is to MOVE a span rather than to drop one.</b>
 * {@link SpanFormations} answers the same crowding by asking which spans hold no water back and
 * dropping them. Moving is the lesser remedy of the two - a span given a foot of its own is
 * still on the map, while a span dropped for sharing one is a piece of void nothing holds - so
 * this runs FIRST, and the dropping is left with the crowds that could not be separated.
 *
 * <p><b>Along the frontage, and onto a point the coast already carries.</b> An anchor is only
 * worth anything if it sits ON the drawn line, and the line is sampled - so a place computed at
 * an exact distance along the arc would sit off its chord, near the coast rather than on it.
 * Stepping to another of the run's own points costs a little precision, since the separation is
 * met to within one sampling step, and keeps every span anchored where the pieces are cut.
 *
 * <p><b>Claimed in laying order.</b> The first span to want a point keeps it and later arrivals
 * step aside, which is what makes the answer the same on every run - and what settles the case
 * across two searches: spans already standing when this is called have claimed their feet
 * before anything being spread is looked at, so a link steps off an inlet span's foot rather
 * than the other way about.
 *
 * <p><b>A frontage with no room leaves the fan standing.</b> A cell squeezed by its neighbours
 * offers a frontage of one point, which is the case the dropping pass was written for. Nothing
 * here can help it, and moving a span somewhere it would cross another or cut back through its
 * own cell would be trading a crowded anchor for a false line.
 */
public final class CrowdedAnchors {

    // No point of a run answers, which is a frontage that cannot help: too short to step along,
    // or every candidate on it taken or barred.
    private static final int NO_POINT = -1;

    private CrowdedAnchors() {
    }

    /**
     * Steps each span's feet off anchors another span has already claimed, as far along their
     * own frontage as the separation asks and the room allows.
     *
     * <p>Answers in the order the room runs out. A frontage with the separation to spare gives
     * the first of its points past that distance, in whichever direction is open - so a foot
     * moves as little as the rule asks. A frontage without it gives an end, which is as far from
     * the crowded point as that stretch of coast goes. A frontage that can offer neither - both
     * ends taken, and no room to meet the separation between them - gives its middle, the
     * furthest into what is left from either crowd.
     *
     * <p>And failing all three, whatever of the stretch remains, from the middle outward. A foot
     * moved one sampling place along has stopped sharing its anchor, which is the whole point;
     * refusing that because the separation asked for more would leave the fan standing for the
     * sake of the number. So the separation is what a move REACHES FOR rather than a floor under
     * it, and a stretch hemmed in on both sides still gives up what little it has.
     *
     * <p>The order the spans arrive in decides who keeps a contested point: what forces a span
     * to move is a foot something SETTLED is standing on, so the first to want a place holds
     * it. Somewhere to move to, though, is weighed against every other span on the map -
     * including those not yet looked at. A foot that lands where a later span stands takes a
     * point that span may have no room to leave, and a moved line that clears everything
     * settled can still cut across one nothing has reached yet.
     *
     * @param spans      the spans to spread, in laying order
     * @param standing   the spans already down, whose feet are claimed before any of these
     * @param frontages  each cell's own stretches of the coast these are anchored on, in walk
     *                   order - the runs rather than the gathered points, since the ends and
     *                   the middle of a stretch are what two of the three answers name
     * @param union      the cells, to keep a moved span clear of them
     * @param separation how far along the frontage a foot is moved from a crowded anchor, in
     *                   map units. Non-positive leaves every span where it stands
     * @return the spans, in the order they came, none of them dropped
     */
    public static List<CellGap> spreadCrowdedAnchors(
            List<CellGap> spans,
            List<CellGap> standing,
            Map<Integer, List<List<double[]>>> frontages,
            DiscUnion union,
            double separation) {

        if (separation <= 0 || spans.isEmpty()) {
            return spans;
        }

        var working = new ArrayList<>(spans);

        // What forces a span to move: a foot that something SETTLED is already standing on.
        // Only the settled count, because a crowd has to leave one span where it is - and the
        // first to want the point is the one that keeps it. Read against every foot instead,
        // every span of a fan would find the point taken and all of them would step off it.
        var settled = gatherFeet(standing);

        for (var index = 0; index < working.size(); index++) {

            var others = gatherOtherSpans(standing, working, index);

            var moved = spreadOneSpan(
                working.get(index),
                settled,
                new MoveContext(gatherFeet(others), others, union, separation),
                frontages);

            working.set(index, moved);
            settled.add(moved.start());
            settled.add(moved.end());
        }
        return List.copyOf(working);
    }

    // Every span on the map but the one being moved, at wherever it currently stands.
    //
    // The ones NOT yet resolved among them, which is what stops the pass trading one crowd for
    // another. A foot that lands where a later span is standing has taken a point that span may
    // not be able to leave, and a moved line that misses everything settled can still cut clean
    // across one nothing has looked at yet.
    private static List<CellGap> gatherOtherSpans(
            List<CellGap> standing,
            List<CellGap> working,
            int moving) {

        var others = new ArrayList<>(standing);

        for (var index = 0; index < working.size(); index++) {

            if (index != moving) {
                others.add(working.get(index));
            }
        }
        return others;
    }

    private static List<double[]> gatherFeet(List<CellGap> spans) {

        var feet = new ArrayList<double[]>(spans.size() * 2);

        for (var span : spans) {

            feet.add(span.start());
            feet.add(span.end());
        }
        return feet;
    }

    // One span's two feet, each stepped off a claimed anchor if it stands on one.
    //
    // The second end is resolved against the first's new place rather than its old one, since
    // moving a foot moves the whole line - and a second end judged against the line as it was
    // would be answering about a span that no longer exists.
    private static CellGap spreadOneSpan(
            CellGap span,
            List<double[]> settled,
            MoveContext context,
            Map<Integer, List<List<double[]>>> frontages) {

        var start = resolveFreeAnchor(
            span.fromSite(), span.start(), span.end(), settled, context, frontages);

        var end = resolveFreeAnchor(
            span.toSite(), span.end(), start, settled, context, frontages);

        if (start == span.start() && end == span.end()) {
            return span;
        }

        return new CellGap(
            span.fromSite(), span.toSite(), start, end, Points.computeDistance(start, end));
    }

    // Where one end of a span stands once the anchors already claimed are taken into account:
    // the point it is on where nothing else wants that point, and otherwise the first place
    // along its own frontage that the separation, the cells and the lines already down all
    // allow.
    private static double[] resolveFreeAnchor(
            int cell,
            double[] anchor,
            double[] otherEnd,
            List<double[]> settled,
            MoveContext context,
            Map<Integer, List<List<double[]>>> frontages) {

        if (!isOccupied(anchor, settled)) {
            return anchor;
        }

        var run = findRunHolding(frontages.get(cell), anchor);

        if (run == null) {
            return anchor;
        }

        var along = measureAlongRun(run);
        var moved = findFreePoint(run, along, indexOfPoint(run, anchor), otherEnd, context);

        return moved == NO_POINT ? anchor : run.get(moved);
    }

    // The first candidate along the run that is free, clear of every cell and across nothing
    // already laid. NO_POINT where the run offers no such place at all.
    private static int findFreePoint(
            List<double[]> run,
            double[] along,
            int at,
            double[] otherEnd,
            MoveContext context) {

        if (at == NO_POINT) {
            return NO_POINT;
        }

        for (var candidate : orderCandidates(run, along, at, context)) {

            var point = run.get(candidate);

            if (!isOccupied(point, context.takenFeet())
                    && CellGaps.isLineClearOfCells(
                        point, otherEnd, context.union().sites(), context.union().reach())
                    && !AnchoredSpans.doesCrossAnySpan(point, otherEnd, context.otherSpans())) {

                return candidate;
            }
        }
        return NO_POINT;
    }

    // Which points of the run to try, best first: the separation, then the ends, then the
    // middle.
    //
    // Everything past the separation, in either direction, nearest first. So a foot moves as
    // little as the rule asks, and moves whichever way along the coast the room and the lines
    // already down allow - the two directions are one ordered list rather than one side tried to
    // exhaustion before the other is looked at. Which way a foot should go is not something this
    // can know in advance: stepping TOWARD the span it is stepping away from swings its line
    // across that span within a point or two, and stepping the other way does not.
    //
    // Then the two ends, which is the snap for a run with less room than the separation wanted;
    // where the run had the room they are already among the points above and add nothing.
    //
    // Then the middle, and outward from it - the points nearer than the separation, which is
    // what a stretch has left to offer when its ends are spoken for and it cannot meet the
    // separation between them.
    //
    // A fallback rather than a branch of its own, which is the correction to a first attempt
    // that asked up front whether both ends were taken and went straight to the middle if they
    // were. The ends of a run are junctions the neighbouring cell's coast passes through too, so
    // with a few hundred feet on the map some span is nearly always standing within tolerance of
    // one - the test was nearly always true, and the separation was never consulted at all.
    private static List<Integer> orderCandidates(
            List<double[]> run,
            double[] along,
            int at,
            MoveContext context) {

        var candidates = new ArrayList<Integer>(run.size());

        for (var index = 0; index < run.size(); index++) {

            if (index != at && Math.abs(along[index] - along[at]) >= context.separation()) {
                candidates.add(index);
            }
        }

        candidates.sort((one, other) -> Double.compare(
            Math.abs(along[one] - along[at]), Math.abs(along[other] - along[at])));

        addEnd(candidates, 0, at);
        addEnd(candidates, run.size() - 1, at);
        addOutwardFromMiddle(candidates, run, along, at);

        return candidates;
    }

    private static void addEnd(List<Integer> candidates, int end, int at) {

        if (end != at && !candidates.contains(end)) {
            candidates.add(end);
        }
    }

    // Whatever the run has left to offer, from its middle outward - and not the crowded point
    // itself, which is the one place a foot standing on it cannot be moved to.
    private static void addOutwardFromMiddle(
            List<Integer> candidates,
            List<double[]> run,
            double[] along,
            int at) {

        var middle = along[run.size() - 1] / 2;
        var left = new ArrayList<Integer>(run.size());

        for (var index = 0; index < run.size(); index++) {

            if (index != at && !candidates.contains(index)) {
                left.add(index);
            }
        }

        left.sort((one, other) -> Double.compare(
            Math.abs(along[one] - middle), Math.abs(along[other] - middle)));

        candidates.addAll(left);
    }

    // How far along the run each of its points lies, so that "a distance along the frontage" is
    // measured on the drawn line rather than as a straight line between two of its points - the
    // two part company on a stretch that curves round a cell, which is every stretch here.
    private static double[] measureAlongRun(List<double[]> run) {

        var along = new double[run.size()];

        for (var index = 1; index < run.size(); index++) {

            along[index] = along[index - 1]
                + Points.computeDistance(run.get(index - 1), run.get(index));
        }
        return along;
    }

    // The stretch of this cell's coast the anchor sits on. Null where the cell offers none,
    // which is a span anchored on a coast this call was not handed - a lake span among the
    // exterior frontages, or the other way about.
    private static List<double[]> findRunHolding(
            List<List<double[]>> runs,
            double[] anchor) {

        if (runs == null) {
            return null;
        }

        for (var run : runs) {

            if (indexOfPoint(run, anchor) != NO_POINT) {
                return run;
            }
        }
        return null;
    }

    // Where a point sits in a run, by value. A span's foot IS one of the run's own points, so
    // this is an identity question rather than a nearest-point one - and answering it by
    // distance would quietly move a foot onto a neighbouring vertex.
    private static int indexOfPoint(List<double[]> run, double[] point) {

        for (var index = 0; index < run.size(); index++) {

            if (Arrays.equals(run.get(index), point)) {
                return index;
            }
        }
        return NO_POINT;
    }

    // Whether a span already stands on a point. Read at the same tolerance two spans are called
    // to share an anchor at, since that is the state this exists to undo.
    private static boolean isOccupied(double[] point, List<double[]> occupied) {

        for (var taken : occupied) {

            if (Points.computeDistance(point, taken) <= DiscUnion.TOUCHING_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a candidate place for a foot is judged against.
     *
     * <p>Gathered because every candidate is weighed against all of it, and passing four
     * arguments through three calls made a parameter list nothing could read.
     *
     * @param takenFeet  where every other span on the map currently has a foot, settled or not.
     *                   Somewhere to land has to be free of all of them: a point another span
     *                   is standing on is a crowd whichever of the two was there first
     * @param otherSpans those same spans as lines, since moving a foot swings the whole span
     *                   and the new line has to cross none of them
     * @param union      the cells, to keep a moved span from cutting back through one
     * @param separation how far along the frontage the move is asked to reach
     */
    private record MoveContext(
        List<double[]> takenFeet,
        List<CellGap> otherSpans,
        DiscUnion union,
        double separation) {
    }
}
