package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Span feet standing too close together, and the room along the frontage to give each its own
 * place.
 *
 * <p><b>A fan is a picture of where anchors were allowed, not of where the void is narrow.</b>
 * Several spans leaving nearly one point read as a spray from a hub, and the eye takes the hub
 * for a feature of the map when it is an artefact of where along the coast each of them happened
 * to settle.
 *
 * <p><b>"Too close" is the separation, not coincidence.</b> Feet a sampling step apart are
 * different vertices of the coast and the same place to a reader - a cell's whole frontage is a
 * few thousand units, a span is drawn a hundred wide, and six spans spread over one stretch
 * still converge to a point on screen. So the knob that says how far a foot moves is the same
 * knob that says how close is too close: one number, asked twice, and a fan is exactly a foot
 * whose nearest neighbour is inside it.
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
 * <p><b>Along the frontage, and onto a point the coast already carries.</b> An anchor has to sit
 * ON the drawn line and ON its own cell's rim at once, and those two are the same places only at
 * the line's vertices: the drawn coast is a chord approximation of the rim, so every place
 * between two vertices lies inside the cell by the sagitta, and every place on the rim between
 * them lies off the drawn line by it.
 *
 * <p>Which is not a free choice. A foot inside its own cell anchors a span that starts under
 * that cell's cover, and the clearance test refuses it - measured, better than nine candidate
 * places in ten. A foot off the drawn line breaks what every reader of an anchor is entitled to
 * assume. So the vertices it is.
 *
 * <p><b>And that is the floor under what the separation can express.</b> The coast is sampled
 * every few hundred units, so settings finer than that all answer with the same vertex. The
 * remedy is not here: it is to sample the coast more finely, which moves the vertices closer
 * together and lets this answer more finely with them.
 *
 * <p><b>Claimed in laying order.</b> The first span to want a place keeps it and later arrivals
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

    // No place on the stretch answers: it is one point long, or every place on it is taken,
    // barred, or not worth the walk. Also what says an anchor is on no stretch of this coast at
    // all, which is a span anchored on a shore this call was not handed.
    private static final int NO_POINT = -1;

    // What staying put is worth, against which every place worth moving to is weighed: no room
    // gained, and no distance walked to gain it.
    private static final double STANDING_STILL_IS_WORTH_NOTHING = 0;

    private CrowdedAnchors() {
    }

    /**
     * Moves each foot that stands within the separation of another onto the roomiest place its
     * own frontage can offer.
     *
     * <p>The nearest place clear of every other foot by the separation, so a foot moves as
     * little as the rule asks; and where the stretch cannot offer that, the place with the most
     * room on it. That single fallback is what produces the three answers worth naming, without
     * any of them having to be written down: a stretch with room to spare gives a place clear by
     * the separation, a stretch too short gives its far end, and a stretch crowded at both ends
     * gives its middle.
     *
     * <p>A foot with nowhere better stays put. Some frontages are a single point, some are
     * hemmed in on both sides, and on some every place worth having would cross a line already
     * down - the fan stands, and {@link SpanFormations} is what answers those.
     *
     * <p>The order the spans arrive in decides who keeps a contested place: what forces a foot
     * to move is a SETTLED foot inside the separation, so the first to want a place holds it.
     * Somewhere to move to, though, is weighed against every other span on the map - including
     * those not yet looked at. A foot that lands beside a later span takes room that span may
     * have no way to make up, and a moved line that clears everything settled can still cut
     * across one nothing has reached yet.
     *
     * @param spans      the spans to spread, in laying order
     * @param standing   the spans already down, whose feet are claimed before any of these
     * @param frontages  each cell's own stretches of the coast these are anchored on, in walk
     *                   order - the runs rather than the gathered points, since a foot may only
     *                   move along the stretch it already stands on
     * @param union      the cells, to keep a moved span clear of them
     * @param separation how close two feet may stand before one of them moves, and how far it
     *                   reaches for when it does, in map units. Non-positive leaves every span
     *                   where it stands
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

    // Where one end of a span stands once the feet already down are taken into account: where
    // it is if nothing crowds it, and otherwise the roomiest place its own frontage can offer.
    private static double[] resolveFreeAnchor(
            int cell,
            double[] anchor,
            double[] otherEnd,
            List<double[]> settled,
            MoveContext context,
            Map<Integer, List<List<double[]>>> frontages) {

        // What forces a move: a foot ALREADY SETTLED standing nearer than the separation. Only
        // the settled count, because a crowd has to leave one span where it is - and the first
        // to want a place is the one that keeps it. Read against every foot instead, every span
        // of a fan would find itself crowded and all of them would step away.
        if (measureClearance(anchor, settled) >= context.separation()) {
            return anchor;
        }

        var run = findRunHolding(frontages.get(cell), anchor);

        if (run == null) {
            return anchor;
        }

        var at = indexOfPoint(run, anchor);

        if (at == NO_POINT) {
            return anchor;
        }

        var moved = findRoomiestPlace(run, measureAlongRun(run), at, otherEnd, context);

        return moved == NO_POINT ? anchor : run.get(moved);
    }

    // Which point of the run to stand on instead: the NEAREST that clears every other foot by
    // the separation, and failing that the place that buys the most room for the least walking.
    //
    // Nearest first, so a foot that can be satisfied moves as little as the rule asks - a span
    // redrawn further from where the search put it than it had to be is a line a reader cannot
    // account for.
    //
    // <b>And when it cannot be satisfied, the room is weighed against the walk.</b> A foot's
    // place is not arbitrary: the search put it where its two cells come closest, so the span
    // marks the crossing it is there to mark. Moving it off that point costs exactly that, and
    // the further it goes the less the line says. So a place is worth taking by how much room it
    // gains LESS how far it is - both distances, so the trade is a subtraction - and standing
    // still is worth nothing, which is the bar a move has to clear.
    //
    // Taking the roomiest place outright instead is what a first attempt did, and above the
    // separation the frontages can actually supply it dragged every crowded foot to the far end
    // of its stretch for a few units of room. The spans grew a fifth of a cell radius on average
    // and stopped pointing at their own gaps.
    //
    // The rule also produces the three cases worth naming without any of them being written
    // down: a stretch with room to spare gives a place clear by the separation, a stretch too
    // short gives its far end, and a stretch crowded at both ends gives its middle - each being
    // where the nearest foot is furthest away, at a distance the gain justifies.
    private static int findRoomiestPlace(
            List<double[]> run,
            double[] along,
            int at,
            double[] otherEnd,
            MoveContext context) {

        var here = measureClearance(run.get(at), context.takenFeet());
        var best = NO_POINT;
        var bestWorth = STANDING_STILL_IS_WORTH_NOTHING;

        for (var candidate : orderByDistanceFrom(run, along, at)) {

            var point = run.get(candidate);

            if (!isPlaceUsable(point, otherEnd, context)) {
                continue;
            }

            var room = measureClearance(point, context.takenFeet());

            if (room >= context.separation()) {
                return candidate;
            }

            var worth = room - here - Math.abs(along[candidate] - along[at]);

            if (worth > bestWorth) {

                best = candidate;
                bestWorth = worth;
            }
        }
        return best;
    }

    // Every other point of the stretch, nearest first along the coast.
    //
    // The stretch's OWN points, and only those, which is what bounds how finely the separation
    // can be answered. A place between two of them is a place on the chord between them, and the
    // drawn coast is a chord approximation of the cell's rim - so such a place lies inside the
    // cell by the sagitta, the span it anchors starts under its own cell's cover, and the
    // clearance test throws it out. Measured over both fixtures, better than nine candidate
    // places in ten were refused for exactly that.
    //
    // Pushing such a place back out onto the rim answers the geometry and breaks the other half
    // of the rule: the anchor is then off the DRAWN line by that same sagitta, and everything
    // that reads an anchor is entitled to find the foot on the line it appears to stand on.
    //
    // So an anchor stays a vertex, and the separation cannot express anything finer than the
    // coast is sampled at - a few hundred units on a typical stretch. That is a limit of the
    // sampling rather than of this rule: sample the coast more finely and this answers more
    // finely with it.
    private static List<Integer> orderByDistanceFrom(
            List<double[]> run,
            double[] along,
            int at) {

        var candidates = new ArrayList<Integer>(run.size());

        for (var index = 0; index < run.size(); index++) {

            if (index != at) {
                candidates.add(index);
            }
        }

        candidates.sort((one, other) -> Double.compare(
            Math.abs(along[one] - along[at]), Math.abs(along[other] - along[at])));

        return candidates;
    }

    // Whether a foot could stand at a place at all, which is a question about the lines rather
    // than about the room: the span it would draw must stay clear of every cell, cross nothing
    // already down, and take no room from a foot that has some.
    private static boolean isPlaceUsable(
            double[] point,
            double[] otherEnd,
            MoveContext context) {

        return CellGaps.isLineClearOfCells(
                point, otherEnd, context.union().sites(), context.union().reach())
            && !AnchoredSpans.doesCrossAnySpan(point, otherEnd, context.otherSpans())
            && !wouldCrowdAFootWithRoom(point, context);
    }

    // Whether standing here would put a foot that currently has room inside the separation.
    //
    // The rule that keeps the pass from making things worse. A place with room to spare cannot
    // do this by definition, so it only ever bites on a second-best one - and a second-best
    // place bought by crowding a foot that was comfortable is not a gain, it is the same fan
    // moved one span along.
    //
    // Landing beside a foot that is already crowded is allowed, since nothing is taken from it:
    // it was going to be looked at anyway, and what it does about this place is its own answer.
    private static boolean wouldCrowdAFootWithRoom(double[] point, MoveContext context) {

        var feet = context.takenFeet();

        for (var index = 0; index < feet.size(); index++) {

            if (Points.computeDistance(point, feet.get(index)) >= context.separation()) {
                continue;
            }

            if (measureClearanceApartFrom(feet.get(index), feet, index)
                    >= context.separation()) {

                return true;
            }
        }
        return false;
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

    // How much room a place has: the distance to the nearest foot standing anywhere else on the
    // map. Straight-line rather than measured along a frontage, because two spans leaving places
    // a few hundred units apart read as one fan whether or not the same stretch of coast carries
    // both of them - and a junction is exactly where two cells' coasts pass through very nearly
    // the same place.
    private static double measureClearance(double[] point, List<double[]> feet) {

        var nearest = Double.MAX_VALUE;

        for (var foot : feet) {
            nearest = Math.min(nearest, Points.computeDistance(point, foot));
        }
        return nearest;
    }

    // The same, for a foot that is itself in the list - which would otherwise answer nought,
    // being no distance from itself.
    private static double measureClearanceApartFrom(
            double[] point,
            List<double[]> feet,
            int own) {

        var nearest = Double.MAX_VALUE;

        for (var index = 0; index < feet.size(); index++) {

            if (index != own) {
                nearest = Math.min(nearest, Points.computeDistance(point, feet.get(index)));
            }
        }
        return nearest;
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
