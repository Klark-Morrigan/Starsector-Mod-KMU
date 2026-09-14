package kmu.maplayers.base.geometry.v3;

/**
 * The pair of cells a span joins, in a settled order.
 *
 * <p>What lets two readings of one span be matched up. A span names its two cells one way round
 * and the boundary walk records the wall built from it in whichever order it met them, so a pair
 * only answers "is this the same joining" once the order is taken out of it.
 *
 * <p>Its own type rather than a key each caller mints, because the two questions asked of it are
 * asked in different places and have to agree: which piece of water a wall closed, and whether
 * two cells are joined already. A key built twice is two answers waiting to differ over which
 * cell was named first.
 *
 * @param lower  the lower-numbered of the two cells
 * @param higher the other
 */
record SpannedCells(
    int lower,
    int higher) {

    static SpannedCells buildFromCells(int one, int other) {

        return new SpannedCells(Math.min(one, other), Math.max(one, other));
    }
}
