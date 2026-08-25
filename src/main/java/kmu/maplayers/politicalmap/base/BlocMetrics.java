package kmu.maplayers.politicalmap.base;

/**
 * The payload half of a picker row: whatever numbers one political-map layer ranks and labels a bloc
 * by. It asks nothing of them, and that is the whole of it - what a layer's numbers mean is the
 * layer's own business, so all this states is that a {@link RankedBloc}'s payload is some layer's
 * metrics for that bloc rather than an arbitrary value carried through the picker.
 *
 * <p>A metrics type whose layer paints by its numbers opts into {@link PaintingBlocMetrics} on top of
 * this, which is where that question and the reason it is opt-in are stated.
 */
public interface BlocMetrics {
}
