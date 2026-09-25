package kmu.maplayers.ownermap.picker;

/**
 * A metrics payload no owner-painted view declares, standing in for whatever a layer painted by
 * another mechanic ranks its blocs by. It shares nothing with any layer's own metrics but the
 * bound every option's payload satisfies, so a case run against it passes only while the picker side
 * leaves that payload type open past the bound.
 *
 * <p>It states no painting rule, which is the second thing it stands in for: a picker whose rows are
 * not painters - one choosing what the map is measured against - has no bloc that paints nothing, so
 * it opts out of {@link PaintingBlocMetrics} and its rows draw plain.
 *
 * <p>Shared by the suites either side of that seam - the option assembly and the option itself - and
 * by the one over the shared sort plumbing, since all of them need a payload from outside the two
 * real vocabularies and one of them is enough.
 *
 * <p>Two numbers rather than one, because a vocabulary's tie-break chain is only a chain with a
 * second number behind the first: one is what a mode promotes, the other is what a tie falls to.
 *
 * @param severity   an arbitrary number, carried only to make this a payload with something in it
 * @param volatility a second arbitrary number, so a chain can be declared over this payload
 */
public record HazardRating(
    int severity,
    int volatility) implements BlocMetrics {
}
