package kmu.maplayers.base.geometry;

/**
 * A map layer's cells together with the geometry revision they stand at.
 *
 * <p>The revision is no input of its own: it names these very cells. Work derived from them and
 * kept across rebuilds - the label placements above all - holds only while the cells behind it
 * are the ones it was derived from, and the revision is how a later pass asks that without
 * having kept the cells to compare. Travelling as two arguments, the pair can be handed over
 * disagreeing, and that disagreement reads as permission rather than as a fault: derived work
 * fitted inside a partition that has since been recut, offered to a rebuild with no way to see
 * it.
 *
 * <p>The cells are the live cache rather than a copy, so a consumer reads whatever they hold
 * now; what is fixed here is which revision that reading answers to. Pairing them makes
 * advancing one without the other something a caller has to write out rather than something it
 * can forget.
 *
 * @param cells    the cells themselves, updated in place as the drawn set changes
 * @param revision the geometry revision those cells were last cut at
 */
public record RevisedCellGeometry(
    CellGeometryCache cells,
    int revision) {
}
