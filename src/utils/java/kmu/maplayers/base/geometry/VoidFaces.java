package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The pieces the drawn lines cut the void into.
 *
 * <p><b>Read off the picture, not off the construction.</b> {@link DiscUnionBoundary} finds
 * holes by walking cells and the walls between them, so every wall it knows runs circle to
 * circle and every piece it can name is bounded by whole walls. That is the wrong shape for a
 * grid: where two spans cross, what bounds the piece between them is a STRETCH of each span,
 * which that walk has no way to say. It answers about a coarser map - far fewer, larger
 * shapes than are on screen - and a size read off it is a size of something else.
 *
 * <p>So the lines are taken as lines. Every coast ring and every span is cut at every place it
 * meets another, and what comes out is a graph whose faces are exactly the pieces a reader
 * sees. Which construction drew which line does not come into it: two lines that cross,
 * cross.
 *
 * <p>Cutting the lines is the whole of what happens here. Finding the faces those cut lines
 * enclose is {@link PlanarGraph}'s job and is documented there; what this adds is everything
 * that walk deliberately knows nothing about - which lines are coast and which are spans,
 * which spans may be taken out again, and which of the faces are land.
 *
 * <p><b>Land is told from water by the cells, and by nothing else.</b> Whether a span bounds a
 * face looks like the same question and is not: a span that crosses a coastline puts itself on
 * the boundary of the continent it cut into, so the largest piece of LAND on the map comes
 * back with a span on it. What separates the two is what is inside - a continent's inside
 * holds its cells, and water holds none - so that is what is asked, and the sites have to be
 * handed in for it to be askable.
 */
public final class VoidFaces {

    // How far along a line a crossing must fall to be a crossing rather than an endpoint two
    // lines share. Relative, because it is asked of a parameter along the line rather than of
    // a distance: shared endpoints are already one vertex, and a "crossing" at a hair's
    // breadth from one is that same vertex found a second way.
    private static final double CLEAR_OF_THE_ENDS = 1e-9;

    // How near parallel two runs may be before the place they meet stops being a place. The
    // sine of the angle between them, so it is a claim about direction and not about length.
    // Loose enough that a crossing this shallow would land further along either run than the
    // run is long, and tight enough to leave every crossing a reader can see.
    private static final double SINE_OF_PARALLEL = 1e-9;

    private VoidFaces() {
    }

    /**
     * One piece of map, as the drawn lines bound it.
     *
     * @param boundary   its corners, anticlockwise
     * @param area       how much map it covers
     * @param holdsCells whether any cell sits inside it, which is what separates a continent's
     *                   own inside from the water around it. One piece per continent holds
     *                   cells; every other piece is void
     */
    public record Face(
        List<double[]> boundary,
        double area,
        boolean holdsCells) {
    }

    /**
     * A map cut into pieces, and the walls that were left standing to cut it.
     *
     * <p>The two together because they have to agree. A fold takes out a STRETCH of a span, so
     * what stands afterwards is not the spans that were laid - and anything drawing both the
     * pieces and the walls has to draw the walls that made THESE pieces, or it puts a line on
     * screen across a piece nothing divides there.
     *
     * @param pieces        every bounded piece, largest first
     * @param standingWalls the stretches of span still standing, as pairs of ends. The
     *                      coastlines are not among them: they were never removable, so they
     *                      stand as the caller drew them
     */
    public record CutMap(
        List<Face> pieces,
        List<double[][]> standingWalls) {
    }

    /**
     * Cuts the map into the pieces the coastlines and spans bound between them, folding away
     * the ones too small to be worth having.
     *
     * <p><b>Stretches are removed; shapes are never stitched.</b> A piece here is not drawn
     * and then edited - it is what the walk finds once the walls are down. So two pieces
     * either side of a stretch of span become one piece the moment that stretch is gone, along
     * the boundary they always had, and the pieces are simply found again. Stitching two rings
     * along a shared edge instead is where seams and outlines that disagree with their own
     * walls come from.
     *
     * @param coastRings the closed coastlines, as drawn
     * @param spans      the spans laid across the void, as drawn
     * @param sites      the cells, which is what says whether a piece is land or water
     * @param rules      what a piece of water has to be to be left standing on its own.
     *                   Nothing is folded when both its tests ask nothing, which is the state
     *                   every setting of them is judged against
     * @return the pieces, and the walls left standing to cut them
     */
    public static CutMap cutMapIntoPieces(
            List<List<double[]>> coastRings,
            List<CellGap> spans,
            List<double[]> sites,
            SmallPieceFolding.FoldRules rules) {

        var walls = collectWalls(coastRings, spans);

        if (walls.isEmpty()) {
            return new CutMap(List.of(), List.of());
        }

        var graph = new PlanarGraph();
        var pieces = cutWallsWhereTheyMeet(walls);

        // Every corner known before any run is broken at one. A span can land part way along a
        // stretch of coast, so the coast has to be broken there too - and it cannot be until
        // the place the span ends is a corner something else can be broken at.
        for (var piece : pieces) {
            graph.findOrAddCorner(piece.start());
            graph.findOrAddCorner(piece.end());
        }

        for (var piece : pieces) {
            for (var settled : cutPieceAtStrayCorners(piece, graph.listCorners())) {

                graph.addRun(new PlanarGraph.Run(
                    settled.start(),
                    settled.end(),
                    settled.isSpan(),
                    settled.spanIndex()));
            }
        }

        var found = graph.walkFaces();
        var isLand = markPiecesHoldingCells(found, sites);

        if (rules.leastArea() <= 0 && rules.leastWidth() <= 0 && rules.leastWholeWidth() <= 0) {
            return new CutMap(toFaces(found, isLand), graph.listStandingRemovableRuns());
        }

        // Which walls to take out, worked out over the pieces as first found; then the pieces
        // found again with those walls gone. Deciding and re-finding are kept apart because a
        // fold changes what the neighbours ARE, and a rule acting on a picture it was in the
        // middle of changing would be answering about neither.
        //
        // Only the water is offered for folding. A continent's inside is a piece of this
        // arrangement like any other, and folding one into the sea beside it would take out a
        // stretch of coastline to do it.
        SmallPieceFolding.dropWallsAroundPoorPieces(graph, found, negate(isLand), rules);

        graph.dropRunsLeftDangling();

        var recut = graph.walkFaces();

        return new CutMap(
            toFaces(recut, markPiecesHoldingCells(recut, sites)),
            graph.listStandingRemovableRuns());
    }

    // Which pieces hold a cell, by index. Asked once per walk and carried, rather than asked
    // again wherever the answer is wanted: it is the dearest question in the pass, walking a
    // ring per cell, and it has one answer per piece.
    private static boolean[] markPiecesHoldingCells(
            List<PlanarGraph.WalkedFace> pieces, List<double[]> sites) {

        var holdsCells = new boolean[pieces.size()];

        for (var piece = 0; piece < pieces.size(); piece++) {
            holdsCells[piece] = isHoldingAnyCell(pieces.get(piece).boundary(), sites);
        }
        return holdsCells;
    }

    private static boolean[] negate(boolean[] flags) {

        var negated = new boolean[flags.length];

        for (var index = 0; index < flags.length; index++) {
            negated[index] = !flags[index];
        }
        return negated;
    }

    private static List<Face> toFaces(
            List<PlanarGraph.WalkedFace> walked, boolean[] holdsCells) {

        var faces = new ArrayList<Face>(walked.size());

        for (var piece = 0; piece < walked.size(); piece++) {
            faces.add(new Face(
                walked.get(piece).boundary(),
                walked.get(piece).area(),
                holdsCells[piece]));
        }
        return List.copyOf(faces);
    }

    /**
     * Whether a cell sits inside a piece, which is what separates land from water.
     *
     * <p>Asked of a piece once it is found rather than while it is being found: what bounds a
     * piece and what is inside it are different questions, and the walk answers the first
     * without ever needing the second.
     *
     * <p>Each piece is offered only the cells its own extent could hold. Nearly every piece is
     * a sliver, so nearly every cell is refused on a pair of comparisons - which is what keeps
     * this from being every corner of every piece walked once per cell on the map.
     */
    private static boolean isHoldingAnyCell(List<double[]> boundary, List<double[]> sites) {

        var extent = Bounds.computeEnclosingBounds(boundary);

        for (var site : sites) {

            if (site[0] >= extent.minX() && site[0] <= extent.maxX()
                    && site[1] >= extent.minY() && site[1] <= extent.maxY()
                    && isPointInside(boundary, site)) {

                return true;
            }
        }
        return false;
    }

    // Whether a point is inside a ring, by how many times a ray out of it crosses the boundary
    // - odd in, even out. The ray runs along -x for no reason but that it has to run somewhere.
    private static boolean isPointInside(List<double[]> boundary, double[] at) {

        var inside = false;

        for (var index = 0; index < boundary.size(); index++) {

            var from = boundary.get(index);
            var to = boundary.get((index + 1) % boundary.size());

            if (from[1] > at[1] != to[1] > at[1]
                    && at[0] < (to[0] - from[0]) * (at[1] - from[1]) / (to[1] - from[1])
                        + from[0]) {

                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Breaks a piece wherever another line's corner sits along it.
     *
     * <p>A span anchors on a coast corner, but nothing says the coast carries a corner where a
     * span ENDS. Left whole, that stretch of coast runs straight past the place the span
     * begins, the walk never turns onto the span, and the water the span closed off comes back
     * as part of the open sea.
     */
    private static List<Wall> cutPieceAtStrayCorners(Wall piece, List<double[]> corners) {

        var cuts = new ArrayList<Double>();

        for (var corner : corners) {

            var along = measureAlong(piece, corner);

            if (isWithinTheRun(along)
                    && Segments.computeDistanceToPoint(
                        piece.start(), piece.end(), corner) <= DiscUnion.TOUCHING_TOLERANCE) {

                cuts.add(along);
            }
        }
        return cutWallAlong(piece, cuts);
    }

    // How far along a piece a point falls, as a fraction of it.
    private static double measureAlong(Wall piece, double[] at) {

        var runX = piece.end()[0] - piece.start()[0];
        var runY = piece.end()[1] - piece.start()[1];
        var length = runX * runX + runY * runY;

        return length <= 0
            ? 0
            : ((at[0] - piece.start()[0]) * runX + (at[1] - piece.start()[1]) * runY) / length;
    }

    /**
     * One straight run of a drawn line, and which span it belongs to.
     *
     * <p>The span rather than a bare "is this a span" flag, because a fold may want to take out
     * a WHOLE bridge rather than the stretch of it between two crossings - and a stretch that
     * has forgotten which bridge it came from cannot be gathered back up with its siblings.
     *
     * @param start     one end
     * @param end       the other
     * @param spanIndex which span it is a stretch of, or {@link #COASTLINE} for a run of coast
     */
    private record Wall(
        double[] start,
        double[] end,
        int spanIndex) {

        boolean isSpan() {
            return spanIndex != COASTLINE;
        }
    }

    // A run of coastline belongs to no span. Not a span index, because the coastline is never
    // removable and so is never gathered up to be taken out.
    private static final int COASTLINE = -1;

    // Every drawn line as the straight runs it is made of. The coast arrives as rings, which
    // close back on their first corner; the spans arrive as single runs already.
    private static List<Wall> collectWalls(
            List<List<double[]>> coastRings,
            List<CellGap> spans) {

        var walls = new ArrayList<Wall>();

        for (var ring : coastRings) {
            for (var index = 0; index < ring.size(); index++) {

                walls.add(new Wall(
                    ring.get(index),
                    ring.get((index + 1) % ring.size()),
                    COASTLINE));
            }
        }

        for (var span = 0; span < spans.size(); span++) {
            walls.add(new Wall(spans.get(span).start(), spans.get(span).end(), span));
        }
        return walls;
    }

    // Every wall broken at every place another wall crosses it.
    //
    // Both walls are cut, which is what makes a crossing a vertex rather than one line passing
    // over another: a face bounded by the stretch between two crossings can only exist if that
    // stretch is an edge of its own.
    private static List<Wall> cutWallsWhereTheyMeet(List<Wall> walls) {

        var cutsAlong = new ArrayList<List<Double>>(walls.size());

        for (var index = 0; index < walls.size(); index++) {
            cutsAlong.add(new ArrayList<>());
        }

        for (var one = 0; one < walls.size(); one++) {
            for (var other = one + 1; other < walls.size(); other++) {

                var crossing = findCrossingAlong(walls.get(one), walls.get(other));

                if (crossing != null) {
                    cutsAlong.get(one).add(crossing[0]);
                    cutsAlong.get(other).add(crossing[1]);
                }
            }
        }

        var pieces = new ArrayList<Wall>(walls.size());

        for (var index = 0; index < walls.size(); index++) {
            pieces.addAll(cutWallAlong(walls.get(index), cutsAlong.get(index)));
        }
        return pieces;
    }

    // How far along each wall they cross, or null where they do not cross within both.
    //
    // Strictly within both, since two walls sharing an end share a vertex already and would
    // otherwise be cut at a place they are not crossing at all.
    private static double[] findCrossingAlong(Wall one, Wall other) {

        var oneX = one.end()[0] - one.start()[0];
        var oneY = one.end()[1] - one.start()[1];
        var otherX = other.end()[0] - other.start()[0];
        var otherY = other.end()[1] - other.start()[1];

        var turn = oneX * otherY - oneY * otherX;

        // Parallel, which includes two runs lying along each other. Left uncut on purpose:
        // there is no single place they meet, and a face cannot be bounded by a crossing that
        // is a stretch rather than a point.
        //
        // Asked of the ANGLE between them rather than of the cross product itself, which
        // carries both their lengths: two long walls a thousandth of a turn apart cross the
        // same way two short ones do, and a threshold on the raw product would call one of
        // them parallel and not the other purely for being shorter.
        var apart = Math.abs(turn)
            / (Points.computeVectorLength(oneX, oneY) * Points.computeVectorLength(otherX, otherY));

        if (apart < SINE_OF_PARALLEL) {
            return null;
        }

        var gapX = other.start()[0] - one.start()[0];
        var gapY = other.start()[1] - one.start()[1];

        var alongOne = (gapX * otherY - gapY * otherX) / turn;
        var alongOther = (gapX * oneY - gapY * oneX) / turn;

        return isWithinTheRun(alongOne) && isWithinTheRun(alongOther)
            ? new double[] {alongOne, alongOther}
            : null;
    }

    private static boolean isWithinTheRun(double along) {
        return along > CLEAR_OF_THE_ENDS && along < 1 - CLEAR_OF_THE_ENDS;
    }

    // One wall as the run of pieces its cuts leave, in order along it.
    private static List<Wall> cutWallAlong(Wall wall, List<Double> cuts) {

        if (cuts.isEmpty()) {
            return List.of(wall);
        }
        cuts.sort(Comparator.naturalOrder());

        var pieces = new ArrayList<Wall>(cuts.size() + 1);
        var from = wall.start();

        for (var cut : cuts) {

            var at = findPointAlong(wall, cut);

            // A cut landing on the piece's own start leaves nothing between them. Dropped
            // rather than kept, since a run of no length has no direction and the angular
            // order a face walk turns on cannot be asked of it.
            if (Points.computeDistance(from, at) > DiscUnion.TOUCHING_TOLERANCE) {
                pieces.add(new Wall(from, at, wall.spanIndex()));
                from = at;
            }
        }

        if (Points.computeDistance(from, wall.end()) > DiscUnion.TOUCHING_TOLERANCE) {
            pieces.add(new Wall(from, wall.end(), wall.spanIndex()));
        }
        return pieces;
    }

    private static double[] findPointAlong(Wall wall, double along) {

        return new double[] {
            wall.start()[0] + (wall.end()[0] - wall.start()[0]) * along,
            wall.start()[1] + (wall.end()[1] - wall.start()[1]) * along};
    }

}
