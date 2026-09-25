package kmu.maplayers.ownermap.picker;

/**
 * A second vocabulary's numbers, for a case proving one shared sort mode binds into more than one.
 *
 * <p>Deliberately a different shape from {@link BlocMetricsFake}: a layer ranking by a single
 * count and a size, against one ranking by several numbers. Two types rather than one used twice,
 * because what a shared mode has to survive is being bound over vocabularies that agree on nothing
 * but the number it is about.
 *
 * @param count      the one number this vocabulary leads with
 * @param marketSize the bloc's summed colony size, which every vocabulary carries
 */
public record CountedBlocMetricsFake(
    int count,
    int marketSize)
        implements SizedBlocMetrics {
}
