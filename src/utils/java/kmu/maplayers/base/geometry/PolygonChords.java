package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;

import java.util.ArrayList;
import java.util.List;

/**
 * Cutting a closed outline by chords that do not cross each other.
 *
 * <p>Its own class because deciding WHERE a pocket should be cut and carrying out the cuts
 * are separate problems, and only the first is about void at all. What is here knows nothing
 * of cells or reaches - it takes an outline and a run of chords and hands back the pieces.
 *
 * <p>It cuts a sampled outline, so a chord end has to be snapped to the nearest vertex. That
 * is bearable when the chord was chosen against that same outline, as a pocket's section cuts
 * are; it is not a way to lay a chord that was worked out independently, because the snap can
 * move the cut by up to a sample. Boundary a shape is DEFINED by belongs in the trace that
 * builds it - see {@link DiscUnionBoundary.Chord}.
 *
 * <p>All the chords go on together, which is the whole point of carrying the pieces rather
 * than cutting the original outline once per chord. A chord cut in ignorance of the others
 * can leave a piece that wholly contains another's, and two such pieces are not a division of
 * anything - they are the same space claimed twice.
 *
 * <p>A chord's ends replace the outline vertices they land nearest rather than being inserted
 * beside them, so each piece closes exactly ON the chord. Leaving the closing edge to run
 * between the nearest sampled vertices instead puts it up to a whole arc sample away from
 * where the chord actually is.
 */
final class PolygonChords {

    // How far a chord end may sit from a vertex and still be taken as landing on it, as a
    // multiple of how far apart that outline's own vertices are. Scaled to the outline
    // because how finely it was sampled is a knob.
    private static final double SNAP_VERTEX_SPACINGS = 1.5;

    private PolygonChords() {
    }

    /**
     * Where a cut comes down on the pocket: which piece it falls on, and which vertices of
     * that piece's outline its two ends land nearest.
     *
     * <p>The outline and its two vertices are carried rather than found again when the piece
     * is split, because they are what decides both questions - whether the cut can be taken
     * at all, and where the outline is opened to take it. Working them out twice would let
     * the two answers disagree, and the split would then be made somewhere the cut was never
     * checked. Carrying the outline itself alongside its index does the same for the piece:
     * handed both separately, a caller can pass an outline that is not the one the index
     * names, and the split lands on a piece nothing was ever measured against.
     *
     * @param piece   which piece of the pocket the cut falls on, for replacing it with the
     *                two the cut leaves
     * @param outline that piece's own outline, which the cut is measured and opened against
     * @param atStart the vertex of {@code outline} its {@code start} end landed nearest
     * @param atEnd   the vertex its {@code end} end landed nearest
     */
    record Landing(
        int piece,
        List<double[]> outline,
        int atStart,
        int atEnd) {
    }

    // Where on the pocket a cut comes down, or null when its two ends come down on two
    // different pieces - which is what a cut crossing one already taken looks like.
    static Landing findLanding(
            List<List<double[]>> pieces,
            double[] start,
            double[] end,
            double snapDistance) {

        for (var index = 0; index < pieces.size(); index++) {

            var piece = pieces.get(index);
            var atStart = findNearestVertex(piece, start);
            var atEnd = findNearestVertex(piece, end);

            if (atStart != atEnd
                    && measureDistance(piece.get(atStart), start) <= snapDistance
                    && measureDistance(piece.get(atEnd), end) <= snapDistance) {

                return new Landing(index, piece, atStart, atEnd);
            }
        }
        return null;
    }

    // The two pieces a cut leaves. Its ends replace the outline vertices they landed nearest,
    // rather than being inserted beside them, so each half closes exactly on the cut and the
    // two halves meet along it with nothing between them.
    static List<List<double[]>> splitPiece(Landing landing, double[] start, double[] end) {

        var piece = landing.outline();
        var first = Math.min(landing.atStart(), landing.atEnd());
        var second = Math.max(landing.atStart(), landing.atEnd());

        var atFirst = first == landing.atStart() ? start : end;
        var atSecond = first == landing.atStart() ? end : start;

        var near = new ArrayList<double[]>();
        near.add(atFirst);
        near.addAll(piece.subList(first + 1, second));
        near.add(atSecond);

        var far = new ArrayList<double[]>();
        far.add(atSecond);
        far.addAll(piece.subList(second + 1, piece.size()));
        far.addAll(piece.subList(0, first));
        far.add(atFirst);

        return near.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA
                || far.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA
            ? List.of()
            : List.of(near, far);
    }

    /**
     * How far apart an outline's vertices are at their widest, which sets how near a chord
     * end has to be to count as landing on one.
     *
     * @param outline the outline
     * @return the distance to snap within
     */
    static double measureSnapDistance(List<double[]> outline) {
        return measureLongestVertexGap(outline) * SNAP_VERTEX_SPACINGS;
    }

    private static double measureLongestVertexGap(List<double[]> outline) {

        var longest = 0.0;

        for (var index = 0; index < outline.size(); index++) {

            var here = outline.get(index);
            var next = outline.get((index + 1) % outline.size());

            longest = Math.max(longest, Math.hypot(next[0] - here[0], next[1] - here[1]));
        }
        return longest;
    }

    private static int findNearestVertex(List<double[]> piece, double[] point) {

        var nearest = 0;
        var least = Double.MAX_VALUE;

        for (var index = 0; index < piece.size(); index++) {

            var vertex = piece.get(index);
            var distance = Math.hypot(point[0] - vertex[0], point[1] - vertex[1]);

            if (distance < least) {
                least = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    private static double measureDistance(double[] from, double[] to) {
        return Math.hypot(to[0] - from[0], to[1] - from[1]);
    }
}
