package kmu.maplayers.base.geometry;

/**
 * One square of a flood's grid as a single number, for the set or map a flood keeps its
 * squares in.
 *
 * <p>Packed rather than boxed as a pair: a flood asks about a square millions of times, and a
 * key object per ask costs more than the lookup it serves. The column takes the high half and
 * the row the low, so no two squares share a key however far from the origin they lie, as long
 * as each index fits an int - and a sector is a few hundred thousand units across.
 */
public final class SquareKey {

    private SquareKey() {
    }

    /**
     * Packs a square's column and row into one key.
     *
     * @param column the square's column, quantised however the caller's grid quantises
     * @param row    the square's row, the same way
     * @return the key, distinct for every distinct column and row
     */
    public static long packSquareKey(int column, int row) {
        return ((long) column << Integer.SIZE) | Integer.toUnsignedLong(row);
    }
}
