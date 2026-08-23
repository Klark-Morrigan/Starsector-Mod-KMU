package kmu.maplayers.base.geometry;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Which walls to take out so the void is not left cut into slivers.
 *
 * <p>A grid laid between every pair of coastline corners divides the water finely - finely
 * enough that most of what comes out is a hundredth of a cell, which is a piece of map nobody
 * can see and nothing can be said about. What is wanted is fewer, larger pieces, and the way to
 * get them is to leave a wall out.
 *
 * <p><b>A stretch or a whole wall, and the two buy different things.</b> What separates two
 * pieces is rarely a whole wall: a wall crossed by two others is three stretches, and only the
 * middle one stands between the two pieces either side of it. Opening that stretch alone is the
 * precise move - it fixes one complaint and leaves the pieces at either end, which had nothing
 * to do with it, exactly as they were.
 *
 * <p>What the precise move cannot do is un-fan a bay. A bay crossed by several walls is cut
 * into a fan of wedges all bounded by stretches of the SAME few walls, so opening one stretch
 * joins two wedges and leaves the rest of that wall standing straight across the piece it just
 * made. Only taking a wall out entire opens the bay along its whole length. So there are two
 * moves under two settings: whole walls first, while a piece is thin enough to want the coarse
 * one, then stretches for what is left.
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

    // The three things every step of a fold needs: the walls it takes out, the index that says
    // what stands between what, and the running measurements. Held rather than threaded through
    // every method, which is what they were - the same three arguments on every signature is
    // the shape that wants to be an object.
    private final PlanarGraph graph;
    private final PieceNeighbours neighbours;
    private final FoldingPile pile;

    private SmallPieceFolding(
            PlanarGraph graph,
            List<PlanarGraph.WalkedFace> pieces,
            boolean[] isFoldable,
            FoldRules rules) {

        this.graph = graph;
        this.pile = new FoldingPile(pieces, isFoldable, rules);
        this.neighbours = new PieceNeighbours(graph, pieces, pile::findRoot);
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
     * Takes out the walls whose removal folds a piece not worth having on its own into a
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
    static void dropWallsAroundPoorPieces(
            PlanarGraph graph,
            List<PlanarGraph.WalkedFace> pieces,
            boolean[] isFoldable,
            FoldRules rules) {

        new SmallPieceFolding(graph, pieces, isFoldable, rules).dropWalls(pieces.size());
    }

    private void dropWalls(int mostPasses) {

        // Whole walls first, then stretches. The coarse move opens a bay along a wall's whole
        // length; the fine one tidies what is left. Run the other way round, the fine pass
        // spends itself joining wedges the coarse pass was about to open anyway, and the
        // result depends on which happened to go first.
        openWholeWallsAroundThinPieces();

        // Smallest first, over and over, so the order the pieces came out of the walk in
        // cannot change the answer. Bounded by the pieces themselves: every pass either folds
        // two together or gives one up, and neither can happen more often than there are
        // pieces to fold.
        for (var pass = 0; pass < mostPasses; pass++) {

            var worst = pile.findSmallestWanting();

            if (worst < 0) {
                return;
            }
            foldUntilItPasses(worst);
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
    private void openWholeWallsAroundThinPieces() {

        // Bounded by the walls: every pass takes one out, and none comes back.
        for (var pass = 0; pass < neighbours.countWalls(); pass++) {

            var thinnest = pile.findThinnestUnderWholeWallWidth();

            if (thinnest < 0) {
                return;
            }

            var wall = findFattestWallToOpen(thinnest);

            if (wall < 0) {

                // No wall around it may be taken out entire. Left for the stretch pass, which
                // is allowed the finer move, rather than given up on here.
                pile.stopOpeningWholeWalls(thinnest);
                continue;
            }
            openWall(wall);
        }
    }

    // The wall whose removal leaves the widest piece, or -1 where none around this one may go.
    private int findFattestWallToOpen(int piece) {

        var fattest = -1;
        var widest = Double.NEGATIVE_INFINITY;

        for (var wall : neighbours.listWallsAround(piece)) {

            if (!isWallOpenable(wall)) {
                continue;
            }

            var width = pile.measureWidthIfJoined(
                neighbours.listPiecesAlongWall(wall),
                neighbours.measureTotalLength(neighbours.listStandingStretchesOf(wall)));

            if (width > widest) {
                widest = width;
                fattest = wall;
            }
        }
        return fattest;
    }

    /**
     * Whether a wall may be taken out entire.
     *
     * <p>Two refusals, asked of two different things. The open sea beyond it is the index's
     * question - a wall across a bay mouth would not join two pieces, it would give the water
     * away. Land beyond it is this rule's - a bridge that cut into a continent has a continent's
     * inside on one side, and folding water into land is not a merge of pieces of void.
     *
     * <p>Both have to hold along the wall's WHOLE standing length. A wall that is water-to-water
     * for most of its run and land-to-water at one end is still a wall that cannot come out
     * entire, and taking it out on the strength of the good part is how land ends up flooded.
     */
    private boolean isWallOpenable(int wall) {

        if (!neighbours.isWallWhollyBetweenPieces(wall)) {
            return false;
        }

        for (var piece : neighbours.listPiecesAlongWall(wall)) {

            if (!pile.isFoldable(piece)) {
                return false;
            }
        }
        return true;
    }

    // One wall out, and everything it stood between joined.
    private void openWall(int wall) {

        var joined = neighbours.listPiecesAlongWall(wall);
        var length = neighbours.measureTotalLength(neighbours.listStandingStretchesOf(wall));

        for (var edge : neighbours.listStandingStretchesOf(wall)) {
            graph.dropEdge(edge);
        }
        pile.joinPieces(joined, length);
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
    private void foldUntilItPasses(int piece) {

        while (pile.isWanting(piece)) {

            var fattest = findFattestMerge(piece);

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
            var shared = neighbours.listStretchesBetween(piece, fattest);

            for (var edge : shared) {
                graph.dropEdge(edge);
            }
            pile.joinPieces(List.of(piece, fattest), neighbours.measureTotalLength(shared));
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
    private int findFattestMerge(int piece) {

        var fattest = -1;
        var widest = Double.NEGATIVE_INFINITY;

        for (var beside : neighbours.listPiecesBeside(piece)) {

            if (!pile.isFoldable(beside)) {
                continue;
            }

            var shared = neighbours.measureTotalLength(
                neighbours.listStretchesBetween(piece, beside));
            var width = pile.measureWidthIfJoined(List.of(piece, beside), shared);

            if (width > widest) {
                widest = width;
                fattest = beside;
            }
        }
        return fattest;
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

        // A shared wall leaves both boundaries when they join: it was walked once by each.
        private static final int BOTH_SIDES = 2;

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

        // Twice the area over the perimeter. For a long thin shape this is its width, and for a
        // round one its radius - so one number covers a wedge and a bay, in map units a reader
        // can hold against the cell radius.
        //
        // Guarded because a piece with no perimeter has no width to speak of, and a join that
        // cancelled a boundary entirely would otherwise divide by nothing.
        private static double measureMeanWidth(double area, double perimeter) {
            return perimeter <= 0 ? 0 : area * BOTH_SIDES / perimeter;
        }

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

        /**
         * How wide a run of pieces would be as one, without joining them to find out.
         *
         * <p>Any number of them, because both moves come down to the same sum. Two pieces
         * either side of a stretch and five along a whole wall differ only in how many are
         * named and how much wall comes out, and writing the arithmetic twice would put the
         * easy-to-mistake half of it - the perimeter - in two places.
         *
         * @param joining    the pieces, which may name the same one more than once
         * @param wallLength how much wall would be taken out between them
         * @return the mean width of what they would leave
         */
        double measureWidthIfJoined(List<Integer> joining, double wallLength) {

            var area = 0.0;
            var perimeter = 0.0;
            var counted = new LinkedHashSet<Integer>();

            for (var piece : joining) {

                var root = findRoot(piece);

                if (counted.add(root)) {
                    area += areas[root];
                    perimeter += perimeters[root];
                }
            }
            return measureMeanWidth(area, perimeter - wallLength * BOTH_SIDES);
        }

        /**
         * Joins a run of pieces into one.
         *
         * @param joining    the pieces, which may name the same one more than once
         * @param wallLength how much wall came out between them
         */
        void joinPieces(List<Integer> joining, double wallLength) {

            var kept = findRoot(joining.get(0));

            for (var piece : joining) {

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

        void giveUp(int piece) {
            hasGivenUp[findRoot(piece)] = true;
        }
    }

}
