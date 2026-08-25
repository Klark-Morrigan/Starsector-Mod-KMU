package kmu.maplayers.politicalmap.base;

/**
 * The payload half of a picker row: whatever numbers one political-map layer ranks and labels a bloc
 * by. It asks nothing of them, and that is the whole of it - what a layer's numbers mean is the
 * layer's own business, so all this states is that a {@link RankedBloc}'s payload is some layer's
 * metrics for that bloc rather than an arbitrary value carried through the picker.
 *
 * <p>A metrics type whose layer paints by its numbers opts into {@link PaintingBlocMetrics} on top of
 * this. That question is segregated rather than defaulted here because not every picker lists
 * painters: a picker choosing what the map is measured <em>against</em> has no bloc that paints
 * nothing, so a default would hand it an inherited answer to a question it cannot be asked.
 */
public interface BlocMetrics {
}
