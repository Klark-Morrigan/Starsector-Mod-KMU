package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Which walls to take out so the void is not left cut into slivers.
 *
 * <p>A grid laid between every pair of coastline corners divides the water finely - finely
 * enough that most of what comes out is a hundredth of a cell, which is a piece of map nobody
 * can see and nothing can be said about. What is wanted is fewer, larger pieces, and the way to
 * get them is to leave a wall out.
 *
 * <p><b>A stretch, not a span.</b> What separates two pieces is rarely a whole span: a span
 * crossed by two others is three stretches, and only the middle one stands between the two
 * pieces either side of it. Dropping the whole span would open three walls to fix one, and take
 * with it the pieces at either end that had nothing to do with the complaint.
 *
 * <p><b>Too small OR too thin.</b> Area alone cannot see shape: a wedge four thousand long and
 * two hundred wide covers as much map as a compact blob nine hundred across, so no cap that
 * keeps the blob will ever fold the wedge. The second test is the piece's mean width - twice
 * its area over its perimeter, which for a long thin shape IS its width - and a piece failing
 * either test is folded.
 *
 * <p><b>Smallest first, into whichever neighbour comes out fattest, until it passes both.</b>
 * Taking the smallest piece each time is what makes the result independent of where the walk
 * started. Choosing the neighbour by the shape of the RESULT is what makes this fight slivers
 * rather than merely hope: a sliver has neighbours end-on and neighbours side-on, joining the
 * end-on one leaves something longer and no wider, and only the merged width tells them apart.
 * Staying on one piece until it passes is what stops a sliver being folded once, still being a
 * sliver, and being left as one.
 *
 * <p><b>Nothing here returns a shape.</b> A piece is not drawn and then edited - it is what the
 * walk finds once the walls are down, so two pieces either side of a stretch become one piece
 * the moment that stretch is gone, along the boundary they always had. Stitching two outlines
 * along a shared edge instead is where seams, slivers and outlines that disagree with their own
 * walls come from.
 */
final class SmallPieceFolding {

    private SmallPieceFolding() {
    }

    /**
     * What a piece has to be to be worth keeping on its own.
     *
     * <p>Two tests rather than one because they catch different failures and neither implies
     * the other. A piece can be roomy and still be a sliver; a piece can be nicely proportioned
     * and still be too small to see. Read together as an OR: failing either is enough to be
     * folded, and a piece has to pass both to be left alone.
     *
     * @param leastArea       the smallest piece worth keeping, in map units of area. Zero asks
     *                        nothing
     * @param leastWidth      the narrowest piece worth keeping, in map units. Measured as mean
     *                        width - twice the area over the perimeter - which for a long thin
     *                        shape is its width, and for a round one is its radius. Zero asks
     *                        nothing
     * @param leastWholeWidth the width under which a piece is opened by taking out a WHOLE
     *                        wall rather than one stretch of it. Its own number because it
     *                        buys a different thing: a stretch at a time cannot un-fan a bay,
     *                        since the rest of each wall stays standing across it. Zero asks
     *                        nothing
     */
    record FoldRules(
        double leastArea,
        double leastWidth,
        double leastWholeWidth) {
    }

    /**
     * Takes out the stretches whose removal folds a piece not worth having on its own into a
     * neighbour.
     *
     * <p>The graph is left with those stretches dropped. What shape that leaves is not answered
     * here - it is answered by walking the graph again, which is the only reading that cannot
     * disagree with the walls it came from.
     *
     * @param graph      the walls, which this drops stretches from
     * @param pieces     the pieces as the walk first found them
     * @param isFoldable which of those pieces may be folded at all, by index. Land is not water
     *                   and is not a piece anything should be folding
     * @param rules      what a piece has to be to be left alone
     */
    static void dropStretchesAroundSmallPieces(
            PlanarGraph graph,
            List<PlanarGraph.WalkedFace> pieces,
            boolean[] isFoldable,
            FoldRules rules) {

        var neighbours = new PieceNeighbours(graph, pieces);
        var pile = new FoldingPile(pieces, isFoldable, rules);

        // Whole walls first, then stretches. The coarse move opens a bay along a wall's whole
        // length; the fine one tidies what is left. Run the other way round, the fine pass
        // spends itself joining wedges the coarse pass was about to open anyway, and the
        // result depends on which happened to go first.
        openWholeWallsAroundThinPieces(graph, neighbours, pile);

        // Smallest first, over and over, so the order the pieces came out of the walk in
        // cannot change the answer. Bounded by the pieces themselves: every pass either folds
        // two together or gives one up, and neither can happen more often than there are
        // pieces to fold.
        for (var pass = 0; pass < pieces.size(); pass++) {

            var worst = pile.findSmallestWanting();

            if (worst < 0) {
                return;
            }
            foldUntilItPasses(graph, neighbours, pile, worst);
        }
    }

    /**
     * Takes out whole walls, while any piece is thinner than the width that asks for it.
     *
     * <p><b>Why a whole wall and not a stretch.</b> A bay crossed by several bridges is cut
     * into a fan of wedges, and every wedge is bounded by stretches of the SAME few bridges.
     * Opening one stretch joins two wedges and leaves the rest of that bridge standing straight
     * across the piece it just made - so the fan comes apart one sliver at a time and the walls
     * that made it are still there. Taking the bridge out entire opens the bay along its whole
     * length at once, which is the only move that un-fans it.
     *
     * <p>Only where every standing stretch of the wall has water it may fold on both sides. A
     * bridge across a bay MOUTH has the open sea on its far side, and taking that one out would
     * not join two pieces - it would give the bay back to the sea and undo the capture the
     * bridge was laid for. The same goes for a bridge that cut into a continent: on one side
     * of it is land.
     *
     * <p>Area is not consulted. This exists for long thin slivers, and a sliver can cover as
     * much map as anything else.
     */
    private static void openWholeWallsAroundThinPieces(
            PlanarGraph graph,
            PieceNeighbours neighbours,
            FoldingPile pile) {

        // Bounded by the walls: every pass takes one out, and none comes back.
        for (var pass = 0; pass < neighbours.countWalls(); pass++) {

            var thinnest = pile.findThinnestUnderWholeWallWidth();

            if (thinnest < 0) {
                return;
            }

            var wall = findFattestWallToOpen(graph, neighbours, pile, thinnest);

            if (wall < 0) {

                // No wall around it may be taken out entire. Left for the stretch pass, which
                // is allowed the finer move, rather than given up on here.
                pile.stopOpeningWholeWalls(thinnest);
                continue;
            }
            openWall(graph, neighbours, pile, wall);
        }
    }

    // The wall whose removal leaves the widest piece, or -1 where none around this one may go.
    private static int findFattestWallToOpen(
            PlanarGraph graph,
            PieceNeighbours neighbours,
            FoldingPile pile,
            int piece) {

        var fattest = -1;
        var widest = Double.NEGATIVE_INFINITY;

        for (var wall : neighbours.listWallsAround(graph, pile, piece)) {

            if (!neighbours.isWallWhollyBetweenFoldablePieces(graph, pile, wall)) {
                continue;
            }

            var width = pile.measureWidthIfWallOpened(
                neighbours.listPiecesAlongWall(graph, wall),
                neighbours.measureWallLength(graph, wall));

            if (width > widest) {
                widest = width;
                fattest = wall;
            }
        }
        return fattest;
    }

    // One wall out, and everything it stood between joined.
    private static void openWall(
            PlanarGraph graph,
            PieceNeighbours neighbours,
            FoldingPile pile,
            int wall) {

        var joined = neighbours.listPiecesAlongWall(graph, wall);
        var length = neighbours.measureWallLength(graph, wall);

        for (var edge : neighbours.listStandingStretchesOf(graph, wall)) {
            graph.dropEdge(edge);
        }
        pile.joinAlongWall(joined, length);
    }

    /**
     * One piece grown by folding neighbour after neighbour into it until it passes, or until
     * there is nothing left beside it to fold.
     *
     * <p>Stays on the one piece rather than going back to the worst on the map after each fold.
     * A sliver folded once is usually still a sliver, and leaving it to be found again later is
     * how a rule comes to leave pieces failing it - which is the complaint it exists to answer.
     *
     * <p>Each fold takes the neighbour whose MERGED shape comes out widest, which is where the
     * work against slivers actually happens. A sliver has neighbours off its end and neighbours
     * along its side; joining the end-on one leaves something longer and no wider, and the only
     * thing that tells the two apart is measuring the result rather than the candidate.
     */
    private static void foldUntilItPasses(
            PlanarGraph graph,
            PieceNeighbours neighbours,
            FoldingPile pile,
            int piece) {

        while (pile.isWanting(piece)) {

            var fattest = findFattestMerge(graph, neighbours, pile, piece);

            if (fattest < 0) {

                // Walled in by things it may not open: the coastline, or the open sea, or
                // pieces already folded into it. Given up rather than pretended to have grown,
                // so that nothing later reads it as having passed.
                pile.giveUp(piece);
                return;
            }

            // Every stretch the two share, not just one. Two pieces can meet along several
            // stretches at once, and opening one of them leaves the rest standing as walls
            // through the middle of what is now a single piece.
            var shared = neighbours.listStretchesBetween(pile, piece, fattest);

            for (var edge : shared) {
                graph.dropEdge(edge);
            }
            pile.joinPieces(piece, fattest, measureTotalLength(graph, shared));
        }
    }

    /**
     * The neighbour that leaves the widest piece behind, or -1 when there is none to fold.
     *
     * <p>Scored on the result, and the result costs nothing to work out: joining two pieces
     * along a shared wall gives exactly the sum of their areas, and the sum of their perimeters
     * less twice what they shared. So each candidate is a few arithmetic operations rather than
     * a trial merge.
     */
    private static int findFattestMerge(
            PlanarGraph graph,
            PieceNeighbours neighbours,
            FoldingPile pile,
            int piece) {

        var fattest = -1;
        var widest = Double.NEGATIVE_INFINITY;

        for (var beside : neighbours.listPiecesBeside(pile, piece)) {

            if (!pile.isFoldable(beside)) {
                continue;
            }

            var shared = measureTotalLength(
                graph, neighbours.listStretchesBetween(pile, piece, beside));
            var width = pile.measureWidthIfJoined(piece, beside, shared);

            if (width > widest) {
                widest = width;
                fattest = beside;
            }
        }
        return fattest;
    }

    private static double measureTotalLength(PlanarGraph graph, List<Integer> edges) {

        var total = 0.0;

        for (var edge : edges) {
            total += graph.measureEdgeLength(edge);
        }
        return total;
    }

    /**
     * The pieces as they stand while they are being folded: what each covers, how far round it
     * is, which piece it has ended up part of, and which have been given up on.
     *
     * <p>Its own type because those four move together. Area and perimeter both have to be
     * carried, because the width test needs both and a merge changes both; keeping them as
     * loose arrays threaded through every method is how one of them comes to be updated and
     * the other not.
     *
     * <p><b>Nothing here re-measures a shape.</b> Faces of a subdivision partition the plane,
     * so a join adds the areas exactly and adds the perimeters less twice the shared wall
     * exactly. Measuring the merged outline again instead would be a second answer that could
     * differ from the walls it came from.
     */
    private static final class FoldingPile {

        private final double[] areas;
        private final double[] perimeters;
        private final int[] joinedTo;
        private final boolean[] isFoldable;

        // Which pieces have nothing left to fold into. Held apart from the measurements rather
        // than written into them: a piece that gave up has not grown, and recording it as
        // though it had would make every later reading of its size a lie.
        private final boolean[] hasGivenUp;

        // Which pieces the coarse pass has run out of whole walls for. Apart from having given
        // up, which is the stretch pass's verdict: a piece with no whole wall to open may
        // still have a stretch worth opening.
        private final boolean[] hasStopped;

        private final FoldRules rules;

        FoldingPile(
                List<PlanarGraph.WalkedFace> pieces,
                boolean[] isFoldable,
                FoldRules rules) {

            this.areas = new double[pieces.size()];
            this.perimeters = new double[pieces.size()];
            this.joinedTo = new int[pieces.size()];
            this.isFoldable = isFoldable;
            this.hasGivenUp = new boolean[pieces.size()];
            this.hasStopped = new boolean[pieces.size()];
            this.rules = rules;

            for (var piece = 0; piece < pieces.size(); piece++) {

                areas[piece] = pieces.get(piece).area();
                perimeters[piece] = pieces.get(piece).perimeter();
                joinedTo[piece] = piece;
            }
        }

        // Which piece a piece has ended up part of, flattening the chain as it goes so a long
        // run of folds does not make every later question walk it again.
        int findRoot(int piece) {

            var root = piece;

            while (joinedTo[root] != root) {
                root = joinedTo[root];
            }

            var walk = piece;

            while (joinedTo[walk] != root) {

                var next = joinedTo[walk];

                joinedTo[walk] = root;
                walk = next;
            }
            return root;
        }

        boolean isFoldable(int piece) {
            return isFoldable[piece];
        }

        // Whether a piece still fails a test, which is what makes it want folding. Either test
        // is enough, and a piece that has given up wants nothing further.
        boolean isWanting(int piece) {

            var root = findRoot(piece);

            return !hasGivenUp[root]
                && (areas[root] < rules.leastArea()
                    || measureMeanWidth(areas[root], perimeters[root]) < rules.leastWidth());
        }

        // The smallest piece still wanting to be folded, or -1 when none does.
        //
        // By area, even where what it failed was the width test. Some settled order is needed
        // for the answer not to depend on the walk, and area is the one measure every piece
        // has a comparable amount of.
        int findSmallestWanting() {

            var smallest = -1;

            for (var piece = 0; piece < areas.length; piece++) {

                if (!isFoldable[piece] || findRoot(piece) != piece || !isWanting(piece)) {
                    continue;
                }
                if (smallest < 0 || areas[piece] < areas[smallest]) {
                    smallest = piece;
                }
            }
            return smallest;
        }

        /**
         * The thinnest piece still under the width that asks for a whole wall, or -1 when
         * none is.
         *
         * <p>Thinnest first rather than smallest, because this pass is about thinness alone -
         * so the piece most in need of the move is the one that gets it, and the order does
         * not depend on how the walk happened to number them.
         */
        int findThinnestUnderWholeWallWidth() {

            var thinnest = -1;
            var least = Double.MAX_VALUE;

            for (var piece = 0; piece < areas.length; piece++) {

                if (!isFoldable[piece] || findRoot(piece) != piece || hasStopped[piece]) {
                    continue;
                }

                var width = measureMeanWidth(areas[piece], perimeters[piece]);

                if (width < rules.leastWholeWidth() && width < least) {
                    least = width;
                    thinnest = piece;
                }
            }
            return thinnest;
        }

        // Marks a piece as having no whole wall left it may open, so the coarse pass stops
        // offering it the move and goes on to the next. Kept apart from having given up
        // altogether: the stretch pass may still have something finer to offer it.
        void stopOpeningWholeWalls(int piece) {
            hasStopped[findRoot(piece)] = true;
        }

        // How wide the pieces along a wall would be as one, without opening it to find out.
        double measureWidthIfWallOpened(List<Integer> along, double wallLength) {

            var area = 0.0;
            var perimeter = 0.0;
            var counted = new LinkedHashSet<Integer>();

            for (var piece : along) {

                var root = findRoot(piece);

                if (counted.add(root)) {
                    area += areas[root];
                    perimeter += perimeters[root];
                }
            }
            return measureMeanWidth(area, perimeter - wallLength * BOTH_SIDES);
        }

        // Every piece a wall stood between, joined into one.
        void joinAlongWall(List<Integer> along, double wallLength) {

            var kept = findRoot(along.get(0));

            for (var piece : along) {

                var folded = findRoot(piece);

                if (folded == kept) {
                    continue;
                }
                joinedTo[folded] = kept;
                areas[kept] += areas[folded];
                perimeters[kept] += perimeters[folded];
            }

            // The wall itself leaves the boundary once, having been walked from both sides.
            // Taken off after the pieces are joined rather than per pair, because a wall
            // standing between three pieces is one length of wall however many it separated.
            perimeters[kept] -= wallLength * BOTH_SIDES;
        }

        // How wide these two would be as one piece, without joining them to find out.
        double measureWidthIfJoined(int one, int other, double sharedLength) {

            var kept = findRoot(one);
            var folded = findRoot(other);

            return measureMeanWidth(
                areas[kept] + areas[folded],
                perimeters[kept] + perimeters[folded] - sharedLength * BOTH_SIDES);
        }

        void joinPieces(int one, int other, double sharedLength) {

            var kept = findRoot(one);
            var folded = findRoot(other);

            if (kept == folded) {
                return;
            }
            joinedTo[folded] = kept;
            areas[kept] += areas[folded];
            perimeters[kept] += perimeters[folded] - sharedLength * BOTH_SIDES;
        }

        void giveUp(int piece) {
            hasGivenUp[findRoot(piece)] = true;
        }

        // A shared wall leaves both boundaries when they join: it was walked once by each.
        private static final int BOTH_SIDES = 2;

        // Twice the area over the perimeter. For a long thin shape this is its width, and for
        // a round one its radius - so one number covers a wedge and a bay, in map units a
        // reader can hold against the cell radius.
        //
        // Guarded because a piece with no perimeter has no width to speak of, and a merge that
        // cancelled a boundary entirely would otherwise divide by nothing.
        private static double measureMeanWidth(double area, double perimeter) {
            return perimeter <= 0 ? 0 : area * BOTH_SIDES / perimeter;
        }
    }

    /**
     * Which pieces each removable stretch stands between.
     *
     * <p>Read off the walk rather than worked out again. Each direction of a run was walked by
     * exactly one face, so the two directions of one run name the two faces either side of it -
     * the whole adjacency is already there, and asking the geometry a second time would be a
     * second answer.
     *
     * <p>A stretch naming only one piece has the open sea on its other side. Folding a piece
     * into the sea is a different act from joining two pieces, so those are left alone.
     */
    private static final class PieceNeighbours {

        // Which piece walked each direction, or -1 for a direction the open sea walked.
        private final int[] pieceOfEdge;

        // Every removable stretch that stands between two different pieces, by one direction.
        private final List<Integer> joins = new ArrayList<>();

        // Which stretches each whole wall was cut into, by one direction each. What lets a
        // wall be taken out entire rather than a stretch at a time.
        private final Map<Integer, List<Integer>> stretchesOfWall = new LinkedHashMap<>();

        PieceNeighbours(PlanarGraph graph, List<PlanarGraph.WalkedFace> pieces) {

            // Sized from the graph rather than from the highest edge the pieces mention. The
            // open sea walks edges too, and those are exactly the ones no piece names - so a
            // table sized to fit the pieces is a table that stops short of the edges it is
            // then asked about.
            pieceOfEdge = new int[graph.countEdges()];

            Arrays.fill(pieceOfEdge, -1);

            for (var piece = 0; piece < pieces.size(); piece++) {
                for (var edge : pieces.get(piece).edges()) {

                    pieceOfEdge[edge] = piece;
                }
            }

            for (var edge = 0; edge < pieceOfEdge.length; edge += 2) {

                if (!graph.isRemovableEdge(edge)) {
                    continue;
                }
                stretchesOfWall
                    .computeIfAbsent(graph.readGroupOfEdge(edge), wall -> new ArrayList<>())
                    .add(edge);

                if (pieceOfEdge[edge] >= 0
                        && pieceOfEdge[edge ^ 1] >= 0
                        && pieceOfEdge[edge] != pieceOfEdge[edge ^ 1]) {

                    joins.add(edge);
                }
            }
        }

        // Every piece this one currently shares an openable wall with, once each. A neighbour
        // met along several stretches is one neighbour, and offering it once per stretch would
        // have it scored - and possibly folded - several times over.
        List<Integer> listPiecesBeside(FoldingPile pile, int piece) {

            var beside = new LinkedHashSet<Integer>();
            var root = pile.findRoot(piece);

            for (var edge : joins) {

                var one = pile.findRoot(pieceOfEdge[edge]);
                var other = pile.findRoot(pieceOfEdge[edge ^ 1]);

                if (one == other) {
                    continue;
                }
                if (one == root) {
                    beside.add(other);
                } else if (other == root) {
                    beside.add(one);
                }
            }
            return new ArrayList<>(beside);
        }

        private static boolean isRootOf(FoldingPile pile, int piece, int root) {
            return piece >= 0 && pile.findRoot(piece) == root;
        }

        // How many whole walls there are to take out, which bounds the coarse pass.
        int countWalls() {
            return stretchesOfWall.size();
        }

        // Every whole wall with a standing stretch bounding this piece, once each.
        List<Integer> listWallsAround(PlanarGraph graph, FoldingPile pile, int piece) {

            var around = new LinkedHashSet<Integer>();
            var root = pile.findRoot(piece);

            for (var wall : stretchesOfWall.keySet()) {
                for (var edge : listStandingStretchesOf(graph, wall)) {

                    // Either side can be the open sea, which is no piece and has no root to
                    // ask for. A wall with the sea on one side still counts as being AROUND
                    // this piece; whether it may be opened is a separate question, asked once
                    // the candidates are in hand.
                    if (isRootOf(pile, pieceOfEdge[edge], root)
                            || isRootOf(pile, pieceOfEdge[edge ^ 1], root)) {

                        around.add(wall);
                        break;
                    }
                }
            }
            return new ArrayList<>(around);
        }

        // Whether every standing stretch of a wall has a piece that may be folded on both
        // sides of it.
        //
        // What this refuses is a wall that is partly the edge of the construction: a bridge
        // across a bay mouth has the open sea beyond it, and one that cut into a continent has
        // land. Taking either out entire would not join two pieces of water - it would give
        // the water away, which is a different act and not one a fold is entitled to.
        boolean isWallWhollyBetweenFoldablePieces(PlanarGraph graph, FoldingPile pile, int wall) {

            var standing = listStandingStretchesOf(graph, wall);

            if (standing.isEmpty()) {
                return false;
            }

            for (var edge : standing) {

                if (pieceOfEdge[edge] < 0
                        || pieceOfEdge[edge ^ 1] < 0
                        || !pile.isFoldable(pieceOfEdge[edge])
                        || !pile.isFoldable(pieceOfEdge[edge ^ 1])) {

                    return false;
                }
            }
            return true;
        }

        // Every piece a wall currently stands between, once each.
        List<Integer> listPiecesAlongWall(PlanarGraph graph, int wall) {

            var along = new LinkedHashSet<Integer>();

            for (var edge : listStandingStretchesOf(graph, wall)) {

                along.add(pieceOfEdge[edge]);
                along.add(pieceOfEdge[edge ^ 1]);
            }
            return new ArrayList<>(along);
        }

        // How much wall would come out, which is what the joined pieces stop having a boundary
        // along.
        double measureWallLength(PlanarGraph graph, int wall) {

            var length = 0.0;

            for (var edge : listStandingStretchesOf(graph, wall)) {
                length += graph.measureEdgeLength(edge);
            }
            return length;
        }

        // The stretches of one wall that have not already been taken out, one direction each.
        List<Integer> listStandingStretchesOf(PlanarGraph graph, int wall) {

            var standing = new ArrayList<Integer>();

            for (var edge : stretchesOfWall.getOrDefault(wall, List.of())) {

                if (!graph.isDroppedEdge(edge)) {
                    standing.add(edge);
                }
            }
            return standing;
        }

        // Every stretch standing between these two pieces, as they now stand.
        List<Integer> listStretchesBetween(FoldingPile pile, int one, int other) {

            var between = new ArrayList<Integer>();
            var keptRoot = pile.findRoot(one);
            var foldedRoot = pile.findRoot(other);

            for (var edge : joins) {

                var sideOne = pile.findRoot(pieceOfEdge[edge]);
                var sideOther = pile.findRoot(pieceOfEdge[edge ^ 1]);

                if (sideOne == keptRoot && sideOther == foldedRoot
                        || sideOne == foldedRoot && sideOther == keptRoot) {

                    between.add(edge);
                }
            }
            return between;
        }
    }
}
