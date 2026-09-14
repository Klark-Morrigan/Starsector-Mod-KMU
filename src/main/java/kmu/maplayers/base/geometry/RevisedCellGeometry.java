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
 * <p>One obligation falls on whoever produces these, and it is the whole of the contract: the
 * revision must change whenever the cells are recut, for <em>any</em> reason. A producer that
 * echoes some upstream signal instead satisfies this only while that signal is the sole thing
 * able to recut them - which is a property of the producer's own inputs, not of the signal, and
 * is exactly the assumption that quietly stops holding when a second input is added. A number
 * counted per recut carries no such assumption. Nothing here can check it, so it is stated:
 * a revision that stands still through a recut is indistinguishable, to every consumer, from
 * cells that never moved.
 *
 * @param cells    the cells themselves, updated in place as the drawn set changes
 * @param revision names the cut of the cells currently held; changes on every recut
 */
public record RevisedCellGeometry(
    CellGeometryCache cells,
    int revision) {

    /**
     * The same cells, now naming a different cut.
     *
     * <p>The cells are updated in place, so a recut leaves the very same object holding
     * different geometry and only the revision beside them has moved. Naming that as one
     * operation is what keeps "these cells are now at revision N" from being written out as a
     * fresh pair each time, where the cells could quietly be swapped for another set.
     *
     * @param cutRevision the revision naming the cut the cells now hold
     * @return a copy naming that revision in place of this one's
     */
    public RevisedCellGeometry copyWithRevision(int cutRevision) {
        return new RevisedCellGeometry(cells, cutRevision);
    }
}
