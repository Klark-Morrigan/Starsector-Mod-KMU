package kmu.maplayers.base.geometry.walls;

/**
 * The names the boundary's joins go by: the crossing between two discs, and each end of a
 * wall.
 *
 * <p>The arcs link by these names rather than by any coordinate. An arc ends where its circle
 * enters some neighbour's disc, and the boundary continues on that neighbour from the point
 * where the neighbour leaves the first disc - a point named by the pair of circles, so two
 * circles computing it separately and landing a rounding apart still agree. A wall's two ends
 * are named the same way, by the wall and the side.
 *
 * <p>Its own class because the sweep writes these names, the walk joins by them, and the
 * diagnostics read them back, and all three have to agree on one encoding.
 */
final class BoundaryTerminals {

    // Stands in for a neighbouring circle where there is none: a disc that overlaps nothing
    // contributes its whole circle as one arc, with no disc entered or left at either end.
    static final int NO_CIRCLE = -1;

    // No arc begins here, so an arc pointed at it simply has no successor. Beyond the range
    // either kind of real terminal can reach, rather than a value one of them might produce.
    static final long NO_TERMINAL = Long.MIN_VALUE;

    // Which of a chord's two circles a terminal belongs to. A chord meets each of its circles
    // once, so the pair of side markers names its two mouths and nothing else does.
    static final int FROM_SIDE = 0;
    static final int TO_SIDE = 1;

    private BoundaryTerminals() {
    }

    // Offset by one so the "no neighbour" marker cannot land on the name of a real pairing,
    // and kept positive so a chord's terminal can never be mistaken for a crossing.
    static long formatDiscTerminal(int circle, int neighbour, int siteCount) {
        return (long) circle * (siteCount + 1) + neighbour + 1;
    }

    // Negative, so a wall's terminal can never collide with a crossing between two discs -
    // those are the positive half of the same space. Offset by one because a wall at index
    // zero would otherwise name terminal zero, which is neither negative nor free.
    static long formatChordTerminal(int chord, int side) {
        return -(2L * chord + side + 1);
    }

    // Which half of the terminal space a name came from. The sweep needs it to tell a mouth
    // from a disc's cover: two mouths that overlap hand the boundary from one wall to the
    // next, while a stretch a disc swallows is off the boundary and joins nothing.
    static boolean isChordTerminal(long terminal) {
        return terminal < 0 && terminal != NO_TERMINAL;
    }

    // Which wall a chord terminal names, undoing formatChordTerminal. The side marker is the
    // low bit, so the wall is what is left once it is taken off.
    static int readChordFrom(long terminal) {
        return (int) ((-terminal - 1) / 2);
    }
}
