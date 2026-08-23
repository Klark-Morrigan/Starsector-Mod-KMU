package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Which pieces each wall stands between, and which walls stand around each piece.
 *
 * <p>Read off the walk rather than worked out again. Each direction of a run was walked by
 * exactly one face, so the two directions of one run name the two faces either side of it - the
 * whole adjacency is already there, and asking the geometry a second time would be a second
 * answer that could differ from the first.
 *
 * <p><b>An index, not a rule.</b> Nothing here decides what to take out or which neighbour is
 * worth joining; it answers where things are and leaves the judging to whatever is doing the
 * judging. What it does need is one thing a bare graph cannot tell it: as pieces are joined,
 * "which piece is this now part of" stops being the index the walk gave it. That comes in as a
 * lookup rather than as a dependency on whoever is keeping the joins, so this stays an index
 * over the graph and does not acquire an opinion.
 *
 * <p>A stretch naming only one piece has the open sea on its other side. This reports such a
 * wall as being around a piece, because it is; whether it may be OPENED is a different question
 * and belongs to the rule.
 */
public final class PieceNeighbours {

    // The direction of a stretch that no piece walked, which is a direction the open sea
    // walked. Its own name because it comes back from an array of piece indexes, where a bare
    // -1 reads as an error rather than as the sea.
    private static final int THE_OPEN_SEA = -1;

    private final PlanarGraph graph;

    // Which piece walked each direction, or THE_OPEN_SEA.
    private final int[] pieceOfEdge;

    // Which piece a piece has since been joined into, asked afresh every time because the
    // answer changes as the folding goes on.
    private final IntUnaryOperator findRoot;

    // Every removable stretch that stood between two different pieces, by one direction.
    private final List<Integer> joins = new ArrayList<>();

    // Which stretches each whole wall was cut into, by one direction each. What lets a wall be
    // taken out entire rather than a stretch at a time.
    private final Map<Integer, List<Integer>> stretchesOfWall = new LinkedHashMap<>();

    /**
     * Indexes the pieces a walk found against the graph they came from.
     *
     * @param graph    the walls
     * @param pieces   the faces the walk found, in the order it found them
     * @param findRoot which piece a piece has been joined into, by index
     */
    PieceNeighbours(
            PlanarGraph graph,
            List<PlanarGraph.WalkedFace> pieces,
            IntUnaryOperator findRoot) {

        this.graph = graph;
        this.findRoot = findRoot;

        // Sized from the graph rather than from the highest edge the pieces mention. The open
        // sea walks edges too, and those are exactly the ones no piece names - so a table sized
        // to fit the pieces is a table that stops short of the edges it is then asked about.
        this.pieceOfEdge = new int[graph.countEdges()];

        Arrays.fill(pieceOfEdge, THE_OPEN_SEA);

        for (var piece = 0; piece < pieces.size(); piece++) {
            for (var edge : pieces.get(piece).edges()) {

                pieceOfEdge[edge] = piece;
            }
        }

        // Every other index, so each stretch is considered once rather than once per direction.
        for (var edge = 0; edge < pieceOfEdge.length; edge += 2) {

            if (!graph.isRemovableEdge(edge)) {
                continue;
            }
            stretchesOfWall
                .computeIfAbsent(graph.readGroupOfEdge(edge), wall -> new ArrayList<>())
                .add(edge);

            if (pieceOfEdge[edge] != THE_OPEN_SEA
                    && pieceOfEdge[edge ^ 1] != THE_OPEN_SEA
                    && pieceOfEdge[edge] != pieceOfEdge[edge ^ 1]) {

                joins.add(edge);
            }
        }
    }

    /**
     * How many whole walls there are to take out.
     *
     * @return the count, which bounds any pass that takes one out at a time
     */
    int countWalls() {
        return stretchesOfWall.size();
    }

    /**
     * Every piece a piece currently shares an openable stretch with, once each.
     *
     * <p>Once each because a neighbour met along several stretches is one neighbour, and
     * offering it once per stretch would have it scored - and possibly joined - several times
     * over.
     *
     * @param piece the piece to look around
     * @return its neighbours, by root
     */
    List<Integer> listPiecesBeside(int piece) {

        var beside = new LinkedHashSet<Integer>();
        var root = findRoot.applyAsInt(piece);

        for (var edge : joins) {

            var one = findRoot.applyAsInt(pieceOfEdge[edge]);
            var other = findRoot.applyAsInt(pieceOfEdge[edge ^ 1]);

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

    /**
     * Every stretch standing between two pieces, as they now stand.
     *
     * @param one   one piece
     * @param other the other
     * @return the stretches, one direction each
     */
    List<Integer> listStretchesBetween(int one, int other) {

        var between = new ArrayList<Integer>();
        var keptRoot = findRoot.applyAsInt(one);
        var foldedRoot = findRoot.applyAsInt(other);

        for (var edge : joins) {

            var sideOne = findRoot.applyAsInt(pieceOfEdge[edge]);
            var sideOther = findRoot.applyAsInt(pieceOfEdge[edge ^ 1]);

            if (sideOne == keptRoot && sideOther == foldedRoot
                    || sideOne == foldedRoot && sideOther == keptRoot) {

                between.add(edge);
            }
        }
        return between;
    }

    /**
     * Every whole wall with a standing stretch bounding a piece, once each.
     *
     * @param piece the piece to look around
     * @return the walls
     */
    List<Integer> listWallsAround(int piece) {

        var around = new LinkedHashSet<Integer>();
        var root = findRoot.applyAsInt(piece);

        for (var wall : stretchesOfWall.keySet()) {
            for (var edge : listStandingStretchesOf(wall)) {

                if (isRootOf(pieceOfEdge[edge], root) || isRootOf(pieceOfEdge[edge ^ 1], root)) {

                    around.add(wall);
                    break;
                }
            }
        }
        return new ArrayList<>(around);
    }

    /**
     * Whether a wall has a piece rather than the open sea on both sides, along its whole
     * standing length.
     *
     * <p>What this refuses is a wall that is partly the edge of the construction - a bridge
     * across a bay mouth has the open sea beyond it. Taking that out entire would not join two
     * pieces; it would give the water away, which is a different act.
     *
     * @param wall the wall
     * @return whether every standing stretch of it stands between two pieces
     */
    boolean isWallWhollyBetweenPieces(int wall) {

        var standing = listStandingStretchesOf(wall);

        if (standing.isEmpty()) {
            return false;
        }

        for (var edge : standing) {

            if (pieceOfEdge[edge] == THE_OPEN_SEA || pieceOfEdge[edge ^ 1] == THE_OPEN_SEA) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every piece a wall currently stands between, once each.
     *
     * @param wall the wall
     * @return the pieces, as the walk numbered them
     */
    List<Integer> listPiecesAlongWall(int wall) {

        var along = new LinkedHashSet<Integer>();

        for (var edge : listStandingStretchesOf(wall)) {

            along.add(pieceOfEdge[edge]);
            along.add(pieceOfEdge[edge ^ 1]);
        }
        return new ArrayList<>(along);
    }

    /**
     * The stretches of a wall that have not already been taken out.
     *
     * @param wall the wall
     * @return the stretches, one direction each
     */
    List<Integer> listStandingStretchesOf(int wall) {

        var standing = new ArrayList<Integer>();

        for (var edge : stretchesOfWall.getOrDefault(wall, List.of())) {

            if (!graph.isDroppedEdge(edge)) {
                standing.add(edge);
            }
        }
        return standing;
    }

    /**
     * How much wall a set of stretches comes to.
     *
     * @param stretches the stretches
     * @return their total length
     */
    double measureTotalLength(Collection<Integer> stretches) {

        var total = 0.0;

        for (var edge : stretches) {
            total += graph.measureEdgeLength(edge);
        }
        return total;
    }

    private boolean isRootOf(int piece, int root) {
        return piece != THE_OPEN_SEA && findRoot.applyAsInt(piece) == root;
    }
}
