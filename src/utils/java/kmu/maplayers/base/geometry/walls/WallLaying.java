package kmu.maplayers.base.geometry.walls;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.LineCrossings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which of the offered walls can actually be laid at one reach, judged greedily in the order
 * they were offered.
 *
 * <p>Two things stop a wall. Its own END can lie inside another disc, which is what becomes of
 * a bridge whose cells have closed over. Or its mouth can be swallowed whole by a mouth an
 * earlier wall on the same circle already took, leaving it no terminal of its own to leave from
 * or land on once the sweep merges the two.
 *
 * <p><b>The one place the laying rule lives.</b> Which walls end up down and why any one of
 * them did not are the same walk asked for two different things. Two spellings of one rule are
 * free to drift, and the drift is invisible: the map goes on being drawn correctly while the
 * report explains a refusal the walk never made. So the walk and {@link ChordRefusals} both
 * read the verdicts made here.
 */
public final class WallLaying {

    private WallLaying() {
    }

    /**
     * The chords that can actually be laid at this reach, which are the only ones traced.
     *
     * <p>Wanted by anything measuring the result: a chord that was dropped has no business
     * being looked for on an outline, and one that was laid has to be on one.
     *
     * <p>The END, not the two edges of the mouth it opens. A mouth is a channel wide, so a
     * wall ending where two circles cross has half of it inside the neighbouring disc whatever
     * the wall did - and refusing it there leaves the void the wall shut in open to the sea
     * with the line still drawn across it.
     *
     * <p>Sharing PART of a mouth is allowed. Both walls keep an outer terminal, and the
     * sweep hands one straight on to the next rather than choosing between them.
     *
     * <p>How much this has to rule on depends on the reach it is asked at, because that is
     * what decides how wide a mouth is. Walked on the cells' own border, no two mouths on a
     * circle overlap at all and neither rule fires. Walked a channel outside it, where a wall
     * cuts into its cells rather than meeting them, mouths widen enough that a few dozen
     * pairs overlap on each sector and a handful swallow one another.
     *
     * <p>Offered in order and taken greedily, so the caller's own ordering decides which of
     * two crowding chords survives.
     *
     * @param union the discs the walls are laid across
     * @param walls the walls on offer
     * @return the chords that can be laid, in the order they were offered
     */
    public static List<Chord> findAttachableChords(DiscUnion union, Walls walls) {

        var attachable = new ArrayList<Chord>();

        for (var verdict : judgeEveryOfferedChord(union, walls)) {

            if (verdict.reason() == RefusalReason.LAID) {
                attachable.add(verdict.chord());
            }
        }

        return attachable;
    }

    /**
     * The two lines a laid chord actually becomes, as their end points.
     *
     * <p>A chord with a channel is two lines rather than one: each runs half a channel from
     * the centre, so each meets the two circles at its own pair of points, and the void on
     * one side of the wall closes on one of them while the void on the other side closes on
     * the other. Handed back paired rather than as four loose points, because the pairing is
     * not the obvious one - a line leaves one circle on the near edge of its mouth and
     * arrives at the other on the FAR edge, since the two circles face opposite ways.
     *
     * <p>No search and no snapping: the mouth's half-width is the angle whose sine is the
     * channel over the reach, and the ends are that angle either side of facing.
     *
     * @param union the discs the chord runs between
     * @param chord the chord
     * @param walls the walls it is one of, for the channel each of its ends keeps
     * @return the two lines, each as its pair of end points
     */
    public static List<List<double[]>> findChordSides(
            DiscUnion union,
            Chord chord,
            Walls walls) {

        var mouthed = MouthedChord.measureMouths(union, chord, walls);
        if (mouthed == null) {
            return List.of();
        }

        var from = union.sites().get(chord.fromCircle());
        var to = union.sites().get(chord.toCircle());
        var fromMouth = mouthed.fromMouth();
        var toMouth = mouthed.toMouth();

        return List.of(
            List.of(
                DiscUnionBoundary.findPointOnCircle(from, union.reach(), fromMouth[0]),
                DiscUnionBoundary.findPointOnCircle(to, union.reach(), toMouth[0] + toMouth[1])),
            List.of(
                DiscUnionBoundary.findPointOnCircle(to, union.reach(), toMouth[0]),
                DiscUnionBoundary.findPointOnCircle(
                    from,
                    union.reach(),
                    fromMouth[0] + fromMouth[1])));
    }

    /**
     * Where two laid walls cross each other, which the walk does not yet know about.
     *
     * <p>Each wall is walked from its mouth on one circle to its mouth on the other, so where
     * two of them cross, each runs on past the crossing into space the other closes. That
     * overshoot is the wedge between their lines, and it is the one shape on this map bounded
     * by something the trace never asks about. Two walls meeting at a shared end overshoot
     * nothing, so that is a touch and not counted.
     *
     * <p>Counted before it is fixed: a handful of crossings is a case to teach the walk, and
     * hundreds would be a different construction.
     *
     * @param union the discs the walls are laid across
     * @param walls the walls on offer
     * @return one entry per crossing pair, as {@code {fromA, toA, fromB, toB}} cells
     */
    public static List<int[]> findWallCrossings(DiscUnion union, Walls walls) {

        var laid = findAttachableChords(union, walls);
        var crossings = new ArrayList<int[]>();

        for (var one = 0; one < laid.size(); one++) {
            for (var other = one + 1; other < laid.size(); other++) {

                var oneWall = laid.get(one);
                var otherWall = laid.get(other);

                if (LineCrossings.isCrossing(
                        oneWall.findStart(),
                        oneWall.findEnd(),
                        otherWall.findStart(),
                        otherWall.findEnd(),
                        LineCrossings.SharedAnchor.IS_TOUCH)) {

                    crossings.add(new int[] {
                        oneWall.fromCircle(),
                        oneWall.toCircle(),
                        otherWall.fromCircle(),
                        otherWall.toCircle()});
                }
            }
        }

        return crossings;
    }

    /**
     * The greedy laying itself: every offered wall judged in the order it was offered, with the
     * mouths each laid wall claimed carried forward.
     *
     * <p>Verdicts rather than a filtered list, because what a refusal WAS is the whole of what
     * the reports want and is thrown away by a method that returns only survivors.
     *
     * <p>No detail string is built here. Naming the cell that covered a wall's end costs a scan
     * over the discs, and this runs on every trace while the answer is wanted only by a report.
     * The rival holding a mouth is the exception: it has to be read while the wall is being
     * judged, since a later wall changes who holds a mouth.
     *
     * @param union the discs the walls are laid across
     * @param walls the walls on offer, in the order they are offered
     * @return one verdict per offered wall, in that same order
     */
    static List<ChordVerdict> judgeEveryOfferedChord(DiscUnion union, Walls walls) {

        var takenByCircle = new LinkedHashMap<Integer, List<TakenMouth>>();
        var verdicts = new ArrayList<ChordVerdict>(walls.chords().size());

        for (var chord : walls.chords()) {

            var verdict = judgeChord(union, chord, walls, takenByCircle);

            verdicts.add(verdict);

            if (verdict.reason() == RefusalReason.LAID) {
                recordBothMouths(takenByCircle, verdict.mouthed());
            }
        }

        return verdicts;
    }

    // Whether a wall still reaches the boundary at this circle - asked of whichever part of
    // it carries the answer for the kind of wall it is, and against whichever circle that part
    // actually sits on.
    //
    // A BRIDGE is asked about its mouth's two EDGES, at the reach being traced. Its line runs
    // through both sites, so it crosses squarely between them and its mouth lies where the gap
    // it spans lies: buried, and the two cells have closed over the gap, which is exactly when
    // the bridge must go.
    //
    // A COAST REACH is asked about its own END, against the circle that end sits on. It leaves
    // a cell along a tangent and can end exactly where two circles cross, and a mouth is a
    // channel wide by construction - so half of it is inside the neighbouring disc whatever the
    // reach did, and the mouth test would refuse a wall that is on the boundary. That refusal
    // leaves the void behind a coast's own reach open to the sea, with the line drawn across
    // the gap and no wall laid under it.
    static boolean isWallOnBoundary(
            DiscUnion union,
            Chord chord,
            int circle,
            double[] mouth) {

        for (var point : collectBoundaryTests(union, chord, circle, mouth)) {

            if (findDeepestCover(union, chord, circle, point) != null) {
                return false;
            }
        }

        return true;
    }

    // The points on one circle that carry the answer for the kind of wall this is: a coast
    // reach's own end, or a bridge's two mouth edges. One list, so the verdict and the account
    // of it are about the same points - an account of a different point is how a refusal
    // comes to be explained by a cell that had nothing to do with it.
    //
    // A mouth is judged at its EDGES. A third disc reaching into the middle of one without
    // touching an edge changes nothing: its cover nests inside the mouth's, so the merged sweep
    // still opens the arcs at the mouth's own edges.
    static List<double[]> collectBoundaryTests(
            DiscUnion union,
            Chord chord,
            int circle,
            double[] mouth) {

        if (chord.kind().isShoreReach()) {
            return List.of(chord.findEndOn(circle));
        }

        var centre = union.sites().get(circle);

        return List.of(
            DiscUnionBoundary.findPointOnCircle(centre, union.reach(), mouth[0]),
            DiscUnionBoundary.findPointOnCircle(centre, union.reach(), mouth[0] + mouth[1]));
    }

    // The disc that covers one of a wall's landings deepest, as {site, depth}, or null where
    // none does. The one reading the verdict and the account of it are both taken from, so a
    // refusal is never explained by a cell that did not cause it.
    static double[] findDeepestCover(
            DiscUnion union,
            Chord chord,
            int circle,
            double[] point) {

        var sites = union.sites();
        var toEdge = measureLandingReach(union, chord, circle, point);

        double[] deepest = null;

        for (var site = 0; site < sites.size(); site++) {

            if (site == circle) {
                continue;
            }

            var under = union.measureCoverOf(point, site, toEdge);
            if (under > DiscUnion.TOUCHING_TOLERANCE
                    && (deepest == null || under > deepest[1])) {

                deepest = new double[] {site, under};
            }
        }

        return deepest;
    }

    // Why one wall was or was not laid, given what is already down. The reason and the rival
    // only: see the note on the laying above about where the explaining happens.
    private static ChordVerdict judgeChord(
            DiscUnion union,
            Chord chord,
            Walls walls,
            Map<Integer, List<TakenMouth>> takenByCircle) {

        var mouthed = MouthedChord.measureMouths(union, chord, walls);
        if (mouthed == null) {
            return new ChordVerdict(chord, null, RefusalReason.NO_MOUTH, null);
        }

        if (!isWallOnBoundaryAtBothEnds(union, mouthed)) {
            return new ChordVerdict(chord, mouthed, RefusalReason.OFF_BOUNDARY, null);
        }

        var holder = findCrowdingHolder(takenByCircle, mouthed);
        return holder == null
            ? new ChordVerdict(chord, mouthed, RefusalReason.LAID, null)
            : new ChordVerdict(chord, mouthed, RefusalReason.CROWDED_OUT, holder);
    }

    // Whether both of a wall's ends sit on the boundary. Asked of the pair rather than of each
    // end, because a wall is laid only if both do and no caller has ever wanted one answer.
    private static boolean isWallOnBoundaryAtBothEnds(DiscUnion union, MouthedChord mouthed) {

        var chord = mouthed.chord();

        return isWallOnBoundary(union, chord, chord.fromCircle(), mouthed.fromMouth())
            && isWallOnBoundary(union, chord, chord.toCircle(), mouthed.toMouth());
    }

    // The laid wall whose mouth leaves this one nowhere to attach, or null where none does.
    //
    // Not asked of a REACH OF COAST, because the sweep hands a swallowed reach the boundary
    // rather than dropping it. A coast leaves a cell along its tangent, so where the coast
    // turns on a cell the mouth it arrives by swallows the mouth it leaves by - two real walls,
    // and crowding would refuse one of them for the shape of the other.
    //
    // Still asked of a BRIDGE. A bridge crosses its circles squarely between its two sites,
    // so its mouth is swallowed only where the gap it spans has closed over, and a wall laid
    // across a gap that is no longer there cuts through cells that have already met.
    private static MouthHolder findCrowdingHolder(
            Map<Integer, List<TakenMouth>> takenByCircle,
            MouthedChord mouthed) {

        var chord = mouthed.chord();
        if (chord.kind().isShoreReach()) {
            return null;
        }

        var holder = findMouthHolderOn(takenByCircle, chord.fromCircle(), mouthed.fromMouth());
        return holder != null
            ? holder
            : findMouthHolderOn(takenByCircle, chord.toCircle(), mouthed.toMouth());
    }

    // The earliest laid wall whose mouth on this circle nests with this one, or null.
    private static MouthHolder findMouthHolderOn(
            Map<Integer, List<TakenMouth>> takenByCircle,
            int circle,
            double[] mouth) {

        for (var taken : takenByCircle.getOrDefault(circle, List.of())) {

            if (isNested(taken.mouth(), mouth)) {
                return new MouthHolder(taken.wall(), circle);
            }
        }

        return null;
    }

    // A mouth is taken when an earlier wall's mouth swallows it whole, or is swallowed by it.
    // The sweep merges what overlaps and the merged cover keeps only the outer pair of
    // terminals, so a mouth wholly inside another loses both of its own: the wall it belongs
    // to then has nowhere to leave from and nowhere to land, which breaks the walk rather than
    // spoiling a shape.
    //
    // Overlapping in part is not that. Each wall keeps the outer terminal the walk needs and
    // the sweep hands one straight on to the other, so refusing those would leave the void
    // behind one of them open to the sea with nothing to close it.
    private static boolean isNested(double[] taken, double[] mouth) {

        return Angles.measureGap(taken[0] + taken[1] / 2, mouth[0] + mouth[1] / 2)
            <= Math.abs(taken[1] - mouth[1]) / 2;
    }

    // Both of a wall's mouths, written down as taken now that it is laid, so the walls offered
    // after this one are held to what is left of each circle rather than to the whole of it.
    // Kept per circle because the question it answers is asked of one circle at a time.
    private static void recordBothMouths(
            Map<Integer, List<TakenMouth>> takenByCircle,
            MouthedChord mouthed) {

        var chord = mouthed.chord();

        takenByCircle
            .computeIfAbsent(chord.fromCircle(), held -> new ArrayList<>())
            .add(new TakenMouth(mouthed.fromMouth(), chord));
        takenByCircle
            .computeIfAbsent(chord.toCircle(), held -> new ArrayList<>())
            .add(new TakenMouth(mouthed.toMouth(), chord));
    }

    // How far out the circle a landing sits on reaches, which is not always the union's.
    //
    // A bridge's mouth edge is a point on the circle BEING TRACED, so the union's reach is the
    // edge it is on. A coast reach's end is a point on the cells' OWN border, wherever the
    // trace is running - it was put there by the smoothing, which walks at the reach that
    // defines void, and no later trace moves it. Out at the reach a shape is drawn at, every
    // disc has grown by the channel, so such an end lies inside each neighbour by as much as
    // that neighbour grew: judged against the union's reach it is refused for the drawing's own
    // inset, and the void behind it goes unclaimed on the very map a reader is looking at.
    //
    // Its own distance from its own site IS that reach, so nothing has to carry it: the end is
    // on that circle by construction, and asking against it is asking whether the end's nearest
    // site is still its own cell - the same question at any reach.
    private static double measureLandingReach(
            DiscUnion union,
            Chord chord,
            int circle,
            double[] point) {

        return chord.kind().isShoreReach()
            ? Points.computeDistance(point, union.sites().get(circle))
            : union.reach();
    }

    /**
     * What the laying did with one offered wall, kept as it was judged.
     *
     * @param chord   the wall
     * @param mouthed it together with its two mouths, or null where it had none
     * @param reason  what the laying did with it
     * @param holder  the rival holding the mouth it wanted, read while it was judged because a
     *                later wall changes who holds one; null unless it was crowded out
     */
    record ChordVerdict(
        Chord chord,
        MouthedChord mouthed,
        RefusalReason reason,
        MouthHolder holder) {
    }

    /**
     * A laid wall holding a mouth another wall wanted, and the cell it holds it on.
     *
     * @param wall   the wall holding the mouth
     * @param circle the cell the two mouths nest on
     */
    record MouthHolder(
        Chord wall,
        int circle) {
    }

    /**
     * A wall together with where it meets each of its two cells.
     *
     * <p>The three never travel apart. A mouth means nothing without the wall it belongs to,
     * and every question asked of a wall from here on - is it on the boundary, has a rival
     * taken its place, which rival - is asked of both mouths in turn. Carried loose, that is
     * three arguments threaded through five methods, in an order two of them could swap
     * without the compiler noticing.
     *
     * @param chord     the wall
     * @param fromMouth where it meets the cell it leaves
     * @param toMouth   where it meets the cell it lands on
     */
    record MouthedChord(
        Chord chord,
        double[] fromMouth,
        double[] toMouth) {

        /**
         * Measures both of a wall's mouths.
         *
         * @param union the discs it is laid across
         * @param chord the wall
         * @param walls the walls it is one of, for the channel each of its ends keeps
         * @return the wall and its mouths, or null where either end has no mouth at all -
         *         which is a wall with nowhere to leave from or nowhere to land, and so not a
         *         wall this can say anything further about
         */
        static MouthedChord measureMouths(DiscUnion union, Chord chord, Walls walls) {

            var fromMouth = WallMouths.measureMouth(
                union,
                chord,
                chord.fromCircle(),
                walls.channelOn(chord.fromCircle()));

            var toMouth = WallMouths.measureMouth(
                union,
                chord,
                chord.toCircle(),
                walls.channelOn(chord.toCircle()));

            return fromMouth == null || toMouth == null
                ? null
                : new MouthedChord(chord, fromMouth, toMouth);
        }
    }

    /**
     * A mouth a laid wall holds, and the wall holding it.
     *
     * @param mouth the stretch of circle taken, as {@code {start, width}}
     * @param wall  the wall that took it
     */
    private record TakenMouth(
        double[] mouth,
        Chord wall) {
    }
}
