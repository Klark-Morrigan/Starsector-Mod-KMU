package kmu.maplayers.politicalmap.base;

/**
 * A metrics payload no political-map view declares, standing in for whatever a layer painted by
 * another mechanic ranks its blocs by. It shares nothing with the dominance or claim metrics but the
 * bound every option's payload satisfies, so a case run against it passes only while the picker side
 * leaves that payload type open past the bound.
 *
 * <p>It states no painting rule, which is the second thing it stands in for: a picker whose rows are
 * not painters - one choosing what the map is measured against - has no bloc that paints nothing, so
 * it opts out of {@link PaintingBlocMetrics} and its rows draw plain.
 *
 * <p>Shared by the suites either side of that seam - the option assembly and the option itself -
 * since both need a payload from outside the two real vocabularies and one of them is enough.
 *
 * @param severity an arbitrary number, carried only to make this a payload with something in it
 */
record HazardRating(
    int severity) implements BlocMetrics {
}
