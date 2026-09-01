package kmu.maplayers.politicalmap.base;

/**
 * The payload half of a picker row: whatever numbers one political-map layer ranks and labels a bloc
 * by. It asks nothing of them, and that is the whole of it - what a layer's numbers mean is the
 * layer's own business, so all this states is that a {@link RankedBloc}'s payload is some layer's
 * metrics for that bloc rather than an arbitrary value carried through the picker.
 *
 * <p>Whatever a record does carry beyond that, it says by opting into a capability on top of this
 * rather than by answering a method declared here: {@link PaintingBlocMetrics} for how a bloc stands
 * on the metric its layer paints by, {@link SizedBlocMetrics} for its whole-sector colony size. A
 * default here would hand a record whose fold never computed the number an inherited answer, and an
 * inherited answer to a question nobody asked reads exactly like a measured one. Opting in is what
 * leaves a record with nothing to say unable to be asked, and each capability states what that buys
 * in its own case.
 */
public interface BlocMetrics {
}
