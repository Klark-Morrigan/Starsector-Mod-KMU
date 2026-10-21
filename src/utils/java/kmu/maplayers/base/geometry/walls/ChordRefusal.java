package kmu.maplayers.base.geometry.walls;

/**
 * What became of one wall the walk was offered.
 *
 * @param reason what the walk did with it
 * @param detail which cell or which rival wall made that the answer, or null where the
 *               reason says the whole of it
 */
public record ChordRefusal(
    RefusalReason reason,
    String detail) {

    @Override
    public String toString() {

        return detail == null
            ? reason.describe()
            : reason.describe() + " " + detail;
    }
}
