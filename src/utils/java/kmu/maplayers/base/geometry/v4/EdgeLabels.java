package kmu.maplayers.base.geometry.v4;

/**
 * What the number on a piece's edge means.
 *
 * <p>One convention, said once. An edge of a piece lies either along a cell's border or along a
 * line some tier laid, and the label says which: a cell is its own index, so zero and up is a
 * cell and nothing else can be; every laid line takes a negative of its own, so a reader can
 * tell the edge of the sector from a coast from a span.
 *
 * <p>The negatives in use, each declared where the thing it names is built:
 *
 * <ul>
 *   <li>{@link BareVoid#THE_FRAME} - the edge of the sector, which is nobody's.</li>
 * </ul>
 *
 * <p>KMLib's {@code VoronoiCellBuilder.BOUND_EDGE} is another negative and deliberately not one
 * of these: it is what a CELL's own edge carries where nothing lies across it, which is the
 * input this reading is built from rather than a label any piece comes back with.
 *
 * <p>Held here rather than asked inline, because <b>"not the frame" is the wrong test and reads
 * as the right one</b>. It was the right one while the frame was the only line laid, and the
 * first tier to lay a second turns every one of those into a cell lookup at a negative index.
 */
public final class EdgeLabels {

    private EdgeLabels() {
    }

    /**
     * Whether this edge runs along a cell's border.
     *
     * @param label the edge's label
     * @return true where it names a cell, false where it names a line some tier laid
     */
    public static boolean isCell(int label) {
        return label >= 0;
    }
}
