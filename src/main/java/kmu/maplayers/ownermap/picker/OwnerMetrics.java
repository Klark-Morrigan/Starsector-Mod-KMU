package kmu.maplayers.ownermap.picker;

/**
 * The payload half of a picker row: whatever numbers one owner-painted layer ranks and labels an owner
 * by. It asks nothing of them, and that is the whole of it - what a layer's numbers mean is the
 * layer's own business, so all this states is that a {@link RankedOwner}'s payload is some layer's
 * metrics for that owner rather than an arbitrary value carried through the picker.
 *
 * <p>Whatever a record does carry beyond that, it says by opting into a capability on top of this
 * rather than by answering a method declared here: {@link PaintingOwnerMetrics} for how an owner
 * stands on the metric its layer paints by, or a capability a layer declares beside its own owners
 * for a number only they carry. A default here would hand a record whose fold never computed the
 * number an inherited answer, and an inherited answer to a question nobody asked reads exactly like a
 * measured one. Opting in is what leaves a record with nothing to say unable to be asked, and each
 * capability states what that buys in its own case.
 */
public interface OwnerMetrics {
}
