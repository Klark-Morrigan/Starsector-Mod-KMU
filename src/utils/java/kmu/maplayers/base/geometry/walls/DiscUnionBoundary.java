package kmu.maplayers.base.geometry.walls;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.CoastMark;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.VoidHole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The boundary of a {@link DiscUnion}, as closed cycles of circular arcs.
 *
 * <p>A point is void when its nearest site is further than the reach, so the void is exactly
 * the complement of the union. The boundary of that union is a set of closed cycles, and each
 * cycle is either the outer silhouette of a run of overlapping cells or a hole enclosed by
 * them. The holes are the void the cells bind.
 *
 * <p>The walk runs in four steps, each in its own class: {@link WallLaying} drops the walls
 * that cannot attach, {@link CoverSweep} cuts every circle into the arcs nothing covers, and
 * this class joins each arc to the one it names and follows the joins round until they close.
 * {@link ChordRefusals} explains the first step to a reader.
 *
 * <p>The arcs link without matching any coordinates. An arc ends where its circle enters some
 * neighbour's disc, and the boundary continues on that neighbour from the point where the
 * neighbour LEAVES the first disc - a point named by the pair of circles rather than by its
 * position, so two circles computing it separately and landing a rounding apart still agree.
 *
 * <p>{@link Walls} join that walk as covering intervals of their own. Where a neighbouring
 * disc takes a stretch of circle out of the boundary, a chord takes out the mouth it comes
 * through - the stretch lying within a channel of the wall, which {@link WallMouths} works
 * out. The arcs either side of that mouth are then the two sides of the chord, one bounding
 * the void on each side of it, and the channel between them is the gap the two never claim.
 *
 * <p>So a chord is exactly a cover whose ends are named by the chord rather than by a
 * neighbouring circle, and one sweep merges both kinds. Nothing is offset and nothing is cut:
 * every vertex of every shape is a point of some circle, arrived at by arithmetic rather than
 * by finding the nearest sample on a finished outline.
 *
 * <p>Flattened onto the cells' own bound. A cell's radius bound is a regular polygon whose
 * vertices sit at ABSOLUTE angles - the same set of angles on every circle - and an arc is
 * sampled at exactly those angles and at no others. A stretch of boundary and the stretch of
 * cell bound it runs along are then ONE chain of points rather than two chains of the same
 * density at a different phase, which is what lets a piece of traced void be handed to the
 * machinery that shapes and clusters cells: two outlines sharing no vertex cannot be told to
 * abut.
 *
 * <p>Shared because the reach is a parameter rather than a fact: run at the true reach it
 * finds the pockets, run at the reach plus the channel it finds what is left of them once the
 * channel is taken out, and run at the reach minus it, what they become when the cells fill
 * right up to them. Every consumer that wants a pocket at some reach wants this, and there is
 * more than one of them.
 *
 * <p><b>Reading the void with nothing laid across it does not come through here.</b> It is the
 * same sweep with no walls given, but that reading is sound in a way this one is not - none of
 * the accommodations a zero-width wall forced is reached when there are no walls - so it has a
 * door of its own at {@link kmu.maplayers.base.geometry.walls.BareVoidBoundary}, outside this
 * package, and what may only have the bare void is shut off from this one.
 */
public final class DiscUnionBoundary {

    // Stands in for the arc a cycle continues onto where there is none, which is a walk that
    // has run off the end of a broken chain rather than closed.
    private static final int NO_SUCCESSOR = -1;

    // A filled shape winds counter-clockwise, and a hole is turned to read the same way.
    private static final boolean IS_FILL_COUNTER_CLOCKWISE = true;

    private DiscUnionBoundary() {
    }

    /**
     * The same holes, with the given walls laid across the boundary.
     *
     * <p>A wall shuts void on one side of it off from void on the other, so a bay that was
     * open to the rest of the map becomes a closed cycle - a pocket in exactly the sense an
     * enclosed one is, and told apart from the silhouette it was cut from by which way it
     * winds. Nothing has to ask which piece holds the cells.
     *
     * <p>The channel is what keeps the two sides apart: each side closes on its own line half
     * a channel out, and the strip between them belongs to neither. It costs nothing to
     * trace, because moving the wall sideways only moves where it meets each circle - which
     * is still one angle, still exact.
     *
     * <p>A wall whose mouth is buried inside some other disc is not on the boundary at all,
     * and is dropped. That is what becomes of one whose two cells have already closed over
     * at this reach, so a bridge too short to still be a gap costs nothing to offer.
     *
     * @param union         the discs to trace
     * @param walls         the walls to lay across the void
     * @param boundSegments sides of the cells' own radius bound, whose vertex angles every
     *                      arc is flattened onto
     * @return the holes, wound the way any other filled shape is
     */
    public static List<VoidHole> traceHolesAcrossWalls(
            DiscUnion union,
            Walls walls,
            int boundSegments) {

        var holes = new ArrayList<VoidHole>();

        for (var cycle : traceCycles(union, walls, boundSegments)) {

            // A cycle is a hole when it winds the opposite way to a silhouette. Each arc is
            // walked anticlockwise on its own circle, which keeps the discs' interior to the
            // left the whole way round, so an outer cycle comes out anticlockwise and a hole
            // clockwise.
            if (isHole(cycle)) {

                // Turned so a hole reads the same way round as any other filled shape. The
                // marks are left in walk order beside it, as the corners and the ringed cells
                // are: they are the walk's own record of where the cycle ran, and reversing
                // that to match a winding chosen for the fill would be answering a question
                // about the drawing with the one form nothing draws.
                holes.add(new VoidHole(
                    PolygonRegions.orientRing(cycle.boundary(), IS_FILL_COUNTER_CLOCKWISE),
                    cycle.corners(),
                    cycle.marks(),
                    cycle.ringing(),
                    cycle.reach(),
                    cycle.walledBy()));
            }
        }

        return holes;
    }

    /**
     * The two kinds of coast the walk finds, as the run of coast marks each is made of.
     *
     * <p>A silhouette is where a run of connected cells meets the open void - the outline
     * anything smoothing the outer edge has to start from. A lake is the other way round:
     * void the cells closed around unaided, so its coast is where those cells meet the water
     * they shut in. Both come out of the one walk, told apart by which way the cycle winds,
     * and handing back only one of them threw the lakes away at the single place they were
     * already in the shape a smoother eats.
     *
     * <p>Holes a WALL closed come back in a bucket of their own rather than among the lakes. A
     * wall shuts void in by construction rather than by the cells' own geometry, so what the
     * pockets fill and what a lake shore rings are two different claims about the map and a
     * caller has to be able to take one without the other. Kept rather than dropped, because the
     * border ringing such a hole is border like any other: a construction that lays its own
     * walls and then asks what the cells show the water has nowhere else to read it from.
     *
     * <p>Handed back as marks in walk order rather than as sampled outlines, because what a
     * smoother wants is one point per stretch of coast and the order they are passed in - and
     * the walk already knows both. Recovering that from a finished polyline would mean
     * working out which sample belongs to which cell, which is the kind of finding that
     * strays. A lake's marks stay in walk order too: each is still the frontage its cell
     * offers the water, and the smoothing reads nothing but the marks and the order.
     *
     * @param union         the discs to trace
     * @param walls         the walls to lay across the void
     * @param boundSegments sides of the cells' own radius bound, whose vertex angles every
     *                      arc is flattened onto, to tell a silhouette from a hole by the
     *                      area it comes out with
     * @return the silhouettes, the lakes and the walled holes, one run of marks each, in walk
     *         order
     */
    public static CoastRuns traceCoastRuns(
            DiscUnion union,
            Walls walls,
            int boundSegments) {

        var silhouettes = new ArrayList<List<CoastMark>>();
        var lakes = new ArrayList<List<CoastMark>>();
        var walled = new ArrayList<List<CoastMark>>();

        for (var cycle : traceCycles(union, walls, boundSegments)) {

            if (!isHole(cycle)) {
                silhouettes.add(cycle.marks());
            } else if (cycle.walledBy().isEmpty()) {
                lakes.add(cycle.marks());
            } else {
                walled.add(cycle.marks());
            }
        }

        return new CoastRuns(silhouettes, lakes, walled);
    }

    /**
     * Where the walk runs off the end of the boundary instead of closing.
     *
     * <p>Every arc names the terminal it runs on to, and that terminal is another arc's
     * beginning - the whole walk is that pairing. An arc whose terminal nothing begins at is a
     * broken link: the run through it cannot close, so every cycle it belonged to is thrown
     * away, and void that IS enclosed comes back as no pocket at all.
     *
     * <p>Wanted by anything asking why a shape that looks shut in was not found. A count of
     * pockets cannot say; the point where the chain snapped can.
     *
     * @param union the discs to trace
     * @param walls the walls to lay across the void
     * @return one entry per broken link, where the arc ended and what it ended on
     */
    public static List<BrokenLink> findBrokenLinks(DiscUnion union, Walls walls) {

        var linked = linkArcs(union, walls);
        var arcs = linked.arcs();
        var broken = new ArrayList<BrokenLink>();

        for (var index = 0; index < arcs.size(); index++) {

            if (linked.successors()[index] == NO_SUCCESSOR) {

                var arc = arcs.get(index);

                broken.add(new BrokenLink(
                    findPointOnCircle(
                        union.sites().get(arc.circle()),
                        union.reach(),
                        arc.toAngle()),
                    arc.circle(),
                    describeTerminal(arc.endsAt(), linked.laid())));
            }
        }

        return broken;
    }

    // The holes the channel leaves inside one pocket - none when it closes over, more than
    // one when it pinches the pocket in two. A point on a narrowed hole's outline is further
    // from every site than the true reach, so it lies in the true void, and in the very
    // pocket it came out of.
    public static List<List<double[]>> findHolesInside(List<VoidHole> narrowed, VoidHole hole) {

        var inside = new ArrayList<List<double[]>>();

        for (var candidate : narrowed) {

            var probe = candidate.boundary().get(0);

            if (PolygonRegions.isPointInsideRing(hole.boundary(), probe[0], probe[1])) {
                inside.add(candidate.boundary());
            }
        }

        return inside;
    }

    /**
     * The point at one angle along one stretch's own circle.
     *
     * <p>The single conversion from an angle on a {@link CoastMark} to a place on the map,
     * so no reader of a mark does the site-and-reach lookup its own way. Here rather than
     * with any one of those readers, because the mark is this class's type and more than one
     * class reads them.
     *
     * @param union the discs the mark was traced against
     * @param mark  the stretch
     * @param angle the direction from the stretch's own centre
     * @return the point on the stretch's circle
     */
    public static double[] findPointOnMark(DiscUnion union, CoastMark mark, double angle) {
        return findPointOnCircle(union.sites().get(mark.circle()), union.reach(), angle);
    }

    /**
     * The point at one angle on one circle.
     *
     * @param centre where the circle is centred
     * @param reach  its radius
     * @param angle  the direction from the centre
     * @return the point on the circle
     */
    static double[] findPointOnCircle(double[] centre, double reach, double angle) {

        return new double[] {
            centre[0] + reach * Math.cos(angle),
            centre[1] + reach * Math.sin(angle)};
    }

    // The walk up to the joins: drop the walls that cannot attach, cut every circle into the
    // arcs nothing covers, and join each arc to the one it names. Shared by the walk and by the
    // search for where it broke, so the two are about the same arcs.
    private static LinkedArcs linkArcs(DiscUnion union, Walls walls) {

        var laid = new Walls(
            WallLaying.findAttachableChords(union, walls),
            walls.channel(),
            walls.pinchedCells());

        var arcs = CoverSweep.findUncoveredArcs(union, laid);

        return new LinkedArcs(laid, arcs, linkArcsIntoCycles(arcs));
    }

    // The whole walk: the joins, then each followed round until it closes.
    private static List<VoidHole> traceCycles(
            DiscUnion union,
            Walls walls,
            int boundSegments) {

        var linked = linkArcs(union, walls);
        var arcs = linked.arcs();
        var cycles = new ArrayList<VoidHole>();
        var walked = new boolean[arcs.size()];

        for (var start = 0; start < arcs.size(); start++) {

            var cycle = walkCycleFrom(start, arcs, linked.successors(), walked);
            if (cycle.isEmpty()) {
                continue;
            }

            var built = buildHole(cycle, arcs, union, linked.laid().chords(), boundSegments);
            if (built != null) {
                cycles.add(built);
            }
        }

        return cycles;
    }

    // Walked anticlockwise on every circle, which keeps the discs' interior to the left the
    // whole way round - so an outer cycle comes out anticlockwise and a hole clockwise, and
    // the sign of the area is the whole test.
    private static boolean isHole(VoidHole cycle) {
        return PolygonRegions.computeSignedArea(cycle.boundary()) < 0;
    }

    // Which arc the boundary continues onto, as a plain join: every arc names the terminal it
    // begins at and the terminal it runs on to, and the two are the same name. That holds for
    // a crossing between two discs and for a chord's two mouths alike, so nothing here has to
    // know which kind it is looking at.
    private static int[] linkArcsIntoCycles(List<BoundaryArc> arcs) {

        var byStart = new LinkedHashMap<Long, Integer>();

        for (var index = 0; index < arcs.size(); index++) {
            byStart.put(arcs.get(index).startsAt(), index);
        }

        var successors = new int[arcs.size()];

        for (var index = 0; index < arcs.size(); index++) {

            var next = byStart.get(arcs.get(index).endsAt());
            successors[index] = next == null ? NO_SUCCESSOR : next;
        }

        return successors;
    }

    // One cycle, followed from a starting arc until it returns to it. A run that stops on an
    // arc it has already walked, rather than on the one it set out from, is a chain leading
    // into some earlier cycle and not a cycle of its own.
    private static List<Integer> walkCycleFrom(
            int start,
            List<BoundaryArc> arcs,
            int[] successors,
            boolean[] walked) {

        if (walked[start]) {
            return List.of();
        }

        var cycle = new ArrayList<Integer>();
        var at = start;

        while (at != NO_SUCCESSOR && !walked[at]) {

            walked[at] = true;
            cycle.add(at);
            at = successors[at];
        }

        // A run that stopped on an arc it had already walked, rather than on the one it
        // started from, is a chain into an earlier cycle and not a cycle of its own.
        return at == start
            ? cycle
            : List.of();
    }

    // A walked cycle as the shape it stands for: its arcs sampled into a closed outline, the
    // corners where they meet, the cells it runs on and the walls it closes against. Null
    // where too few points came back to enclose anything, which is a cycle that has collapsed
    // rather than a pocket too small to matter.
    private static VoidHole buildHole(
            List<Integer> cycle,
            List<BoundaryArc> arcs,
            DiscUnion union,
            List<Chord> laid,
            int boundSegments) {

        var boundary = new ArrayList<double[]>();
        var corners = new ArrayList<double[]>(cycle.size());
        var marks = new ArrayList<CoastMark>(cycle.size());
        var ringing = new LinkedHashSet<Integer>();
        var walledBy = new LinkedHashSet<Chord>();

        for (var index : cycle) {

            var arc = arcs.get(index);

            // The arc as the stretch of border it is, before it becomes samples. Every reading
            // of the cycle that names cells comes off this one list: the ringed cells here, and
            // whatever a caller later asks about which part of a cell the cycle took.
            marks.add(new CoastMark(arc.circle(), arc.fromAngle(), arc.toAngle()));
            ringing.add(arc.circle());

            var points = sampleArc(
                union.sites().get(arc.circle()),
                union.reach(),
                arc.fromAngle(),
                arc.toAngle(),
                boundSegments);

            corners.add(points.get(0));
            boundary.addAll(points);

            // An arc running on to a chord has to carry its own far end, because the next arc
            // round the cycle begins on the OTHER circle and so puts in the chord's far end
            // rather than this one. Left off, the straight run would start a sample short of
            // where the chord actually does, which is the whole failure this construction
            // exists to avoid.
            if (BoundaryTerminals.isChordTerminal(arc.endsAt())) {

                walledBy.add(laid.get(BoundaryTerminals.readChordFrom(arc.endsAt())));

                boundary.add(findPointOnCircle(
                    union.sites().get(arc.circle()),
                    union.reach(),
                    arc.toAngle()));
            }
        }

        if (boundary.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return null;
        }
        return new VoidHole(
            boundary,
            corners,
            List.copyOf(marks),
            List.copyOf(ringing),
            union.reach(),
            List.copyOf(walledBy));
    }

    // Sampled at the angles the cells' own radius bound has its vertices at, and at no others,
    // so an arc and the run of cell bound beneath it are the same chain of points. Sampling at
    // its own even spacing instead put the two chains half a step out of phase, sharing no
    // vertex at all, and left every stretch of them a sagitta apart - which is what stopped a
    // traced piece of void being handed to machinery built for cells.
    //
    // The arc's own ends are not samples and are not moved onto that set. Each is a crossing
    // the walk names rather than a point it chose: an end is where this circle enters or
    // leaves a neighbour's disc, and the next arc round the cycle picks the boundary up from
    // the same crossing on the other circle. Snapping either would part the two.
    private static List<double[]> sampleArc(
            double[] centre,
            double reach,
            double fromAngle,
            double toAngle,
            int boundSegments) {

        var points = new ArrayList<double[]>();

        // The near end is the arc's own, and the far end is left off: the next arc round the
        // cycle begins on it. A stretch of no width is still a join - it is where two walls
        // meet at a point on this circle - and that one point is the whole of it.
        points.add(findPointOnCircle(centre, reach, fromAngle));

        var firstVertex = (int) Math.floor(fromAngle * boundSegments / Angles.FULL_TURN) + 1;

        // Counted rather than walked until the far end is reached, because no arc sweeps more
        // than one turn: a turn holds one bound vertex per side, the arc's own start stands in
        // for the one it begins at, and a full-turn arc whose end lands a rounding past its
        // start would otherwise close on a repeat of that start.
        for (var offset = 0; offset < boundSegments - 1; offset++) {

            var vertex = firstVertex + offset;

            if (measureBoundVertexAngle(vertex, boundSegments) >= toAngle) {
                break;
            }

            points.add(findBoundVertexOnCircle(centre, reach, vertex, boundSegments));
        }

        return points;
    }

    // Where a cell's radius bound puts vertex number {@code vertex}. Absolute rather than
    // measured from anything the arc knows: the same angles on every circle, which is the
    // whole of what makes two shapes on one circle land on each other.
    private static double measureBoundVertexAngle(int vertex, int boundSegments) {
        return Angles.FULL_TURN * vertex / boundSegments;
    }

    // One of those vertices as a point, with the count taken back into the first turn before
    // the angle is worked out. A vertex a turn along is the same vertex, but the angle naming
    // it that way is a turn's worth of rounding away from the one the cell's own bound was
    // built at - and the two shapes then land near each other rather than on each other.
    private static double[] findBoundVertexOnCircle(
            double[] centre,
            double reach,
            int vertex,
            int boundSegments) {

        return findPointOnCircle(
            centre,
            reach,
            measureBoundVertexAngle(Math.floorMod(vertex, boundSegments), boundSegments));
    }

    // What a terminal is, in words: a wall's own end, or a crossing between two discs. The one
    // thing a broken link needs said about it - which of the two kinds of join failed to pair
    // decides where to look for the reason.
    private static String describeTerminal(long terminal, Walls laid) {

        if (terminal == BoundaryTerminals.NO_TERMINAL) {
            return "nothing at all";
        }

        if (!BoundaryTerminals.isChordTerminal(terminal)) {
            return "a crossing between discs";
        }

        var chord = laid
            .chords()
            .get(BoundaryTerminals.readChordFrom(terminal));

        return chord.kind() + " " + chord.fromCircle() + "-" + chord.toCircle();
    }

    /**
     * What {@link #traceCoastRuns} finds: every stretch of the union's boundary, sorted by what
     * closed the space behind it.
     *
     * <p>One value rather than three methods because all of them come out of one walk, and the
     * walk is not cheap - asked for separately, each caller pays for it again and the answers
     * can be about different walks.
     *
     * <p>Three buckets rather than two because a caller wants different ones. What draws a map's
     * coast wants the silhouettes and the lakes; what asks where the cells face water a wall
     * shut in wants the third, and drawing that one beside the pockets would answer for the same
     * water twice. Sorted here rather than left to a caller to sort, since the walk is the only
     * thing that knows what walled each cycle.
     *
     * @param silhouettes one run of marks per outer silhouette, in walk order
     * @param lakes       one run of marks per hole the cells closed unaided, in walk order
     * @param walled      one run of marks per hole a laid wall closed, in walk order
     */
    public record CoastRuns(
        List<List<CoastMark>> silhouettes,
        List<List<CoastMark>> lakes,
        List<List<CoastMark>> walled) {
    }

    /**
     * One place the walk ran off the end of the boundary.
     *
     * @param at      where the arc ended
     * @param circle  whose cell's border it ran along
     * @param endedOn what it ran on to, which is the name nothing began at
     */
    public record BrokenLink(
        double[] at,
        int circle,
        String endedOn) {
    }

    /**
     * The arcs the walk runs over, joined: the walls actually laid, the arcs they and the discs
     * leave, and for each arc the one it runs on to.
     *
     * @param laid       the walls that attached, whose order the arcs' chord terminals index
     * @param arcs       every uncovered arc
     * @param successors per arc, the index of the arc it continues onto, or
     *                   {@link #NO_SUCCESSOR} where nothing begins at its terminal
     */
    private record LinkedArcs(
        Walls laid,
        List<BoundaryArc> arcs,
        int[] successors) {
    }
}
